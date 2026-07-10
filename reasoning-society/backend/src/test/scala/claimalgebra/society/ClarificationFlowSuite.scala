package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{Deferred, IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The EFFECTFUL two-move human turn end to end (clarification-feature slice 2), over real actors
  * and the LogActor round loop, driven by a hermetic stub `LlmCall` + a scripted [[Oracle]]. The
  * critical safety is the GROUNDING PAUSE: a `Challenge` emits NO `AnswerGiven` and closes no
  * round; grounding resumes only when the human ANSWERS, and the answer records the `governing`
  * terms it was grounded to. Determinism comes from `Ref`/`Deferred` synchronization inside the
  * substrate and from injecting the round-timeout scheduler (no sleep-as-sync): in the clean-order
  * test the re-ask's answer is GATED on a `Deferred` the sink completes when `definition_given` is
  * emitted, so the definition provably precedes the answer.
  */
class ClarificationFlowSuite extends CatsEffectSuite:

  private def mkAnswer(raw: String): Answer = Answer.from(raw).fold(e => fail(e), identity)
  private def mkTerm(raw: String): Term = Term.from(raw).fold(e => fail(e), identity)
  private val apple = mkAnswer("apple")
  private val alive = mkTerm("alive")
  private val animal = mkTerm("animal")

  private val fast = SocietyConfig(maxRounds = 6, roundTimeout = 1.hour, hardDeadline = 10.seconds)

  // The round timeout never fires; rounds close on full-cohort report (deterministic, order-free).
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  // --- move / definition script helpers ---

  private def assertOf(c: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("assert", c, "guess"))
  private def corroborateOf(c: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("corroborate", c, "agreed"))
  private def proposeOf(q: String): Either[CallError, AgentMoveDto] =
    Right(StubLlm.move("propose", "", q))
  private val passMove: Either[CallError, AgentMoveDto] = Right(StubLlm.pass)
  private def defineOf(meaning: String): Either[CallError, DefinitionDto] =
    Right(StubLlm.define(meaning))

  private val passStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(passMove)

  // A definer for an agent that is never expected to define — fails closed if ever called.
  private val noDefinerStub: LlmCall[DefinitionDto] = new LlmCall[DefinitionDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, DefinitionDto]] =
      IO.pure(Left(CallError.Malformed("unscripted definer")))

  private def scriptedLlms(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, passStub))

  private def scriptedDefiners(
      scripts: Map[String, List[Either[CallError, DefinitionDto]]]
  ): IO[AgentId => LlmCall[DefinitionDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scriptedDefiner(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, noDefinerStub))

  // The winning narrow-to-apple scripts, shared across the tests: driller asserts apple, splitter
  // proposes a question then (after the answer) corroborates apple, skeptic passes — the gate
  // abstains at one backer, signs at two. The splitter is the PROPOSER, so it is who defines.
  private val appleScripts = Map(
    "driller" -> List(assertOf("apple"), passMove),
    "splitter" -> List(proposeOf("Is it a fruit?"), corroborateOf("apple")),
    "skeptic" -> List(passMove, passMove)
  )

  // --- event predicates (isInstanceOf is banned by the Scalazzi subset) ---

  private def isClarificationRequested(e: Event): Boolean = e match
    case _: Event.ClarificationRequested => true
    case _ => false
  private def isDefinitionGiven(e: Event): Boolean = e match
    case _: Event.DefinitionGiven => true
    case _ => false
  private def isQuestionAsked(e: Event): Boolean = e match
    case _: Event.QuestionAsked => true
    case _ => false
  private def isAnswerGiven(e: Event): Boolean = e match
    case _: Event.AnswerGiven => true
    case _ => false
  private def isGateSign(e: Event): Boolean = e match
    case _: Event.GateSign => true
    case _ => false

  private def collectingSink: IO[(EventSink, IO[Vector[Event]])] =
    Ref[IO].of(Vector.empty[Event]).map { ref =>
      val sink: EventSink = event => ref.update(_ :+ event)
      (sink, ref.get)
    }

  /** A sink that appends every event AND completes `gate` the moment the FIRST `definition_given`
    * is emitted — so a gated oracle can hold the re-ask's answer until the definition is on the
    * log, making the clean order deterministic with no sleep.
    */
  private def gatingSink(
      collector: Ref[IO, Vector[Event]],
      gate: Deferred[IO, Unit]
  ): EventSink = event =>
    collector.update(_ :+ event) *>
      (if isDefinitionGiven(event) then gate.complete(()).void else IO.unit)

  /** An oracle that CHALLENGES `term` on the first ask, then (on the re-ask) ANSWERS — but the
    * answer waits on `gate`, so the definition the challenge triggers provably precedes it. Later
    * asks default to `Answer(answer)`.
    */
  private def challengeThenGatedAnswer(
      term: Term,
      gate: Deferred[IO, Unit],
      answer: OracleAnswer
  ): IO[Oracle] =
    Ref[IO].of(0).map { cursor =>
      new Oracle:
        def respond(question: Question): IO[HumanMove] =
          cursor.getAndUpdate(_ + 1).flatMap {
            case 0 => IO.pure(HumanMove.Challenge(term))
            case _ => gate.get.as(HumanMove.Answer(answer))
          }
    }

  // --- the tests ---

  test(
    "grounding pause: challenge → clarification_requested → definition_given → answer_given(governing)"
  ) {
    for
      gate <- Deferred[IO, Unit]
      collector <- Ref[IO].of(Vector.empty[Event])
      sink = gatingSink(collector, gate)
      llmFor <- scriptedLlms(appleScripts)
      definerFor <- scriptedDefiners(
        Map("splitter" -> List(defineOf("a living creature currently alive")))
      )
      oracle <- challengeThenGatedAnswer(alive, gate, OracleAnswer.Yes)
      outcome <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        oracle,
        sink,
        fast,
        noTimeout,
        definerFor
      )
      events <- collector.get
    yield
      // The game still signs apple — grounding paused, then resumed, then the society reasoned on.
      assertEquals(outcome, Outcome.Signed(apple))
      // The exact negotiated sequence, in log order.
      val askIdx = events.indexWhere(isQuestionAsked)
      val clarIdx = events.indexWhere(isClarificationRequested)
      val defIdx = events.indexWhere(isDefinitionGiven)
      val ansIdx = events.indexWhere(isAnswerGiven)
      assert(askIdx >= 0, "the question was asked")
      assert(clarIdx > askIdx, "clarification_requested followed the question")
      assert(defIdx > clarIdx, "definition_given followed the challenge (grounding paused)")
      assert(ansIdx > defIdx, "answer_given followed the definition (answered against the meaning)")
      // Exactly ONE answer entered — the challenge did not itself produce an answer.
      assertEquals(events.count(isAnswerGiven), 1, clue("a challenge emits no AnswerGiven"))
      // The answer records the governing term, and it is a genuinely established definition.
      val governing = events.collectFirst { case Event.AnswerGiven(_, _, _, _, g) => g }
      assertEquals(governing, Some(List(alive)), clue("grounded to the agreed meaning"))
      val defined = events.collect { case Event.DefinitionGiven(_, _, _, _, t, _) => t }.toSet
      assert(
        governing.exists(_.forall(defined.contains)),
        clue("governing cites only terms with a DefinitionGiven in the log")
      )
      // The PROPOSER (splitter) is who defined the term.
      val definer = events.collectFirst { case Event.DefinitionGiven(_, _, a, _, t, _) =>
        (a.value, t)
      }
      assertEquals(
        definer,
        Some(("splitter", alive)),
        clue("the asking agent defines its own term")
      )
  }

  test(
    "a full challenge→define→answer game reaches the SAME Signed outcome as the no-challenge path"
  ) {
    val play = (oracle: Oracle, definerFor: AgentId => LlmCall[DefinitionDto]) =>
      for
        gate <- Deferred[IO, Unit]
        collector <- Ref[IO].of(Vector.empty[Event])
        sink = gatingSink(collector, gate)
        llmFor <- scriptedLlms(appleScripts)
        outcome <- Society.play(
          AgentStrategy.cohort,
          llmFor,
          oracle,
          sink,
          fast,
          noTimeout,
          definerFor
        )
      yield outcome
    for
      plain <- Oracle.scripted(List(OracleAnswer.Yes)).flatMap(o => play(o, _ => noDefinerStub))
      gate <- Deferred[IO, Unit]
      challengeOracle <- challengeThenGatedAnswer(alive, gate, OracleAnswer.Yes)
      // Re-bind the gate into a fresh collector by reusing the challenge oracle with its own sink.
      collector <- Ref[IO].of(Vector.empty[Event])
      sink = gatingSink(collector, gate)
      llmFor <- scriptedLlms(appleScripts)
      definerFor <- scriptedDefiners(Map("splitter" -> List(defineOf("a living creature"))))
      clarified <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        challengeOracle,
        sink,
        fast,
        noTimeout,
        definerFor
      )
    yield
      assertEquals(plain, Outcome.Signed(apple), clue("the no-challenge path signs apple"))
      assertEquals(
        clarified,
        Outcome.Signed(apple),
        clue("the challenge path signs the SAME answer")
      )
  }

  test("a second challenge on the same question re-asks again (two clarification_requested)") {
    for
      sinkAndGet <- collectingSink
      (sink, getEvents) = sinkAndGet
      llmFor <- scriptedLlms(appleScripts)
      definerFor <- scriptedDefiners(
        Map(
          "splitter" -> List(
            defineOf("a living creature currently alive"),
            defineOf("a member of the animal kingdom")
          )
        )
      )
      oracle <- Oracle.scriptedMoves(
        List(
          HumanMove.Challenge(alive),
          HumanMove.Challenge(animal),
          HumanMove.Answer(OracleAnswer.Yes)
        )
      )
      outcome <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        oracle,
        sink,
        fast,
        noTimeout,
        definerFor
      )
      events <- getEvents
    yield
      assertEquals(
        outcome,
        Outcome.Signed(apple),
        clue("the game proceeds only after a real answer")
      )
      assertEquals(
        events.count(isClarificationRequested),
        2,
        clue("each challenge re-asked and produced its own clarification_requested")
      )
      assertEquals(
        events.count(isAnswerGiven),
        1,
        clue("neither challenge produced an AnswerGiven")
      )
  }

  test("agent fault on Clarify → no definition, no wedge, no wrong sign (fail-closed)") {
    for
      sinkAndGet <- collectingSink
      (sink, getEvents) = sinkAndGet
      llmFor <- scriptedLlms(appleScripts)
      // The proposer's definer FAILS — it posts no definition, so grounding cannot complete.
      definerFor <- scriptedDefiners(Map("splitter" -> List(Left(CallError.Timeout))))
      oracle <- Oracle.scriptedMoves(
        List(HumanMove.Challenge(alive), HumanMove.Answer(OracleAnswer.Yes))
      )
      outcome <- Society.play(
        AgentStrategy.cohort,
        llmFor,
        oracle,
        sink,
        fast,
        noTimeout,
        definerFor
      )
      events <- getEvents
    yield
      // The game is not wedged and does not sign anything WRONG — it signs apple (belief unaffected).
      assertEquals(outcome, Outcome.Signed(apple), clue("no wedge; the correct answer still signs"))
      assert(events.exists(isClarificationRequested), "the challenge was recorded")
      assertEquals(
        events.count(isDefinitionGiven),
        0,
        clue("the failed define posted no definition")
      )
      // The answer that entered is UNGROUNDED — the human answered without an established definition.
      val governing = events.collectFirst { case Event.AnswerGiven(_, _, _, _, g) => g }
      assertEquals(
        governing,
        Some(Nil),
        clue("ungrounded answer — governing is empty, never fabricated")
      )
      // A sign only ever follows the real answer.
      val ansIdx = events.indexWhere(isAnswerGiven)
      val signIdx = events.indexWhere(isGateSign)
      assert(ansIdx >= 0 && signIdx > ansIdx, "the signature followed the human's real answer")
  }
