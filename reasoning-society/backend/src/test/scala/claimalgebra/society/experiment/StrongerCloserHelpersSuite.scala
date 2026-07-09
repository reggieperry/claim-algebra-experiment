package claimalgebra.society
package experiment

import munit.FunSuite

/** The pure helpers the stronger-closer trial rests on: the Newcombe difference interval (the
  * pre-committed unblock test), the wrong-guess-reached-confirmation base-rate `w`, and the typed
  * role ids the model-allocation seam derives the strong set from.
  */
class StrongerCloserHelpersSuite extends FunSuite with SocietyFixtures:

  // --- ProportionDiff.newcombe95 ---

  test("newcombe95: a clear difference excludes 0 (unblock detected)") {
    // S1 = 8/8, W = 0/8 — an unmistakable unblock
    val (lo, hi) = ProportionDiff.newcombe95(8, 8, 0, 8)
    assert(lo > 0.0, clue((lo, hi)))
    assert(ProportionDiff.excludesZero(8, 8, 0, 8))
  }

  test("newcombe95: a tiny difference at small N includes 0 (not distinguishable)") {
    // S1 = 2/20 (0.10) vs W = 1/20 (0.05) — inside the noise
    val (lo, hi) = ProportionDiff.newcombe95(2, 20, 1, 20)
    assert(lo < 0.0 && hi > 0.0, clue((lo, hi)))
    assert(!ProportionDiff.excludesZero(2, 20, 1, 20))
  }

  test("newcombe95: the point estimate p1-p2 lies inside the interval") {
    val (lo, hi) = ProportionDiff.newcombe95(10, 32, 4, 32)
    val point = 10.0 / 32 - 4.0 / 32
    assert(lo <= point && point <= hi, clue((lo, point, hi)))
  }

  // --- Adjudication.wrongGuessReachedConfirmation (the base rate w) ---

  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  private def logWith(events: Event*): Vector[Event] =
    Event.TargetRegistered(1, 0L, dog, 1L) +: events.toVector

  test("wrongGuessReachedConfirmation: a wrong guess posed+answered is true (w counts it)") {
    val log = logWith(Event.GuessAnswered(2, 0L, cat, OracleAnswer.Yes)) // guessed cat, truth dog
    assert(Adjudication.wrongGuessReachedConfirmation(log))
  }

  test("wrongGuessReachedConfirmation: only correct guesses is false") {
    val log = logWith(Event.GuessAnswered(2, 0L, dog, OracleAnswer.Yes)) // guessed the truth
    assert(!Adjudication.wrongGuessReachedConfirmation(log))
  }

  test("wrongGuessReachedConfirmation: no guess reached is false") {
    assert(!Adjudication.wrongGuessReachedConfirmation(logWith()))
  }

  // --- AgentStrategy role ids (the allocation seam's strong set) ---

  // --- StrongerCloserOutcome.classify (the direction-aware adjudicator) ---

  import StrongerCloserOutcome.Outcome
  private def classify(cW: Int, cS1: Int, cD: Int): Outcome =
    StrongerCloserOutcome.classify(cW, 64, cS1, 64, cD, 64, driftThreshold = 0.12)

  test(
    "classify: S1 does not clear W but D does — SEARCH binds (the bug the D arm exists to catch)"
  ) {
    // the reviewer's scenario (a): W=2, S1=4 (S1-W includes 0), D=20 (D>>W). Must NOT be NotUnblocked.
    assertEquals(classify(cW = 2, cS1 = 4, cD = 20), Outcome.SearchBinds)
  }

  test("classify: S1 clears W and D exceeds S1 — SEARCH binds") {
    assertEquals(classify(cW = 0, cS1 = 8, cD = 24), Outcome.SearchBinds)
  }

  test("classify: S1 clears W and D ~ S1 — closer suffices") {
    assertEquals(classify(cW = 0, cS1 = 16, cD = 17), Outcome.CloserSuffices)
  }

  test("classify: D distinguishably below S1 — the D<S1 anomaly, not 'D exceeds S1'") {
    assertEquals(classify(cW = 0, cS1 = 16, cD = 2), Outcome.AnomalyDBelowS1)
  }

  test("classify: nothing clears W — not unblocked") {
    assertEquals(classify(cW = 0, cS1 = 1, cD = 1), Outcome.NotUnblocked)
  }

  test("classify: W above the drift threshold — Drift caveat, checked first") {
    assertEquals(classify(cW = 10, cS1 = 20, cD = 30), Outcome.Drift) // 10/64 = 0.156 > 0.12
  }

  test("classify: S1 distinguishably below W (drift disabled) — anomaly") {
    assertEquals(
      StrongerCloserOutcome.classify(16, 64, 2, 64, 2, 64, driftThreshold = 1.0),
      Outcome.AnomalyS1BelowW
    )
  }

  test("the role ids are the three distinct cohort roles, typed") {
    assertEquals(AgentStrategy.closerId.map(_.value), Some("driller"))
    assertEquals(AgentStrategy.adversaryId.map(_.value), Some("skeptic"))
    assertEquals(AgentStrategy.proposerId.map(_.value), Some("splitter"))
    val ids =
      List(AgentStrategy.closerId, AgentStrategy.adversaryId, AgentStrategy.proposerId).flatten
    assertEquals(ids.distinct.size, 3, clue(ids))
  }
