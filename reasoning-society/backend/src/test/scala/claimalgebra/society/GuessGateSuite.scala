package claimalgebra.society

/** B1 (recovery-and-endgame) — the pure GameCore behaviour of the guess-to-oracle folds. A
  * `GuessAnswered` is belief-inert (projects to nothing); a `No` MASKS the candidate off the fold
  * (never a Refute, which would glut the slot's channel TOTALS and deadlock all future signing); a
  * `Yes` relaxes ONLY the no-lone-sign floor, and only BEHIND `Gate.accept` — a contested candidate
  * is still held.
  */
class GuessGateSuite extends munit.FunSuite with SocietyFixtures:

  private val driller = mkAgent("driller")
  private val splitter = mkAgent("splitter")
  private val skeptic = mkAgent("skeptic")
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  private def guess(seq: Int, c: Answer, a: OracleAnswer): Event =
    Event.GuessAnswered(seq, seq.toLong, c, a)

  test("a GuessAnswered is belief-inert — it projects to no Evidence") {
    assertEquals(GameCore.project(Vector(guess(1, dog, OracleAnswer.Yes))), Nil)
    assertEquals(GameCore.project(Vector(guess(1, dog, OracleAnswer.No))), Nil)
  }

  test("a lone unconfirmed candidate signs once the oracle CONFIRMS it (floor relaxed)") {
    val log = Vector(mkAssert(1, driller, dog))
    assertEquals(GameCore.decide(log, log.size), GateDecision.Abstain(AbstainReason.Unconfirmed(1)))
    val confirmed = log :+ guess(2, dog, OracleAnswer.Yes)
    assertEquals(GameCore.decide(confirmed, confirmed.size), GateDecision.Sign(dog))
  }

  test("an oracle NO masks the candidate off the fold — never a Refute (no glut, no deadlock)") {
    val log = Vector(mkAssert(1, driller, dog), guess(2, dog, OracleAnswer.No))
    assertEquals(
      GameCore.slot(log, log.size).value,
      None,
      clue("dog is masked out — gap, not glut")
    )
    assert(
      !GameView.from(log).hypotheses.exists((c, _) => c == dog),
      clue("dog off the live board")
    )
    assert(GameCore.maskedCandidates(log).contains(dog))
  }

  test(
    "after a NO, a FRESH clean candidate still reads True and can be confirmed (B-1 deadlock gone)"
  ) {
    val log = Vector(
      mkAssert(1, driller, dog),
      guess(2, dog, OracleAnswer.No),
      mkAssert(3, splitter, cat)
    )
    // dog masked; cat live and unconfirmed — NOT gluted by the rejected dog.
    assertEquals(GameCore.slot(log, log.size).value, Some(cat))
    assertEquals(GameCore.decide(log, log.size), GateDecision.Abstain(AbstainReason.Unconfirmed(1)))
    assertEquals(GameView.from(log).hypotheses, List(cat -> 1))
    // and the oracle can then confirm cat:
    val confirmed = log :+ guess(4, cat, OracleAnswer.Yes)
    assertEquals(GameCore.decide(confirmed, confirmed.size), GateDecision.Sign(cat))
  }

  test("a YES CANNOT sign a contested candidate — the floor relaxation is behind Gate.accept") {
    val log = Vector(
      mkAssert(1, driller, dog),
      guess(2, dog, OracleAnswer.Yes),
      refute(3, skeptic, dog) // dog now pro+con → Glut → Gate blocks before the floor is consulted
    )
    GameCore.decide(log, log.size) match
      case GateDecision.Abstain(AbstainReason.Blocked(_)) => () // correct: contested → held
      case other => fail(s"a confirmed-but-contested candidate must NOT sign, got $other")
  }

  // --- fallible-oracle Slice 4: the k-confirmation quorum ---

  test("oracleConfirmations counts the distinct YES guesses for a candidate") {
    val base = Vector(mkAssert(1, driller, dog))
    val twoYes = base :+ guess(2, dog, OracleAnswer.Yes) :+ guess(3, dog, OracleAnswer.Yes)
    assertEquals(GameCore.oracleConfirmations(base, dog), 0)
    assertEquals(GameCore.oracleConfirmations(twoYes, dog), 2)
    assertEquals(GameCore.oracleConfirmations(twoYes, cat), 0, clue("counts per-candidate"))
  }

  test("k=1 is byte-identical (one YES signs); k=2 needs TWO genuine confirmations") {
    val oneYes = Vector(mkAssert(1, driller, dog), guess(2, dog, OracleAnswer.Yes))
    val twoYes = oneYes :+ guess(3, dog, OracleAnswer.Yes)
    // k=1 (the shipped default): one Yes signs a lone candidate — exactly as B1.
    assertEquals(GameCore.decide(oneYes, oneYes.size), GateDecision.Sign(dog))
    // k=2: one Yes is short of quorum — still Unconfirmed; the second Yes signs.
    assertEquals(
      GameCore.decide(oneYes, oneYes.size, k = 2),
      GateDecision.Abstain(AbstainReason.Unconfirmed(1))
    )
    assertEquals(GameCore.decide(twoYes, twoYes.size, k = 2), GateDecision.Sign(dog))
  }

  test(
    "the k-quorum stays BEHIND Gate.accept — k confirmations cannot sign a contested candidate"
  ) {
    val log = Vector(
      mkAssert(1, driller, dog),
      guess(2, dog, OracleAnswer.Yes),
      guess(3, dog, OracleAnswer.Yes),
      refute(4, skeptic, dog) // glut → Blocked before the floor, regardless of k
    )
    GameCore.decide(log, log.size, k = 2) match
      case GateDecision.Abstain(AbstainReason.Blocked(_)) => ()
      case other => fail(s"k confirmations must NOT sign a contested candidate, got $other")
  }

  // --- fallible-oracle E2: the seam closure (corroborationSigns) ---

  test(
    "seam-gated: a 2-backer candidate no longer signs standalone — it reads Unconfirmed, but the oracle still signs it"
  ) {
    val twoBackers =
      Vector(mkAssert(1, driller, dog), Event.Corroborate(2, 2L, splitter, dog, "agreed"))
    // seam-open (default): the two-backer disjunct C signs, exactly as shipped.
    assertEquals(GameCore.decide(twoBackers, twoBackers.size), GateDecision.Sign(dog))
    // seam-gated: C is dropped → Unconfirmed, so the winner must reach the oracle.
    assertEquals(
      GameCore.decide(twoBackers, twoBackers.size, corroborationSigns = false),
      GateDecision.Abstain(AbstainReason.Unconfirmed(2)),
      clue("dropping C narrows verify toward O — a 2-backer alone no longer signs")
    )
    // the check is ADDED, not the path removed: an oracle Yes still signs the 2-backer candidate.
    val confirmed = twoBackers :+ guess(3, dog, OracleAnswer.Yes)
    assertEquals(
      GameCore.decide(confirmed, confirmed.size, corroborationSigns = false),
      GateDecision.Sign(dog)
    )
  }

  test("alreadyGuessed — the k-pose budget terminates, and stops after any non-Yes") {
    val yes = guess(2, dog, OracleAnswer.Yes)
    val twoYes = Vector(yes, guess(3, dog, OracleAnswer.Yes))
    // never posed → budget available (both k).
    assert(!GameCore.alreadyGuessed(Vector.empty, dog, k = 1))
    assert(!GameCore.alreadyGuessed(Vector.empty, dog, k = 2))
    // k=1: any single guess spends the budget — exactly "posed at all" (byte-identical to B1).
    assert(GameCore.alreadyGuessed(Vector(yes), dog, k = 1))
    // k=2: one Yes leaves room for a second; two Yes spend it.
    assert(!GameCore.alreadyGuessed(Vector(yes), dog, k = 2), clue("one Yes: budget left"))
    assert(GameCore.alreadyGuessed(twoYes, dog, k = 2), clue("two Yes: budget spent"))
    // a non-Yes stops re-posing immediately (quorum unreachable → fail-closed).
    assert(GameCore.alreadyGuessed(Vector(guess(2, dog, OracleAnswer.Unknown)), dog, k = 2))
    assert(GameCore.alreadyGuessed(Vector(guess(2, dog, OracleAnswer.No)), dog, k = 2))
    // per-candidate: a guess on cat does not spend dog's budget.
    assert(!GameCore.alreadyGuessed(Vector(guess(2, cat, OracleAnswer.Yes)), dog, k = 1))
  }
