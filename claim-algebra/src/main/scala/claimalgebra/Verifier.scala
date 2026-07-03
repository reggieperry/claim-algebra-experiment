package claimalgebra

/** The verification axis — the THIRD axis, orthogonal to the Belnap value and to the grade
  * (claim-algebra.html §4). Given a testimony's cited provenance, does its value actually reproduce
  * against the source? The pure algebra does not know HOW to check that — re-resolution needs the
  * corpus — so the predicate is injected: a node supplies a real `Verifier`, and the gate only
  * consults it.
  */
type Verifier[A] = Testimony[A] => Boolean
