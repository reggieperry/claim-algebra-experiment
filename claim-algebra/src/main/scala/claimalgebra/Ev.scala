package claimalgebra

import cats.kernel.{BoundedSemilattice, Eq}

/** The bilattice element: an evidential PAIR `(pro, con)` over the per-channel lattice [[Lev]] —
  * the twist `L ⊙ L` (claim-algebra.html §5). `pro` is support-for, `con` is support-against. The
  * four Belnap corners are pairs and the four operations are all componentwise.
  */
final case class Ev(pro: Lev, con: Lev)

object Ev:

  // The four Belnap corners as pairs.
  val N: Ev = Ev(Lev.bottom, Lev.bottom) // gap — knowledge-bottom (UNRESOLVED)
  val T: Ev = Ev(Lev.top, Lev.bottom) // true — supported, unrefuted
  val F: Ev = Ev(Lev.bottom, Lev.top) // false — refuted, unsupported
  val B: Ev = Ev(Lev.top, Lev.top) // glut — knowledge-top (told both)

  /** knowledge-meet `⊗ₖ` — fail-closed: `N ⊗ₖ x = N`. Identity the glut `B`. */
  def kmeet(a: Ev, b: Ev): Ev = Ev(Lev.meet(a.pro, b.pro), Lev.meet(a.con, b.con))

  /** knowledge-join `⊕ₖ` — corroboration; a conflict climbs to the glut `B`. Identity the gap `N`.
    */
  def kjoin(a: Ev, b: Ev): Ev = Ev(Lev.join(a.pro, b.pro), Lev.join(a.con, b.con))

  /** truth-meet `∧ₜ` — `F ∧ₜ x = F`. Identity `T`. */
  def tmeet(a: Ev, b: Ev): Ev = Ev(Lev.meet(a.pro, b.pro), Lev.join(a.con, b.con))

  /** truth-join `∨ₜ`. Identity `F`. */
  def tjoin(a: Ev, b: Ev): Ev = Ev(Lev.join(a.pro, b.pro), Lev.meet(a.con, b.con))

  /** negation `¬` — swap the two channels. Involutive; preserves `≤ₖ`, inverts `≤ₜ`. */
  def negate(a: Ev): Ev = Ev(a.con, a.pro)

  /** Read the Belnap corner off the rendered pair — no model in the loop. A channel "fires" when it
    * carries any evidence (is not the bottom). Shares [[Belnap.from]] with the structural corner on
    * the provenance, so a rendered grade and its polynomial agree on the corner whenever the
    * valuation sends "no support" to the bottom.
    */
  def corner(a: Ev): Belnap = Belnap.from(a.pro.isBottom, a.con.isBottom)

  /** Apply a per-channel endomorphism of [[Lev]] to both lineages — the carrier of the per-channel
    * homomorphism (claim-algebra.html §4, "homomorphic evaluation"): compute the two lineages once,
    * render any trust measure.
    */
  def perChannel(f: Lev => Lev)(a: Ev): Ev = Ev(f(a.pro), f(a.con))

  given Eq[Ev] = Eq.fromUniversalEquals

  // The four operations as named bounded semilattices. Named, not `given`: one
  // type carries four monoids, so an implicit instance would be ambiguous.

  /** `⊕ₖ` — the corroboration combiner. Identity the gap `N`: corroborating a claim with "told
    * nothing" leaves it unchanged.
    */
  val corroboration: BoundedSemilattice[Ev] = semilattice(N, kjoin)

  /** `⊗ₖ` — the conjunctive (derive) grade combiner; fail-closed. Identity the glut `B`: conjoining
    * with "told both" leaves a claim unchanged.
    */
  val conjunction: BoundedSemilattice[Ev] = semilattice(B, kmeet)

  /** `∧ₜ` — truth-meet. Identity `T`. */
  val truthMeet: BoundedSemilattice[Ev] = semilattice(T, tmeet)

  /** `∨ₜ` — truth-join. Identity `F`. */
  val truthJoin: BoundedSemilattice[Ev] = semilattice(F, tjoin)

  private def semilattice(id: Ev, op: (Ev, Ev) => Ev): BoundedSemilattice[Ev] =
    new BoundedSemilattice[Ev]:
      def empty: Ev = id
      def combine(x: Ev, y: Ev): Ev = op(x, y)
