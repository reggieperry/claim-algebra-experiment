package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** κ̂ — the kind read (G3). On the provenance polynomial it is an ADDITIVE-monoid homomorphism
  * ℕ[X_K] → (Set[Kind], ∪, ∅): it sends both `+` and `·` to ∪ and both `0` and `1` to ∅, so it is
  * NOT a rig homomorphism (it identifies the two units) — which is exactly why it is a pure read,
  * never a `CommutativeRig` instance and never folded into ν̂. On a carrier, `conflictKinds` reads
  * it off the con-channel total: the kind-set of a glut's refutation — which kinds of refuting
  * evidence a conflict carries (a consumer routes those to whoever owns them).
  */
class KappaSuite extends ScalaCheckSuite:

  test("κ̂(0) = ∅ and κ̂(1) = ∅ — the units collapse, so κ̂ is not a rig homomorphism") {
    assertEquals(Prov.zero.kinds, Set.empty[Kind])
    assertEquals(Prov.one.kinds, Set.empty[Kind])
  }

  property("κ̂(single(l)) = l.kind.toSet — a citation contributes its kind, or ∅ if un-annotated") {
    forAll((l: Lineage) => Prov.single(l).kinds == l.kind.toSet)
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

  test("conflictKinds(gap) = ∅ — a gap has no conflict kind") {
    assertEquals(Testimony.conflictKinds(Testimony.gap[Int]), Set.empty[Kind])
  }

  test("a refuted token carries its con-channel kind — the worked read") {
    // A figure refuted by a con-channel token that carries a kind: κ̂ names that kind (a consumer
    // then routes it). Uses a neutral test-kind; a domain re-checks the same read over its own enum.
    val con = Lineage.from("withdrawn", Some(Generators.AlphaKind)).fold(Prov.zero)(Prov.single)
    val refuted = Testimony.single(1, Prov.zero, con)
    assertEquals(Testimony.conflictKinds(refuted), Set[Kind](Generators.AlphaKind))
  }
