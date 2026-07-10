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
  // A k=2 confirmation quorum (fallible-oracle Slice 4): a lone candidate needs TWO oracle Yes-es.
  private val k2 =
    SocietyConfig(maxRounds = 3, roundTimeout = 1.hour, hardDeadline = 10.seconds, k = 2)

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
    // roundComplete = false → nextMove Abstains — attrition never signs via the internal path. B1's
    // give-up guess is DECLINED by the oracle (No), so the candidate is masked and the game still ends
    // Inconclusive: neither the incomplete-round path nor the oracle-guess path drives a signature.
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(corroborateOf("apple")),
      "skeptic" -> List(timeoutErr)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.No), eager, attrition)
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
    "a lone unconfirmed candidate the oracle CONFIRMS at give-up → Signed (B1 closes a winnable game)"
  ) {
    // One backer only → decide = Unconfirmed; the internal 2-backer quorum never comes, so the
    // society reaches a give-up and GUESSES. The oracle confirms → the ground-truth Yes relaxes the
    // no-lone-sign floor and the game signs the confirmed candidate.
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(passMove),
      "skeptic" -> List(passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.Yes), noTimeout, fast)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Signed(apple))
      assertEquals(gateSigns(events), Vector(apple), clue("the oracle-confirmed candidate signs"))
      // the sign is ORACLE-confirmed: a GuessAnswered(apple, Yes) precedes the GateSign.
      val guessSeq = events.collectFirst {
        case Event.GuessAnswered(seq, _, c, OracleAnswer.Yes) if c == apple => seq
      }
      val signSeq = events.collectFirst { case Event.GateSign(seq, _, _) => seq }
      (guessSeq, signSeq) match
        case (Some(g), Some(sg)) => assert(g < sg, clue("the guess precedes the sign"))
        case other => fail(s"expected a Yes-guess then a sign, got $other")
  }

  test(
    "the oracle DECLINES the guess → the candidate is masked, guessed once, the game ends Inconclusive"
  ) {
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(passMove),
      "skeptic" -> List(passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.No), noTimeout, fast)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Inconclusive)
      assertEquals(gateSigns(events), Vector.empty, clue("a declined guess never signs"))
      // guessed EXACTLY once — the No masks the candidate off the fold, so the give-up ladder has
      // nothing left to re-guess (termination).
      val guesses = events.collect { case Event.GuessAnswered(_, _, c, a) => (c, a) }
      assertEquals(guesses, Vector((apple, OracleAnswer.No)))
  }

  test("k=2: a lone candidate signs only after TWO oracle confirmations (the re-pose loop)") {
    // A lone backer never reaches the internal 2-backer quorum, so the society guesses. At k=2 one
    // Yes is short of quorum: onGuessAnswered re-poses through the give-up ladder, and the SECOND
    // Yes signs. The scripted oracle delivers Yes, Yes in order across the two poses.
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(passMove),
      "skeptic" -> List(passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.Yes, OracleAnswer.Yes), noTimeout, k2)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Signed(apple))
      assertEquals(gateSigns(events), Vector(apple), clue("signs once, on the quorum"))
      val yesGuesses = events.count {
        case Event.GuessAnswered(_, _, c, OracleAnswer.Yes) => c == apple
        case _ => false
      }
      assertEquals(yesGuesses, 2, clue("k=2 re-posed the guess and collected TWO confirmations"))
  }

  test("k=2: a SINGLE confirmation is short of quorum → Inconclusive, never a sign below k") {
    // Only one Yes is scripted; the re-pose draws Unknown (the exhausted-oracle default), which can
    // never reach the k=2 Yes-quorum — the game fails closed to Inconclusive rather than signing on
    // one confirmation (which is what k=1 would have done — see the B1 test above).
    val scripts = Map(
      "driller" -> List(assertOf("apple")),
      "splitter" -> List(passMove),
      "skeptic" -> List(passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      result <- play(llmFor, List(OracleAnswer.Yes), noTimeout, k2)
    yield
      val (outcome, events) = result
      assertEquals(outcome, Outcome.Inconclusive, clue("one confirmation cannot reach k=2"))
      assertEquals(gateSigns(events), Vector.empty, clue("never signs below quorum"))
  }

  test(
    "SPIKE (B2 de-risk): the round loop RESUMES coherently over a non-empty seeded prefix"
  ) {
    // The committee's one empirical unknown: the round loop has only ever run from an EMPTY
    // LogState.initial — no run has forked it over a pre-populated log. Seed a round-boundary prefix
    // (a hypothesis + a proposed-but-UNASKED question q1) and resume. If B2's wholesale rewind is
    // buildable, the loop must re-probe over GameView.from(prefix) and re-ask q1 (pendingQuestion
    // re-surfaces it) rather than wedge or crash. This validates the resume mechanism the design of
    // record rests on, before the full slice is built.
    val prefix = Vector[Event](
      Event.Assert(1, 1L, mkAgentId("driller"), apple, "guess"),
      Event.QuestionProposed(2, 2L, mkAgentId("splitter"), mkQuestionId("q1"), "Is it big?")
    )
    val seeded = LogState(
      log = prefix,
      round = RoundId.first,
      barrier = Barrier(RoundId.first, Set.empty, Set.empty, closed = true),
      roundsUsed = 1,
      phase = Phase.Playing,
      agents = Nil
    )
    val scripts = Map(
      "driller" -> List(passMove),
      "splitter" -> List(passMove),
      "skeptic" -> List(passMove)
    )
    for
      llmFor <- scriptedLlms(scripts)
      oracle <- Oracle.scripted(List(OracleAnswer.No))
      sinkAndGet <- collectingSink
      outcome <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        oracle,
        sinkAndGet._1,
        fast,
        noTimeout,
        initial = seeded
      )
      events <- sinkAndGet._2
    yield
      // (a) The resume did not wedge or crash — it reached a terminal outcome (a hang would trip the
      //     hardDeadline and fail differently).
      assertEquals(outcome, Outcome.Inconclusive)
      // (b) The pending question q1 from the seeded prefix was RE-ASKED — pendingQuestion re-surfaced
      //     it over the non-empty prefix (the committee's core worry, resolved).
      assert(
        events.exists {
          case Event.QuestionAsked(_, _, _, qid, _) => qid.value == "q1"
          case _ => false
        },
        clue(events)
      )
      // (c) The human's re-answer flowed in — the resumed turn completed end to end.
      assert(events.exists(isAnswerGiven), clue("the re-asked question was answered"))
  }

  test(
    "an answer opens a NEW round: agents react to a 'no', retiring the abandoned candidate — it never signs"
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
      // The sole asserter (driller) reacted to the 'no' by refuting its own candidate — a
      // SELF-WITHDRAWAL — and a second refuter (splitter) stands, so dog is a DEFEATED hypothesis,
      // not a live glut. The lifecycle predicate retires it (masks both channels), so the slot reads
      // a clean Gap and the gate abstains for the correct reason. It still never signs — the
      // critical safety. (Pre-lifecycle this jammed as a false Glut for the rest of the game.)
      val corner = Testimony.corner(GameCore.slot(events, events.size))
      assertEquals(
        corner,
        Belnap.Gap,
        clue("both the asserter's withdrawal and a second refuter → retired to trace, a clean gap")
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
