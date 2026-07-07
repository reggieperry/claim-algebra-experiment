package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The convergence monitor wired into the single-writer LogActor (librarian-convergence-monitor):
  * driven through the effectful shell, a genuinely stuck game emits the belief-inert
  * `ConvergenceWarning` through the one writer, at most once per episode, and — the load-bearing
  * property — the gate/sign path is UNCHANGED: the flag never manufactures a signature, and belief
  * is byte-identical with the marker stripped.
  *
  * The stuck game: "fossil" is asserted once and refuted once — a live glut (the asserter stands
  * behind it, one refuter) that persists round after round, never resolving and never
  * consolidating. A proposer advances the rounds. Nothing ever reaches two clean backers, so the
  * gate correctly abstains to a clean Inconclusive — and the monitor notices the stall and flags
  * it.
  */
class ConvergenceWiringSuite extends CatsEffectSuite:

  // A high budget so the game is ended by question-exhaustion, not the budget cap — the flag fires
  // on the structural stall (a persistent glut), well before budget.
  private val slow = SocietyConfig(maxRounds = 8, roundTimeout = 1.hour, hardDeadline = 20.seconds)

  // Rounds close on full-cohort report (deterministic); the timeout never fires.
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  private def canned(dto: AgentMoveDto): Either[CallError, AgentMoveDto] = Right(dto)
  private def assertOf(c: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("assert", c, "guess"))
  private def refuteOf(c: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("refute", c, "no"))
  private def proposeOf(q: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("propose", "", q))
  private val passMove: Either[CallError, AgentMoveDto] = canned(StubLlm.pass)

  private val passStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(passMove)

  private def collectingSink: IO[(EventSink, IO[Vector[Event]])] =
    Ref[IO].of(Vector.empty[Event]).map { ref =>
      val sink: EventSink = event => ref.update(_ :+ event)
      (sink, ref.get)
    }

  private def scriptedLlms(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(pairs => pairs.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, passStub))

  private def play(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]],
      replies: List[OracleAnswer]
  ): IO[(Outcome, Vector[Event])] =
    for
      llmFor <- scriptedLlms(scripts)
      oracle <- Oracle.scripted(replies)
      sinkAndGet <- collectingSink
      outcome <- Society.play(AgentStrategy.cohort, llmFor, oracle, sinkAndGet._1, slow, noTimeout)
      events <- sinkAndGet._2
    yield (outcome, events)

  // The persistent-glut game: driller asserts "fossil" once and stands behind it; skeptic refutes it
  // once — a live glut that never retires and never resolves. Splitter proposes a fresh question each
  // round to keep the loop advancing; from round five on it passes and the game ends. The oracle
  // answers each asked question (four rounds get a question).
  private val stuck: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
    "driller" -> List(assertOf("fossil"), passMove, passMove, passMove, passMove),
    "skeptic" -> List(refuteOf("fossil"), passMove, passMove, passMove, passMove),
    "splitter" -> List(
      proposeOf("Is it once-living?"),
      proposeOf("Is it mineralized?"),
      proposeOf("Is it in rock?"),
      proposeOf("Is it ancient?"),
      passMove
    )
  )

  private def convergenceWarnings(events: Vector[Event]): Vector[Event] =
    events.collect { case w: Event.ConvergenceWarning => w }

  private def isWarning(e: Event): Boolean = e match
    case _: Event.ConvergenceWarning => true
    case _ => false

  private def gateSigns(events: Vector[Event]): Vector[Answer] =
    events.collect { case Event.GateSign(_, _, c) => c }

  test("the single writer emits EXACTLY ONE belief-inert ConvergenceWarning on a stuck game") {
    for result <- play(stuck, List.fill(4)(OracleAnswer.Yes))
    yield
      val events = result._2
      val warnings = convergenceWarnings(events)
      assertEquals(
        warnings.size,
        1,
        clue(s"fires once per stuck episode — not every round; got ${warnings.size}")
      )
      // The evidence is structural — a persistent glut across ≥ WindowRounds rounds.
      warnings.headOption match
        case Some(Event.ConvergenceWarning(_, _, rwc, gp)) =>
          assert(gp >= Convergence.WindowRounds, clue(s"a persistent glut drove it: gp=$gp"))
          assert(
            rwc >= Convergence.WindowRounds,
            clue(s"a sustained no-consolidation run: rwc=$rwc")
          )
        case _ => fail("expected a ConvergenceWarning")
  }

  test("the flag manufactures NO sign — the stuck game still ends Inconclusive, nothing signs") {
    for result <- play(stuck, List.fill(4)(OracleAnswer.Yes))
    yield
      val (outcome, events) = result
      assertEquals(
        outcome,
        Outcome.Inconclusive,
        clue("the flag is a request for help, not a sign")
      )
      assertEquals(gateSigns(events), Vector.empty, clue("the gate stays honest — nothing signs"))
  }

  test("the emitted warning is BELIEF-INERT — project and the gate decision are unchanged by it") {
    for result <- play(stuck, List.fill(4)(OracleAnswer.Yes))
    yield
      val events = result._2
      assert(convergenceWarnings(events).nonEmpty, "precondition: a warning was emitted")
      val stripped = events.filterNot(isWarning)
      assertEquals(
        GameCore.project(events),
        GameCore.project(stripped),
        clue("the flag contributes nothing to the fold")
      )
      assertEquals(
        GameCore.decide(events, events.size),
        GameCore.decide(stripped, stripped.size),
        clue("the gate reads the same belief with the marker present or stripped")
      )
  }

  test("the warning lands AFTER the third answer and BEFORE the round that would abstain on it") {
    for result <- play(stuck, List.fill(4)(OracleAnswer.Yes))
    yield
      val events = result._2
      val warnSeq = events.collectFirst { case Event.ConvergenceWarning(seq, _, _, _) => seq }
      val thirdAnswerSeq =
        events.collect { case Event.AnswerGiven(seq, _, _, _, _) => seq }.lift(2)
      assert(warnSeq.isDefined, "a warning was emitted")
      assert(
        (warnSeq, thirdAnswerSeq).mapN(_ > _).getOrElse(false),
        clue(
          s"the flag fired once ≥ WindowRounds rounds of stall accumulated: $warnSeq vs $thirdAnswerSeq"
        )
      )
  }
