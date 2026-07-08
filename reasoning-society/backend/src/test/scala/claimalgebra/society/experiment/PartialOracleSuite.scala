package claimalgebra.society
package experiment

import cats.effect.IO
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** Arm 2 (fallible-oracle-experiment-design §The arms · §Analysis "Partial oracle"): the
  * running-system exercise of Theorem 6.7's gap-only, path-local fail-closed propagation. The
  * oracle returns a genuine `Unknown` (a gap, Belnap's N corner) on a chosen class of question. Two
  * correctness assertions, small-N and high-scrutiny — not statistics:
  *
  *   - ON-PATH gap (the guess-confirmation, which discharges `verify()`): must force ABSTENTION,
  *     never a signature — even when the guessed candidate is in fact the truth.
  *   - OFF-PATH gap (a property answer, which is belief-inert): must NOT block; the society routes
  *     around it and still signs.
  *
  * Together these are 6.7 on the bench: a gap blocks the sign exactly when it lands on the question
  * the sign depends on, and nowhere else.
  */
class PartialOracleSuite extends CatsEffectSuite with SocietyFixtures:

  private val apple = mkAnswer("apple")
  private val driller = mkAgent("driller")
  private val cfg = SocietyConfig(maxRounds = 4, roundTimeout = 5.seconds, hardDeadline = 1.minute)
  private val truthYes = TruthOracle.pure((_, _) => OracleAnswer.Yes)

  private val fallbackStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(s: String, u: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(Right(StubLlm.pass))

  private def cohortFrom(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(_.toMap)
      .map(byId => (id: AgentId) => byId.getOrElse(id.value, fallbackStub))

  private val loneApple: IO[AgentId => LlmCall[AgentMoveDto]] =
    cohortFrom(
      Map("driller" -> List(Right(StubLlm.move("assert", "apple", "lone")), Right(StubLlm.pass)))
    )

  // --- the pure gate behaviour under a guess gap ---

  test(
    "a guess answered Unknown neither confirms nor masks — the lone winner stays Unconfirmed (6.7 core)"
  ) {
    val log = Vector(
      mkAssert(1, driller, apple),
      Event.GuessAnswered(2, 2L, apple, OracleAnswer.Unknown)
    )
    assertEquals(
      GameCore.oracleConfirmations(log, apple),
      0,
      clue("an Unknown is not a confirmation")
    )
    assert(
      !GameCore.maskedCandidates(log).contains(apple),
      clue("an Unknown does not mask, unlike a No")
    )
    assertEquals(GameCore.decide(log, log.size), GateDecision.Abstain(AbstainReason.Unconfirmed(1)))
    assert(
      GameCore.alreadyGuessed(log, apple, k = 1),
      clue("the Unknown spends the pose budget → the ladder stops")
    )
  }

  // --- end to end: on-path gap forces abstention ---

  test(
    "ON-PATH gap: a gap on the guess forces ABSTAIN, never SIGN — even though the guess is correct"
  ) {
    // Target = apple, and the society guesses apple (the correct answer). The oracle returns Unknown to
    // the guess-confirmation. 6.7 says: the gate must abstain, never sign on the gap — fail-closed even
    // when signing would have been right.
    val onPath = SweepCell(1.0, "gap-guess", "6.7-on-path")
    for
      llmFor <- loneApple
      result <- OracleSweep.runOneGame(llmFor, cfg, onPath, apple, truthYes, seed = 1L)
    yield
      val rec = result._1
      assertEquals(
        rec.outcome,
        PrimaryOutcome.Abstain,
        clue("a gap on the on-path guess blocks the sign")
      )
      assertEquals(
        rec.signed,
        None,
        clue("never a signature on an Unknown guess, though apple is the truth")
      )
  }

  // --- end to end: off-path gap does not block ---

  test(
    "OFF-PATH gap: a gap on a property answer does NOT block — the society routes around and signs"
  ) {
    // The society asks a property question ("Is it a fruit?") and the oracle returns Unknown to it, but
    // the guess-confirmation is answered truthfully. 6.7 is path-local: an off-path gap (a belief-inert
    // property answer) must not block the sign.
    val offPath = SweepCell(1.0, "gap-property", "6.7-off-path")
    val scripts = Map(
      "splitter" -> List(Right(StubLlm.move("propose", "", "Is it a fruit?"))),
      "driller" -> List(Right(StubLlm.move("assert", "apple", "lone")), Right(StubLlm.pass))
    )
    for
      llmFor <- cohortFrom(scripts)
      result <- OracleSweep.runOneGame(llmFor, cfg, offPath, apple, truthYes, seed = 1L)
    yield
      val rec = result._1
      assertEquals(
        rec.outcome,
        PrimaryOutcome.SignCorrect,
        clue("the off-path property gap did not block")
      )
      assertEquals(
        rec.signed,
        Some(apple),
        clue("the society routed around the gap and signed the truth")
      )
  }
