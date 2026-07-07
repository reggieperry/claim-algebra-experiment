package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import claimalgebra.{Belnap, Testimony}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The effectful shell end to end: real actors (the substrate), the LogActor round loop, and
  * diverse agents driven by a hermetic STUB `LlmCall` + a SCRIPTED oracle. Determinism comes from
  * `Ref`/ `Deferred` synchronization inside the substrate and from injecting the round-timeout
  * SCHEDULER — no sleep-as-sync: a `noTimeout` scheduler closes rounds only on full-cohort report
  * (deterministic order-free outcome), and an `eager` scheduler fires the timeout to exercise
  * attrition.
  */
class SocietyGameSuite extends CatsEffectSuite:

  // --- fixtures ---

  private def mkAnswer(raw: String): Answer = Answer.from(raw).fold(e => fail(e), identity)
  private def mkAgentId(raw: String): AgentId = AgentId.from(raw).fold(e => fail(e), identity)
  private def mkTermId(raw: String): Term = Term.from(raw).fold(e => fail(e), identity)
  private def mkQuestionId(raw: String): QuestionId =
    QuestionId.from(raw).fold(e => fail(e), identity)
  private val apple = mkAnswer("apple")
  private val dog = mkAnswer("dog")

  private val fast = SocietyConfig(maxRounds = 6, roundTimeout = 1.hour, hardDeadline = 10.seconds)
  private val attrition =
    SocietyConfig(maxRounds = 3, roundTimeout = 1.hour, hardDeadline = 10.seconds)

  // The round timeout never fires; rounds close on full-cohort report (deterministic).
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  // The round timeout fires as soon as it is armed — a cohort with a silent member closes on attrition.
  private def eager(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void

  private def canned(dto: AgentMoveDto): Either[CallError, AgentMoveDto] = Right(dto)
  private def assertOf(c: String): Either[CallError, AgentMoveDto] = canned(
    StubLlm.move("assert", c, "guess")
  )
  private def corroborateOf(c: String): Either[CallError, AgentMoveDto] =
    canned(StubLlm.move("corroborate", c, "agreed"))
  private def refuteOf(c: String): Either[CallError, AgentMoveDto] = canned(
    StubLlm.move("refute", c, "no")
  )
  private def proposeOf(q: String): Either[CallError, AgentMoveDto] = canned(
    StubLlm.move("propose", "", q)
  )
  private val passMove: Either[CallError, AgentMoveDto] = canned(StubLlm.pass)
  private val timeoutErr: Either[CallError, AgentMoveDto] = Left(CallError.Timeout)

  private val passStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(passMove)

  private val raisingStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.raiseError(new RuntimeException("boom"))

  private def collectingSink: IO[(EventSink, IO[Vector[Event]])] =
    Ref[IO].of(Vector.empty[Event]).map { ref =>
      val sink: EventSink = event => ref.update(_ :+ event)
      (sink, ref.get)
    }

  /** Build a per-agent scripted `LlmCall` map (keyed by the agent id string); any unscripted agent
    * falls back to a pass stub.
    */
  private def scriptedLlms(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(pairs => pairs.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, passStub))

  private def play(
      llmFor: AgentId => LlmCall[AgentMoveDto],
      replies: List[OracleAnswer],
      schedulerOf: Supervisor[IO] => Scheduler,
      config: SocietyConfig,
      strategies: List[AgentStrategy] = AgentStrategy.cohort
  ): IO[(Outcome, Vector[Event])] =
    for
      oracle <- Oracle.scripted(replies)
      sinkAndGet <- collectingSink
      outcome <- Society.play(strategies, llmFor, oracle, sinkAndGet._1, config, schedulerOf)
      events <- sinkAndGet._2
    yield (outcome, events)

  private def gateSigns(events: Vector[Event]): Vector[Answer] =
    events.collect { case Event.GateSign(_, _, candidate) => candidate }

  // Pattern-matching predicates (isInstanceOf is banned by the Scalazzi subset).
  private def isGateAbstain(event: Event): Boolean = event match
    case _: Event.GateAbstain => true
    case _ => false
  private def isGateSign(event: Event): Boolean = event match
    case _: Event.GateSign => true
    case _ => false
  private def isAnswerGiven(event: Event): Boolean = event match
    case _: Event.AnswerGiven => true
    case _ => false

  // --- tests ---

  test(
    "a full game narrows, the gate abstains at one backer, then signs on the second (≥2 backers)"
  ) {
    val scripts = Map(
      "driller" -> List(assertOf("apple"), passMove),
      "splitter" -> List(proposeOf("Is it a fruit?"), corroborateOf("apple")),
      "skeptic" -> List(passMove, passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.Yes), noTimeout, fast)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Signed(apple))
      assertEquals(events.lastOption.collect { case Event.GateSign(_, _, c) => c }, Some(apple))
      assertEquals(
        GameCore.distinctBackers(events, apple),
        2,
        clue("signed on two DISTINCT backers")
      )
      assert(events.exists(isAnswerGiven), "a Q&A round happened")
      val abstainIdx = events.indexWhere(isGateAbstain)
      val signIdx = events.indexWhere(isGateSign)
      val corrIdx = events.indexWhere {
        case Event.Corroborate(_, _, _, c, _) => c == apple
        case _ => false
      }
      assert(abstainIdx >= 0 && abstainIdx < signIdx, "abstained before signing (it narrowed)")
      assert(corrIdx >= 0 && corrIdx < signIdx, "the second backer preceded the signature")
  }

  test("each agent posts only under its own id — the moves carry the wired distinct ids") {
    val scripts = Map(
      "driller" -> List(assertOf("apple"), passMove),
      "splitter" -> List(proposeOf("Is it a fruit?"), corroborateOf("apple")),
      "skeptic" -> List(passMove, passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.Yes), noTimeout, fast)
    yield
      val events = result._2
      val cohortIds = AgentStrategy.cohort.map(_.id.value).toSet
      val posted = events.flatMap(_.agentId.map(_.value)).toSet
      assert(posted.subsetOf(cohortIds), s"every posted id is a wired agent: $posted ⊄ $cohortIds")
      val asserter = events.collectFirst {
        case Event.Assert(_, _, a, c, _) if c == apple => a.value
      }
      assertEquals(
        asserter,
        Some("driller"),
        clue("the driller's assert carries the driller's own id")
      )
  }

  test(
    "a silent agent times the round out → Abstain, never a wrong Sign (through nextMove, not decide)"
  ) {
    // The two speaking agents alone WOULD sign — proven purely here:
    val backed = Vector(
      Event.Assert(1, 1L, mkAgentId("driller"), apple, "guess"),
      Event.Corroborate(2, 2L, mkAgentId("splitter"), apple, "agreed")
    )
    assertEquals(
      GameCore.decide(backed, backed.size),
      GateDecision.Sign(apple),
      clue("the two speaking agents are a signable 2-backer field")
    )
    // But the third agent is silent, so the round never completes; the eager timeout closes it with
    // roundComplete = false → nextMove Abstains. The game never signs.
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(corroborateOf("apple")),
      "skeptic" -> List(timeoutErr)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, Nil, eager, attrition)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Inconclusive)
      assertEquals(gateSigns(events), Vector.empty, clue("attrition never drives a signature"))
  }

  test("a raising LlmCall is contained — that agent abstains and the game runs to a clean end") {
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(corroborateOf("apple"))
    )
    for
      llmFor <- scriptedLlms(scripts)
      // the skeptic raises inside its call; the others are scripted
      combined = (agent: AgentId) => if agent.value == "skeptic" then raisingStub else llmFor(agent)
      result <- play(combined, Nil, eager, attrition)
    yield
      val (outcome, events) = result
      assertEquals(
        outcome,
        Outcome.Inconclusive,
        clue("a raised call is an abstention, not a crash")
      )
      assertEquals(gateSigns(events), Vector.empty)
      assert(
        events.exists(isGateAbstain),
        "the loop kept running (a round closed)"
      )
  }

  test(
    "an answer opens a NEW round: agents react to a 'no', gluting the candidate — it never signs"
  ) {
    val scripts = Map(
      "driller" -> List(assertOf("dog"), refuteOf("dog")),
      "splitter" -> List(proposeOf("Is it a dog?"), refuteOf("dog")),
      "skeptic" -> List(passMove, passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.No), noTimeout, fast)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Inconclusive, clue("the refuted candidate never signs"))
      assertEquals(gateSigns(events), Vector.empty)
      val answerSeq = events.collectFirst { case Event.AnswerGiven(seq, _, _, OracleAnswer.No, _) =>
        seq
      }
      val reactionSeq = events.collectFirst {
        case Event.Refute(seq, _, _, c, _) if c == dog => seq
      }
      assert(answerSeq.isDefined, "the human 'no' was recorded")
      assert(
        (answerSeq, reactionSeq).mapN(_ < _).getOrElse(false),
        "the agents' refute followed the answer (they reacted in the new round)"
      )
      val corner = Testimony.corner(GameCore.slot(events, events.size))
      assertEquals(
        corner,
        Belnap.Glut,
        clue("the reacted-to 'no' gluts the candidate — a real conflict")
      )
  }

  test(
    "a seeded game emits the recalled definitions at the head (seq 1..K) and begins at gap"
  ) {
    // Persistent memory recalled into a fresh game (two-tier-reset-design): two established
    // definitions seeded at the head. They are emitted as belief-inert DefinitionRemembered events
    // BEFORE round one; belief still begins at gap and nothing signs on them. A passing cohort adds
    // no evidence, so the game ends Inconclusive — exactly as an unseeded one does.
    val q1 = mkQuestionId("q1")
    val seedDefs = List(
      Definition(
        mkTermId("alive"),
        "a living creature currently alive",
        DefinitionProvenance(mkAgentId("driller"), q1, 21, Some(GameId.first))
      ),
      Definition(
        mkTermId("animal"),
        "of the animal kingdom",
        DefinitionProvenance(mkAgentId("splitter"), q1, 22, Some(GameId.first))
      )
    )
    for
      oracle <- Oracle.scripted(Nil)
      sinkAndGet <- collectingSink
      seededOutcome <- Society.play(
        AgentStrategy.cohort,
        _ => passStub,
        oracle,
        sinkAndGet._1,
        fast,
        noTimeout,
        seed = seedDefs
      )
      events <- sinkAndGet._2
      // The unseeded control: same cohort, empty seed.
      oracle2 <- Oracle.scripted(Nil)
      plainSinkAndGet <- collectingSink
      plainOutcome <- Society.play(
        AgentStrategy.cohort,
        _ => passStub,
        oracle2,
        plainSinkAndGet._1,
        fast,
        noTimeout
      )
      plainEvents <- plainSinkAndGet._2
    yield
      val remembered = events.take(2).collect { case e: Event.DefinitionRemembered => e }.toList
      assertEquals(remembered.map(_.seq), List(1, 2), clue("the seeds occupy seq 1..K at the head"))
      assertEquals(remembered.map(_.term.value), List("alive", "animal"))
      assertEquals(
        remembered.map(_.origin.gameId),
        List(Some(GameId.first), Some(GameId.first)),
        clue("each recalled definition carries its origin game verbatim")
      )
      assertEquals(
        GameCore.project(events.take(2)),
        Nil,
        clue("the seeds project to nothing — belief begins at gap")
      )
      assertEquals(gateSigns(events), Vector.empty, clue("a seed can never sign"))
      assertEquals(
        Definitions.established(events).map(_.term.value),
        List("alive", "animal"),
        clue("the recalled vocabulary is established from question one")
      )
      // Seed-invariance at the outcome level: seeding changes nothing but the definition prefix.
      assertEquals(seededOutcome, Outcome.Inconclusive)
      assertEquals(seededOutcome, plainOutcome)
      assert(
        !plainEvents.exists {
          case _: Event.DefinitionRemembered => true
          case _ => false
        },
        "the unseeded control emits no DefinitionRemembered events"
      )
  }

  test(
    "wiring rejects duplicate agent ids (one actor = one unique id — the floor's identity contract)"
  ) {
    val dupStrategies = List(
      AgentStrategy(mkAgentId("twin"), "a", "prompt a"),
      AgentStrategy(mkAgentId("twin"), "b", "prompt b")
    )
    interceptIO[DuplicateAgentIds](
      play(_ => passStub, Nil, noTimeout, fast, strategies = dupStrategies).void
    )
  }
