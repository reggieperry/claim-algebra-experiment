package claimalgebra.calculus

import claimalgebra.*
import claimalgebra.Generators.given
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

/** Property pins for the supersession / withdrawal fold semantics — the settled answer the calculus
  * gives (algebra §5.5-5.6 supersede-by-strike; Thm 6.1 withdrawal idempotence; Remark 6.2
  * rehabilitation asymmetry). Each law pins already-correct behavior of the shipped fold; a RED
  * here is a live fail-closed defect, not a test to relax. Complements [[LedgerFoldSuite]]
  * (per-shape examples) and [[LedgerLawsSuite]] (the Thm 4/5/6 meta-theorems) with the
  * supersession-specific properties they do not carry:
  *
  *   - L1/L2: the superseded operative signs CLEAN — the struck prior never lands on the
  *     operative's live con (the misread-glut that Thm 6.4 exclusivity rules out is unreachable
  *     through the public fold).
  *   - L3/L4: the rehabilitation asymmetry — after a whole-slot Withdraw, a fresh assertion of the
  *     same value gluts forever (ℕ[X] is zero-sum-free, so con never cancels), while a supersession
  *     signs. A withdrawn value returns only by supersession, never by re-assertion.
  *   - L5: withdrawal is idempotent at the belief level on the Repl (supersession-pair) shape — the
  *     Open shape is pinned in [[LedgerFoldSuite]].
  *   - L6: chained supersession keeps only the last struck prior (the §3 Repl+Supersede row
  *     replaces the struck prior each step; the full audit lives in the event term).
  */
