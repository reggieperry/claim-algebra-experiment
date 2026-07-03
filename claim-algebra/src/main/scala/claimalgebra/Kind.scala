package claimalgebra

/** The KIND of evidence a citation carries (G3) — a closed set, each member routing a conflict to a
  * DIFFERENT desk. A pure routing/blame-attribution decoration: it rides a [[Lineage]] token and is
  * read out of a glut's con-channel by κ̂ ([[Testimony.conflictKinds]]); it is NEVER a rejection
  * axis and never an input to the gate or the grade (the kind axis is a conservative extension —
  * `accept` is unchanged by it; foundations: T-kind-conservative-extension,
  * T-kind-grade-orthogonal).
  *
  *   - `Extraction` — a figure misread from the document → DATA QUALITY (a recheck).
  *   - `Definitional` — two defensible readings of a defined term → CREDIT POLICY / legal.
  *   - `TemporalRetraction` — a restatement asserted then withdrawn → the DEAL LEAD / agent.
  *   - `Verification` — an independent source refutes the figure → AUDIT. (Distinct from the gate's
  *     verify axis, which is the claim's boolean self-check; this is a refuting SOURCE on the
  *     con-channel.)
  */
enum Kind:
  case Extraction, Definitional, TemporalRetraction, Verification

object Kind:
  /** The conservative default: an un-annotated citation is a plain extraction. This is what makes
    * the kind a conservative widening — every pre-G3 `Lineage.from(id)` resolves to a
    * default-Extraction token, and erasing the kind recovers the original ℕ[X] behavior.
    */
  val Default: Kind = Extraction
