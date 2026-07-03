package claimalgebra

/** The desk that owns reconciling a conflict — who κ̂'s kinds route to (G3). Each evidence kind is
  * a different person's problem: a misread figure is data-quality's, a defined-term dispute is
  * credit policy's, a withdrawn restatement is the deal lead's, an independent refutation is
  * audit's.
  */
enum Desk:
  case DataQuality, CreditPolicy, DealLead, Audit

/** The fixed routing from evidence kinds to the desks that own them — the consumer surface for κ̂
  * ([[Testimony.conflictKinds]]). A glut's kind-set names which desks must reconcile it; this is
  * the routing / blame-attribution capability the kind tag exists for. Pure and total. It composes
  * across an arbitrary topology for free: κ̂ unions the kinds through the provenance algebra, so
  * the desks fall out of one read with no per-kind plumbing.
  */
object Routing:

  /** The desk owning a single kind. Exhaustive — a new [[Kind]] forces a routing decision here. */
  def deskFor(kind: Kind): Desk = kind match
    case Kind.Extraction => Desk.DataQuality
    case Kind.Definitional => Desk.CreditPolicy
    case Kind.TemporalRetraction => Desk.DealLead
    case Kind.Verification => Desk.Audit

  /** The desks a conflict's kind-set routes to. The empty set (a non-glut) routes to no desk. */
  def desks(kinds: Set[Kind]): Set[Desk] = kinds.map(deskFor)

  /** Route a claim's conflict directly: the desks of the kinds on its con-channel (∅ for a
    * non-glut).
    */
  def route[A](t: Testimony[A]): Set[Desk] = desks(Testimony.conflictKinds(t))
