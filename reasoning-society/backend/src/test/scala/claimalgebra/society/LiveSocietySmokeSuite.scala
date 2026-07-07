package claimalgebra.society

import cats.effect.{IO, Ref}
import claimalgebra.extract.AnthropicLlmCall
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** A LIVE smoke test — real, BILLED Haiku calls, so the whole suite is gated: ignored unless
  * `RUN_LIVE_SOCIETY` is set, which keeps `sbt check` hermetic. Run it with:
  *
  * {{{RUN_LIVE_SOCIETY=1 sbt "reasoningSociety/testOnly *LiveSocietySmokeSuite"}}}
  *
  * (needs `ANTHROPIC_API_KEY` in the environment). It proves the one thing the hermetic tests
  * cannot: the three diverse [[AgentActor]]s make real structured calls, post real moves, and the
  * LogActor round loop drives them to a terminal outcome. The oracle is SCRIPTED (not console), so
  * the run is bounded and unattended; the gate decisions are the shipped [[GameCore]], already
  * proven hermetic.
  */
class LiveSocietySmokeSuite extends CatsEffectSuite:

  override def munitIgnore: Boolean = !sys.env.contains("RUN_LIVE_SOCIETY")

  override def munitIOTimeout: Duration = 5.minutes

  test("real Haiku agents play — they post moves and the round loop reaches a terminal outcome") {
    val config = SocietyConfig(maxRounds = 4, roundTimeout = 60.seconds, hardDeadline = 4.minutes)
    AnthropicLlmCall.clientResource.use { client =>
      val llm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      for
        oracle <- Oracle.scripted(
          List(OracleAnswer.Yes, OracleAnswer.No, OracleAnswer.Yes, OracleAnswer.No)
        )
        collected <- Ref[IO].of(Vector.empty[Event])
        sink = new EventSink:
          def emit(event: Event): IO[Unit] =
            collected.update(_ :+ event) *> IO.println(event.toString)
        outcome <- Society.play(AgentStrategy.cohort, _ => llm, oracle, sink, config)
        events <- collected.get
      yield
        assert(events.exists(isRealMove), "the live agents produced at least one real move")
        assert(events.exists(isGateDecision), "the gate reached at least one decision")
        // Either terminal outcome is acceptable — cheap models rarely converge on an exact label; the
        // point is that the round loop reached a terminal state (both Outcome cases are terminal).
        assertEquals(isTerminal(outcome), true)
    }
  }

  test("a real Haiku agent defines its challenged term end to end (clarification)") {
    val config = SocietyConfig(maxRounds = 4, roundTimeout = 60.seconds, hardDeadline = 4.minutes)
    val alive = Term.from("alive").fold(fail(_), identity)
    AnthropicLlmCall.clientResource.use { client =>
      val llm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      for
        // The human CHALLENGES "alive" on the first asked question, then answers the rest — so the
        // proposing agent must define its term with a real structured call before grounding resumes.
        oracle <- Oracle.scriptedMoves(
          List(
            HumanMove.Challenge(alive),
            HumanMove.Answer(OracleAnswer.Yes),
            HumanMove.Answer(OracleAnswer.No),
            HumanMove.Answer(OracleAnswer.Yes)
          )
        )
        collected <- Ref[IO].of(Vector.empty[Event])
        sink = new EventSink:
          def emit(event: Event): IO[Unit] =
            collected.update(_ :+ event) *> IO.println(event.toString)
        _ <- Society.play(
          AgentStrategy.cohort,
          _ => llm,
          oracle,
          sink,
          config,
          definerFor = _ => definer
        )
        events <- collected.get
      yield
        assert(events.exists(isClarificationRequested), "the challenge was recorded")
        val definitions = events.collect { case Event.DefinitionGiven(_, _, _, _, _, m) => m }
        assert(definitions.nonEmpty, "a real agent supplied at least one definition")
        assert(definitions.forall(_.trim.nonEmpty), "every definition carries a non-blank meaning")
    }
  }

  private def isClarificationRequested(event: Event): Boolean = event match
    case _: Event.ClarificationRequested => true
    case _ => false

  // Pattern-matching predicates (isInstanceOf is banned by the Scalazzi subset).
  private def isRealMove(event: Event): Boolean = event match
    case _: Event.Assert => true
    case _: Event.QuestionProposed => true
    case _ => false

  private def isGateDecision(event: Event): Boolean = event match
    case _: Event.GateAbstain => true
    case _: Event.GateSign => true
    case _ => false

  private def isTerminal(outcome: Outcome): Boolean = outcome match
    case Outcome.Signed(_) => true
    case Outcome.Inconclusive => true
