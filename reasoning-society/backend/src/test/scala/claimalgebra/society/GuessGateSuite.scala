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
