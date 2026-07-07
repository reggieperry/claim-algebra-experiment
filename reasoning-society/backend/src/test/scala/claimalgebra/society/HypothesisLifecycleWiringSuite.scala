package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import claimalgebra.BlockReason
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** Slice 2 of the hypothesis-lifecycle fix — the VISIBLE half, driven through the effectful shell.
  * Slice 1 delivered the pure self-withdrawal retirement (masking, the `reconcileRetirements` PURE
  * marker computation). This slice wires it into the single-writer [[LogActor]] (the markers now
  * flow to the log/sink) and drops retired candidates from what agents SEE ([[GameView]]).
  *
  * §C gate scoping is DROPPED (`hypothesis-lifecycle-decision-record.md`): the gate stays exactly
  * as slice 1 left it — `Gate.accept` on the masked slot. This slice changes what is SHOWN and what
  * is TRACED, never what SIGNS.
  *
  * The running-system validations are the point (the review addendum's Caution 3 — confidence
  * attaches at the running check, not the pure predicate): the FAVORABLE plant-or-fungus pipeline
  * must SIGN apple-tree (retirement clears the false glut), and the ADVERSARIAL
  * contested-hypothesis pipeline must HOLD (a live glut is never retired and never signed around).
  */
class HypothesisLifecycleWiringSuite extends CatsEffectSuite:

  // --- fixtures (the effectful-shell harness, mirroring SocietyGameSuite) ---

  private def mkAnswer(raw: String): Answer = Answer.from(raw).fold(e => fail(e), identity)
  private def mkAgentId(raw: String): AgentId = AgentId.from(raw).fold(e => fail(e), identity)

  private val pof = mkAnswer("plant or fungus")
  private val appleTree = mkAnswer("apple tree")
  private val mineral = mkAnswer("mineral") // the contested H
  private val quartz = mkAnswer("quartz") // the clean, well-backed rival C

  private val fast = SocietyConfig(maxRounds = 6, roundTimeout = 1.hour, hardDeadline = 10.seconds)

  // The round timeout never fires; rounds close on full-cohort report (deterministic, order-free).
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  private def canned(dto: AgentMoveDto): Either[CallError, AgentMoveDto] = Right(dto)
  private def assertOf(c: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("assert", c, "guess"))
  private def corroborateOf(c: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("corroborate", c, "agreed"))
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
      replies: List[OracleAnswer],
      strategies: List[AgentStrategy]
  ): IO[(Outcome, Vector[Event])] =
    for
      llmFor <- scriptedLlms(scripts)
      oracle <- Oracle.scripted(replies)
      sinkAndGet <- collectingSink
      outcome <- Society.play(strategies, llmFor, oracle, sinkAndGet._1, fast, noTimeout)
      events <- sinkAndGet._2
    yield (outcome, events)

  /** A stub strategy — the id is the routing identity the floor counts; the prompt is inert (the
    * scripted [[StubLlm]] ignores it, returning the per-round scripted move).
    */
  private def strat(id: String): AgentStrategy =
    AgentStrategy(mkAgentId(id), id, s"stub prompt for $id")

  private def gateSigns(events: Vector[Event]): Vector[Answer] =
    events.collect { case Event.GateSign(_, _, candidate) => candidate }

  private def isMarker(event: Event): Boolean = event match
    case _: Event.Retired => true
    case _: Event.Resurrected => true
    case _ => false

  private def retiredMarkers(events: Vector[Event]): Vector[Answer] =
    events.collect { case Event.Retired(_, _, c) => c }

  // The favorable plant-or-fungus scenario, on the standard three-role cohort. Driller asserts
  // "plant or fungus" (r1), then the driller AND the skeptic refute it (r2) — the driller's
  // self-withdrawal plus the skeptic gives two standing refuters, so pof is DEFEATED and retires.
  // Splitter proposes a question each round to advance, then corroborates "apple tree" (r3) which
  // the driller asserts (r3) — two distinct backers on a clean candidate, so it SIGNS.
  private def playPof: IO[(Outcome, Vector[Event])] =
    val scripts = Map(
      "splitter" -> List(
        proposeOf("Is it alive?"),
        proposeOf("Does it grow?"),
        corroborateOf("apple tree")
      ),
      "driller" -> List(
        assertOf("plant or fungus"),
        refuteOf("plant or fungus"),
        assertOf("apple tree")
      ),
      "skeptic" -> List(passMove, refuteOf("plant or fungus"), passMove)
    )
    play(scripts, List(OracleAnswer.Yes, OracleAnswer.Yes), AgentStrategy.cohort)

  // --- (1) marker emission through the single writer, belief-inert ---

  test("a Retired(pof) marker is emitted through the LogActor once pof is defeated") {
    for result <- playPof
    yield
      val events = result._2
      assertEquals(
        retiredMarkers(events),
        Vector(pof),
        clue("the single writer emits exactly one Retired marker for the defeated hypothesis")
      )
      // It is emitted AFTER the two refutations that defeat pof and BEFORE apple-tree signs — the
      // replay order the design requires (retirement, then the board proceeds to the signature).
      val secondRefuteSeq = events.collect { case Event.Refute(seq, _, _, c, _) if c == pof => seq }
      val retiredSeq = events.collectFirst { case Event.Retired(seq, _, `pof`) => seq }
      val signSeq = events.collectFirst { case Event.GateSign(seq, _, `appleTree`) => seq }
      assert(secondRefuteSeq.sizeIs == 2, s"pof was refuted twice: $secondRefuteSeq")
      assert(
        (retiredSeq, secondRefuteSeq.maxOption).mapN(_ > _).getOrElse(false),
        "Retired came after both refutations defeated pof"
      )
      assert(
        (retiredSeq, signSeq).mapN(_ < _).getOrElse(false),
        "Retired came before the apple-tree signature"
      )
  }

  test("the Retired marker is BELIEF-INERT — belief and the sign decision are unchanged by it") {
    for result <- playPof
    yield
      val events = result._2
      val noMarkers = events.filterNot(isMarker)
      // project drops the markers to nothing, so the belief stream is identical with or without
      // them — the marker can neither move a candidate nor invent a backer.
      assertEquals(
        GameCore.project(events),
        GameCore.project(noMarkers),
        clue("the markers contribute nothing to the fold")
      )
      // The gate's decision reads the same masked slot either way (masking is the recomputed
      // predicate, not the marker), so the sign is identical with the markers stripped out.
      assertEquals(
        GameCore.decide(events, events.size),
        GameCore.decide(noMarkers, noMarkers.size),
        clue("the sign decision does not depend on the marker's presence")
      )
      assertEquals(GameCore.decide(events, events.size), GateDecision.Sign(appleTree))
  }

  // --- (2) FAVORABLE running-system validation: the pipeline SIGNS apple tree ---

  test(
    "RUNNING SYSTEM (favorable) — the plant-or-fungus pipeline SIGNS apple tree, not abstain-to-exhaustion"
  ) {
    for result <- playPof
    yield
      val (outcome, events) = result
      assertEquals(
        outcome,
        Outcome.Signed(appleTree),
        clue("retirement masks the false glut; the standard gate signs apple-tree on its 2 backers")
      )
      assertEquals(gateSigns(events), Vector(appleTree))
      assertEquals(
        GameCore.distinctBackers(events, appleTree),
        2,
        clue("signed on two DISTINCT backers — the floor, unchanged from slice 1")
      )
  }

  // --- (3) ADVERSARIAL running-system validation: a contested H HOLDS (never retired/signed) ---
  //
  // X (asserter) asserts H and stays SILENT; Y and Z refute H; a separate clean candidate C has two
  // distinct backers (Y and Z backed it before refuting H). Because X still stands behind H, H is a
  // LIVE glut, not a defeat — it must be HELD. The gate reads the whole slot, so H's con keeps the
  // corner Glut and the gate does NOT sign C around the contested H. A four-agent cohort (a
  // dedicated question-proposer advances the rounds).

  private val adversarialCohort: List[AgentStrategy] =
    List(strat("x"), strat("y"), strat("z"), strat("q"))

  test("RUNNING SYSTEM (adversarial) — a contested H is never retired and never signed around") {
    val scripts = Map(
      "x" -> List(assertOf("mineral"), passMove), // asserts H, then SILENT (never withdraws)
      "y" -> List(assertOf("quartz"), refuteOf("mineral")), // backs C, then refutes H
      "z" -> List(corroborateOf("quartz"), refuteOf("mineral")), // backs C, then refutes H
      "q" -> List(proposeOf("Is it hard?"), passMove) // advances round one
    )
    for result <- play(scripts, List(OracleAnswer.Yes), adversarialCohort)
    yield
      val (outcome, events) = result
      assertEquals(
        outcome,
        Outcome.Inconclusive,
        clue("the contested claim holds the gate — a safe non-signature")
      )
      assertEquals(gateSigns(events), Vector.empty, clue("nothing signs"))
      assertEquals(retiredMarkers(events), Vector.empty, clue("no retirement fired"))
      assert(
        !GameCore.retiredCandidates(events).contains(mineral),
        "the contested H is NEVER retired — its asserter still stands behind it"
      )
      // The clean rival IS well-backed (two distinct backers) — yet the gate HOLDS on the global
      // glut rather than signing C around the contested H (no §C gate-scoping).
      assertEquals(
        GameCore.distinctBackers(events, quartz),
        2,
        clue("a clean, well-backed rival exists")
      )
      assertEquals(
        GameCore.decide(events, events.size),
        GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Conflict)),
        clue("the gate holds the contested glut — it does not sign around it")
      )
  }

  test(
    "RUNNING SYSTEM (adversarial, re-assertion variant) — X re-asserts H after the refutes → still held"
  ) {
    val scripts = Map(
      "x" -> List(assertOf("mineral"), passMove, assertOf("mineral")), // assert, silent, RE-ASSERT
      "y" -> List(assertOf("quartz"), refuteOf("mineral"), passMove),
      "z" -> List(corroborateOf("quartz"), refuteOf("mineral"), passMove),
      "q" -> List(proposeOf("Is it hard?"), proposeOf("Is it shiny?"), passMove) // advance r1, r2
    )
    for result <- play(scripts, List(OracleAnswer.Yes, OracleAnswer.Yes), adversarialCohort)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Inconclusive)
      assertEquals(gateSigns(events), Vector.empty)
      assertEquals(retiredMarkers(events), Vector.empty)
      assert(
        !GameCore.retiredCandidates(events).contains(mineral),
        "a re-asserted H is even more clearly live support — never retired"
      )
      assertEquals(
        GameCore.decide(events, events.size),
        GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Conflict)),
        clue("still held after the re-assertion")
      )
  }

  // --- (4) GameView drops retired targets; keeps a contested (not retired) one (pure) ---

  test("GameView drops a retired candidate from agents' live targets; a contested one stays") {
    val driller = mkAgentId("driller")
    val skeptic = mkAgentId("skeptic")
    val grower = mkAgentId("grower")

    // pof: driller asserts then self-withdraws, skeptic refutes → 2 standing refuters, DEFEATED.
    val retiredLog = Vector(
      Event.Assert(1, 1L, driller, pof, "guess"),
      Event.Refute(2, 2L, skeptic, pof, "no"),
      Event.Refute(3, 3L, driller, pof, "withdrawn") // the self-withdrawal
    )
    assert(GameCore.retiredCandidates(retiredLog).contains(pof), "pof is defeated")
    assert(
      !GameView.from(retiredLog).hypotheses.exists((c, _) => c == pof),
      "a retired candidate is OFF the live board — agents stop attacking it"
    )

    // mineral: driller asserts (stays behind), skeptic + grower refute → a LIVE glut, NOT retired.
    val contestedLog = Vector(
      Event.Assert(1, 1L, driller, mineral, "guess"),
      Event.Refute(2, 2L, skeptic, mineral, "no"),
      Event.Refute(3, 3L, grower, mineral, "no") // driller never withdrew → held
    )
    assert(!GameCore.retiredCandidates(contestedLog).contains(mineral), "a contested claim is held")
    assert(
      GameView.from(contestedLog).hypotheses.exists((c, _) => c == mineral),
      "a contested (not retired) candidate STAYS a live target — dissent is held, not silenced"
    )
  }

  // --- (5) the no-retirement path emits no markers (byte-identical to the pre-slice-2 log) ---

  test("a game that retires nothing emits NO lifecycle markers (byte-identical to pre-slice-2)") {
    val apple = mkAnswer("apple")
    val scripts = Map(
      "driller" -> List(assertOf("apple"), passMove),
      "splitter" -> List(proposeOf("Is it a fruit?"), corroborateOf("apple")),
      "skeptic" -> List(passMove, passMove)
    )
    for result <- play(scripts, List(OracleAnswer.Yes), AgentStrategy.cohort)
    yield
      val (outcome, events) = result
      assertEquals(
        outcome,
        Outcome.Signed(apple),
        clue("the same clean 2-backer signature as before")
      )
      assert(
        events.forall(e => !isMarker(e)),
        "no Retired/Resurrected on a game with no defeated hypothesis — the log is unchanged"
      )
  }
