package claimalgebra

import cats.kernel.laws.discipline.{BoundedSemilatticeTests, OrderTests}
import munit.DisciplineSuite

import Generators.given

/** `Lev`, the per-channel confidence lattice: a total `Order` by evidence strength (which the
  * gate's `grade ≥ θ` test uses, and which subsumes the `Eq` laws), and `join` (max) / `meet` (min)
  * as bounded semilattices with identities `Bot` and `top`.
  */
class LevLawsSuite extends DisciplineSuite:

  checkAll("Lev.order", OrderTests[Lev].order)

  checkAll(
    "Lev.join ⊔ (boundedSemilattice, identity Bot)",
    BoundedSemilatticeTests[Lev](using Lev.joinSemilattice).boundedSemilattice
  )
  checkAll(
    "Lev.meet ⊓ (boundedSemilattice, identity top)",
    BoundedSemilatticeTests[Lev](using Lev.meetSemilattice).boundedSemilattice
  )
