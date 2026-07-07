package claimalgebra.society

import claimalgebra.calculus.Status
import org.scalacheck.Prop.forAll

/** The fold's determinism and the round decision. The sharpest property is that an INCOMPLETE round
  * never signs — round attrition must never drive the decision to a signature (actor-abstraction
  * §9).
  */
class FoldRoundSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  test("adding a rival candidate raises the cardinality") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, cat))
    assertEquals(GameCore.slot(log, 1).cardinality, 1)
    assertEquals(GameCore.slot(log, 2).cardinality, 2)
  }

  test("a refute of a live candidate flips the read to Conflict") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, dog), refute(3, a1, dog))
    assertEquals(GameCore.belief(log, 2).status, Status.Resolved)
    assertEquals(GameCore.belief(log, 3).status, Status.Conflict)
  }

  property("belief is prefix-stable: events after the cut do not change the read at the cut") {
    forAll(genLog, genLog) { (head: Vector[Event], tail: Vector[Event]) =>
      GameCore.belief(head ++ tail, head.size) == GameCore.belief(head, head.size)
    }
  }

  property("belief is deterministic — the same prefix always reads the same") {
    forAll(genLog) { (log: Vector[Event]) =>
      (0 to log.size).forall(k => GameCore.belief(log, k) == GameCore.belief(log, k))
    }
  }

  property("an INCOMPLETE round never signs, whatever the log or prefix") {
    forAll(genLog) { (log: Vector[Event]) =>
      (0 to log.size).forall { upTo =>
        GameCore.nextMove(log, upTo, roundComplete = false) match
          case Move.Sign(_) => false
          case Move.Abstain => true
      }
    }
  }

  test("an incomplete round abstains EVEN when the gate would sign (no attrition-signature)") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, dog))
    assertEquals(GameCore.decide(log, 2), GateDecision.Sign(dog), clue("the gate WOULD sign"))
    assertEquals(GameCore.nextMove(log, 2, roundComplete = false), Move.Abstain)
    assertEquals(GameCore.nextMove(log, 2, roundComplete = true), Move.Sign(dog))
  }

  property("a completed round signs iff the gate decides to sign") {
    forAll(genLog) { (log: Vector[Event]) =>
      val gateSigns = GameCore.decide(log, log.size) match
        case GateDecision.Sign(_) => true
        case GateDecision.Abstain(_) => false
      val roundSigns = GameCore.nextMove(log, log.size, roundComplete = true) match
        case Move.Sign(_) => true
        case Move.Abstain => false
      gateSigns == roundSigns
    }
  }
