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

/** The LogActor's fixed collaborators — everything but the evolving game [[LogState]]. `seed` is
  * the persistent memory replayed into this game (two-tier-reset-design): the established
  * definitions recalled from prior games, emitted as belief-inert [[Event.DefinitionRemembered]]
  * events at the head of the log before round one. Defaults to `Nil` — the empty-memory path — so
  * every existing construction is unchanged and byte-identical.
  */
final case class LogDeps(
    oracle: Oracle,
    scheduler: Scheduler,
    sink: EventSink,
    now: IO[Long],
    onDone: Outcome => IO[Unit],
    config: SocietyConfig,
    seed: List[Definition] = Nil
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
    case ToLog.Challenged(qid, term) => onChallenged(qid, term).map(withState)
    case ToLog.Defined(qid, agent, term, meaning) =>
      onDefined(qid, agent, term, meaning).map(withState)

  private def withState(s: LogState): LogActor = new LogActor(context, deps, s)

  // --- message handlers ---

  private def onBegin(agents: List[(AgentId, ActorRef[ToAgent])]): IO[LogState] =
    if agents.isEmpty then endInconclusive(state)
    else
      val withAgents = state.copy(agents = agents)
      // Empty memory is the common path — guard it so it is BYTE-IDENTICAL to the pre-persistence
      // `open(withAgents, first)` (invariant 6: no seed emit, no extra IO step). A non-empty seed
      // replays the recalled definitions at seq 1..K BEFORE round one opens.
      if deps.seed.isEmpty then open(withAgents, RoundId.first)
      else seedDefinitions(withAgents).flatMap(open(_, RoundId.first))

  /** Emit one belief-inert [[Event.DefinitionRemembered]] per seeded definition, at the head of the
    * log (seq 1..K), flowing to the sink/Topic like any event. Belief-inert by construction
    * ([[GameCore.project]] drops it), so belief still begins at `gap` (invariant 1). Each recalled
    * definition carries its `origin` provenance verbatim — the audit surface's "recalled from game
    * N".
    */
  private def seedDefinitions(s: LogState): IO[LogState] =
    deps.seed.foldLeft(IO.pure(s)) { (accIO, definition) =>
      accIO.flatMap { acc =>
        appendEmit(acc.log)((seq, ts) =>
          Event.DefinitionRemembered(
            seq,
            ts,
            definition.term,
            definition.meaning,
            definition.provenance
          )
        ).map { case (log2, _) => acc.copy(log = log2) }
      }
    }

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
      // `governing` is derived from the LOG (clarification-feature §4): the terms whose meaning was
      // established FOR THIS QUESTION in this exchange — every one has a `DefinitionGiven`, so the
      // record can never cite a term with no established definition. Empty when nothing was
      // challenged-and-defined (a plain, non-clarified answer).
      val governing = governingTerms(state.log, qid)
      appendEmit(state.log)((seq, ts) => Event.AnswerGiven(seq, ts, qid, ans, governing)).flatMap {
        case (log2, _) =>
          open(
            state.copy(log = log2),
            state.round.next
          ) // the answer opens a NEW round (the barrier)
      }

  /** A CHALLENGE pauses grounding (clarification-feature §1): log `ClarificationRequested` (no
    * `AnswerGiven`, so nothing enters the ledger and the round does not close), route a
    * [[ToAgent.Clarify]] to the question's PROPOSER (§2 — it must defend its own question's
    * meaning), and RE-ASK the same question so the human can answer against the definition or
    * challenge again. Re-asking here (not on the agent's reply) is what keeps the human unblocked
    * even if the agent never defines — fail-closed, never a wedge.
    */
  private def onChallenged(qid: QuestionId, term: Term): IO[LogState] =
    if state.phase == Phase.Ended then IO.pure(state)
    else
      appendEmit(state.log)((seq, ts) => Event.ClarificationRequested(seq, ts, qid, term)).flatMap {
        case (log2, _) =>
          val s2 = state.copy(log = log2)
          sendClarify(s2, qid, term) *> reAsk(s2, qid).as(s2)
      }

  /** The asking agent supplied a meaning (clarification-feature §2): log it as a `DefinitionGiven`
    * claim, available to every agent for the rest of the game. Belief-inert — no round effect, no
    * barrier effect, never a signature; the re-ask already happened on the challenge.
    */
  private def onDefined(
      qid: QuestionId,
      agent: AgentId,
      term: Term,
      meaning: String
  ): IO[LogState] =
    if state.phase == Phase.Ended then IO.pure(state)
    else
      appendEmit(state.log)((seq, ts) => Event.DefinitionGiven(seq, ts, agent, qid, term, meaning))
        .map { case (log2, _) => state.copy(log = log2) }

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
    // Reconcile the retirement trace BEFORE the gate read (hypothesis-lifecycle §A/§B): emit a
    // belief-inert `Retired`/`Resurrected` marker for any candidate the recomputed predicate now
    // defeats (or recovers). The markers move NO belief and masking is the recomputed predicate,
    // so `nextMove` reads the same masked slot with or without them — this grows the LOG (the
    // audit/UI trace) but never changes the sign decision.
    reconcileRetirements(closed).flatMap { reconciled =>
      // Raise a NON-CONVERGENCE flag if the belief-state history shows the search is clearly stuck
      // (librarian-convergence-monitor). This is a belief-inert `ConvergenceWarning` — it projects
      // to nothing, so `nextMove` below reads the SAME masked slot with or without it and the sign
      // decision is byte-identical. It is a request for help, never a gate change; it grows the LOG
      // (the audit/UI trace) but never drives a signature. Idempotent — at most once per stuck
      // episode ([[Convergence.warningToEmit]]).
      maybeWarnNonConvergence(reconciled).flatMap { flagged =>
        GameCore.nextMove(flagged.log, flagged.log.size, roundComplete) match
          case Move.Sign(winner) => sign(flagged, winner)
          case Move.Abstain =>
            val reason = abstainReason(flagged.log, roundComplete)
            appendEmit(flagged.log)((seq, ts) => Event.GateAbstain(seq, ts, reason)).flatMap {
              case (log2, _) =>
                advance(flagged.copy(log = log2))
            }
      }
    }

  /** Bring the retirement trace in line with the recomputed predicate (hypothesis-lifecycle §A/§B),
    * appending and emitting a `Retired`/`Resurrected` marker for each candidate whose defeat-state
    * changed since the last reconcile. The masking AUTHORITY is [[GameCore.retiredCandidates]]
    * (threaded through `belief`/`decide`/`GameView.from`); these markers are only its audit/UI
    * trace ([[GameCore.project]] drops them), so appending them moves no belief and changes no sign
    * decision. Single-writer and contiguous: [[GameCore.reconcileRetirements]] mints each marker at
    * `seq = log.size + 1` in this actor's serialization order, and they are appended in that order,
    * so no seq races. A round with no newly-retired/resurrected candidate returns `Nil` and emits
    * nothing — byte-identical to the pre-lifecycle path.
    */
  private def reconcileRetirements(s: LogState): IO[LogState] =
    deps.now.flatMap { ts =>
      GameCore.reconcileRetirements(s.log, s.log.size + 1, ts) match
        case Nil => IO.pure(s)
        case markers =>
          markers
            .foldLeft(IO.pure(s.log))((accIO, marker) =>
              accIO.flatMap(log => deps.sink.emit(marker).as(log :+ marker))
            )
            .map(log2 => s.copy(log = log2))
    }

  /** Raise the librarian's non-convergence flag if the belief-state history shows the search is
    * clearly stuck (librarian-convergence-monitor) and no flag is already standing for this stuck
    * episode. Appends and emits ONE belief-inert [[Event.ConvergenceWarning]] carrying the
    * structural evidence (rounds-without-consolidation, glut-persistence) — no candidate name, no
    * diagnosis. Belief-inert by construction ([[GameCore.project]] drops it), so it moves no belief
    * and changes no sign decision; a round with a clearly-converging (or already-flagged) search
    * returns `s` unchanged — byte-identical to the pre-monitor path.
    */
  private def maybeWarnNonConvergence(s: LogState): IO[LogState] =
    Convergence.warningToEmit(s.log, deps.config) match
      case None => IO.pure(s)
      case Some(warning) =>
        appendEmit(s.log)((seq, ts) =>
          Event.ConvergenceWarning(
            seq,
            ts,
            warning.roundsWithoutConsolidation,
            warning.glutPersistence
          )
        ).map { case (log2, _) => s.copy(log = log2) }

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
            submitRespond(pending.id, pending.text).as(s.copy(log = log2))
          }

  /** Background one oracle round-trip for a question and route the human's move back as a self-send
    * (clarification-feature): an [[HumanMove.Answer]] becomes [[ToLog.Answered]], an
    * [[HumanMove.Challenge]] becomes [[ToLog.Challenged]]. Shared by the FIRST ask
    * ([[askQuestion]], which also emits `QuestionAsked`) and the RE-ASK ([[reAsk]], which does
    * not). An oracle FAULT (e.g. a stdin IOException) degrades to a belief-inert `Answer(Unknown)`
    * that still opens the next round rather than wedging the loop until the hard deadline.
    */
  private def submitRespond(qid: QuestionId, text: String): IO[Unit] =
    val reply = deps.oracle
      .respond(Question(qid, text))
      .handleError(_ => HumanMove.Answer(OracleAnswer.Unknown))
      .flatMap(routeHumanMove(qid, _))
    deps.scheduler.submit(reply)

  private def routeHumanMove(qid: QuestionId, move: HumanMove): IO[Unit] = move match
    case HumanMove.Answer(ans) =>
      MessageId.from(s"answered-${qid.value}") match
        case Right(mid) => context.self.tell(mid, ToLog.Answered(qid, ans))
        case Left(_) => IO.unit
    case HumanMove.Challenge(term) =>
      MessageId.from(s"challenged-${qid.value}-${term.value}") match
        case Right(mid) => context.self.tell(mid, ToLog.Challenged(qid, term))
        case Left(_) => IO.unit

  /** Re-ask a question after a challenge (clarification-feature §1) — background a fresh oracle
    * round-trip WITHOUT re-emitting `QuestionAsked` (the question was already asked; only the human
    * gets another turn). Fail-closed if the question text can no longer be found (impossible on the
    * live path — a challenge names a question that was asked): nothing to re-ask.
    */
  private def reAsk(s: LogState, qid: QuestionId): IO[Unit] =
    questionText(s.log, qid) match
      case Some(text) => submitRespond(qid, text)
      case None => IO.unit

  /** Route a [[ToAgent.Clarify]] to the PROPOSER of the challenged question (clarification-feature
    * §2). Fail-closed at every gap: an unknown proposer, a proposer no longer in the registry, or a
    * missing question text simply sends no Clarify — the human answers ungrounded, never a wedge
    * and never a wrong sign (the definition is belief-inert regardless).
    */
  private def sendClarify(s: LogState, qid: QuestionId, term: Term): IO[Unit] =
    (proposerOf(s.log, qid), questionText(s.log, qid)) match
      case (Some(agentId), Some(text)) =>
        refOf(s.agents, agentId) match
          case Some(ref) =>
            MessageId.from(s"clarify-${qid.value}-${term.value}") match
              case Right(mid) => send(ref, mid, ToAgent.Clarify(qid, text, term))
              case Left(_) => IO.unit
          case None => IO.unit
      case _ => IO.unit

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

  /** The terms established (challenged-and-defined) for THIS question — one per distinct
    * `DefinitionGiven` on `qid` (clarification-feature §4). Every term returned has a
    * `DefinitionGiven` in the log by construction, so `AnswerGiven.governing` can never cite a term
    * with no established definition.
    */
  private def governingTerms(log: Vector[Event], qid: QuestionId): List[Term] =
    log
      .collect {
        case Event.DefinitionGiven(_, _, _, q, term, _) if q == qid => term
      }
      .distinct
      .toList

  /** Who proposed `qid` — the asking agent the challenge routes its Clarify to (§2). */
  private def proposerOf(log: Vector[Event], qid: QuestionId): Option[AgentId] =
    log.collectFirst {
      case Event.QuestionProposed(_, _, agent, q, _) if q == qid => agent
    }

  /** The text of `qid` as it was asked (falling back to its proposal) — the question the human is
    * answering/challenging and the context the agent defines the term in.
    */
  private def questionText(log: Vector[Event], qid: QuestionId): Option[String] =
    log
      .collectFirst {
        case Event.QuestionAsked(_, _, _, q, content) if q == qid => content
      }
      .orElse(log.collectFirst {
        case Event.QuestionProposed(_, _, _, q, content) if q == qid => content
      })

  /** The live handle of a registered agent, by id — `None` if it is not in the cohort registry. */
  private def refOf(
      agents: List[(AgentId, ActorRef[ToAgent])],
      agentId: AgentId
  ): Option[ActorRef[ToAgent]] =
    agents.collectFirst { case (id, ref) if id == agentId => ref }

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
