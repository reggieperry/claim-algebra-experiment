package claimalgebra

import munit.FunSuite

/** The render carriers' constructors are fail-closed at the boundary, mirroring `Lev.deg`: a
  * non-finite or out-of-range input becomes the safe bottom, never a propagated `NaN` that could
  * later clear the acceptance threshold (a fail-OPEN confidence). Surfaced by the foundations proof
  * `T-carrier-fail-closed`.
  */
class TrustSuite extends FunSuite:

  test("Viterbi.apply is fail-closed on NaN — no confidence, not a propagated NaN") {
    assertEqualsDouble(Viterbi(Double.NaN).value, 0.0, 0.0)
  }

  test("Viterbi.apply clamps to [0, 1] and keeps an interior value") {
    assertEqualsDouble(Viterbi(-0.5).value, 0.0, 0.0)
    assertEqualsDouble(Viterbi(1.5).value, 1.0, 0.0)
    assertEqualsDouble(Viterbi(0.4).value, 0.4, 1e-12)
  }

  test("Count.apply is fail-closed on a negative count") {
    assertEquals(Count(-3L).value, 0L)
  }
