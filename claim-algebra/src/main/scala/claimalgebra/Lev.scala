package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.{BoundedSemilattice, Order}

/** The per-channel confidence lattice `L` of the twist (claim-algebra.html §5).
  *
  * A channel carries either no evidence (`bottom`, the lattice bottom `⊥`) or a degree of evidence
  * in `(0, 1]`, with `top` (`Deg(1.0)`) the top `⊤` ("certain").
  *
  * A DISTINCT sealed type, deliberately not an opaque alias of `Double`. The only constructors are
  * `bottom`, `top`, and the clamping, fail-closed `deg`, so the `(0, 1]` invariant holds for every
  * value (the `Deg` case is private). And because `Lev` is genuinely a different type from
  * `Double`, a derived instance such as `Order.by(_.strength)` resolves the real `Order[Double]`
  * rather than looping back on itself — the trap an opaque `= Double` sets, since inside its own
  * scope the alias and the underlying type are indistinguishable to implicit resolution.
  *
  * `join` is max (more evidence), `meet` is min — pure selection.
  */
sealed trait Lev

object Lev:

  /** Bottom: no evidence on this channel. A valid, invariant-respecting value, so it is public.
    */
  case object Bot extends Lev

  /** A degree of evidence in `(0, 1]`. Private: the only way to build one is `deg`, which clamps
    * and rejects, so no out-of-range or `NaN` degree can exist.
    */
  final private case class Deg(p: Double) extends Lev

  /** Bottom: no evidence. */
  val bottom: Lev = Bot

  /** Top: full evidence. */
  val top: Lev = Deg(1.0)

  /** The safe constructor: a strength clamped to `(0, 1]`. Fail-closed at the bottom — a
    * non-positive or non-finite input (including `NaN`) is `bottom`; anything at or above one is
    * `top`.
    */
  def deg(p: Double): Lev =
    if !(p > 0.0) then Bot // catches NaN and p <= 0 -> no evidence (fail-closed)
    else if p >= 1.0 then top
    else Deg(p)

  extension (x: Lev)
    /** Evidence strength as a number in `[0, 1]`; `bottom` = 0. */
    def strength: Double = x match
      case Bot => 0.0
      case Deg(p) => p

    /** Whether this channel carries no evidence at all. */
    def isBottom: Boolean = x match
      case Bot => true
      case Deg(_) => false

  /** Lattice meet — the weaker evidence (min). */
  def meet(x: Lev, y: Lev): Lev = if x.strength <= y.strength then x else y

  /** Lattice join — the stronger evidence (max). */
  def join(x: Lev, y: Lev): Lev = if x.strength >= y.strength then x else y

  // A total order by evidence strength; supplies `Eq[Lev]` too (the gate tests
  // `grade ≥ θ`). Safe as `Order.by` now that `Lev` is distinct from `Double`;
  // `fromLessThan` keeps it explicit and import-free. Exact: no NaN is
  // constructible, so equality coincides with structural equality.
  given Order[Lev] = Order.fromLessThan((x, y) => x.strength < y.strength)

  /** `join` (max) as a bounded semilattice: identity `bottom`, idempotent. Named, not `given`,
    * because `Lev` carries two semilattices and an implicit would clash.
    */
  val joinSemilattice: BoundedSemilattice[Lev] = new BoundedSemilattice[Lev]:
    def empty: Lev = bottom
    def combine(x: Lev, y: Lev): Lev = join(x, y)

  /** `meet` (min) as a bounded semilattice: identity `top`, idempotent. */
  val meetSemilattice: BoundedSemilattice[Lev] = new BoundedSemilattice[Lev]:
    def empty: Lev = top
    def combine(x: Lev, y: Lev): Lev = meet(x, y)

  /** `Lev` as a render carrier: the FUZZY / weakest-link trust model (claim-algebra.html §4.2), the
    * bounded distributive lattice `([⊥, ⊤], join, meet)` read as a commutative rig — `+` = `join`
    * (max, the corroboration/alternative direction), `·` = `meet` (min, the joint-use direction),
    * `0` = `bottom`, `1` = `top`. Both operations are idempotent, so it is the conservative model
    * that does not reward repeated evidence. This is the carrier the gate renders into by default
    * and the underlying lattice of the [[Ev]] bilattice; [[Viterbi]] and [[Count]] are the
    * non-idempotent alternatives.
    */
  given CommutativeRig[Lev] with
    def zero: Lev = bottom
    def one: Lev = top
    def plus(x: Lev, y: Lev): Lev = join(x, y)
    def times(x: Lev, y: Lev): Lev = meet(x, y)
