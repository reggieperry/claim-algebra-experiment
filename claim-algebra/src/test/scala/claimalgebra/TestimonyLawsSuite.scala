package claimalgebra

import cats.kernel.laws.discipline.CommutativeMonoidTests
import munit.DisciplineSuite

import Generators.given

/** The candidate-carrier corroboration monoid, proven law-first. `corroborate ⊕ₖ` is the
  * experiment's spine — the order in which evidence is folded must not change the verdict — so its
  * commutative-monoid laws (associativity, commutativity, the gap identity) are pinned DIRECTLY at
  * the `Testimony` carrier with the stock bundle, not only indirectly via the per-channel `Prov`
  * homomorphism on the channel totals (scala-testing.md: prefer `checkAll` against the type class
  * an operation must satisfy). The instance under test is the
  * `given CommutativeMonoid[Testimony[A]]` in the `Testimony` companion.
  */
class TestimonyLawsSuite extends DisciplineSuite:
  checkAll("Testimony.commutativeMonoid", CommutativeMonoidTests[Testimony[Int]].commutativeMonoid)
