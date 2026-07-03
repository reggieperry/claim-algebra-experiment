package claimalgebra

import cats.kernel.laws.discipline.{BoundedSemilatticeTests, EqTests}
import munit.DisciplineSuite

import Generators.given

/** Law-first proof that the bilattice grade's operations are the type classes the algebra claims
  * (claim-algebra.html §4). Each of the four operations is a bounded semilattice over `Ev`; the
  * bilattice-specific laws no stock bundle carries are checked through [[ClaimAlgebraLaws]].
  */
class EvLawsSuite extends DisciplineSuite:

  checkAll("Ev.eq", EqTests[Ev].eqv)

  checkAll(
    "Ev.corroboration ⊕ₖ (boundedSemilattice, identity N)",
    BoundedSemilatticeTests[Ev](using Ev.corroboration).boundedSemilattice
  )
  checkAll(
    "Ev.conjunction ⊗ₖ (boundedSemilattice, identity B)",
    BoundedSemilatticeTests[Ev](using Ev.conjunction).boundedSemilattice
  )
  checkAll(
    "Ev.truthMeet ∧ₜ (boundedSemilattice, identity T)",
    BoundedSemilatticeTests[Ev](using Ev.truthMeet).boundedSemilattice
  )
  checkAll(
    "Ev.truthJoin ∨ₜ (boundedSemilattice, identity F)",
    BoundedSemilatticeTests[Ev](using Ev.truthJoin).boundedSemilattice
  )

  checkAll("Ev.claimAlgebra (bilattice-specific laws)", ClaimAlgebraLaws.all)
