package claimalgebra.society

import cats.effect.IO
import cats.effect.std.Supervisor
import cats.syntax.all.*
import claimalgebra.BlockReason

import scala.concurrent.duration.FiniteDuration

/** The per-game knobs. `maxRounds` bounds the game (a give-up after the budget is
  * [[Outcome.Inconclusive]], a safe non-signature); `roundTimeout` is how long a round waits for
  * its cohort before closing complete-with-attrition (→ Abstain, never Sign); `hardDeadline` is a
  * whole-game safety timeout the wiring bounds `play` with, so a wedged run fails loudly rather
  * than hanging.
  */
final case class SocietyConfig(
    maxRounds: Int,
    roundTimeout: FiniteDuration,
    hardDeadline: FiniteDuration
)

/** Where the ordered event log is emitted — stdout for a live run now, an SSE transport in slice 3,
  * a collecting `Ref` in tests. Emit-per-event, in the single-writer's serialization order.
  */
trait EventSink:
  def emit(event: Event): IO[Unit]

object EventSink:
  val stdout: EventSink = event => IO.println(event.toString)

/** The fire-and-forget effect seam the LogActor needs beyond message passing: [[submit]]
  * backgrounds the oracle round-trip (so `receive` stays non-blocking, and the reply arrives as a
  * message), and [[arm]] schedules the round timeout as a delayed self-send. Both are owned by a
  * `Supervisor`, so no fiber outlives the game (scala-concurrency.md). Injected so tests drive
  * timing DETERMINISTICALLY — never a real sleep — by disarming the timeout (cohort-driven closure)
  * or firing it eagerly (attrition).
  */
trait Scheduler:
  def submit(action: IO[Unit]): IO[Unit]
  def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit]

object Scheduler:
  /** The production scheduler: the oracle call and the delayed timeout are supervised fibers. */
  def supervised(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] =
      supervisor.supervise(IO.sleep(delay) *> action).void

/** The LogActor's fixed collaborators — everything but the evolving game [[LogState]]. */
final case class LogDeps(
    oracle: Oracle,
    scheduler: Scheduler,
    sink: EventSink,
    now: IO[Long],
    onDone: Outcome => IO[Unit],
    config: SocietyConfig
)

/** One round's barrier — the DISTINCT current-round agents probed (`cohort`), who has reported
  * (`reported`), and whether the round is closed. The round completes ONLY when every cohort member
  * reported ([[isComplete]]); an idempotent [[markClosed]] guard makes the timeout path and the
  * Nth-post path exclusive. Counting distinct agents, not messages, is what stops a duplicate or a
  * stale post from over-counting (actor-abstraction §9).
  */
final case class Barrier(
    round: RoundId,
    cohort: Set[AgentId],
    reported: Set[AgentId],
    closed: Boolean
):
  def isComplete: Boolean = cohort.nonEmpty && cohort.subsetOf(reported)
  def record(agent: AgentId): Barrier =
    if closed || !cohort.contains(agent) then this else copy(reported = reported + agent)
  def markClosed: Barrier = copy(closed = true)

/** The evolving game state the LogActor carries via `become` — never a `var` (the
  * replacement-behavior encoding, §4). `agents` is registered at [[ToLog.Begin]]; `roundsUsed`
  * bounds the game.
  */
final case class LogState(
    log: Vector[Event],
    round: RoundId,
    barrier: Barrier,
    roundsUsed: Int,
    phase: Phase,
    agents: List[(AgentId, ActorRef[ToAgent])]
)

object LogState:
  val initial: LogState =
    LogState(
      Vector.empty,
      RoundId.first,
      Barrier(RoundId.first, Set.empty, Set.empty, closed = true),
      0,
      Phase.Playing,
      Nil
    )

/** The single-writer LogActor (actor-abstraction §7–§9): its mailbox is the global serialization
  * point (Agha's arbitration — one message at a time, so `seq = log.size + 1` is race-free), it
  * owns the event log in `become`-state, folds belief via [[GameCore]], and drives the round loop.
  *
  * The forward-carry safety spine (the adversarial-verify findings, §9):
  *   - EVERY sign routes through [[GameCore.nextMove]] (round-completeness-gated) at exactly one
  *     site ([[closeRound]]); `decide` is consulted only to RENDER an abstain reason, never to
  *     sign.
  *   - A timed-out round closes with `roundComplete = false`, so [[GameCore.nextMove]] Abstains —
  *     round attrition can never drive a signature.
  *   - An oracle answer ([[ToLog.Answered]]) opens a NEW round with a fresh, incomplete barrier, so
  *     the gate cannot sign a candidate a fresh human answer contradicts until the agents have
  *     re-reported (and emitted any `Refute`, which gluts → Conflict → blocked). This is the
  *     structural realization of the Oracle→Refute guard.
  */