class FoldSemanticsSuite extends ScalaCheckSuite:

  private def lin(id: String): Lineage =
    Lineage.from(id).getOrElse(fail(s"bad lineage: $id"))

  private val genId: Gen[String] = Gen.oneOf("s1", "s2", "s3", "s4", "s5")

  /** A non-empty provenance channel — one to three known-valid singleton tokens summed, so it is
    * never the gap and a leaf built from it actually supports its value.
    */
  private val genNonEmptyProv: Gen[Prov] =
    for
      n <- Gen.choose(1, 3)
      ids <- Gen.listOfN(n, genId)
    yield ids.foldLeft(Prov.zero)((p, id) => Prov.plus(p, Prov.single(lin(id))))

  /** A clean single-candidate, pro-only leaf — a grounded amendment's shape (corner True, card 1).
    */
  private val genCleanLeaf: Gen[(Int, Testimony[Int])] =
    for
      v <- Gen.choose(0, 50)
      p <- genNonEmptyProv
    yield (v, Testimony.leaf(v, p))

  /** A non-gap prior in any shape the pair path may face: a clean leaf, a glut (one candidate on
    * both channels), or a rival ambiguity (two pro candidates). Never the gap — so a Superseded
    * event builds the pair rather than treating the amendment as a fresh assertion.
    */
  private val genNonGapPrior: Gen[Testimony[Int]] =
    Gen.oneOf(
      genCleanLeaf.map(_._2),
      for
        v <- Gen.choose(0, 50)
        p <- genNonEmptyProv
        c <- genNonEmptyProv
      yield Testimony.single(v, p, c),
      for
        v1 <- Gen.choose(0, 24)
        v2 <- Gen.choose(25, 50)
        p1 <- genNonEmptyProv
        p2 <- genNonEmptyProv
      yield Testimony.corroborate(Testimony.leaf(v1, p1), Testimony.leaf(v2, p2))
    )

  // L1 — the superseded operative is the fresh amendment: corner True, card 1, con empty, and it
  // signs. Nothing from the prior appears in the operative's live channels (algebra §5.6
  // supersede-by-strike; Thm 6.4 exclusivity).
  property(
    "L1 supersededOperativeSignsClean — the operative is the fresh amendment, con empty, signs"
  ) {
    forAll(genNonGapPrior, genCleanLeaf) { (t, vg) =>
      val (v, g) = vg
      val s = Testimony.supersede(t, g)
      (Testimony.corner(s.operative) == Belnap.True) &&
      (s.operative.cardinality == 1) &&
      s.operative.provCon.isZero &&
      (s.operative.value == Some(v)) &&
      (BelnapReader.resolveSuperseded(s).value == Some(v))
    }
  }

  // L2 — the public fold never corroborates the struck prior into the operative (the misread-glut
  // regression): [Assert(t); Supersede(g)] keeps the pair and signs g with the operative's con empty.
  property("L2 noOneTestimonyClobber — [Assert(t); Supersede(g)] signs g, operative con empty") {
    forAll(genNonGapPrior, genCleanLeaf) { (t, vg) =>
      val (v, g) = vg
      val evs = Seq(Evidence.Asserted(t), Evidence.Superseded(g))
      Ledger.belief(evs) match
        case Right(s) =>
          s.operative.provCon.isZero &&
          (s.operative.value == Some(v)) &&
          (Ledger.resolve(evs).value == Some(v))
        case Left(_) => false // both speak, so the fold must keep the pair, never one glutted claim
    }
  }

  // L3 — a whole-slot Withdraw makes a fresh assertion of the same value glut, and it stays glutted
  // under any number of further assertions (ℕ[X] zero-sum-free: con never subtracts). Remark 6.2
  // (rehabilitation asymmetry).
  property("L3 assertAfterWithdrawGlutsForever — Assert; Withdraw; Assert(v)+ stays Glut, blocked") {
    forAll(genCleanLeaf, Gen.choose(1, 4), genNonEmptyProv) { (vg, k, p2) =>
      val (v, g) = vg
      val reasserts = Seq.fill(k)(Evidence.Asserted(Testimony.leaf(v, p2)))
      val evs = Seq(Evidence.Asserted(g), Evidence.Withdrawn[Int]()) ++ reasserts
      val r = Ledger.resolve(evs)
      Ledger.belief(evs).fold(t => Testimony.corner(t) == Belnap.Glut, _ => false) &&
      (r.status == Status.Conflict) &&
      (r.value == None)
    }
  }

  // L4 — a withdrawn value returns only by supersession: Supersede after Withdraw signs the amendment
  // (including when it restates the withdrawn value), with the withdrawn prior retained as the trace.
  property(
    "L4 supersedeAfterWithdrawSigns — Assert(v); Withdraw; Supersede(w) signs w, v retained"
  ) {
    forAll(genCleanLeaf, genCleanLeaf) { (vg, wg) =>
      val (v, gv) = vg
      val (w, gw) = wg
      val evs = Seq(Evidence.Asserted(gv), Evidence.Withdrawn[Int](), Evidence.Superseded(gw))
      val r = Ledger.resolve(evs)
      (r.status == Status.Superseded) && (r.value == Some(w)) && (r.struck == Some(v))
    }
  }

  // L5 (Repl half) — withdrawal is idempotent at the belief level on a supersession pair (striking
  // the operative only). The Open half is pinned in LedgerFoldSuite; this is the Repl shape (Thm 6.1).
  property(
    "L5 withdrawIdempotentAtBeliefLevel (Repl) — a second Withdraw on a superseded slot is a no-op"
  ) {
    forAll(genNonGapPrior, genCleanLeaf) { (t, vg) =>
      val (_, g) = vg
      val base = Seq(Evidence.Asserted(t), Evidence.Superseded(g))
      val once = base :+ Evidence.Withdrawn[Int]()
      val twice = base ++ Seq(Evidence.Withdrawn[Int](), Evidence.Withdrawn[Int]())
      Ledger.belief(twice) == Ledger.belief(once)
    }
  }

  // L6 — chained supersession keeps only the LAST struck prior (the §3 Repl+Supersede row replaces
  // the struck prior each step). Assert(a); Supersede(b); Supersede(c) keeps struck = b (not a), signs c.
  property(
    "L6 chainedSupersessionTraceDepth — struck is the last prior (b), a is dropped, c signs"
  ) {
    val genThree =
      for
        a <- Gen.choose(0, 30)
        b <- Gen.choose(31, 60)
        c <- Gen.choose(61, 90)
        pa <- genNonEmptyProv
        pb <- genNonEmptyProv
        pc <- genNonEmptyProv
      yield (a, Testimony.leaf(a, pa), b, Testimony.leaf(b, pb), c, Testimony.leaf(c, pc))
    forAll(genThree) { case (a, ga, b, gb, c, gc) =>
      val evs = Seq(Evidence.Asserted(ga), Evidence.Superseded(gb), Evidence.Superseded(gc))
      val r = Ledger.resolve(evs)
      Ledger.belief(evs) match
        case Right(s) =>
          (s.operative.value == Some(c)) &&
          (s.struck.figure == Some(b)) &&
          (s.struck.figure != Some(a)) &&
          (r.status == Status.Superseded) &&
          (r.value == Some(c)) &&
          (r.struck == Some(b))
        case Left(_) => false
    }
  }

  // ————— Token-scoped withdrawal (Withdraw(id)) — the completion of the retraction op-set —————
  // `withoutToken` removes ONE assertion's support while rivals stand; whole-slot `strike`/`Withdrawn`
  // moves a whole testimony to con. The audit trace is the recorded event, not a carrier channel.

  private val genTokenA: Gen[Lineage] = Gen.oneOf("s1", "s2", "s3").map(lin)
  private val genIdB: Gen[String] = Gen.oneOf("s4", "s5")

  // Prov-level: withoutToken drops exactly the monomials that USED the token (including a joint
  // derivation through it), keeps independent monomials for the same value, and is idempotent.
  test("Prov.withoutToken drops l-containing monomials (incl. joint use), keeps the rest") {
    val l1 = lin("s1")
    val l2 = lin("s2")
    val p = Prov.plus(
      Prov.plus(Prov.single(l1), Prov.single(l2)),
      Prov.times(Prov.single(l1), Prov.single(l2)) // the joint derivation s1·s2
    )
    assertEquals(p.withoutToken(l1), Prov.single(l2)) // s1 and s1·s2 dropped, s2 survives
    assertEquals(p.withoutToken(l1).withoutToken(l1), p.withoutToken(l1)) // idempotent
    assertEquals(p.withoutToken(lin("s3")), p) // a token not present is a no-op
  }

  // L7 — token withdrawal is idempotent (the token-scoped analog of Thm 6.1, whole-slot withdrawal
  // idempotence).
  property("L7 tokenWithdrawIdempotent — withdrawing the same token twice equals once") {
    forAll { (t: Testimony[Int], l: Lineage) =>
      Testimony.withoutToken(Testimony.withoutToken(t, l), l) == Testimony.withoutToken(t, l)
    }
  }

  // L8 — token withdrawal quarantines: the token leaves BOTH live channels (no con-residue, so it
  // cannot glut). The retained trace is the recorded event, not the carrier.
  property("L8 tokenWithdrawQuarantines — the token is absent from both channel supports") {
    forAll { (t: Testimony[Int], l: Lineage) =>
      val r = Testimony.withoutToken(t, l)
      !r.provPro.support.contains(l) && !r.provCon.support.contains(l)
    }
  }

  // L9 — no resurrection: after WithdrawToken(l), a disjoint further assertion never brings l back.
  property("L9 tokenWithdrawNoResurrection — a withdrawn token stays absent from the belief") {
    forAll(Gen.choose(0, 50), genTokenA, Gen.choose(0, 50), genIdB) { (v, l, v2, idB) =>
      val evs = Seq(
        Evidence.Asserted(Testimony.leaf(v, Prov.single(l))),
        Evidence.WithdrawnToken[Int](l),
        Evidence.Asserted(Testimony.leaf(v2, Prov.single(lin(idB))))
      )
      Ledger.belief(evs) match
        case Left(t) => !t.provPro.support.contains(l) && !t.provCon.support.contains(l)
        case Right(s) => !s.operative.provPro.support.contains(l)
    }
  }

  // L10 — token withdrawal commutes with corroboration of a testimony NOT containing the token
  // (widens Theorem 5.1 assertion permutation invariance to the mixed assert/token-withdraw fragment
  // on disjoint tokens).
  property("L10 tokenWithdrawCommutesWithDisjointCorroborate — ∖l distributes over a disjoint ⊕") {
    forAll { (t: Testimony[Int], g0: Testimony[Int], l: Lineage) =>
      val g = Testimony.withoutToken(g0, l) // l-free by construction, so the disjointness holds
      Testimony.withoutToken(Testimony.corroborate(t, g), l) ==
        Testimony.corroborate(Testimony.withoutToken(t, l), g)
    }
  }

  // L11 — the completeness divergence: whole-slot Withdraw CANNOT express token withdrawal. Token-
  // withdraw then a fresh assertion of the same value SIGNS (no con-residue); whole-slot Withdraw
  // then the same assertion GLUTS forever (strike's con-residue is uncancellable, ℕ[X] zero-sum-free).
  property(
    "L11 tokenWithdrawVsWholeSlot — token-withdraw re-assert signs; whole-slot re-assert gluts"
  ) {
    forAll(Gen.choose(0, 50), genTokenA, genIdB) { (v, la, idB) =>
      val a = Testimony.leaf(v, Prov.single(la))
      val reassert = Testimony.leaf(v, Prov.single(lin(idB)))
      val tokenPath =
        Seq(Evidence.Asserted(a), Evidence.WithdrawnToken[Int](la), Evidence.Asserted(reassert))
      val slotPath =
        Seq(Evidence.Asserted(a), Evidence.Withdrawn[Int](), Evidence.Asserted(reassert))
      (Ledger.resolve(tokenPath).status == Status.Resolved) &&
      (Ledger.resolve(tokenPath).value == Some(v)) &&
      (Ledger.resolve(slotPath).status == Status.Conflict)
    }
  }

  // L12 — Non-theorem 5.2: full-language permutation invariance is FALSE by design. The SAME event
  // multiset reordered changes the result once it contains a supersession. [Assert(g); Supersede(h)]
  // supersedes g by h (Superseded, value h, g struck); [Supersede(h); Assert(g)] lands h into the empty
  // slot as a fresh assertion (nothing to strike), then corroborates the rival g — two positive values,
  // a Conflict. Order matters; supersessions must be serialized per slot. Complements the assertion-
  // fragment permutation INVARIANCE (LedgerLawsSuite), which holds precisely because assert-only folds
  // commute — this is the exact complement, the witness that the FULL language does not.
  test(
    "L12 fullLanguagePermutationIsFalse — reordering a supersession multiset changes the result"
  ) {
    val g = Testimony.leaf(1, Prov.single(lin("s1")))
    val h = Testimony.leaf(2, Prov.single(lin("s2")))
    val fwd = Seq(Evidence.Asserted(g), Evidence.Superseded(h))
    val rev = Seq(Evidence.Superseded(h), Evidence.Asserted(g))
    assertNotEquals(Ledger.resolve(fwd), Ledger.resolve(rev))
    // fwd: h governs, g struck.
    assertEquals(Ledger.resolve(fwd).status, Status.Superseded)
    assertEquals(Ledger.resolve(fwd).value, Some(2))
    // rev: h is fresh (nothing to supersede yet), then the rival g makes the slot ambiguous.
    assertEquals(Ledger.resolve(rev).status, Status.Conflict)
    assertEquals(Ledger.resolve(rev).value, None)
  }

  // L13 — §3 row 7 (Repl(s,o), Assert(g) -> Repl(s, o ⊞ g)): a later assertion after a supersession
  // has formed accumulates onto the OPERATIVE, not the struck prior. A rival value makes the operative
  // ambiguous (Conflict, card 2); the same value re-asserted still signs. Pins the contested-
  // supersession-by-later-assertion cell (reached only generatively otherwise; Cor 7.2).
  test(
    "L13 assertAfterSupersedeHitsOperative — a rival goes Conflict, the same value still signs"
  ) {
    val a = Testimony.leaf(1, Prov.single(lin("s1")))
    val b = Testimony.leaf(2, Prov.single(lin("s2")))
    val rival = Testimony.leaf(3, Prov.single(lin("s3")))
    val contested =
      Ledger.resolve(Seq(Evidence.Asserted(a), Evidence.Superseded(b), Evidence.Asserted(rival)))
    assertEquals(contested.status, Status.Conflict)
    assertEquals(contested.value, None)
    val bAgain = Testimony.leaf(2, Prov.single(lin("s4")))
    val reinforced =
      Ledger.resolve(Seq(Evidence.Asserted(a), Evidence.Superseded(b), Evidence.Asserted(bAgain)))
    assertEquals(reinforced.status, Status.Superseded)
    assertEquals(reinforced.value, Some(2))
    assertEquals(reinforced.struck, Some(1))
  }

  // L14 — §3 row 11 (Repl(s,o), WithdrawToken(l) -> Repl(s, o ∖ l)): token withdrawal on a superseded
  // slot drops just that token from the OPERATIVE, leaves the struck prior untouched, and leaves no
  // con residue (the Repl-shape parity of L9/L11's Open-shape token withdrawal; cf. L5's whole-slot).
  test(
    "L14 tokenWithdrawOnReplHitsOperativeOnly — token dropped from the operative, prior intact"
  ) {
    val a = Testimony.leaf(1, Prov.single(lin("s1")))
    val b = Testimony.leaf(2, Prov.plus(Prov.single(lin("s2")), Prov.single(lin("s3"))))
    val evs =
      Seq(Evidence.Asserted(a), Evidence.Superseded(b), Evidence.WithdrawnToken[Int](lin("s2")))
    Ledger.belief(evs) match
      case Right(s) =>
        assert(!s.operative.provPro.support.contains(lin("s2")), "s2 dropped from the operative")
        assert(s.operative.provPro.support.contains(lin("s3")), "the independent token s3 survives")
        assert(s.operative.provCon.isZero, "no con residue on the operative")
        assertEquals(
          s.struck,
          Testimony.strike(a)
        ) // the struck prior untouched by token withdrawal
      case Left(_) => fail("a formed supersession pair must stay Repl")
  }
