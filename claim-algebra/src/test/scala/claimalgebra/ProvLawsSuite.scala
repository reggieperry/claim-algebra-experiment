package claimalgebra

import algebra.laws.RingLaws
import cats.kernel.laws.discipline.{CommutativeMonoidTests, EqTests, OrderTests}
import munit.DisciplineSuite

import Generators.given

/** `Prov`, the provenance polynomial ℕ[X]: a free commutative semiring, NOT a semilattice — its `+`
  * is a commutative monoid (`p + p = 2p`), so corroboration's multiplicity survives a
  * non-idempotent render. `commutativeRig` pins the two monoids, commutativity, the `0`/`1`
  * identities, and distributivity; the fail-closed annihilator `0 · x = 0` is a SEPARATE rig axiom,
  * checked by the custom [[ProvRigLaws]]. The homomorphism [[ProvHomomorphismLaws]] is then checked
  * into three carriers spanning the faithfulness dimensions — [[Lev]] (the fuzzy carrier,
  * idempotent both), [[Viterbi]] (non-idempotent `·`, exponents read), [[Count]] (non-idempotent
  * `+`, coefficients read) — and each carrier is itself proven a lawful CommutativeRig over the
  * (dyadic, bounded) domain its generator draws, since the homomorphism's soundness depends on it.
  */
class ProvLawsSuite extends DisciplineSuite:

  // The multiplicative `combineAll`/`product` laws multiply a generated LIST of polynomials; over the
  // ~25-token pool a long list's product rarely merges and blows up combinatorially (up to 3^k
  // monomials → an OutOfMemoryError on an unlucky seed). Cap the list size so the product stays
  // bounded — per-value coverage is unchanged (the genProv/genMonomial sizes are fixed, not
  // size-driven). scala-testing.md: constrain at generation time, never pin a lucky seed.
  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMaxSize(8)

  checkAll("Prov.eq", EqTests[Prov].eqv)
  checkAll(
    "Monomial.commutativeMonoid (ℕ^(X), the Prov key)",
    CommutativeMonoidTests[Monomial].commutativeMonoid
  )
  checkAll("Prov.commutativeRig (ℕ[X])", RingLaws[Prov].commutativeRig)
  checkAll("Prov.annihilator 0 · x = 0 (fail-closed)", ProvRigLaws.annihilation)

  // The render carriers must themselves be lawful rigs — ν̂'s soundness theorem requires it.
  checkAll("Lev.commutativeRig (fuzzy carrier)", RingLaws[Lev].commutativeRig)
  checkAll("Viterbi.commutativeRig (dyadic domain)", RingLaws[Viterbi].commutativeRig)
  checkAll("Count.commutativeRig", RingLaws[Count].commutativeRig)

  // The gate compares the rendered grade through Order[M], so the carrier Orders are law-checked
  // like Lev's (OrderTests[Lev] is in LevLawsSuite) — not just their rig structure.
  checkAll("Viterbi.order", OrderTests[Viterbi].order)
  checkAll("Count.order", OrderTests[Count].order)

  checkAll("Prov.ν̂ into Lev (fuzzy, idempotent + and ·)", ProvHomomorphismLaws.all[Lev])
  checkAll(
    "Prov.ν̂ into Viterbi (non-idempotent ·, exponents read)",
    ProvHomomorphismLaws.all[Viterbi]
  )
  checkAll(
    "Prov.ν̂ into Count (non-idempotent +, coefficients read)",
    ProvHomomorphismLaws.all[Count]
  )

  // Why the trust model must be a counting/probability RIG and never noisy-OR: ν̂ is a homomorphism
  // only if the target is DISTRIBUTIVE, and noisy-OR (a ⊕ b = a + b − a·b), the tempting
  // "independent evidence" combinator, is NOT distributive — so it is not a lawful CommutativeRig and
  // cannot be a ν̂ target. typeclass laws are not compiler-enforced, so pin the disqualifying witness
  // directly: a · (b ⊕ c) ≠ (a·b) ⊕ (a·c). Exact dyadic values, so the inequality is not a float artifact.
  test(
    "noisy-OR is non-distributive — disqualified as a ν̂ target (why we chose the counting rig)"
  ) {
    def noisyOr(x: Double, y: Double): Double = x + y - x * y
    val (a, b, c) = (0.5, 0.5, 0.5)
    val lhs = a * noisyOr(b, c) // 0.5 · 0.75              = 0.375
    val rhs = noisyOr(a * b, a * c) // noisyOr(0.25, 0.25) = 0.4375
    assert(
      lhs != rhs,
      s"noisy-OR distributed ($lhs == $rhs) — it would wrongly qualify as a target"
    )
  }

  // Negative space: the normalizing constructor drops a zero-coefficient term, so no phantom
  // polynomial is distinct-but-equal to the gap. Removing the `c > 0` filter from Prov.of fails here
  // even though every law above draws only canonical values.
  test("of drops a zero-coefficient term — it cannot manufacture a non-canonical polynomial") {
    assertEquals(Prov.of(Map(Monomial.unit -> BigInt(0))), Prov.zero)
  }

  // Fail-closed coefficients: a product beyond the Int range must be kept, not wrapped to ≤0 and
  // silently dropped by `of` — which would empty a non-empty polynomial, flipping a corner fail-OPEN.
  // With plain Int coefficients 50000·50000 = 2.5e9 overflows and this polynomial would read as the gap.
  test("a coefficient product beyond Int range survives — BigInt, not a fail-open drop") {
    val big = Prov.of(Map(Monomial.unit -> BigInt(50000)))
    val squared = Prov.times(big, big)
    assert(!squared.isZero, "coefficient overflow must not empty the polynomial")
    assertEquals(squared, Prov.of(Map(Monomial.unit -> BigInt(2500000000L))))
  }

  // The unit cites nothing yet is supported — distinct from the gap. Guards blame attribution
  // against ever proxying "no support" with `support.isEmpty` (which holds for the unit too).
  test("Prov.one is supported but cites nothing — distinct from the gap") {
    assert(Prov.one.support.isEmpty, "the unit cites no token")
    assert(!Prov.one.isZero, "but the unit is not the gap")
    assert(Prov.zero.isZero, "the gap is the gap")
  }

  // Deterministic pins that the non-idempotent carriers actually read what they exist to read,
  // independent of whether the generators happen to reach those corners on a given seed.
  test("Count reads the coefficient: ν̂(2·s1) doubles a token's value") {
    val twiceS1 = Prov.plus(Generators.prov("s1"), Generators.prov("s1")) // coefficient 2
    assertEquals(twiceS1.evaluate(_ => Count(3L)), Count(6L)) // 3 + 3, not 3
  }

  test("Viterbi reads the exponent: ν̂(s1²) squares a token's value") {
    val s1Squared = Prov.times(Generators.prov("s1"), Generators.prov("s1")) // exponent 2
    assertEquals(s1Squared.evaluate(_ => Viterbi(0.5)), Viterbi(0.25)) // 0.5 × 0.5, not 0.5
  }
