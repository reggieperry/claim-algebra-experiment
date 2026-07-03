package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.Eq
import cats.syntax.eq.*
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws

/** The GKT homomorphism laws no stock bundle carries: that [[Prov.evaluate]] (ν̂) is the unique
  * semiring homomorphism ℕ[X] → M induced by a token valuation `nu` (claim-algebra.html §3.3).
  * These are the consistency contract that lets the grade be RENDERED from the provenance rather
  * than stored — "compute the lineage once, evaluate it under any trust model," with no second
  * source of truth to drift. Checked per carrier `M` with `checkAll`, the same as any RuleSet.
  *
  * Quantifying over a random valuation `nu: Lineage => M` exercises the homomorphism against every
  * assignment, not one fixed model.
  */
object ProvHomomorphismLaws extends Laws:

  def all[M](using
      arbProv: Arbitrary[Prov],
      arbLineage: Arbitrary[Lineage],
      arbValuation: Arbitrary[Lineage => M],
      rig: CommutativeRig[M],
      eqM: Eq[M]
  ): RuleSet =
    new DefaultRuleSet(
      name = "provHomomorphism",
      parent = None,
      // ν̂ preserves the two semiring identities — the 0-vs-1 contract on which fail-closed rests.
      "ν̂(0) = 0 (the gap renders to the carrier's bottom)" ->
        forAll((nu: Lineage => M) => Prov.zero.evaluate(nu) === rig.zero),
      "ν̂(1) = 1 (the unit renders to the carrier's one)" ->
        forAll((nu: Lineage => M) => Prov.one.evaluate(nu) === rig.one),
      // ν̂ is a semiring homomorphism: it commutes with + and · — running the network then
      // rendering equals rendering the leaves then combining.
      "ν̂ is additive: ν̂(p + q) = ν̂(p) ⊕ ν̂(q)" ->
        forAll((p: Prov, q: Prov, nu: Lineage => M) =>
          Prov.plus(p, q).evaluate(nu) === rig.plus(p.evaluate(nu), q.evaluate(nu))
        ),
      "ν̂ is multiplicative: ν̂(p · q) = ν̂(p) ⊗ ν̂(q)" ->
        forAll((p: Prov, q: Prov, nu: Lineage => M) =>
          Prov.times(p, q).evaluate(nu) === rig.times(p.evaluate(nu), q.evaluate(nu))
        ),
      // ν̂ extends the valuation: a single cited token renders to exactly its valuation.
      "ν̂ extends nu: ν̂(single l) = nu(l)" ->
        forAll((l: Lineage, nu: Lineage => M) => Prov.single(l).evaluate(nu) === nu(l))
    )
