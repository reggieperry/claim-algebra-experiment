package claimalgebra

import munit.FunSuite

/** The routing consumer surface for κ̂ (G3): a glut's kind-set names which desks must reconcile it.
  * This is the capability the kind tag exists for — conflict routing / blame-attribution — read off
  * any claim's con-channel, composed across sources for free by the provenance algebra.
  */
class RoutingSuite extends FunSuite:

  test("each kind routes to its owning desk") {
    assertEquals(Routing.deskFor(Kind.Extraction), Desk.DataQuality)
    assertEquals(Routing.deskFor(Kind.Definitional), Desk.CreditPolicy)
    assertEquals(Routing.deskFor(Kind.TemporalRetraction), Desk.DealLead)
    assertEquals(Routing.deskFor(Kind.Verification), Desk.Audit)
  }

  test("the empty kind-set routes to no desk — a non-glut routes to no one") {
    assertEquals(Routing.desks(Set.empty), Set.empty[Desk])
  }

  test("a multi-kind conflict routes to multiple desks") {
    assertEquals(
      Routing.desks(Set(Kind.Verification, Kind.TemporalRetraction)),
      Set(Desk.Audit, Desk.DealLead)
    )
  }

  test("route reads a clean True or gap as no desk — nothing to reconcile") {
    assertEquals(Routing.route(Testimony.leaf(1, Generators.prov("s"))), Set.empty[Desk])
    assertEquals(Routing.route(Testimony.gap[Int]), Set.empty[Desk])
  }

  test(
    "a conflict composed from two kinds routes to both desks — via one corroborate, no per-kind code"
  ) {
    // Two refuting sources of DIFFERENT kinds against the same value pool to a glut whose con carries
    // both kinds; κ̂ unions them through ⊕ₖ automatically, and routing names both desks.
    val verify = Lineage.from("audit-refutes", Kind.Verification).fold(Prov.zero)(Prov.single)
    val withdrawn =
      Lineage.from("notes-withdrawn", Kind.TemporalRetraction).fold(Prov.zero)(Prov.single)
    val asserted = Generators.prov("balance-sheet")
    val a = Testimony.single(1, asserted, verify) // glut: asserted, refuted (verification)
    val b = Testimony.single(1, Prov.zero, withdrawn) // refuted (temporal retraction)
    val pooled = Testimony.corroborate(a, b)
    assertEquals(Testimony.conflictKinds(pooled), Set(Kind.Verification, Kind.TemporalRetraction))
    assertEquals(Routing.route(pooled), Set(Desk.Audit, Desk.DealLead))
  }
