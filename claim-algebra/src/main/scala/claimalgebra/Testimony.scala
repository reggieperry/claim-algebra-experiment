package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.{CommutativeMonoid, Eq}

/** One candidate's two provenance channels — `pro` (for) and `con` (against) — as NAMED fields, so
  * a channel can never be confused for the other positionally (scala-types.md: two named,
  * separately typed fields, never a single untyped pair).
  */
final case class Channels(pro: Prov, con: Prov)

/** The carrier: a finite map from each CANDIDATE value to its `(for, against)` provenance pair (the
  * candidate carrier, claim-algebra-foundations §carrier). A clean leaf is a single entry; a glut
  * or an ambiguity holds the rivals, each with its own un-collapsed ℕ[X] provenance — so
  * corroboration is a commutative monoid (no left-biased `orElse`) and a reconciler is handed every
  * rival, not one arbitrary survivor. The map is NORMALIZED to drop any entry whose pair is
  * `(0, 0)`.
  *
  * The grade is NOT stored — it is rendered on demand by ν̂ from the channel totals ([[render]] /
  * [[renderEv]]); the Belnap corner is read structurally from whether each channel total is empty
  * ([[corner]]), model-free. `value`, `provPro`, and `provCon` are DERIVED reads, kept so callers
  * that only consume them are unchanged. INVARIANT in `A`: `A` is a map key, so it needs a lawful
  * `Eq[A]` coinciding with `==`/`hashCode` (a value-less meta-claim is `Testimony[Unit]`, keyed by
  * the unit).
  */
final case class Testimony[A] private (private[claimalgebra] candidates: Map[A, Channels]):

  /** The candidate entries — each value with its named (pro, con) [[Channels]]. The typed read for
    * consumers; the underlying map is package-private so the normalized representation does not
    * leak.
    */
  def entries: Iterable[(A, Channels)] = candidates

  /** The pro-channel total — the sum of every candidate's for-provenance (for rendering and
    * corner).
    */
  def provPro: Prov = candidates.values.foldLeft(Prov.zero)((acc, ch) => Prov.plus(acc, ch.pro))

  /** The con-channel total. */
  def provCon: Prov = candidates.values.foldLeft(Prov.zero)((acc, ch) => Prov.plus(acc, ch.con))

  /** The for-candidates — every value carrying non-zero pro-support. The single shared read behind
    * [[value]], [[cardinality]], and [[supported]], so the for-support predicate lives in one
    * place.
    */
  private def forCandidates: Iterable[A] =
    candidates.collect { case (a, ch) if !ch.pro.isZero => a }

  /** The operative (signable) value: present only when exactly one candidate has for-support and no
    * candidate has against-support — a clean, unambiguous `True`. A gap, glut, refutation, or
    * ambiguity (≥ 2 rival for-candidates) yields `None`.
    */
  def value: Option[A] =
    val anyCon = candidates.exists { case (_, ch) => !ch.con.isZero }
    val fors = forCandidates
    if anyCon || fors.sizeIs != 1 then None else fors.headOption

  /** The number of rival for-candidates: 1 is unambiguous, ≥ 2 is ambiguous (the gate refuses an
    * ambiguous claim). Model-free, render-invariant.
    */
  def cardinality: Int = forCandidates.size

  /** The for-candidate value SET — every candidate carrying non-zero pro-support. [[value]] is the
    * lone signable one (a clean, unambiguous `True`); this is the full set: empty for a gap, a
    * singleton for a clean `True`, ≥ 2 when ambiguous. Used to tally which independent readers
    * backed which value (the panel) — a display read kept out of the grade.
    */
  def supported: Set[A] = forCandidates.toSet

  /** The extracted figure a VALUE-ONLY reader sees — the lone candidate's value, IGNORING the
    * for/against channels, so a refuted figure (F7: the figure unchanged, a contradiction on the
    * con-channel) still reads as a figure. `None` for the gap, or when rivals disagree (≥ 2
    * candidates). This is what the prose and structured-untyped baselines read; it is deliberately
    * blind to the Belnap structure the bilattice arm gates on. Distinct from [[value]], the
    * bilattice-aware SIGNABLE read (a clean, unambiguous `True` only).
    */
  def figure: Option[A] =
    if candidates.sizeIs == 1 then candidates.keys.headOption else None

