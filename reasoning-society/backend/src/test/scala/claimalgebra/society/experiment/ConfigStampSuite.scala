package claimalgebra.society
package experiment

import munit.FunSuite

import scala.concurrent.duration.*

/** The config-surface stamp: a stable fingerprint of everything a run's comparability depends on.
  * It must be deterministic (same inputs → same stamp) AND sensitive (a change to any hashed
  * element → a different stamp), so a later edit to a prompt, the budget, the quorum, or a model id
  * cannot silently redefine the condition under test.
  */
class ConfigStampSuite extends FunSuite:

  private val cfg =
    SocietyConfig(maxRounds = 16, roundTimeout = 30.seconds, hardDeadline = 5.minutes)
  private val cell = SweepCell(1.0, "perfect", "dev", k = 1)

  test("the stamp is 16 lowercase hex characters") {
    val s = ConfigStamp.of(cfg, cell, "haiku", "sonnet")
    assertEquals(s.length, 16)
    assert(s.forall(c => "0123456789abcdef".contains(c)), clue(s))
  }

  test("the stamp is stable across identical inputs") {
    assertEquals(
      ConfigStamp.of(cfg, cell, "haiku", "sonnet"),
      ConfigStamp.of(cfg, cell, "haiku", "sonnet")
    )
  }

  test("the stamp changes when the round budget changes") {
    assertNotEquals(
      ConfigStamp.of(cfg, cell, "haiku", "sonnet"),
      ConfigStamp.of(cfg.copy(maxRounds = 12), cell, "haiku", "sonnet")
    )
  }

  test("the stamp changes when the confirmation quorum k changes") {
    assertNotEquals(
      ConfigStamp.of(cfg, cell, "haiku", "sonnet"),
      ConfigStamp.of(cfg, cell.copy(k = 2), "haiku", "sonnet")
    )
  }

  test("the stamp changes when a model id changes") {
    assertNotEquals(
      ConfigStamp.of(cfg, cell, "haiku", "sonnet"),
      ConfigStamp.of(cfg, cell, "haiku", "opus")
    )
  }

  test("the inputs fold in the cohort, oracle, nudge, and grader fingerprints") {
    val ins = ConfigStamp.inputs(cfg, cell, "haiku", "sonnet")
    assert(ins.exists(_.startsWith("cohort=")), clue(ins))
    assert(ins.exists(_.startsWith("oracle=")), clue(ins))
    assert(ins.exists(_.startsWith("grader=")), clue(ins))
    // the nudge input carries the actual endgame urge, so an edit to it moves the stamp
    assert(
      ins.find(_.startsWith("nudge=")).exists(_.contains("FEW QUESTIONS REMAIN")),
      clue(ins)
    )
  }