final class LogActor(
    context: ActorContext[ToLog],
    deps: LogDeps,
    state: LogState
) extends Actor[ToLog](context):

  def receive(message: ToLog): IO[Actor[ToLog]] = message match
    case ToLog.Begin(agents) => onBegin(agents).map(withState)
    case ToLog.Post(round, agent, move) => onPost(round, agent, move).map(withState)
    case ToLog.RoundTimeout(round) => onTimeout(round).map(withState)
    case ToLog.Answered(qid, ans) => onAnswered(qid, ans).map(withState)

  private def withState(s: LogState): LogActor = new LogActor(context, deps, s)

  // --- message handlers ---

  private def onBegin(agents: List[(AgentId, ActorRef[ToAgent])]): IO[LogState] =
    if agents.isEmpty then endInconclusive(state)
    else open(state.copy(agents = agents), RoundId.first)

  private def onPost(round: RoundId, agent: AgentId, move: AgentMove): IO[LogState] =
    if state.phase == Phase.Ended then IO.pure(state)
    else
      appendMove(state.log, agent, move).flatMap { log2 =>
        val s2 = state.copy(log = log2)
        if round == s2.round && !s2.barrier.closed && s2.barrier.cohort.contains(agent) then
          val advanced = s2.copy(barrier = s2.barrier.record(agent))
          if advanced.barrier.isComplete then closeRound(advanced, roundComplete = true)
          else IO.pure(advanced)
        else IO.pure(s2) // stale / late / foreign post: appended for replay, no barrier effect
      }

  private def onTimeout(round: RoundId): IO[LogState] =
    if state.phase != Phase.Ended && round == state.round && !state.barrier.closed then
      closeRound(state, roundComplete = false)
    else IO.pure(state) // idempotent: a round already closed (or a stale timeout) is a no-op

  private def onAnswered(qid: QuestionId, ans: OracleAnswer): IO[LogState] =
    if state.phase == Phase.Ended then IO.pure(state)
    else
      appendEmit(state.log)((seq, ts) => Event.AnswerGiven(seq, ts, qid, ans)).flatMap {
        case (log2, _) =>
          open(
            state.copy(log = log2),
            state.round.next
          ) // the answer opens a NEW round (the barrier)
      }

  // --- the round loop ---

  private def open(s: LogState, roundId: RoundId): IO[LogState] =
    val cohort = s.agents.map((id, _) => id).toSet
    if cohort.isEmpty then endInconclusive(s)
    else
      val view = GameView.from(s.log)
      val opened = s.copy(
        round = roundId,
        roundsUsed = s.roundsUsed + 1,
        barrier = Barrier(roundId, cohort, Set.empty, closed = false)
      )
      probeAll(opened.agents, roundId, view) *> armTimeout(roundId).as(opened)

  private def closeRound(s: LogState, roundComplete: Boolean): IO[LogState] =
    val closed = s.copy(barrier = s.barrier.markClosed)
    GameCore.nextMove(closed.log, closed.log.size, roundComplete) match
      case Move.Sign(winner) => sign(closed, winner)
      case Move.Abstain =>
        val reason = abstainReason(closed.log, roundComplete)
        appendEmit(closed.log)((seq, ts) => Event.GateAbstain(seq, ts, reason)).flatMap {
          case (log2, _) =>
            advance(closed.copy(log = log2))
        }

  private def advance(s: LogState): IO[LogState] =
    if s.roundsUsed >= deps.config.maxRounds then endInconclusive(s)
    else askQuestion(s)

  private def askQuestion(s: LogState): IO[LogState] =
    pendingQuestion(s.log) match
      case None => endInconclusive(s) // no unasked question proposed → nothing to advance on
      case Some(pending) =>
        appendEmit(s.log)((seq, ts) =>
          Event.QuestionAsked(seq, ts, pending.asker, pending.id, pending.text)
        )
          .flatMap { case (log2, _) =>
            // An oracle FAULT (e.g. a stdin IOException) degrades to a belief-inert Unknown that
            // still opens the next round, rather than wedging the loop until the hard deadline.
            val reply = deps.oracle
              .answer(Question(pending.id, pending.text))
              .handleError(_ => OracleAnswer.Unknown)
              .flatMap { ans =>
                MessageId.from(s"answered-${pending.id.value}") match
                  case Right(mid) => context.self.tell(mid, ToLog.Answered(pending.id, ans))
                  case Left(_) => IO.unit
              }
            deps.scheduler.submit(reply).as(s.copy(log = log2))
          }

  private def sign(s: LogState, winner: Answer): IO[LogState] =
    appendEmit(s.log)((seq, ts) => Event.GateSign(seq, ts, winner)).flatMap { case (log2, _) =>
      deps.onDone(Outcome.Signed(winner)).as(s.copy(log = log2, phase = Phase.Ended))
    }

  private def endInconclusive(s: LogState): IO[LogState] =
    appendEmit(s.log)((seq, ts) =>
      Event.GateAbstain(seq, ts, "inconclusive — no signature within the round budget")
    )
      .flatMap { case (log2, _) =>
        deps.onDone(Outcome.Inconclusive).as(s.copy(log = log2, phase = Phase.Ended))
      }

  // --- effects ---

  private def probeAll(
      agents: List[(AgentId, ActorRef[ToAgent])],
      round: RoundId,
      view: GameView
  ): IO[Unit] =
    agents.traverse_ { case (id, ref) =>
      MessageId.from(s"probe-r${round.value}-${id.value}") match
        case Right(mid) => send(ref, mid, ToAgent.Probe(round, view))
        case Left(_) => IO.unit
    }

  private def armTimeout(round: RoundId): IO[Unit] =
    MessageId.from(s"timeout-r${round.value}") match
      case Right(mid) =>
        deps.scheduler.arm(deps.config.roundTimeout)(
          context.self.tell(mid, ToLog.RoundTimeout(round))
        )
      case Left(_) => IO.unit

  /** Append an event under the next `seq` and the display timestamp, emit it, return the grown log.
    */
  private def appendEmit(log: Vector[Event])(mk: (Int, Long) => Event): IO[(Vector[Event], Event)] =
    deps.now.flatMap { ts =>
      val event = mk(log.size + 1, ts)
      deps.sink.emit(event).as((log :+ event, event))
    }

  /** Append (and emit) the move's event, if any. `Pass` projects to nothing — a reported non-move.
    */
  private def appendMove(log: Vector[Event], agent: AgentId, move: AgentMove): IO[Vector[Event]] =
    AgentMove.event(agent, move, log.size + 1) match
      case None => IO.pure(log)
      case Some(mk) =>
        deps.now.flatMap { ts =>
          val event = mk(ts)
          deps.sink.emit(event).as(log :+ event)
        }

  // --- pure reads ---

  private def pendingQuestion(log: Vector[Event]): Option[LogActor.Pending] =
    val asked: Set[String] =
      log.collect { case Event.QuestionAsked(_, _, _, qid, _) => qid.value }.toSet
    log.toList.reverse.collectFirst {
      case Event.QuestionProposed(_, _, agent, qid, content) if !asked.contains(qid.value) =>
        LogActor.Pending(agent, qid, content)
    }

  private def abstainReason(log: Vector[Event], roundComplete: Boolean): String =
    if !roundComplete then "round incomplete — held (attrition), never signing on a partial round"
    else
      GameCore.decide(log, log.size) match
        case GateDecision.Sign(_) => "signable" // unreachable: nextMove returned Abstain
        case GateDecision.Abstain(AbstainReason.Unconfirmed(n)) =>
          s"unconfirmed — a lone-ish hypothesis backed by $n of ${GameCore.MinCorroboration} needed"
        case GateDecision.Abstain(AbstainReason.Blocked(reason)) => blockText(reason)

  private def blockText(reason: BlockReason): String = reason match
    case BlockReason.Gap => "no hypothesis on the table yet"
    case BlockReason.Conflict => "conflict — a hypothesis is both asserted and refuted"
    case BlockReason.Refuted => "refuted — the leading hypothesis was struck"
    case BlockReason.Ambiguous => "ambiguous — rival hypotheses, no single leader"
    case BlockReason.BelowThreshold => "below threshold"
    case BlockReason.Unverified => "unverified"

object LogActor:
  /** A proposed-but-unasked question and who proposed it (the asker on the `QuestionAsked` event).
    */
  final private case class Pending(asker: AgentId, id: QuestionId, text: String)
