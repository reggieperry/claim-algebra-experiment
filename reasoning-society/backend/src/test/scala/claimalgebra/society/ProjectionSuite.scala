package claimalgebra.society

import claimalgebra.*
import claimalgebra.calculus.{Evidence, Ledger, Status}
import org.scalacheck.Prop.forAll

/** The Event→Evidence projection (actor-abstraction §7's corrected table) and the four `Evidence`
  * cases' resolution. The centerpiece is the contradiction→glut path — verified structurally
  * against `Testimony.corner`, not just via the status.
  */
class ProjectionSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val dog = mkAnswer("dog")

  test("an assert projects to pro-only Asserted — a Resolved-eligible True") {
    val t = GameCore.slot(Vector(mkAssert(1, a1, dog)), 1)
    assertEquals(Testimony.corner(t), Belnap.True, clue("pro-only reads True"))
    assertEquals(t.value, Some(dog))
  }

  test("a corroborate is pro too — a second backer on the same value") {
    val log = Vector(mkAssert(1, a1, dog), corroborate(2, a2, dog))
    val t = GameCore.slot(log, 2)
    assertEquals(Testimony.corner(t), Belnap.True)
    assertEquals(t.cardinality, 1)
  }

  test("a refute of a LIVE candidate builds a genuine Glut (the §7 contradiction path)") {
    val log = Vector(mkAssert(1, a1, dog), refute(2, a2, dog))
    val t = GameCore.slot(log, 2)
    // Verified against Testimony.scala: corroborate merges pro (assert) and con (refute) pointwise
    // on `dog`, so both channels fire and the structural corner is Glut.
    assertEquals(Testimony.corner(t), Belnap.Glut, clue("pro>0 ∧ con>0 → Glut"))
    assertEquals(GameCore.belief(log, 2).status, Status.Conflict)
  }

  test("a strike retracts the operative whole — reads Superseded") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, dog), strike(3, a1, dog))
    assertEquals(GameCore.belief(log, 3).status, Status.Superseded)
    assertEquals(Testimony.corner(GameCore.slot(log, 3)), Belnap.False)
  }

  test("the non-claim events (question / answer / gate / convergence flag) project to nothing") {
    val inert = List(
      Event.QuestionProposed(1, 1L, a1, mkQuestion("q1"), "?"),
      questionAsked(2, a1, mkQuestion("q1")),
      answerGiven(3, mkQuestion("q1"), OracleAnswer.Yes),
      Event.GateAbstain(4, 4L, "watching"),
      Event.GateSign(5, 5L, dog),
      // The convergence flag is belief-inert — it MUST project to nothing (else the monitor could
      // move its own input and the flag could change the gate).
      Event.ConvergenceWarning(6, 6L, 5, 4)
    )
    assertEquals(GameCore.project(inert), Nil)
  }

  test("only the four hypothesis-moving events project") {
    val mixed = Vector(
      mkAssert(1, a1, dog),
      corroborate(2, a2, dog),
      refute(3, a1, mkAnswer("cat")),
      strike(4, a2, dog),
      questionAsked(5, a1, mkQuestion("q1")),
      answerGiven(6, mkQuestion("q1"), OracleAnswer.No)
    )
    assertEquals(GameCore.project(mixed).size, 4)
  }

  // The four `Evidence` cases each resolve to the right Status. Asserted and Withdrawn are reachable
  // from the wire (assert, strike); Superseded and WithdrawnToken are calculus capabilities with no
  // wire event yet, so they are pinned at the Evidence level directly.

  test("Asserted resolves to Resolved") {
    val ev = List(Evidence.Asserted(Testimony.leaf(dog, Prov.single(mkLineage("l1")))))
    assertEquals(Ledger.resolve(ev).status, Status.Resolved)
  }

  test("Withdrawn (after an assertion) resolves to Superseded — struck") {
    val ev = List(
      Evidence.Asserted(Testimony.leaf(dog, Prov.single(mkLineage("l1")))),
      Evidence.Withdrawn[Answer]()
    )
    assertEquals(Ledger.resolve(ev).status, Status.Superseded)
  }

  test("Superseded (over a live operative) resolves to Superseded — a governing amendment") {
    val cat = mkAnswer("cat")
    val ev = List(
      Evidence.Asserted(Testimony.leaf(dog, Prov.single(mkLineage("l1")))),
      Evidence.Superseded(Testimony.leaf(cat, Prov.single(mkLineage("l2"))))
    )
    val res = Ledger.resolve(ev)
    assertEquals(res.status, Status.Superseded)
    assertEquals(res.value, Some(cat), clue("the amendment governs"))
  }

  test("WithdrawnToken (of the sole backing token) resolves to Missing — no con-residue") {
    val tok = mkLineage("l1")
    val ev = List(
      Evidence.Asserted(Testimony.leaf(dog, Prov.single(tok))),
      Evidence.WithdrawnToken[Answer](tok)
    )
    // Distinct from Withdrawn: the token leaves NO con-mass, so the slot is a clean gap, not struck.
    assertEquals(Ledger.resolve(ev).status, Status.Missing)
  }

  property("every projected evidence originates in a belief-moving event (nothing invented)") {
    forAll(genLog) { (log: Vector[Event]) =>
      val moving = log.count {
        case _: Event.Assert | _: Event.Corroborate | _: Event.Refute | _: Event.Strike => true
        case _ => false
      }
      GameCore.project(log).sizeIs == moving
    }
  }
