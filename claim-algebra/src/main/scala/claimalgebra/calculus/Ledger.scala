package claimalgebra.calculus

import claimalgebra.*

/** A single graded-evidence event on one (deal, concept) SLOT — the unit of the event/fold model
  * (the committee's verdict: adopt the event/fold MODEL now, not a streaming runtime). Per-slot
  * belief is the fold of an ordered `Seq` of these events, and the four-state [[Row]] is a read off
  * the fold ([[Ledger.resolve]]) — recompute-on-read, no stored status to drift.
  *
  * Each event carries an ALREADY-GROUNDED testimony, so the fold can never bypass the
  * verbatim-grounding fail-closed guard: the caller (the grounding layer) emits an event only for
  * evidence that actually grounded, exactly as the grounding layer already decided.
  *
  * Why a `foldLeft`, not a `foldMap(corroborate)`: assertions corroborate and are order-insensitive
  * (`corroborate` is a commutative monoid), but the amendment/operator decisions are
  * NON-commutative channel transforms — `supersede` strikes the prior to the con-channel and
  * installs a fresh operative, and the algebra keeps that as a `Supersession` PAIR precisely so it
  * never reads as a glut (`Testimony.corner` reads the channel TOTALS, so a struck prior plus a
  * fresh operative folded into one testimony would read `Glut`). So the carrier is an ordered `Seq`
  * folded left, and the non-commutative supersede/strike path is what must be serialized per slot —
  * the exact boundary the architecture committee named.
  */
enum Evidence[A]:
  /** Grounded pro evidence — corroborated in. A second `Asserted` on a RIVAL value makes the slot
    * ambiguous (→ Conflict); on the SAME value it accumulates support.
    */
  case Asserted(grounded: Testimony[A])

  /** A grounded value that SUPERSEDES the current operative — an amendment, or the operator's "set
    * operative" / "mark supersession". The prior operative is struck to the con-channel and kept on
    * record; this value governs. If nothing is operative yet (the slot is a gap), it is simply a
    * fresh assertion — nothing is struck.
    */
  case Superseded(grounded: Testimony[A])

  /** The current operative is STRUCK with no replacement — a deletion (a covenant removed by
    * amendment). The struck value is retained on the con-channel for audit; the slot reads Struck.
    */
  case Withdrawn[A]() extends Evidence[A]

object Ledger:

  /** A slot's belief: either a plain corroborated position ([[Left]]) or a supersession pair
    * ([[Right]]) once an amendment has struck a prior — the two shapes the resolvers read.
    */
  type Belief[A] = Either[Testimony[A], Supersession[A]]

  /** Fold an ordered event sequence into the slot's belief. Assertions corroborate (commutative);
    * `Superseded`/`Withdrawn` are order-sensitive channel transforms applied in turn.
    */
  def belief[A](events: Seq[Evidence[A]]): Belief[A] =
    events.foldLeft[Belief[A]](Left(Testimony.gap[A])) { (b, ev) =>
      (b, ev) match
        case (Left(t), Evidence.Asserted(g)) =>
          Left(Testimony.corroborate(t, g))
        case (Right(s), Evidence.Asserted(g)) =>
          Right(Supersession(s.struck, Testimony.corroborate(s.operative, g)))
        case (Left(t), Evidence.Superseded(g)) =>
          // an empty payload (amendment grounded nothing) is INERT — the prior stands; nothing
          // operative yet → a fresh assertion (nothing struck); else the prior is superseded
          if Testimony.corner(g) == Belnap.Gap then Left(t)
          else if Testimony.corner(t) == Belnap.Gap then Left(g)
          else Right(Testimony.supersede(t, g))
        case (Right(s), Evidence.Superseded(g)) =>
          // an empty payload is inert; else the operative becomes the new struck prior (a chain)
          if Testimony.corner(g) == Belnap.Gap then Right(s)
          else Right(Testimony.supersede(s.operative, g))
        case (Left(t), Evidence.Withdrawn()) =>
          Left(Testimony.strike(t)) // absorbing: a second Withdrawn keeps it struck (not refute)
        case (Right(s), Evidence.Withdrawn()) =>
          Right(Supersession(s.struck, Testimony.strike(s.operative)))
    }

  /** Resolve a slot to its four-state [[Row]] — the recompute-on-read off the folded belief, routed
    * through the existing fail-closed resolvers ([[BelnapReader.resolve]] /
    * [[BelnapReader.resolveSuperseded]]), so a value signs only if it gate-accepts on its own
    * document.
    */
  def resolve[A](term: String, events: Seq[Evidence[A]]): Row[A] =
    belief(events).fold(BelnapReader.resolve(term, _), BelnapReader.resolveSuperseded(term, _))
