package claimalgebra

/** A `Claim` is a `Testimony` that has passed verification — a testimony that asserts, and has been
  * checked against its own provenance (claim-algebra.html §5, "a Testimony refined by a
  * verification predicate").
  *
  * It is an opaque wrapper whose only constructor runs the verifier, so an unverified claim cannot
  * be built: that verification happened is a fact carried in the type, not a flag a later reader
  * must remember to re-check.
  *
  * INVARIANT in `A`: it wraps a [[Testimony]], which keys its candidates by `A` (needing `Eq[A]`)
  * and so cannot vary.
  */
opaque type Claim[A] = Testimony[A]

object Claim:

  /** Verify a testimony, yielding a `Claim` only when the predicate holds. The one and only way to
    * construct a `Claim`.
    */
  def verify[A](t: Testimony[A], verifier: Verifier[A]): Option[Claim[A]] =
    Option.when(verifier(t))(t)

  extension [A](c: Claim[A])
    /** The underlying, verified testimony. */
    def testimony: Testimony[A] = c
