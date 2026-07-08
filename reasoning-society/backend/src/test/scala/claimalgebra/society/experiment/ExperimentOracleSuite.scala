package claimalgebra.society
package experiment

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite

/** Slice 1 (fallible-oracle): the error models (pure) and the [[ExperimentOracle]] (IO) — the
  * fallible oracle channel that consults the sealed truth, applies an error model, and records
  * reproducible draws.
  */
class ExperimentOracleSuite extends CatsEffectSuite with SocietyFixtures:

  private def qid(s: String): QuestionId = QuestionId.from(s).fold(fail(_), identity)
  private def q(id: String, text: String): Question = Question(qid(id), text)
  private val apple = mkAnswer("apple")

  // A deterministic pseudo-uniform draw in [0, 1) per (candidate, pose) — a hermetic, reproducible
  // stand-in for the oracle's seeded RNG, so the statistical correlation tests never flake.
  private def drawOf(j: Int, i: Int): Double =
    (scala.util.hashing.MurmurHash3.stringHash(
      s"draw:$j:$i"
    ) & 0x7fffffff).toDouble / Int.MaxValue.toDouble

  private def answerOf(m: HumanMove): OracleAnswer = m match
    case HumanMove.Answer(a) => a
    case HumanMove.Challenge(_) => fail("expected an Answer, got a Challenge")

  // --- ErrorModel (pure) ---

  test("flip swaps yes<->no and leaves a gap a gap") {
    assertEquals(ErrorModel.flip(OracleAnswer.Yes), OracleAnswer.No)
    assertEquals(ErrorModel.flip(OracleAnswer.No), OracleAnswer.Yes)
    assertEquals(ErrorModel.flip(OracleAnswer.Unknown), OracleAnswer.Unknown)
  }

  test("perfect & IndependentUniform(1.0) never corrupt; IndependentUniform(0.0) always flips") {
    val question = q("q1", "is it a fruit?")
    assertEquals(ErrorModel.perfect.corrupt(question, OracleAnswer.Yes, 0.99), OracleAnswer.Yes)
    assertEquals(
      ErrorModel.IndependentUniform(1.0).corrupt(question, OracleAnswer.Yes, 0.99),
      OracleAnswer.Yes
    )
    assertEquals(
      ErrorModel.IndependentUniform(0.0).corrupt(question, OracleAnswer.Yes, 0.0),
      OracleAnswer.No
    )
  }

  test("SystematicPerQuestion is deterministic on re-ask — same question, same answer") {
    val model = ErrorModel.SystematicPerQuestion(p = 0.7, seed = 99L)
    val question = q("q1", "is it a living organism?")
    // The per-answer draw is IGNORED; two different draws yield the same answer for the same question.
    assertEquals(
      model.corrupt(question, OracleAnswer.Yes, 0.1),
      model.corrupt(question, OracleAnswer.Yes, 0.9),
      clue("systematic error re-asks identically — a blind spot is stable")
    )
  }

  test("SystematicPerQuestion has blind spots — some questions wrong, others right (at p<1)") {
    val model = ErrorModel.SystematicPerQuestion(p = 0.5, seed = 3L)
    val outcomes =
      (1 to 40).map(i => model.corrupt(q(s"q$i", s"question number $i?"), OracleAnswer.Yes, 0.5))
    assert(outcomes.contains(OracleAnswer.Yes), clue("some questions answered truthfully"))
    assert(outcomes.contains(OracleAnswer.No), clue("some questions in the blind spot (flipped)"))
  }

  // --- ExperimentOracle (IO) ---

  test("perfect oracle delivers the truthful table answer and records zero corruption") {
    val truth = TruthOracle.table(Map((apple, "is it a fruit?") -> OracleAnswer.Yes))
    for
      oracle <- ExperimentOracle.make(apple, truth, ErrorModel.perfect, seed = 1L)
      move <- oracle.respond(q("q1", "is it a fruit?"))
      draws <- oracle.recordedDraws
    yield
      assertEquals(answerOf(move), OracleAnswer.Yes)
      assertEquals(draws.map(_.corrupted), Vector(false))
  }

  test("a corrupting oracle flips the truthful answer and records the corruption") {
    val truth = TruthOracle.table(Map((apple, "is it a fruit?") -> OracleAnswer.Yes))
    for
      oracle <- ExperimentOracle.make(apple, truth, ErrorModel.IndependentUniform(0.0), seed = 1L)
      move <- oracle.respond(q("q1", "is it a fruit?"))
      draws <- oracle.recordedDraws
    yield
      assertEquals(answerOf(move), OracleAnswer.No, clue("p=0 flips every answer"))
      assertEquals(
        draws.map(d => (d.truthful, d.delivered, d.corrupted)),
        Vector((OracleAnswer.Yes, OracleAnswer.No, true))
      )
  }

  test("a question absent from the truth table is a fail-closed gap (Unknown)") {
    for
      oracle <- ExperimentOracle.make(apple, TruthOracle.table(Map.empty), ErrorModel.perfect, 1L)
      move <- oracle.respond(q("q9", "an unregistered question?"))
    yield assertEquals(answerOf(move), OracleAnswer.Unknown)
  }

  test("the same seed reproduces the same draw sequence, at roughly the expected corruption rate") {
    val truth = TruthOracle.pure((_, _) => OracleAnswer.Yes)
    def run: IO[Vector[Boolean]] =
      ExperimentOracle.make(apple, truth, ErrorModel.IndependentUniform(0.8), seed = 4242L).flatMap {
        o =>
          (1 to 60).toList.traverse_(i => o.respond(q(s"q$i", s"q$i?"))) *>
            o.recordedDraws.map(_.map(_.corrupted))
      }
    for
      a <- run
      b <- run
    yield
      assertEquals(a, b, clue("a seeded oracle is bit-reproducible"))
      val rate = a.count(identity).toDouble / a.size
      assert(rate > 0.05 && rate < 0.40, clue(s"corruption rate ~ 1-p = 0.2, got $rate"))
  }

  test("ModelTruthOracle.parse is fail-closed — only yes/no map, everything else is a gap") {
    assertEquals(ModelTruthOracle.parse("yes"), OracleAnswer.Yes)
    assertEquals(ModelTruthOracle.parse(" NO "), OracleAnswer.No)
    assertEquals(ModelTruthOracle.parse("unknown"), OracleAnswer.Unknown)
    assertEquals(ModelTruthOracle.parse("maybe"), OracleAnswer.Unknown)
    assertEquals(ModelTruthOracle.parse(""), OracleAnswer.Unknown)
  }

  // --- Slice 4: structural guess-truth ---

  test(
    "guessTruth is structural on guess qids (Yes iff candidate==target); None for a property Q"
  ) {
    val dog = mkAnswer("dog")
    assertEquals(
      ExperimentOracle.guessTruth(apple, q("guess-apple", "Is it apple?")),
      Some(OracleAnswer.Yes)
    )
    assertEquals(
      ExperimentOracle.guessTruth(apple, q("guess-dog", "Is it dog?")),
      Some(OracleAnswer.No),
      clue(s"$dog is not the target")
    )
    assertEquals(ExperimentOracle.guessTruth(apple, q("q1", "is it a fruit?")), None)
  }

  test(
    "respond answers a guess STRUCTURALLY, bypassing the truth oracle (closes the model-noise confound)"
  ) {
    // A truth oracle that would (wrongly) say Yes to EVERYTHING; the structural short-circuit ignores it.
    val lying = TruthOracle.pure((_, _) => OracleAnswer.Yes)
    for
      oracle <- ExperimentOracle.make(apple, lying, ErrorModel.perfect, seed = 1L)
      correct <- oracle.respond(q("guess-apple", "Is it apple?"))
      wrong <- oracle.respond(q("guess-dog", "Is it dog?"))
    yield
      assertEquals(
        answerOf(correct),
        OracleAnswer.Yes,
        clue("guess of the target → structural Yes")
      )
      assertEquals(
        answerOf(wrong),
        OracleAnswer.No,
        clue("guess of a non-target → structural No, NOT the lying oracle's Yes")
      )
  }

  // --- Slice 4: CorrelatedConfirmations — the redundancy/correlation crown jewel ---

  test("CorrelatedConfirmations: the per-pose MARGINAL flip rate is ~1-p at both ρ endpoints") {
    val p = 0.6
    def marginal(rho: Double): Double =
      val model = ErrorModel.CorrelatedConfirmations(p, rho, seed = 11L)
      val n = 5000
      val flips = (0 until n).count(j =>
        model.corrupt(
          q(s"guess-c$j", s"Is it c$j?"),
          OracleAnswer.No,
          drawOf(j, 0)
        ) == OracleAnswer.Yes
      )
      flips.toDouble / n
    assert(
      math.abs(marginal(0.0) - (1 - p)) < 0.05,
      clue(s"ρ=0 marginal ≈ ${1 - p}, got ${marginal(0.0)}")
    )
    assert(
      math.abs(marginal(1.0) - (1 - p)) < 0.05,
      clue(s"ρ=1 marginal ≈ ${1 - p}, got ${marginal(1.0)}")
    )
  }

  test(
    "CorrelatedConfirmations: ρ=0 suppresses the joint fail-open to (1-p)^k; ρ=1 does NOT (monoculture)"
  ) {
    val p = 0.5
    val k = 2
    def jointFlipRate(rho: Double): Double =
      val model = ErrorModel.CorrelatedConfirmations(p, rho, seed = 11L)
      val n = 5000
      val allFlipped = (0 until n).count { j =>
        val qn = q(s"guess-c$j", s"Is it c$j?")
        (0 until k).forall(i =>
          model.corrupt(qn, OracleAnswer.No, drawOf(j, i)) == OracleAnswer.Yes
        )
      }
      allFlipped.toDouble / n
    val independent = jointFlipRate(0.0) // ≈ (1-p)^k = 0.25
    val correlated = jointFlipRate(1.0) //  ≈ (1-p)   = 0.50
    assert(independent > 0.18 && independent < 0.32, clue(s"ρ=0 joint ≈ 0.25, got $independent"))
    assert(correlated > 0.42 && correlated < 0.58, clue(s"ρ=1 joint ≈ 0.50, got $correlated"))
    assert(
      correlated > independent + 0.1,
      clue("redundancy suppresses the fail-open ONLY when the errors are independent")
    )
  }
