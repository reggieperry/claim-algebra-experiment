package claimalgebra

import munit.FunSuite

/** Behavioral pins for the `Lev` smart constructor — the `[0, 1]` invariant the opaque type
  * enforces. The fail-closed handling of `NaN` and out-of-range input is what keeps the gate's
  * `grade ≥ θ` test from being cleared by a forged or non-finite confidence, so it is pinned as a
  * regression.
  */
class LevSuite extends FunSuite:

  test("deg is fail-closed on NaN and non-positive input — no evidence") {
    assertEquals(Lev.deg(Double.NaN), Lev.bottom)
    assertEquals(Lev.deg(0.0), Lev.bottom)
    assertEquals(Lev.deg(-1.0), Lev.bottom)
  }

  test("deg clamps at or above one to the top") {
    assertEquals(Lev.deg(1.0), Lev.top)
    assertEquals(Lev.deg(2.0), Lev.top)
  }

  test("deg keeps an interior strength unchanged") {
    assertEqualsDouble(Lev.deg(0.5).strength, 0.5, 0.0)
  }

  test("bottom is the only value with zero evidence") {
    assert(Lev.bottom.isBottom)
    assert(!Lev.top.isBottom)
    assert(!Lev.deg(0.01).isBottom)
  }
