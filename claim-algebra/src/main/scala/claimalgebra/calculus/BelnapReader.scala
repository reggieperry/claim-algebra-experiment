package claimalgebra.calculus

import claimalgebra.*

/** The four operator-facing states (the UI grammar) — each a reading of the algebra:
  *   - Resolved = the gate Accepted a single, grounded value (Belnap True, cardinality 1)
  *   - Missing = no signable value — nothing grounded (Belnap Gap), or grounded-but-unsignable
  *     (below threshold / unverified)
  *   - Conflict = the gate Blocked on rival candidates or a glut (Ambiguous / Belnap Glut)
  *   - Superseded = struck (Belnap False) — by an amendment (via `resolveSuperseded`, which carries
  *     a governing replacement) or a plain refutation (via `resolve`, no replacement);
  *     distinguished by the CALL PATH, not by reading a kind tag (the kind axis is orthogonal, for
  *     routing only)
  */
enum Status:
  case Resolved, Missing, Conflict, Superseded

/** One operator-view row: the term, its status, the governing value, the struck prior (if any), and
  * a human note. This is exactly what slide 6 renders.
  */
final case class Row[A](
    term: String,
    status: Status,
    value: Option[A],
    struck: Option[A],
    note: String
)

/** The four-state read — the recompute-on-read from an already-grounded [[Testimony]] (or a
  * [[Supersession]]) to a four-state [[Row]]. The grounding is done upstream by the caller; here
  * the claim algebra (`Testimony` → `Claim` → `Gate`) does the deciding and the status is a direct
  * reading of the algebra's decision, not a heuristic. This is the operational-semantics projection
  * that co-locates with the [[Ledger]] fold.
  */
object BelnapReader:

  // The read's DEFAULT gate parameters — a workbench policy, not the only θ: threshold θ = ⊥, full
  // confidence, verification a separate axis (here trivially satisfied). In this reading the
  // GROUNDING is what gates — a value reaches the algebra only if its citation held upstream.
  // A different consumer (e.g. the experiment) supplies its own θ/ν/verifier to `Gate.accept`.
  private val theta: Lev = Lev.bottom
  private val nu: Lineage => Lev = _ => Lev.top
  private def trusting[A]: Verifier[A] = _ => true

  /** Resolve a single (non-superseded) item: run the grounded testimony through the acceptance gate
    * and read the four-state status off the decision. A `Claim` is this testimony once it has
    * passed the verifier (`Claim.verify`); the gate accepts it only if, IN ADDITION, corner = True
    * ∧ cardinality = 1 ∧ grade ≥ θ. So the status is a direct reading of the algebra, not a
    * heuristic.
    */
  def resolve[A](term: String, t: Testimony[A]): Row[A] =
    Gate.accept(t, theta, nu, trusting) match
      case Decision.Accepted(v) =>
        Row(term, Status.Resolved, Some(v), None, "cited, single candidate — signs")
      case Decision.Blocked(reason) =>
        reason match
          case BlockReason.Gap =>
            Row(term, Status.Missing, None, None, "no clause grounded — fail-closed")
          case BlockReason.Ambiguous =>
            Row(term, Status.Conflict, None, None, "rival candidates — reconcile")
          case BlockReason.Conflict =>
            Row(term, Status.Conflict, None, None, "asserted and contradicted — reconcile")
          case BlockReason.Refuted =>
            Row(
              term,
              Status.Superseded,
              None,
              t.figure,
              "struck (refuted) — no governing replacement"
            )
          case BlockReason.BelowThreshold =>
            Row(term, Status.Missing, None, None, "grounded but below the confidence threshold")
          case BlockReason.Unverified =>
            Row(term, Status.Missing, None, None, "grounded but unverified")

  /** Resolve a SUPERSEDED item: an amendment struck the base value (con-channel) and entered a
    * fresh True. Gate the operative (amendment) value to get the governing figure; retain the
    * struck prior — read channel-blind via `figure`, since its corner is now False (`struck to F
    * and kept`).
    */
  def resolveSuperseded[A](term: String, s: Supersession[A]): Row[A] =
    Gate.accept(s.operative, theta, nu, trusting) match
      case Decision.Accepted(v) =>
        val prior = s.struck.figure
        val note =
          if prior.isDefined then "struck by amendment; governing value cited, prior retained"
          else "amendment value governs; no prior on record"
        Row(term, Status.Superseded, Some(v), prior, note)
      case Decision.Blocked(reason) =>
        reason match
          case BlockReason.Gap =>
            Row(
              term,
              Status.Superseded,
              None,
              s.struck.figure,
              "struck by amendment; no governing replacement grounded"
            )
          case BlockReason.Ambiguous | BlockReason.Conflict =>
            Row(term, Status.Conflict, None, s.struck.figure, "contested supersession — reconcile")
          case BlockReason.Refuted =>
            Row(
              term,
              Status.Conflict,
              None,
              s.struck.figure,
              "amendment itself refuted — reconcile"
            )
          case BlockReason.BelowThreshold | BlockReason.Unverified =>
            Row(term, Status.Conflict, None, s.struck.figure, s"supersession unresolved: $reason")

  /** Resolve an item ACROSS an amendment — the live path to Status.Superseded. Ground it in the
    * base and in the amendment, then dispatch on the structural corner: if the amendment grounds
    * nothing for this item (corner Gap), the base stands ([[resolve]]); if the base is silent but
    * the amendment speaks, the amendment is the value (a fresh statement — nothing struck); if both
    * speak, the amendment SUPERSEDES — build the [[Supersession]] and [[resolveSuperseded]], which
    * yields Superseded when the amendment cleanly governs (prior struck and retained) and Conflict
    * when the amendment is itself contested. Fail-closed throughout: a value signs only if it
    * gate-accepts, on whichever side carries it. (A same-value amendment reads as Superseded with
    * an equal governing/prior — it did restate the clause; rare and honest.)
    */
  def resolveAmended[A](term: String, base: Testimony[A], amendment: Testimony[A]): Row[A] =
    if Testimony.corner(amendment) == Belnap.Gap then resolve(term, base)
    else if Testimony.corner(base) == Belnap.Gap then resolve(term, amendment)
    else resolveSuperseded(term, Testimony.supersede(base, amendment))
