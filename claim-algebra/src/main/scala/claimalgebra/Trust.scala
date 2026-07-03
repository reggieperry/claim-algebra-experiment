package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.Order

/** The non-idempotent trust-model render carriers — the per-channel semirings `L` a provenance
  * polynomial evaluates into (claim-algebra.html §4.2). Each is a [[algebra.ring.CommutativeRig]],
  * so [[Prov.evaluate]] is the unique induced GKT homomorphism into it. Together with the FUZZY
  * carrier [[Lev]] itself (idempotent on both operations, the default render), these span the
  * faithfulness dimensions a homomorphism law must exercise:
  *
  *   - [[Lev]] (in `Lev.scala`) — idempotent on BOTH operations (weakest-link): the conservative
  *     reading where re-receiving evidence changes nothing. The gate renders into this by default,
  *     and it is the underlying lattice of the [[Ev]] bilattice.
  *   - [[Viterbi]] — non-idempotent product: a most-likely-derivation confidence where a token's
  *     EXPONENT (joint-use multiplicity) is read.
  *   - [[Count]] — non-idempotent sum: a counting model where a monomial's COEFFICIENT
  *     (alternative-derivation multiplicity) is read.
  *
  * The carriers are distinct opaque types over their underlying numbers, so the standard numeric
  * instances cannot leak in and a derived `Order` cannot loop (the [[Lev]] footgun). `Order` is
  * provided via `fromLessThan` and supplies the `Eq` each law bundle needs; no separate `Eq` is
  * given, to keep a single instance in scope.
  */

/** Viterbi / most-likely-derivation confidence in `[0, 1]`: `+` = max, `·` = `×`. The product is
  * not idempotent, so joint use (exponents) compounds — the dimension the eager min-grade hides.
  */
opaque type Viterbi = Double

object Viterbi:
  /** Fail-closed at the boundary, like `Lev.deg`: `!(d > 0.0)` catches NaN and d ≤ 0 (no
    * confidence), d ≥ 1 saturates to 1, an interior value passes through. A bare
    * `max(0, min(1, d))` would propagate NaN — a fail-OPEN confidence that could clear θ.
    */
  def apply(d: Double): Viterbi =
    if !(d > 0.0) then 0.0
    else if d >= 1.0 then 1.0
    else d
  extension (x: Viterbi) def value: Double = x

  given CommutativeRig[Viterbi] with
    def zero: Viterbi = 0.0
    def one: Viterbi = 1.0
    def plus(a: Viterbi, b: Viterbi): Viterbi = math.max(a, b)
    def times(a: Viterbi, b: Viterbi): Viterbi = a * b

  given Order[Viterbi] = Order.fromLessThan((a, b) => a < b)

/** A counting model over ℕ: `+` and `·` are ordinary addition and multiplication. The sum is not
  * idempotent, so alternative derivations (coefficients) compound — the dimension only ℕ
  * coefficients carry. A `Long` to give the multiplicities room before overflow matters.
  */
opaque type Count = Long

object Count:
  /** Fail-closed at zero: a negative count is no count. */
  def apply(n: Long): Count = math.max(0L, n)
  extension (x: Count) def value: Long = x

  given CommutativeRig[Count] with
    def zero: Count = 0L
    def one: Count = 1L
    def plus(a: Count, b: Count): Count = a + b
    def times(a: Count, b: Count): Count = a * b

  given Order[Count] = Order.fromLessThan((a, b) => a < b)
