package claimalgebra.calculus

import claimalgebra.BlockReason

/** The four structural states of the four-state read — each a direct reading of the algebra's gate
  * decision:
  *   - `Resolved` = the gate accepted a single grounded value (Belnap True, cardinality 1)
  *   - `Missing` = no signable value — nothing grounded (Belnap Gap), or grounded-but-unsignable
  *   - `Conflict` = the gate blocked on rival candidates or a glut (Ambiguous / Belnap Glut)
  *   - `Superseded` = struck (Belnap False) — by an amendment (a governing replacement) or a plain
  *     refutation (no replacement); the two are told apart by the [[Resolution.Reason]], not a tag
  */
enum Status:
  case Resolved, Missing, Conflict, Superseded

/** The structural result of the four-state read: the gate decision projected to a [[Status]], the
  * governing value (present only when one signs), the struck prior (if any), and the
  * [[Resolution.Reason]] that distinguishes branches which collapse to the same
  * `(status, value, struck)` triple. A pure structural value with NO presentation — a consumer
  * renders it (mapping it to a display row). Invariant in `A` (like the value it carries).
  */
final case class Resolution[A](
    status: Status,
    value: Option[A],
    struck: Option[A],
    reason: Resolution.Reason
)

object Resolution:

  /** Why the read reached its [[Status]] — the structural distinguisher for branches that share a
    * `(status, value, struck)` triple (rival candidates vs asserted-and-contradicted are both
    * `Conflict`; a plain refutation vs a struck-with-no-replacement are both `Superseded`). It also
    * makes the plain-refute-vs-amendment call-path distinction structural rather than
    * prose-encoded. A pure SELECTOR emitted downstream of the decision — never an input to the gate
    * or the grade.
    */
  enum Reason:
    case Signed // resolve: gate Accepted a single grounded value
    case NoGrounding // resolve: Gap — nothing grounded
    case RivalCandidates // resolve: Ambiguous — ≥ 2 rival for-candidates
    case Contradicted // resolve: Glut — asserted and refuted
    case RefutedNoReplacement // resolve: Refuted — struck, no governing value
    case BelowThreshold // resolve: grounded but below θ
    case Unverified // resolve: grounded but the verifier refused
    case SupersededGoverning // resolveSuperseded: amendment governs (prior in `struck`, if any)
    case SupersededNoReplacement // resolveSuperseded: amendment struck the prior, none grounded
    case ContestedSupersession // resolveSuperseded: the amendment is itself contested
    case AmendmentRefuted // resolveSuperseded: the amendment is itself refuted
    case SupersessionUnresolved(via: BlockReason) // resolveSuperseded: operative below-θ/unverified
