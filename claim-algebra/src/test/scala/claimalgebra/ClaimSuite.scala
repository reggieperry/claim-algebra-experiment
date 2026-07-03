package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** `Claim` is a verified `Testimony`: its only constructor runs the verifier, so a claim exists iff
  * verification passed, and it carries the testimony unchanged.
  */
class ClaimSuite extends ScalaCheckSuite:

  property("Claim.verify yields a claim exactly when the verifier passes") {
    forAll { (t: Testimony[Int], v: Boolean) =>
      val verifier: Verifier[Int] = _ => v
      Claim.verify(t, verifier).isDefined == v
    }
  }

  property("a verified claim carries its testimony unchanged") {
    forAll { (t: Testimony[Int]) =>
      Claim.verify(t, _ => true).map(_.testimony) == Some(t)
    }
  }
