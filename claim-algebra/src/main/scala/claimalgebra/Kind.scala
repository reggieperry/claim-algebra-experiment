package claimalgebra

/** An abstract evidence-KIND mark carried on a [[Lineage]] token and unioned by κ̂ ([[Prov.kinds]]
  * / [[Testimony.conflictKinds]]). A pure routing / blame-attribution decoration: it rides a token
  * and is read out of a glut's con-channel; it is NEVER a rejection axis and never an input to the
  * gate or the grade (foundations: T-kind-conservative-extension, T-kind-grade-orthogonal).
  *
  * The library only TAGS and UNIONS marks — it never pattern-matches on one — so it carries no
  * taxonomy of its own: a consumer supplies its own closed set of marks by extending this trait
  * (e.g. `enum MyKind extends Kind`). κ̂ returns a `Set[Kind]` of abstract marks; a consumer
  * narrows it to its own enum where it interprets them.
  *
  * CONTRACT: an implementation MUST have value-based `equals`/`hashCode` consistent with `==`,
  * because a [[Lineage]] is a `Monomial` / `Prov` map key — a mark with reference-only equality
  * would corrupt those keys and mis-render the grade upward (a fail-open). Every Scala 3 `enum`
  * case and `case object` satisfies this; do not extend `Kind` with a class using reference
  * equality.
  */
trait Kind
