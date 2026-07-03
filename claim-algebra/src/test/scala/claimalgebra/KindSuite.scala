package claimalgebra

import munit.FunSuite

/** `Kind` (G3) — the closed set of evidence kinds a citation can carry, each routing a conflict to
  * a different desk. A pure routing decoration: never a gate or grade input. These pin the closed
  * set and the conservative default.
  */
class KindSuite extends FunSuite:

  test("Kind is a closed set of four routing kinds") {
    assertEquals(Kind.values.length, 4)
    assertEquals(
      Kind.values.toSet,
      Set(Kind.Extraction, Kind.Definitional, Kind.TemporalRetraction, Kind.Verification)
    )
  }

  test("the conservative default kind is Extraction") {
    assertEquals(Kind.Default, Kind.Extraction)
  }
