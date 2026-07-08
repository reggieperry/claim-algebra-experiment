package claimalgebra.society

/** A2 (recovery-and-endgame): at budget exhaustion the abstain surfaces the backer-sorted leading
  * candidate as a clearly-labelled TENTATIVE read — display only, never a signature. The game still
  * ends `Outcome.Inconclusive` (a `GateAbstain`, not a `GateSign`); this suite pins the reason
  * prose, the fail-closed labelling, and the plain-abstain-when-empty case.
  */
class EndgameSuite extends munit.FunSuite with SocietyFixtures:

  private val driller = mkAgent("driller")
  private val splitter = mkAgent("splitter")
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  private val base = "inconclusive — no signature within the round budget"

  test("surfaces the backer-sorted leading candidate, labelled unconfirmed and not signed") {
    // dog: driller asserts + splitter corroborates (2 distinct backers); cat: splitter asserts (1).
    val log = Vector(
      mkAssert(1, driller, dog),
      corroborate(2, splitter, dog),
      mkAssert(3, splitter, cat)
    )
    val reason = LogActor.inconclusiveReason(log)
    assert(reason.startsWith(base), clue(reason))
    assert(reason.contains("leading guess: \"dog\" (backed by 2)"), clue(reason))
    // It must NEVER read as a signature — display-only, fail-closed.
    assert(reason.contains("unconfirmed"), clue(reason))
    assert(reason.contains("not signed"), clue(reason))
  }

  test("abstains plainly when no hypothesis is on the table — no tentative guess") {
    assertEquals(LogActor.inconclusiveReason(Vector.empty[Event]), base)
  }
