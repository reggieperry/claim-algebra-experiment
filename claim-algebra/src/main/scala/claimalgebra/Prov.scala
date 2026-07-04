package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.{CommutativeMonoid, Eq}

/** A provenance atom: one lineage citation, optionally carrying the [[Kind]] of evidence it is. An
  * identifier for now; a consumer refines it into a cited source span. A DISTINCT case class behind
  * a private constructor — not an opaque alias of `String` — so a raw `String` cannot be passed
  * where a lineage is wanted, and a citation that points nowhere cannot exist. The optional `kind`
  * is a pure routing decoration read by κ̂ ([[Testimony.conflictKinds]]); it never affects the gate
  * or the grade (`nu` keys on the `id`), so it is a CONSERVATIVE decoration — an un-annotated
  * citation carries no kind (`None`), and κ̂ of it is ∅.
  */
final case class Lineage private (id: String, kind: Option[Kind])

object Lineage:
  /** The only constructor: a non-blank citation id, trimmed, optionally annotated with an evidence
    * kind (default `None` — un-annotated). Fail-closed — a blank or whitespace-only id is no
    * citation, so it yields `None` rather than a degenerate `Lineage`.
    */
  def from(raw: String, kind: Option[Kind] = None): Option[Lineage] =
    Option.when(raw.trim.nonEmpty)(Lineage(raw.trim, kind))

  // Structural over (id, kind) — so two same-id citations of different kinds are distinct map keys.
  given Eq[Lineage] = Eq.fromUniversalEquals

/** A monomial: a multiset of lineage tokens — JOINT use, the semiring product `·`, with ℕ exponents
  * counting multiplicity. Normalized so every exponent is strictly positive; the empty multiset is
  * the multiplicative unit `1` (a derivation that cited nothing specific).
  *
  * The constructor is private and the only builders — [[Monomial.unit]], [[Monomial.single]],
  * [[Monomial.times]] — each preserve the invariant, so two equal monomials are structurally equal.
  * That is what makes `Monomial` sound as a `Map` key in [[Prov]]: a token left at exponent zero,
  * or two construction paths disagreeing on a zero entry, would hash apart and double-count.
  */
final case class Monomial private (exps: Map[Lineage, Int])

object Monomial:

  /** The multiplicative unit `1`: a derivation citing no token. NOT "no derivation" (that is
    * [[Prov.zero]]); `1 · x = x`.
    */
  val unit: Monomial = Monomial(Map.empty)

  /** A single cited token, used once. */
  def single(l: Lineage): Monomial = Monomial(Map(l -> 1))

  /** Joint use: union the token multisets, adding exponents. Both inputs are normalized and only
    * positive exponents are summed, so the result is normalized without filtering.
    *
    * Exponents stay `Int`, not `BigInt` (unlike [[Prov]] coefficients): they are POWERS — the
    * render raises a token's value to `e` by repeated rig multiplication ([[Prov.evaluate]]) — and
    * they grow only ADDITIVELY with derivation depth, never near the bound in a finite topology.
    * The addition is CHECKED (`Math.addExact`) — checked arithmetic that fails CLOSED: a
    * (physically impossible) overflow throws rather than silently wrapping to a non-positive
    * exponent, which would mis-render the grade — possibly upward, a latent grade fail-open in the
    * general algebra. The structural corner is unaffected either way (the monomial stays a
    * non-empty key), so this guards only the grade.
    */
  def times(a: Monomial, b: Monomial): Monomial =
    Monomial(b.exps.foldLeft(a.exps) { case (acc, (l, e)) =>
      acc.updated(l, Math.addExact(acc.getOrElse(l, 0), e))
    })

  given Eq[Monomial] = Eq.fromUniversalEquals

  /** The free commutative monoid ℕ^(X): `times` is pointwise ℕ-addition of exponents, `unit` the
    * empty multiset. Exposed as a `given` so the law bundle pins the monoid directly — otherwise it
    * is exercised only through `Prov.times`.
    */
  given CommutativeMonoid[Monomial] = CommutativeMonoid.instance(unit, (a, b) => times(a, b))

