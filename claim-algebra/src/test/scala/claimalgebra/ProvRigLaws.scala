package claimalgebra

import cats.kernel.Eq
import cats.syntax.eq.*
import org.scalacheck.Arbitrary
import org.scalacheck.Prop.forAll
import org.typelevel.discipline.Laws

/** The ℕ[X] semiring law no stock bundle carries: the multiplicative annihilator `0 · x = 0`.
  * `RingLaws.commutativeRig` proves the two monoids, commutativity, the `0`/`1` identities, and
  * distributivity — but NOT annihilation, which for a rig with no additive inverse is an
  * independent axiom rather than a consequence of distributivity (you cannot derive `0 · x = 0` by
  * cancelling). It is the fail-closed law the experiment rests on — a gap times anything is the
  * gap, on both channels, under every trust model — so it gets its own RuleSet, per
  * scala-testing.md's instruction to author the annihilators as a custom `discipline.Laws`.
  */
object ProvRigLaws extends Laws:

  def annihilation(using Arbitrary[Prov], Eq[Prov]): RuleSet =
    new DefaultRuleSet(
      name = "provRig",
      parent = None,
      "left annihilator: 0 · x = 0" -> forAll((x: Prov) => Prov.times(Prov.zero, x) === Prov.zero),
      "right annihilator: x · 0 = 0" -> forAll((x: Prov) => Prov.times(x, Prov.zero) === Prov.zero)
    )
