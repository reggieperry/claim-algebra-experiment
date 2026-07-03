package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** κ̂ — the kind read (G3). On the provenance polynomial it is an ADDITIVE-monoid homomorphism
  * ℕ[X_K] → (Set[Kind], ∪, ∅): it sends both `+` and `·` to ∪ and both `0` and `1` to ∅, so it is
  * NOT a rig homomorphism (it identifies the two units) — which is exactly why it is a pure read,
  * never a `CommutativeRig` instance and never folded into ν̂. On a carrier, `conflictKinds` reads
  * it off the con-channel total: the kind-set of a glut's refutation — which desks a conflict
  * routes to.
  */
class KappaSuite extends ScalaCheckSuite:

  test("κ̂(0) = ∅ and κ̂(1) = ∅ — the units collapse, so κ̂ is not a rig homomorphism") {
    assertEquals(Prov.zero.kinds, Set.empty[Kind])
    assertEquals(Prov.one.kinds, Set.empty[Kind])
  }

  property("κ̂(single(l)) = {l.kind} — a single citation contributes exactly its kind") {
    forAll((l: Lineage) => Prov.single(l).kinds == Set(l.kind))
  }

  property("κ̂(p + q) = κ̂(p) ∪ κ̂(q) — additive-monoid homomorphism") {
    forAll((p: Prov, q: Prov) => Prov.plus(p, q).kinds == (p.kinds union q.kinds))
  }

  property("κ̂(p · q) = κ̂(p) ∪ κ̂(q) when both are non-zero, ∅ when either is zero (joint use)") {
    forAll { (p: Prov, q: Prov) =>
      val expected = if p.isZero || q.isZero then Set.empty[Kind] else p.kinds union q.kinds
      Prov.times(p, q).kinds == expected
    }
  }

  // The carrier read — propagation (foundations: T-kind-propagation).

  property("conflictKinds(leaf) = ∅ — a clean leaf has no con-channel, so no conflict kind") {
    forAll((l: Lineage) => Testimony.conflictKinds(Testimony.leaf(1, Prov.single(l))).isEmpty)
  }

  property("conflictKinds(refute(t)) = κ̂ of t's pro-channel — refutation swaps the channels") {
    forAll((t: Testimony[Int]) => Testimony.conflictKinds(Testimony.refute(t)) == t.provPro.kinds)
  }

  property("conflictKinds(corroborate(s, t)) = conflictKinds(s) ∪ conflictKinds(t)") {
    forAll { (s: Testimony[Int], t: Testimony[Int]) =>
      Testimony.conflictKinds(Testimony.corroborate(s, t)) ==
        (Testimony.conflictKinds(s) union Testimony.conflictKinds(t))
    }
  }

  test("conflictKinds(gap) = ∅ — a gap routes to no desk") {
    assertEquals(Testimony.conflictKinds(Testimony.gap[Int]), Set.empty[Kind])
  }

  test("a refuted figure carries its con-channel kind — the worked routing read") {
    // A balance-sheet figure refuted by a temporal retraction: the glut's con cites a
    // TemporalRetraction span, so κ̂ names that kind (slice 3 maps it to the deal-lead desk).
    val retraction = Lineage.from("notes-withdrawn", Kind.TemporalRetraction)
    val con = retraction.fold(Prov.zero)(Prov.single)
    val refuted =
      Testimony.single(1, Prov.zero, con) // corner False/Glut depending; con cites the kind
    assertEquals(Testimony.conflictKinds(refuted), Set(Kind.TemporalRetraction))
  }
