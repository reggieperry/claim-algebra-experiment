package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** The carrier's combiners over the `Prov` polynomial ℕ[X]. The grade is no longer stored — it is
  * rendered from the two polynomials — so these properties pin: the JOINT-use (`·`) vs ALTERNATIVE
  * (`+`) provenance distinction `derive` and `corroborate` must NOT share, fail-closed `derive` by
  * the semiring annihilator, the structural Belnap corner, the channel swap of `refute`, and the
  * consistency law that makes dropping the stored grade safe — the rendered grade equals the
  * bilattice combination of the rendered inputs.
  */
class TestimonySuite extends ScalaCheckSuite:

  // The annihilator — the essential fail-closed law (foundations: N ⊗ₖ x = N). A gap on either side
  // drives the derivation to the gap, with no candidate to manufacture a value from.
  property("derive with a gap on either side is the gap — N ⊗ₖ x = N") {
    forAll { (a: Testimony[Int]) =>
      (Testimony.derive(Testimony.gap[Int], a)(_ + _) == Testimony.gap[Int]) &&
      (Testimony.derive(a, Testimony.gap[Int])(_ + _) == Testimony.gap[Int])
    }
  }

  // The safety direction of fail-closure: a derivation can only present a signable value if BOTH
  // inputs carried pro-support and the result took on no con. (The converse is not a biconditional
  // under the candidate carrier — a glut ⊗ₖ a clean True renders a clean value, since joint-use con
  // needs both sides to carry con; the gate's value is therefore a derived read, not stored.)
  property("derive presents a value only if both inputs had pro-support and the result has no con") {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val d = Testimony.derive(a, b)(_ + _)
      !d.value.isDefined ||
      (!a.provPro.isZero && !b.provPro.isZero && d.provCon.isZero)
    }
  }

  property("derive combines provenance by JOINT use · (Prov.times) on both channels") {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val d = Testimony.derive(a, b)(_ + _)
      (d.provPro == Prov.times(a.provPro, b.provPro)) &&
      (d.provCon == Prov.times(a.provCon, b.provCon))
    }
  }

  property(
    "corroborate combines provenance by ALTERNATIVE derivation + (Prov.plus) on both channels"
  ) {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val c = Testimony.corroborate(a, b)
      (c.provPro == Prov.plus(a.provPro, b.provPro)) &&
      (c.provCon == Prov.plus(a.provCon, b.provCon))
    }
  }

  test(
    "· and + are distinct on BOTH channels, and the distinction survives a non-idempotent render"
  ) {
    // A two-channel testimony so the con-channel is exercised too (a leaf has an empty con-channel).
    val t = Testimony.single(1, Generators.prov("s1"), Generators.prov("s2"))
    val derived = Testimony.derive(t, t)(_ + _) // provPro = s1², provCon = s2²
    val corroborated = Testimony.corroborate(t, t) // provPro = 2·s1, provCon = 2·s2
    assertNotEquals(derived.provPro, corroborated.provPro, "pro channel: s1² ≠ 2·s1")
    assertNotEquals(derived.provCon, corroborated.provCon, "con channel: s2² ≠ 2·s2")
    // The structural difference is visible to a non-idempotent render — Viterbi squares (0.25) vs
    // max-of-two (0.5) — so a regression collapsing · and + would change the rendered grade.
    val nu: Lineage => Viterbi = _ => Viterbi(0.5)
    assertNotEquals(derived.provPro.evaluate(nu), corroborated.provPro.evaluate(nu))
  }

  // The structural corner — read from the two channels' emptiness, not a rendered grade.
  test("the Belnap corner is read structurally from the two channels") {
    assertEquals(Testimony.corner(Testimony.leaf(1, Generators.prov("s1"))), Belnap.True)
    assertEquals(Testimony.corner(Testimony.single(1, Prov.zero, Prov.zero)), Belnap.Gap)
    assertEquals(
      Testimony.corner(Testimony.single(1, Prov.zero, Generators.prov("s1"))),
      Belnap.False
    )
    assertEquals(
      Testimony.corner(Testimony.single(1, Generators.prov("s1"), Generators.prov("s2"))),
      Belnap.Glut
    )
  }

  // The consistency law — why dropping the stored grade is safe. Because ν̂ is a homomorphism and
  // Lev's ·/+ are meet/join, rendering the combined polynomial equals combining the rendered inputs:
  // renderEv(derive(a,b)) = kmeet(renderEv a, renderEv b), and likewise corroborate/kjoin. This is
  // exactly the identity the old eager grade relied on, now proven rather than duplicated as state.
  property("renderEv(derive) = ⊗ₖ of the rendered inputs (fuzzy)") {
    forAll { (a: Testimony[Int], b: Testimony[Int], nu: Lineage => Lev) =>
      Testimony.renderEv(Testimony.derive(a, b)(_ + _))(nu) ==
        Ev.kmeet(Testimony.renderEv(a)(nu), Testimony.renderEv(b)(nu))
    }
  }

  property("renderEv(corroborate) = ⊕ₖ of the rendered inputs (fuzzy)") {
    forAll { (a: Testimony[Int], b: Testimony[Int], nu: Lineage => Lev) =>
      Testimony.renderEv(Testimony.corroborate(a, b))(nu) ==
        Ev.kjoin(Testimony.renderEv(a)(nu), Testimony.renderEv(b)(nu))
    }
  }

  // refute swaps the two channels per candidate. The candidate VALUES are untouched, but `value`
  // (the signable read) does not survive a retraction — flipping a True leaf's support onto the
  // con-channel makes it a False, whose `value` is `None`. So the channel swap is the law here.
  property("refute swaps the two provenance channels") {
    forAll { (a: Testimony[Int]) =>
      val r = Testimony.refute(a)
      (r.provPro == a.provCon) && (r.provCon == a.provPro)
    }
  }

  property("refute is involutive") {
    forAll((a: Testimony[Int]) => Testimony.refute(Testimony.refute(a)) == a)
  }

  // strike — the ABSORBING deletion, distinct from refute's involution. It clears pro and folds all
  // support onto con, so it is IDEMPOTENT: a value struck twice STAYS struck (the fail-closed law the
  // Ledger fold needs — a double refute would resurrect it and sign a struck value).
  property("strike is idempotent — striking an already-struck testimony is a no-op") {
    forAll((a: Testimony[Int]) => Testimony.strike(Testimony.strike(a)) == Testimony.strike(a))
  }

  property("strike clears the pro-channel — nothing signs after a strike") {
    forAll { (a: Testimony[Int]) =>
      val s = Testimony.strike(a)
      s.provPro.isZero && s.value.isEmpty
    }
  }

  // ∧ₜ (truthMeet) — the F7 fork. Pro combines by JOINT use · (like ⊗ₖ); con by the THREE-term
  // cross-term `ca·pb + pa·cb + ca·cb` (the relaxable realization of the essential fork, fold 1).
  // Summed over the candidate map, the con-total telescopes to the cross-term on the channel totals.
  property(
    "truthMeet combines pro by · and con by the three-term cross-term — the per-channel asymmetry"
  ) {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val m = Testimony.truthMeet(a, b)(_ + _)
      val con = Prov.plus(
        Prov.plus(
          Prov.times(a.provCon, b.provPro),
          Prov.times(a.provPro, b.provCon)
        ),
        Prov.times(a.provCon, b.provCon)
      )
      (m.provPro == Prov.times(a.provPro, b.provPro)) && (m.provCon == con)
    }
  }

  test("the F7 fork: a refuted conjunct goes to the gap N under ⊗ₖ but to F under ∧ₜ") {
    val t = Testimony.leaf(1, Generators.prov("s1")) // True
    val refuted = Testimony.single(2, Prov.zero, Generators.prov("s2")) // False (con support)
    // ⊗ₖ treats the refutation as missing support — the conjunct is annihilated to the gap.
    assertEquals(Testimony.corner(Testimony.derive(t, refuted)(_ + _)), Belnap.Gap)
    // ∧ₜ carries the refutation on the con-channel — the conjunct is driven to False.
    assertEquals(Testimony.corner(Testimony.truthMeet(t, refuted)(_ + _)), Belnap.False)
  }

  test("∧ₜ's pro-channel is · not + — the wrong-on-pro bug would hide under an idempotent render") {
    val t = Testimony.single(1, Generators.prov("s1"), Generators.prov("s2"))
    val m = Testimony.truthMeet(t, t)(_ + _) // provPro = s1² (·), provCon = 2·s2 (+)
    val nu: Lineage => Viterbi = _ => Viterbi(0.5)
    assertEquals(m.provPro.evaluate(nu), Viterbi(0.25)) // 0.5 × 0.5 (·), not max = 0.5 (+)
  }

  test(
    "F5 supersession: the superseded clause is struck to F and kept, the amendment is a fresh T"
  ) {
    val original = Testimony.leaf(100, Generators.prov("base"))
    val amendment = Testimony.leaf(120, Generators.prov("amend"))
    val s = Testimony.supersede(original, amendment)
    assertEquals(Testimony.corner(s.struck), Belnap.False) // retracted, not gone
    assertEquals(s.struck.provCon, original.provPro) // its support rides the con-channel
    assertEquals(Testimony.corner(s.operative), Belnap.True) // the amendment governs
    assertEquals(s.operative.value, Some(120))
  }

  // Supersession strikes the prior by STRIKE (withdrawal), NOT by refute (¬ channel swap) — calculus
  // Def 2.14 / Remark 6.4, per the strike-based-supersession revision. The F5 test above uses a CLEAN
  // (leaf) prior, on which the two coincide — refute swaps (pro, 0) → (0, pro), strike folds
  // (pro, 0) → (0, pro) — so it cannot tell them apart. They diverge only on a CONTESTED prior: strike
  // CLEARS pro and folds all prior evidence to con ((pro, con) → (0, pro + con)), refute SWAPS the
  // channels ((pro, con) → (con, pro)). Pin the choice there, so a refute-for-strike regression fails
  // here. Strike is right because a re-superseded (already-struck) prior must stay struck: refute is
  // involutive, so refuting a refuted prior would resurrect it, whereas strike is idempotent.
  test("supersede strikes the prior by strike, not refute — pinned on a contested prior") {
    val contestedPrior = Testimony.single(1, Generators.prov("s1"), Generators.prov("s2"))
    val amendment = Testimony.single(2, Generators.prov("s3"), Prov.zero)
    val struck = Testimony.supersede(contestedPrior, amendment).struck
    assertEquals(
      struck,
      Testimony.strike(contestedPrior),
      "struck = strike(prior) — pro cleared, all prior evidence folded to con"
    )
    assertNotEquals(
      struck,
      Testimony.refute(contestedPrior)
    ) // refute would swap channels, not clear pro
  }
