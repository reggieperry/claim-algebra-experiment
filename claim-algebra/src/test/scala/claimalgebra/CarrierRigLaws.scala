package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.Eq
import cats.syntax.eq.*
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws

/** The carrier-level multiplicative annihilator `0_M · x = 0_M`, per render carrier `M`. Exactly as
  * [[ProvRigLaws]] does for `Prov`, `RingLaws.commutativeRig` proves the two monoids, the
  * identities, and distributivity but NOT annihilation (an independent rig axiom). Yet `ν̂`'s
  * multiplicativity at the gap — `ν̂(0) = M.zero`, the hinge that keeps fail-closed model-free —
  * needs every carrier to annihilate. The foundations proof flagged that no `RuleSet` pinned this
  * for the carriers; this closes that gap for `Lev` / `Viterbi` / `Count`.
  */
object CarrierRigLaws extends Laws:

  def annihilation[M](using rig: CommutativeRig[M], arb: Arbitrary[M], eq: Eq[M]): RuleSet =
    new DefaultRuleSet(
      name = "carrierRig",
      parent = None,
      "left annihilator: 0 · x = 0" -> forAll((x: M) => rig.times(rig.zero, x) === rig.zero),
      "right annihilator: x · 0 = 0" -> forAll((x: M) => rig.times(x, rig.zero) === rig.zero)
    )
