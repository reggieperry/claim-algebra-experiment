package claimalgebra

import munit.FunSuite

/** The validating smart constructor for the `Lineage` opaque type — the negative space the type
  * itself cannot state (scala-types.md): a citation must point somewhere, so a blank id is rejected
  * and surrounding whitespace is normalized.
  */
class LineageSuite extends FunSuite:

  test("from rejects a blank or whitespace-only id — fail-closed") {
    assert(Lineage.from("").isEmpty)
    assert(Lineage.from("   ").isEmpty)
  }

  test("from keeps a non-blank id, trimmed") {
    assertEquals(Lineage.from("s1").map(_.id), Option("s1"))
    assertEquals(Lineage.from("  s1  ").map(_.id), Option("s1"))
  }

  test("from defaults the kind to None — an un-annotated citation carries no kind") {
    assertEquals(Lineage.from("s1").flatMap(_.kind), None)
  }

  test("from carries a kind when given one") {
    assertEquals(
      Lineage.from("s1", Some(Generators.AlphaKind)).flatMap(_.kind),
      Option[Kind](Generators.AlphaKind)
    )
  }

  test("same id with different kinds are distinct — map-key sound over (id, kind)") {
    assertNotEquals(
      Lineage.from("s1", Some(Generators.AlphaKind)),
      Lineage.from("s1", Some(Generators.BetaKind))
    )
  }
