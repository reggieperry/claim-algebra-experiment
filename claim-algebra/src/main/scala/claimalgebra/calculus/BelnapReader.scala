package claimalgebra.calculus

import claimalgebra.*

/** The four-state read — the recompute-on-read from an already-grounded [[Testimony]] (or a
  * [[Supersession]]) to a structural [[Resolution]]. The grounding is done upstream by the caller;
  * here the claim algebra (`Testimony` → `Claim` → `Gate`) does the deciding and the status is a
  * direct reading of the algebra's decision, not a heuristic. This is the operational-semantics
  * projection that co-locates with the [[Ledger]] fold.
  */
object BelnapReader:

  // The read's DEFAULT gate parameters: threshold θ = ⊥, full confidence, verification a separate
  // axis (here trivially satisfied). In this reading the GROUNDING is what gates — a value reaches
  // the algebra only if its citation held upstream. A different consumer supplies its own
  // θ/ν/verifier to `Gate.accept`.
  private val theta: Lev = Lev.bottom
  private val nu: Lineage => Lev = _ => Lev.top
  private def trusting[A]: Verifier[A] = _ => true

  import Resolution.Reason

  /** Resolve a single (non-superseded) item: run the grounded testimony through the acceptance gate
    * and read the four-state status off the decision. A `Claim` is this testimony once it has
    * passed the verifier (`Claim.verify`); the gate accepts it only if, IN ADDITION, corner = True
    * ∧ cardinality = 1 ∧ grade ≥ θ. So the status is a direct reading of the algebra, not a
    * heuristic.
    */
  def resolve[A](t: Testimony[A]): Resolution[A] =
    Gate.accept(t, theta, nu, trusting) match
      case Decision.Accepted(v) =>
        Resolution(Status.Resolved, Some(v), None, Reason.Signed)
      case Decision.Blocked(reason) =>
        reason match
          case BlockReason.Gap =>
            Resolution(Status.Missing, None, None, Reason.NoGrounding)
          case BlockReason.Ambiguous =>
            Resolution(Status.Conflict, None, None, Reason.RivalCandidates)
          case BlockReason.Conflict =>
            Resolution(Status.Conflict, None, None, Reason.Contradicted)
          case BlockReason.Refuted =>
            Resolution(Status.Superseded, None, t.figure, Reason.RefutedNoReplacement)
          case BlockReason.BelowThreshold =>
            Resolution(Status.Missing, None, None, Reason.BelowThreshold)
          case BlockReason.Unverified =>
            Resolution(Status.Missing, None, None, Reason.Unverified)

  /** Resolve a SUPERSEDED item: an amendment struck the base value (con-channel) and entered a
    * fresh True. Gate the operative (amendment) value to get the governing figure; retain the
    * struck prior — read channel-blind via `figure`, since its corner is now False (struck to F and
    * kept).
    */
  def resolveSuperseded[A](s: Supersession[A]): Resolution[A] =
    Gate.accept(s.operative, theta, nu, trusting) match
      case Decision.Accepted(v) =>
        Resolution(Status.Superseded, Some(v), s.struck.figure, Reason.SupersededGoverning)
      case Decision.Blocked(reason) =>
        reason match
          case BlockReason.Gap =>
            Resolution(Status.Superseded, None, s.struck.figure, Reason.SupersededNoReplacement)
          case BlockReason.Ambiguous | BlockReason.Conflict =>
            Resolution(Status.Conflict, None, s.struck.figure, Reason.ContestedSupersession)
          case BlockReason.Refuted =>
            Resolution(Status.Conflict, None, s.struck.figure, Reason.AmendmentRefuted)
          case BlockReason.BelowThreshold | BlockReason.Unverified =>
            Resolution(
              Status.Conflict,
              None,
              s.struck.figure,
              Reason.SupersessionUnresolved(reason)
            )

  /** Resolve an item ACROSS an amendment — the path to Status.Superseded. Ground it in the base and
    * in the amendment, then dispatch on the structural corner: if the amendment grounds nothing for
    * this item (corner Gap), the base stands ([[resolve]]); if the base is silent but the amendment
    * speaks, the amendment is the value (a fresh statement — nothing struck); if both speak, the
    * amendment SUPERSEDES — build the [[Supersession]] and [[resolveSuperseded]], which yields
    * Superseded when the amendment cleanly governs (prior struck and retained) and Conflict when
    * the amendment is itself contested. Fail-closed throughout: a value signs only if it
    * gate-accepts, on whichever side carries it. (A same-value amendment reads as Superseded with
    * an equal governing/prior — it did restate the clause; rare and honest.)
    */
  def resolveAmended[A](base: Testimony[A], amendment: Testimony[A]): Resolution[A] =
    if Testimony.corner(amendment) == Belnap.Gap then resolve(base)
    else if Testimony.corner(base) == Belnap.Gap then resolve(amendment)
    else resolveSuperseded(Testimony.supersede(base, amendment))
