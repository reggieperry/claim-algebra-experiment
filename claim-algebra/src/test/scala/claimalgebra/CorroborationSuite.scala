package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.{collect, forAll}

import Generators.given

/** Behavioral pins for the headline combiner and the corner read — the example tests beside the
  * laws (scala-testing.md). The algebraic laws live in [[EvLawsSuite]]; these name the domain
  * behaviors a reader checks first.
  */
class CorroborationSuite extends ScalaCheckSuite:

  property("the Ev generator reaches all four Belnap corners") {
    forAll((a: Ev) => collect(Ev.corner(a))(true))
  }

  // The shipped carrier is the candidate MAP, not Option[A], so the property suites that draw
  // genTestimony (the gate biconditional, the Testimony monoid) need it to keep reaching the gap,
  // the glut, False, AND multi-candidate ambiguity (cardinality ≥ 2). A distribution report — always
  // passes, the coverage IS the point — the direct analog of the Ev-corner property above; a narrowed
  // generator shows up here as a missing bucket. (scala-testing.md: classify/collect the corners.)
  property("the Testimony generator reaches gap, glut, False, and cardinality ≥ 2") {
    forAll { (t: Testimony[Int]) =>
      collect((Testimony.corner(t), if t.cardinality >= 2 then "card≥2" else "card<2"))(true)
    }
  }

  property("corroborate ⊕ₖ is commutative — which node spoke first cannot matter") {
    forAll((a: Ev, b: Ev) => Ev.kjoin(a, b) == Ev.kjoin(b, a))
  }

  test("two agreeing supports corroborate to a support") {
    assertEquals(Ev.corner(Ev.kjoin(Ev.T, Ev.T)), Belnap.True)
  }

  test("a support meeting a refutation corroborates to the glut") {
    assertEquals(Ev.corner(Ev.kjoin(Ev.T, Ev.F)), Belnap.Glut)
  }

  test("the gap is the corroboration identity — told nothing changes nothing") {
    assertEquals(Ev.kjoin(Ev.N, Ev.T), Ev.T)
  }

  test("the knowledge-meet of a gap is the gap — fail-closed") {
    assertEquals(Ev.kmeet(Ev.N, Ev.T), Ev.N)
  }

  test("a refuted conjunct drives the truth-meet to false, distinct from a gap") {
    assertEquals(Ev.corner(Ev.tmeet(Ev.F, Ev.T)), Belnap.False)
  }
