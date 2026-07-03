package claimalgebra

import cats.kernel.Order
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** The acceptance gate is the sole signing point, so it is pinned to its spec as a biconditional,
  * plus an example for every blocking reason. The corner is read structurally from the provenance
  * and the grade is rendered on the pro-channel under the fuzzy carrier [[Lev]] (the valuation `nu`
  * assigns each token its confidence), so the threshold `θ` lives in `Lev`.
  */
class GateSuite extends ScalaCheckSuite:

  private val yes: Verifier[Int] = _ => true
  private val no: Verifier[Int] = _ => false
  private val support = Generators.prov("s1")
  private val full: Lineage => Lev = _ => Lev.top // every token at full confidence

  private def isAccepted[A](d: Decision[A]): Boolean = d match
    case Decision.Accepted(_) => true
    case Decision.Blocked(_) => false

  // The spec itself: accept(c) ⟺ corner = True ∧ rendered support ≥ θ ∧ verify(c) ∧ value present.
  property(
    "the gate signs exactly when the corner is True, the rendered support ≥ θ, verified, and present"
  ) {
    forAll { (t: Testimony[Int], theta: Lev, c: Lev, v: Boolean) =>
      val nu: Lineage => Lev = _ => c
      val verifier: Verifier[Int] = _ => v
      val grade = t.provPro.evaluate(nu)
      val expected =
        Testimony.corner(t) == Belnap.True &&
          Order[Lev].gteqv(grade, theta) &&
          v &&
          t.value.isDefined
      isAccepted(Gate.accept(t, theta, nu, verifier)) == expected
    }
  }

  property("a non-True corner is never signed — the foundation of the never-sign-wrong asymmetry") {
    forAll { (t: Testimony[Int], theta: Lev, c: Lev) =>
      val nu: Lineage => Lev = _ => c
      (Testimony.corner(t) == Belnap.True) || !isAccepted(Gate.accept(t, theta, nu, yes))
    }
  }

  test("a verified, full-support True claim is accepted with its value") {
    val t = Testimony.leaf(42, support)
    assertEquals(Gate.accept(t, Lev.top, full, yes), Decision.Accepted(42))
  }

  test("a gap is blocked as Gap") {
    val t = Testimony.gap[Int]
    assertEquals(Gate.accept(t, Lev.bottom, full, yes), Decision.Blocked(BlockReason.Gap))
  }

  test("a glut is blocked as Conflict") {
    val t = Testimony.single(1, support, support)
    assertEquals(Gate.accept(t, Lev.bottom, full, yes), Decision.Blocked(BlockReason.Conflict))
  }

  test("a refuted claim is blocked as Refuted") {
    val t = Testimony.single(1, Prov.zero, support)
    assertEquals(Gate.accept(t, Lev.bottom, full, yes), Decision.Blocked(BlockReason.Refuted))
  }

  test("a True claim below the threshold is blocked as BelowThreshold") {
    val weak: Lineage => Lev = _ => Lev.deg(0.3)
    val t = Testimony.leaf(1, support)
    assertEquals(
      Gate.accept(t, Lev.deg(0.9), weak, yes),
      Decision.Blocked(BlockReason.BelowThreshold)
    )
  }

  test("a strong True claim that fails verification is blocked as Unverified") {
    val t = Testimony.leaf(1, support)
    assertEquals(Gate.accept(t, Lev.top, full, no), Decision.Blocked(BlockReason.Unverified))
  }

  // An AMBIGUOUS claim — two rival for-candidates, no con: a True corner with cardinality 2. The
  // gate clears the corner but has no single value to sign, so it blocks as Ambiguous (the
  // cardinality conjunct, foundations fold 2). Even at θ=⊥ with a passing verifier it cannot sign.
  test("a True corner with rival values (ambiguous) is blocked as Ambiguous") {
    val t = Testimony.corroborate(
      Testimony.leaf(1, support),
      Testimony.leaf(2, Generators.prov("s2"))
    )
    assertEquals(Testimony.corner(t), Belnap.True)
    assertEquals(t.cardinality, 2)
    assertEquals(Gate.accept(t, Lev.bottom, full, yes), Decision.Blocked(BlockReason.Ambiguous))
  }

  // The block reason — not just accept/reject — is recorded in the trial record, so
  // pin each non-True corner to its reason across the whole input space.
  property("each non-True corner maps to its block reason") {
    forAll { (t: Testimony[Int]) =>
      Testimony.corner(t) match
        case Belnap.Gap =>
          Gate.accept(t, Lev.bottom, full, yes) == Decision.Blocked(BlockReason.Gap)
        case Belnap.False =>
          Gate.accept(t, Lev.bottom, full, yes) == Decision.Blocked(BlockReason.Refuted)
        case Belnap.Glut =>
          Gate.accept(t, Lev.bottom, full, yes) == Decision.Blocked(BlockReason.Conflict)
        case Belnap.True => true // the True branch is pinned by the biconditional
    }
  }

  test("on a True claim, BelowThreshold takes precedence over a failing verifier") {
    val weak: Lineage => Lev = _ => Lev.deg(0.3)
    val t = Testimony.leaf(1, support)
    assertEquals(
      Gate.accept(t, Lev.deg(0.9), weak, no),
      Decision.Blocked(BlockReason.BelowThreshold)
    )
  }

  test("the gate runs the verifier on the claim's own testimony") {
    val onlySeven: Verifier[Int] = _.value.contains(7)
    assertEquals(
      Gate.accept(Testimony.leaf(7, support), Lev.top, full, onlySeven),
      Decision.Accepted(7)
    )
    assertEquals(
      Gate.accept(Testimony.leaf(8, support), Lev.top, full, onlySeven),
      Decision.Blocked(BlockReason.Unverified)
    )
  }
