package claimalgebra.society
package experiment

import cats.effect.IO
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** E2 — closing the seam (fallible-oracle-interpretation §E2). The gate's accept rule is
  * `verify = C ∨ O`, and the weak branch C (two-agent corroboration) signs without consulting
  * ground truth, so two agents agreeing on a wrong candidate sign it even at a PERFECT oracle. The
  * fix (`SocietyConfig.corroborationSigns = false`) drops the standalone C disjunct: a 2-backer
  * candidate is demoted to `Unconfirmed` and must obtain a ground-truth guess-confirmation before
  * signing — `verify` narrows toward `O` alone. These are the A/B and the structural safety proof,
  * hermetic:
  *
  *   - seam-OPEN reproduces the perfect-oracle fail-open (a wrong 2-backer signs),
  *   - seam-GATED does not (the oracle rejects the wrong candidate, so the game abstains),
  *   - seam-GATED never signs without a `Yes` confirmation of the signed candidate (the direct
  *     proof that no unchecked sign occurs — the strongest evidence per the E2 committee).
  */
class SeamClosureSuite extends CatsEffectSuite with SocietyFixtures:

  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")
  private val perfectCell = SweepCell(1.0, "perfect", "seam")
  private val truthUnknown =
    TruthOracle.pure((_, _) => OracleAnswer.Unknown) // guesses are structural

  private def cfg(corroborationSigns: Boolean): SocietyConfig =
    SocietyConfig(
      maxRounds = 3,
      roundTimeout = 5.seconds,
      hardDeadline = 1.minute,
      corroborationSigns = corroborationSigns
    )

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

  /** Two distinct agents both back candidate `c` → a clean 2-backer winner. */
  private def agreeCohort(c: String): IO[AgentId => LlmCall[AgentMoveDto]] =
    cohortFrom(
      Map(
        "driller" -> List(Right(StubLlm.move("assert", c, "x")), Right(StubLlm.pass)),
        "splitter" -> List(Right(StubLlm.move("corroborate", c, "agreed")), Right(StubLlm.pass))
      )
    )

  test(
    "seam-OPEN: two agents agreeing on a WRONG candidate sign it — the perfect-oracle fail-open (baseline)"
  ) {
    for
      llmFor <- agreeCohort("cat") // both back cat; the target is dog
      result <- OracleSweep.runOneGame(llmFor, cfg(true), perfectCell, dog, truthUnknown, seed = 1L)
    yield
      val rec = result._1
      assertEquals(
        rec.outcome,
        PrimaryOutcome.SignWrong,
        clue("seam-open: two backers sign cat, the wrong answer, unchecked")
      )
      assertEquals(rec.signed, Some(cat))
  }

  test(
    "seam-GATED: the same wrong 2-backer candidate is NOT signed — the oracle rejects it (E2 closes the seam)"
  ) {
    for
      llmFor <- agreeCohort("cat")
      result <- OracleSweep.runOneGame(
        llmFor,
        cfg(false),
        perfectCell,
        dog,
        truthUnknown,
        seed = 1L
      )
    yield
      val rec = result._1
      assertEquals(
        rec.outcome,
        PrimaryOutcome.Abstain,
        clue("seam-gated: cat must pass the oracle guess-confirmation, which structurally says No")
      )
      assertEquals(rec.signed, None)
  }

  test(
    "seam-GATED: a signature exists only after a Yes confirmation of it — no unchecked sign, and signPath reads OracleConfirmed"
  ) {
    for
      llmFor <- agreeCohort("dog") // both back the TRUE target → the oracle confirms → it signs
      result <- OracleSweep.runOneGame(
        llmFor,
        cfg(false),
        perfectCell,
        dog,
        truthUnknown,
        seed = 1L
      )
    yield
      val (rec, log) = result
      assertEquals(
        rec.outcome,
        PrimaryOutcome.SignCorrect,
        clue("a correct 2-backer, oracle-confirmed, signs")
      )
      assertEquals(
        rec.signPath,
        Some(SignPath.OracleConfirmed),
        clue("a confirmed 2-backer sign is OracleConfirmed, not mislabeled BackerQuorum")
      )
      // The structural safety invariant: every GateSign is preceded by a Yes confirmation of it.
      val signs = log.zipWithIndex.collect { case (Event.GateSign(_, _, c), i) => (c, i) }
      assert(signs.nonEmpty, clue("this game signs"))
      signs.foreach { (c, i) =>
        val confirmedBefore = log.take(i).exists {
          case Event.GuessAnswered(_, _, `c`, OracleAnswer.Yes) => true
          case _ => false
        }
        assert(
          confirmedBefore,
          clue(s"GateSign($c) must follow GuessAnswered($c, Yes) under seam-gated")
        )
      }
  }
