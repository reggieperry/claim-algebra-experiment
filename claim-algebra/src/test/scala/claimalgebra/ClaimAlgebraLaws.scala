package claimalgebra

import cats.kernel.Eq
import cats.syntax.eq.*
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

/** The bilattice laws no stock cats/algebra bundle carries — the §4 properties table of
  * claim-algebra.html, stated as named properties so a failure points at the exact law. Checked
  * with `discipline`'s `checkAll` like any other RuleSet (scala-testing.md). Each statement is
  * verified against claim-algebra.html §4, not assumed.
  */
object ClaimAlgebraLaws extends Laws:

  def all(using Arbitrary[Ev], Arbitrary[Lev], Eq[Ev]): RuleSet =
    new DefaultRuleSet(
      name = "claimAlgebra",
      parent = None,
      // Annihilation (two): fail-closed across hops, with a chosen meaning.
      "annihilator: N ⊗ₖ x = N" -> forAll((x: Ev) => Ev.kmeet(Ev.N, x) === Ev.N),
      "annihilator: F ∧ₜ x = F" -> forAll((x: Ev) => Ev.tmeet(Ev.F, x) === Ev.F),
      // The dual absorbing elements (the joins' tops).
      "absorber: B ⊕ₖ x = B" -> forAll((x: Ev) => Ev.kjoin(Ev.B, x) === Ev.B),
      "absorber: T ∨ₜ x = T" -> forAll((x: Ev) => Ev.tjoin(Ev.T, x) === Ev.T),
      // Negation / involution: ¬ inverts ≤ₜ, preserves ≤ₖ; De Morgan in truth.
      "negation is involutive: ¬¬a = a" ->
        forAll((a: Ev) => Ev.negate(Ev.negate(a)) === a),
      "negation is a ⊗ₖ-homomorphism" ->
        forAll((a: Ev, b: Ev) =>
          Ev.negate(Ev.kmeet(a, b)) === Ev.kmeet(Ev.negate(a), Ev.negate(b))
        ),
      "negation is a ⊕ₖ-homomorphism" ->
        forAll((a: Ev, b: Ev) =>
          Ev.negate(Ev.kjoin(a, b)) === Ev.kjoin(Ev.negate(a), Ev.negate(b))
        ),
      "De Morgan in the truth lattice: ¬(a ∧ₜ b) = ¬a ∨ₜ ¬b" ->
        forAll((a: Ev, b: Ev) =>
          Ev.negate(Ev.tmeet(a, b)) === Ev.tjoin(Ev.negate(a), Ev.negate(b))
        ),
      // Gap ≠ glut: contradiction is a value, not a collapse to "unknown".
      "gap ≠ glut: N ≠ B" -> Prop(Ev.N =!= Ev.B),
      // Interlacing / distributivity, and the lattice absorption that links the
      // knowledge meet and join — what makes the twist valid.
      "⊗ₖ distributes over ⊕ₖ" ->
        forAll((a: Ev, b: Ev, c: Ev) =>
          Ev.kmeet(a, Ev.kjoin(b, c)) === Ev.kjoin(Ev.kmeet(a, b), Ev.kmeet(a, c))
        ),
      "knowledge absorption: a ⊗ₖ (a ⊕ₖ b) = a" ->
        forAll((a: Ev, b: Ev) => Ev.kmeet(a, Ev.kjoin(a, b)) === a),
      // Homomorphic evaluation (per channel): a threshold trust model is a
      // bounded-lattice endomorphism of Lev; applied to both channels it commutes
      // with every bilattice operation — compute the lineages once, render any
      // trust measure without recomputing the network.
      "per-channel homomorphism commutes with ⊕ₖ" ->
        forAll { (a: Ev, b: Ev, t: Lev) =>
          val phi = threshold(t)
          Ev.perChannel(phi)(Ev.kjoin(a, b)) ===
            Ev.kjoin(Ev.perChannel(phi)(a), Ev.perChannel(phi)(b))
        },
      "per-channel homomorphism commutes with ⊗ₖ" ->
        forAll { (a: Ev, b: Ev, t: Lev) =>
          val phi = threshold(t)
          Ev.perChannel(phi)(Ev.kmeet(a, b)) ===
            Ev.kmeet(Ev.perChannel(phi)(a), Ev.perChannel(phi)(b))
        },
      // Order behaviour of negation (§4): the twist flips the truth axis and
      // leaves the knowledge axis fixed. Stated on the derived orders so a
      // failure names the axis, not just an algebraic identity.
      "negation preserves the knowledge order: a ≤ₖ b ⇔ ¬a ≤ₖ ¬b" ->
        forAll((a: Ev, b: Ev) => leqK(a, b) == leqK(Ev.negate(a), Ev.negate(b))),
      "negation inverts the truth order: a ≤ₜ b ⇔ ¬b ≤ₜ ¬a" ->
        forAll((a: Ev, b: Ev) => leqT(a, b) == leqT(Ev.negate(b), Ev.negate(a))),
      // Knowledge-monotonicity (§4): evidence only ever climbs ≤ₖ — corroboration
      // never loses what a node already knew, and conjunction never invents it.
      "knowledge-monotonicity: a ≤ₖ a ⊕ₖ b" ->
        forAll((a: Ev, b: Ev) => leqK(a, Ev.kjoin(a, b))),
      "knowledge-monotonicity: a ⊗ₖ b ≤ₖ a" ->
        forAll((a: Ev, b: Ev) => leqK(Ev.kmeet(a, b), a)),
      // Interlacing (§4): each operation is monotone w.r.t. the OTHER order — the
      // condition that makes this a distributive bilattice rather than two
      // unrelated lattices. b is built ≥ a in the relevant order so the antecedent
      // holds by construction (no ScalaCheck implication to starve).
      "interlacing: ⊗ₖ is monotone in the truth order ≤ₜ" ->
        forAll { (a: Ev, c: Ev, x: Ev) =>
          val b = Ev.tjoin(a, x) // a ≤ₜ b by construction
          leqT(Ev.kmeet(a, c), Ev.kmeet(b, c))
        },
      "interlacing: ∧ₜ is monotone in the knowledge order ≤ₖ" ->
        forAll { (a: Ev, c: Ev, x: Ev) =>
          val b = Ev.kjoin(a, x) // a ≤ₖ b by construction
          leqK(Ev.tmeet(a, c), Ev.tmeet(b, c))
        },
      // Truth-lattice duals of the knowledge laws already pinned above, so both
      // halves of the bilattice are covered symmetrically (§4: "the lattices
      // distribute"; De Morgan holds on both sides).
      "⊕ₖ distributes over ⊗ₖ" ->
        forAll((a: Ev, b: Ev, c: Ev) =>
          Ev.kjoin(a, Ev.kmeet(b, c)) === Ev.kmeet(Ev.kjoin(a, b), Ev.kjoin(a, c))
        ),
      "∧ₜ distributes over ∨ₜ" ->
        forAll((a: Ev, b: Ev, c: Ev) =>
          Ev.tmeet(a, Ev.tjoin(b, c)) === Ev.tjoin(Ev.tmeet(a, b), Ev.tmeet(a, c))
        ),
      "truth absorption: a ∧ₜ (a ∨ₜ b) = a" ->
        forAll((a: Ev, b: Ev) => Ev.tmeet(a, Ev.tjoin(a, b)) === a),
      "De Morgan in the truth lattice (dual): ¬(a ∨ₜ b) = ¬a ∧ₜ ¬b" ->
        forAll((a: Ev, b: Ev) => Ev.negate(Ev.tjoin(a, b)) === Ev.tmeet(Ev.negate(a), Ev.negate(b)))
    )

  /** The derived knowledge order: `a ≤ₖ b` iff the knowledge-join lands on `b` (the standard "join =
    * least upper bound" characterisation). Stated on an order so the monotonicity and negation laws
    * read as order facts rather than restated algebraic identities.
    */
  private def leqK(a: Ev, b: Ev)(using Eq[Ev]): Boolean = Ev.kjoin(a, b) === b

  /** The derived truth order: `a ≤ₜ b` iff the truth-join lands on `b`. */
  private def leqT(a: Ev, b: Ev)(using Eq[Ev]): Boolean = Ev.tjoin(a, b) === b

  /** A threshold trust model: the bounded-lattice endomorphism of `Lev` that sends every value at
    * or above `t`'s strength to the top and the rest to the bottom. Preserves both `meet` and
    * `join`, so it is a genuine lattice homomorphism.
    */
  private def threshold(t: Lev)(x: Lev): Lev =
    if Lev.join(x, t) == x then Lev.top else Lev.bottom // join(x, t) == x  ⇔  x ≥ t