/** A provenance polynomial — the free commutative semiring ℕ[X] over [[Lineage]] tokens
  * (claim-algebra.html §4). A multiset of [[Monomial]]s with ℕ coefficients: the sum `+` is
  * ALTERNATIVE derivation (corroboration), the product `·` is JOINT use (conjunctive derivation).
  *
  * Normalized so every coefficient is strictly positive; the empty polynomial is the additive
  * `zero` — no derivation, no support, the gap. It is DISTINCT from [[Prov.one]] (the unit monomial
  * with coefficient one — supported, but citing nothing specific): only `zero` is annihilated by
  * `·`, so conflating the two would defeat fail-closed. The grade is not stored here; it is
  * RENDERED from this polynomial by the homomorphism [[evaluate]] under a chosen trust model.
  */
final case class Prov private (terms: Map[Monomial, BigInt])

object Prov:

  /** The additive zero `0`: no derivation, no support — the gap. `0 · x = 0` (fail-closed). */
  val zero: Prov = Prov(Map.empty)

  /** The multiplicative unit `1`: one derivation that cites nothing. NOT the gap — `1 · x = x`. */
  val one: Prov = Prov(Map(Monomial.unit -> BigInt(1)))

  /** A leaf's support: one derivation citing exactly `l`. */
  def single(l: Lineage): Prov = Prov(Map(Monomial.single(l) -> BigInt(1)))

  /** The normalizing smart constructor — drops any non-positive coefficient so equal polynomials
    * are structurally equal. The raw constructor is private; everything routes through here.
    */
  def of(terms: Map[Monomial, BigInt]): Prov = Prov(terms.filter { case (_, c) => c > 0 })

  /** Alternative derivation `+`: merge terms, adding coefficients. */
  def plus(x: Prov, y: Prov): Prov =
    of(y.terms.foldLeft(x.terms) { case (acc, (m, c)) =>
      acc.updated(m, acc.getOrElse(m, BigInt(0)) + c)
    })

  /** Joint use `·`: distribute — every pair of monomials multiplies (exponents add, checked) and
    * their coefficients multiply. Coefficients are `BigInt` so `·` cannot overflow and silently
    * drop a term — the candidate convolution amplifies coefficient growth, and an `Int` wrap to a
    * non-positive value would be dropped by [[of]], a fail-OPEN corner flip (foundations: BigInt is
    * essential). Exponents stay checked-`Int` (see [[Monomial.times]]).
    */
  def times(x: Prov, y: Prov): Prov =
    of(x.terms.foldLeft(Map.empty[Monomial, BigInt]) { case (outer, (mx, cx)) =>
      y.terms.foldLeft(outer) { case (acc, (my, cy)) =>
        val m = Monomial.times(mx, my)
        acc.updated(m, acc.getOrElse(m, BigInt(0)) + cx * cy)
      }
    })

  extension (p: Prov)

    /** Whether this is the additive zero — no support at all. The structural gap test, model-free:
      * the Belnap corner is read from the two channels' `isZero`, never from a rendered grade.
      */
    def isZero: Boolean = p.terms.isEmpty

    /** The support set — the tokens appearing with a nonzero coefficient. The §5 `Set[Lineage]`
      * reading, recovered as a derived projection for blame attribution, never the storage. NOT a
      * gap test: `one.support` is also empty (the unit cites nothing), so it cannot tell the unit
      * from the gap — use [[isZero]] for "no support."
      */
    def support: Set[Lineage] = p.terms.keySet.flatMap(_.exps.keySet)

    /** Token-scoped retraction `∖` — drop every monomial that USED the token `l` (any monomial in
      * which `l` has a nonzero exponent). A joint derivation through `l` collapses; an independent
      * monomial for the same value survives. Additive (`(p + q) ∖ l = p∖l + q∖l`) and idempotent,
      * an `ℕ[X] → ℕ[X∖{l}]` projection — NOT a rig homomorphism, and it need not be: it is applied
      * per-channel, never through `·`. This is the primitive behind token-scoped withdrawal
      * ([[Testimony.withoutToken]]): removing ONE assertion's support while rivals stand, distinct
      * from the whole-testimony [[Testimony.strike]] (which MOVES support to con). Fail-closed on a
      * token-id collision — it OVER-drops, never resurrects.
      */
    def withoutToken(l: Lineage): Prov =
      Prov.of(p.terms.filterNot { case (m, _) => m.exps.contains(l) })

    /** κ̂ — the KIND-set of the citations in this provenance (G3). An additive-monoid homomorphism
      * ℕ[X_K] → (Set[Kind], ∪, ∅): `κ̂(0) = κ̂(1) = ∅`, `κ̂(p + q) = κ̂(p) ∪ κ̂(q)`, and
      * `κ̂(x · y) = κ̂(x) ∪ κ̂(y)` for non-zero factors. It identifies the two units, so it is NOT
      * a rig homomorphism — a pure read, never a `CommutativeRig` instance and never folded into
      * [[evaluate]] (ν̂), which would corrupt the grade contract. Read off a glut's con-channel it
      * names which KINDS of refutation are present (foundations: T-kappa-additive-homomorphism).
      */
    def kinds: Set[Kind] = p.support.flatMap(_.kind)

    /** Render: the unique semiring homomorphism ν̂ : ℕ[X] → M induced by a token valuation `nu`
      * (claim-algebra.html §3.3). Evaluating the trust model and running the network commute, so
      * the grade is computed once as the polynomial and rendered under any `M` without rerunning
      * the network. The coefficient `c` adds the monomial's value `c` times; the exponent `e`
      * multiplies a token's value `e` times.
      */
    def evaluate[M](nu: Lineage => M)(using M: CommutativeRig[M]): M =
      p.terms.foldLeft(M.zero) { case (acc, (mono, coeff)) =>
        val monoValue = mono.exps.foldLeft(M.one) { case (mv, (l, e)) =>
          M.times(mv, powM(nu(l), e))
        }
        M.plus(acc, sumN(monoValue, coeff))
      }

  /** `x` multiplied `e` times in `M` (a small exponent fold; no reliance on a library `pow`). */
  private def powM[M](x: M, e: Int)(using M: CommutativeRig[M]): M =
    (0 until e).foldLeft(M.one)((acc, _) => M.times(acc, x))

  /** `x` added `n` times in `M`, by doubling — O(log n), so a large BigInt coefficient (the
    * candidate-convolution regime) is summed without an O(n) fold.
    */
  private def sumN[M](x: M, n: BigInt)(using M: CommutativeRig[M]): M =
    if n <= 0 then M.zero
    else
      val half = sumN(x, n / 2)
      val doubled = M.plus(half, half)
      if n.testBit(0) then M.plus(doubled, x) else doubled

  given Eq[Prov] = Eq.fromUniversalEquals

  /** ℕ[X] as the free commutative semiring. The `RingLaws.commutativeRig` bundle pins `+`/`·`
    * associativity, commutativity, the `0`/`1` identities, and distributivity; the multiplicative
    * annihilator `0 · x = 0` — the fail-closed law a gap rests on — is an independent rig axiom (no
    * additive inverse makes it irreducible to distributivity) and is pinned separately by the
    * custom `ProvRigLaws` RuleSet, not by this bundle.
    */
  given CommutativeRig[Prov] with
    def zero: Prov = Prov.zero
    def one: Prov = Prov.one
    def plus(x: Prov, y: Prov): Prov = Prov.plus(x, y)
    def times(x: Prov, y: Prov): Prov = Prov.times(x, y)