object Testimony:

  /** Normalize: drop any candidate whose pair is the gap on both channels. */
  private def of[A](m: Map[A, Channels]): Testimony[A] =
    Testimony(m.filter { case (_, ch) => !(ch.pro.isZero && ch.con.isZero) })

  /** The gap `N` — no candidate on either channel. Generic, since `A` is now invariant. */
  def gap[A]: Testimony[A] = Testimony(Map.empty)

  /** A single-candidate claim with explicit channels — the general smart constructor. */
  def single[A](value: A, pro: Prov, con: Prov): Testimony[A] = of(Map(value -> Channels(pro, con)))

  /** A leaf with positive support and no refuting evidence — the pro-channel cites the source. */
  def leaf[A](value: A, support: Prov): Testimony[A] = single(value, support, Prov.zero)

  /** Conjunctive derivation by the KNOWLEDGE-meet `⊗ₖ` (the fail-closed default): both channels by
    * JOINT use `·`, so a gap (the empty map — no pairs to form) annihilates to the gap `N`.
    */
  def derive[A, B, C](a: Testimony[A], b: Testimony[B])(f: (A, B) => C): Testimony[C] =
    convolve(a, b)(f)((_, ca, _, cb) => Prov.times(ca, cb))

  /** Conjunctive derivation by the TRUTH-meet `∧ₜ` (the F7 fork): pro by JOINT use `·`, con by the
    * THREE-term cross-term `ca·pb + pa·cb + ca·cb`. A refuted conjunct (corner `F`) is carried to
    * `F`, distinct from the gap `N` a merely-missing one yields under `⊗ₖ`; the three-term form
    * recovers `F ∧ₜ F = F`. The con-OPERATION here is a relaxable realization of the essential fork
    * (foundations: the fork is essential, this operation is not).
    */
  def truthMeet[A, B, C](a: Testimony[A], b: Testimony[B])(f: (A, B) => C): Testimony[C] =
    convolve(a, b)(f) { (pa, ca, pb, cb) =>
      Prov.plus(Prov.plus(Prov.times(ca, pb), Prov.times(pa, cb)), Prov.times(ca, cb))
    }

  /** The fail-closed conjunction fork. A CONTESTED conjunct — one carrying con-channel mass,
    * whether corner `F` (actively refuted) or `Glut` (asserted AND refuted) — routes to the
    * TRUTH-meet `∧ₜ` ([[truthMeet]]), which carries the contest to the root so the composite's
    * corner stays non-`True` and blocks. A merely-missing or clean conjunct takes the
    * KNOWLEDGE-meet `⊗ₖ` default ([[derive]]): `N ⊗ₖ x = N` (a gap annihilates) and `T ⊗ₖ T = T`
    * (two clean conjuncts compose clean).
    *
    * WHY the glut must join `∧ₜ` rather than fall to the `⊗ₖ` default: `⊗ₖ`'s con-channel is a
    * PRODUCT of the two cons, so a gluted `(p, c)` conjoined with a clean-`True` `(q, 0)` yields
    * con `= c · 0 = 0` — the composite reads a clean `True` and would sign, LAUNDERING the
    * contradiction away. `∧ₜ`'s three-term con (`ca·pb + pa·cb + ca·cb`) is non-zero whenever a
    * conjunct is contested, so it is carried to the root and blocks. This fork therefore routes `F`
    * and `Glut` alike; routing only `F` would leave the glut to launder.
    */
  def conjoin[A, B, C](a: Testimony[A], b: Testimony[B])(f: (A, B) => C): Testimony[C] =
    if contested(a) || contested(b) then truthMeet(a, b)(f) else derive(a, b)(f)

  /** A conjunct carrying con-channel mass — corner `F` (refuted) or `Glut` (asserted and refuted).
    * These are the two corners [[conjoin]] must keep OFF the knowledge-meet `⊗ₖ` default (which
    * launders their con-channel), routing them to the truth-meet `∧ₜ` instead.
    */
  private def contested[A](t: Testimony[A]): Boolean =
    corner(t) == Belnap.False || corner(t) == Belnap.Glut

  /** The conjunction skeleton: f-convolution over candidate pairs. Each pair `(va, vb)` yields the
    * result candidate `f(va, vb)` with pro `pa·pb` and con per the caller; pairs colliding on the
    * same result value accumulate by `+`. An empty input has no pairs, so the result is the gap —
    * fail-closed `N ⊗ₖ x = N`.
    */
  private def convolve[A, B, C](a: Testimony[A], b: Testimony[B])(f: (A, B) => C)(
      con: (Prov, Prov, Prov, Prov) => Prov
  ): Testimony[C] =
    val pairs =
      for
        (va, cha) <- a.candidates.iterator
        (vb, chb) <- b.candidates.iterator
      yield (f(va, vb), Prov.times(cha.pro, chb.pro), con(cha.pro, cha.con, chb.pro, chb.con))
    of(pairs.foldLeft(Map.empty[C, Channels]) { case (acc, (c, p, cc)) =>
      val prev = acc.getOrElse(c, Channels(Prov.zero, Prov.zero))
      acc.updated(c, Channels(Prov.plus(prev.pro, p), Prov.plus(prev.con, cc)))
    })

  /** Corroboration `⊕ₖ` — pointwise `(+, +)` per candidate over the union of keys. A COMMUTATIVE
    * MONOID on the full carrier with identity the gap (no `orElse` bias; rivals coexist as distinct
    * keys, so two disagreeing positives read as cardinality ≥ 2, not a forced glut). The monoid is
    * the `given` below, proven law-first in `TestimonyLawsSuite`.
    */
  def corroborate[A](a: Testimony[A], b: Testimony[A]): Testimony[A] =
    of(b.candidates.foldLeft(a.candidates) { case (acc, (v, chb)) =>
      val cha = acc.getOrElse(v, Channels(Prov.zero, Prov.zero))
      acc.updated(v, Channels(Prov.plus(cha.pro, chb.pro), Prov.plus(cha.con, chb.con)))
    })

  /** Negation / retraction `¬` — swap each candidate's two channels. Involutive. */
  def refute[A](a: Testimony[A]): Testimony[A] =
    of(a.candidates.map { case (v, ch) => v -> Channels(ch.con, ch.pro) })

  /** Strike — a deletion: send all support to the con-channel and CLEAR the pro-channel, retaining
    * each candidate on con for audit. Unlike [[refute]] (involutive — a channel SWAP, so two
    * applications cancel), strike is IDEMPOTENT and ABSORBING: striking an already-struck testimony
    * leaves it struck. This is the fail-closed requirement for a deletion folded over an event
    * stream — a value struck twice must NOT resurrect, which a double [[refute]] would do. corner
    * becomes `F` (or stays `N` on the gap); [[value]] is `None` (the pro-channel is empty, so
    * nothing signs).
    */
  def strike[A](a: Testimony[A]): Testimony[A] =
    of(a.candidates.map { case (v, ch) => v -> Channels(Prov.zero, Prov.plus(ch.pro, ch.con)) })

  /** F5 supersession — a retraction yielding TWO claims: the [[Supersession.struck]] prior clause
    * refuted to `F` (its support kept on the con-channel for audit) and the
    * [[Supersession.operative]] amendment that governs. Two claims, not one, so the pair never
    * reads as a glut.
    */
  def supersede[A](superseded: Testimony[A], amendment: Testimony[A]): Supersession[A] =
    Supersession(refute(superseded), amendment)

  /** Token-scoped withdrawal — remove ONE assertion's support (the lineage token `l`) from every
    * candidate's channels, dropping any candidate left empty (routed through [[of]], so a `(0, 0)`
    * entry does not linger as an empty-channel ghost). The completion of the retraction op-set
    * below the whole-testimony [[strike]]: strike MOVES a whole testimony's support to con (so it
    * gluts a later re-assertion — an absorbing "distrust this slot"); a token withdrawal leaves NO
    * con-mass, so a fresh assertion of the same value signs clean ("retract this one testimony").
    * The retained audit trace is the recorded withdrawal EVENT
    * (`calculus.Evidence.WithdrawnToken`), not a carrier channel — the object keeps its audit in
    * the event term, not the belief. Well-formedness (token-uniqueness): a lineage id names exactly
    * one assertion, so a withdrawn token is never reissued; on a collision this OVER-drops
    * (fail-closed), never resurrects.
    */
  def withoutToken[A](a: Testimony[A], l: Lineage): Testimony[A] =
    of(a.candidates.map { case (v, ch) =>
      v -> Channels(ch.pro.withoutToken(l), ch.con.withoutToken(l))
    })

  /** The Belnap corner, read STRUCTURALLY from the channel totals — model-free. */
  def corner[A](t: Testimony[A]): Belnap = Belnap.from(t.provPro.isZero, t.provCon.isZero)

  /** κ̂ on the carrier — the kind-SET of a glut's refutation, read STRUCTURALLY from the
    * con-channel total (model-free, the same provenance the corner is read from).
    * `Glut → Conflict(κ̂)`: this names WHICH KINDS of refuting evidence are present, so a consumer
    * can route the conflict to whoever owns that kind. Empty for a gap or a clean `True` (no con).
    * A pure routing / blame-attribution read: never an input to the gate or the grade. Propagates
    * for free through the provenance algebra (foundations: T-kind-propagation).
    */
  def conflictKinds[A](t: Testimony[A]): Set[Kind] = t.provCon.kinds

  /** Render the grade as a `(pro, con)` pair in any carrier `M` by ν̂ over the channel totals. */
  def render[A, M](t: Testimony[A])(nu: Lineage => M)(using CommutativeRig[M]): (M, M) =
    (t.provPro.evaluate(nu), t.provCon.evaluate(nu))

  /** Render the grade as the [[Ev]] bilattice element under the fuzzy carrier [[Lev]]. */
  def renderEv[A](t: Testimony[A])(nu: Lineage => Lev): Ev =
    Ev(t.provPro.evaluate(nu), t.provCon.evaluate(nu))

  given [A]: Eq[Testimony[A]] = Eq.fromUniversalEquals

  /** corroborate `⊕ₖ` as a commutative monoid with identity the gap — the lawful instance behind
    * the fold-order independence corroboration relies on (proven directly in `TestimonyLawsSuite`).
    * Supersession is deliberately NOT folded through this — it keeps a struck/operative pair (see
    * `Ledger`), so this is corroboration only.
    */
  given [A]: CommutativeMonoid[Testimony[A]] =
    CommutativeMonoid.instance(gap[A], (a, b) => corroborate(a, b))

/** The two claims an amendment leaves on the record (F5): the [[struck]] prior clause — refuted to
  * `F`, kept for audit — and the [[operative]] amendment that governs.
  */
final case class Supersession[A](struck: Testimony[A], operative: Testimony[A])
