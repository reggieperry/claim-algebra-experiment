package claimalgebra

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

import Generators.given

/** [[Testimony.conjoin]] — the fail-closed conjunction fork, glut-safe. The residue the type cannot
  * state: `conjoin` presents a signable, clean-`True` composite ONLY when both conjuncts are
  * themselves cleanly `True`, and a CONTESTED conjunct (corner `F` or `Glut`) is carried to a
  * non-`True` corner rather than laundered. The headline check is the worked glut divergence — a
  * gluted conjunct and a clean-`True` conjunct SIGN under bare `⊗ₖ` ([[Testimony.derive]], which
  * launders: con `= c·0 = 0`) but BLOCK under `conjoin` (routed to `∧ₜ`, which carries the
  * contest).
  */
class ConjoinSuite extends ScalaCheckSuite:

  private def prov(id: String): Prov = Generators.prov(id)

  // The four corners as constructed testimonies, each pinned to its corner as a precondition.
  private val trueT: Testimony[Int] = Testimony.leaf(1, prov("t"))
  private val gapT: Testimony[Int] = Testimony.gap[Int]
  private val falseT: Testimony[Int] = Testimony.single(2, Prov.zero, prov("f"))
  private val glutT: Testimony[Int] = Testimony.single(3, prov("gp"), prov("gc"))

  test("the four corner fixtures read as their intended corners") {
    assertEquals(Testimony.corner(trueT), Belnap.True)
    assertEquals(Testimony.corner(gapT), Belnap.Gap)
    assertEquals(Testimony.corner(falseT), Belnap.False)
    assertEquals(Testimony.corner(glutT), Belnap.Glut)
  }

  // The running-system check (decision record 2026-07-07): organism-slot gluted, fruit-slot clean
  // True. Under bare ⊗ₖ the composite signs (the bug); under conjoin it blocks (the fix). The two
  // operators DIVERGE exactly as predicted — this is what closes the glut-laundering hole.
  test("worked glut divergence: derive launders (signs True), conjoin blocks (non-True)") {
    val org = Testimony.single(1, prov("p"), prov("c")) // Glut: pro AND con
    val fruit = Testimony.leaf(2, prov("q")) // clean True
    assertEquals(Testimony.corner(org), Belnap.Glut, "precondition: org is a glut")
    assertEquals(Testimony.corner(fruit), Belnap.True, "precondition: fruit is a clean True")

    // ⊗ₖ LAUNDERS — the con-channel product c·0 = 0 washes the contradiction away → clean True.
    assertEquals(
      Testimony.corner(Testimony.derive(org, fruit)(_ + _)),
      Belnap.True,
      "derive launders the glut to a clean True (the bug the fix closes)"
    )
    // conjoin routes the gluted conjunct to ∧ₜ, which carries the con-mass → NOT a clean True.
    assertNotEquals(
      Testimony.corner(Testimony.conjoin(org, fruit)(_ + _)),
      Belnap.True,
      "conjoin blocks the laundered glut (the fix)"
    )
    // Precisely: the surviving con-mass leaves the composite a Glut.
    assertEquals(Testimony.corner(Testimony.conjoin(org, fruit)(_ + _)), Belnap.Glut)
    // Order-independent: a gluted conjunct blocks whether it is the left or the right operand.
    assertNotEquals(
      Testimony.corner(Testimony.conjoin(fruit, org)(_ + _)),
      Belnap.True,
      "conjoin blocks the glut on the right too"
    )
  }

  // The all-corners fail-closed table: conjoin yields a clean-True (signable) composite IFF BOTH
  // conjuncts are cleanly True; it blocks (corner ≠ True — gap, false, or glut) on every pair where
  // either conjunct is Gap, False, or Glut. The oracle `both cornered True` is the specification of
  // "sign only two clean Trues"; it is read from the INPUT corners, not from re-running conjoin.
  test("all-corners table: conjoin signs (corner True) iff both conjuncts are cleanly True") {
    val corners: List[(Belnap, Testimony[Int])] =
      List(
        Belnap.True -> trueT,
        Belnap.Gap -> gapT,
        Belnap.False -> falseT,
        Belnap.Glut -> glutT
      )
    for
      (ca, a) <- corners
      (cb, b) <- corners
    do
      val signs = Testimony.corner(Testimony.conjoin(a, b)(_ + _)) == Belnap.True
      val bothCleanTrue = ca == Belnap.True && cb == Belnap.True
      assertEquals(signs, bothCleanTrue, clue(s"conjoin($ca, $cb) — signs=$signs"))
  }

  // Property: a contested conjunct (corner ∈ {False, Glut}) NEVER lets the composite sign. ℕ[X] has
  // no subtraction, so a contested conjunct's con-mass cannot cancel — it survives to the root (or
  // the whole conjunction annihilates to the gap). Either way the corner is not a clean True.
  property("a contested conjunct never signs the composite (corner ≠ True)") {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val ca = Testimony.corner(a)
      val cb = Testimony.corner(b)
      val contested =
        ca == Belnap.False || ca == Belnap.Glut || cb == Belnap.False || cb == Belnap.Glut
      !contested || Testimony.corner(Testimony.conjoin(a, b)(_ + _)) != Belnap.True
    }
  }

  // Property: when NEITHER conjunct is contested, conjoin takes the ⊗ₖ path unchanged — it agrees
  // with derive exactly. This pins that the extension touches only the contested branch.
  property("conjoin agrees with derive when neither conjunct is contested") {
    forAll { (a: Testimony[Int], b: Testimony[Int]) =>
      val ca = Testimony.corner(a)
      val cb = Testimony.corner(b)
      val contested =
        ca == Belnap.False || ca == Belnap.Glut || cb == Belnap.False || cb == Belnap.Glut
      contested ||
      (Testimony.conjoin(a, b)(_ + _) == Testimony.derive(a, b)(_ + _))
    }
  }
