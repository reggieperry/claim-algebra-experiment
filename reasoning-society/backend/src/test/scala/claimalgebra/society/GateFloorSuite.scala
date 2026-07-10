package claimalgebra.society

import claimalgebra.{Belnap, BlockReason, Testimony}
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

/** The gate reduced to `corner = True ∧ cardinality = 1` (θ/verify wired off) and the no-lone-sign
  * FLOOR — the critical safety of this slice. The floor counts DISTINCT agents from the events (not
  * the algebra's `Prov` lineage), so a lone-agent winner can never be signed.
  */
class GateFloorSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  // --- the gate, reduced to corner ∧ cardinality on this path ---

  test("an empty prefix abstains with Gap") {
    assertEquals(
      GameCore.decide(Vector.empty, 0),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Gap))
    )
  }

  test(
    "rival candidates abstain with Ambiguous (the cardinality conjunct fires before the floor)"
  ) {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, cat))
    assertEquals(
      GameCore.decide(log, 2),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Ambiguous))
    )
  }

  test("a glut abstains with Conflict, even with two backers (the corner conjunct fires first)") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, dog), refute(3, a1, dog))
    assertEquals(
      GameCore.decide(log, 3),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Conflict))
    )
  }

  // --- the no-lone-sign floor ---

  test("≥ 2 distinct agents on an uncontested True winner sign") {
    val log = Vector(mkAssert(1, a1, dog), mkAssert(2, a2, dog))
    assertEquals(GameCore.decide(log, 2), GateDecision.Sign(dog))
  }

  test("a lone agent holds as Unconfirmed, never signs — however many times it repeats") {
    val log = Vector(mkAssert(1, a1, dog), corroborate(2, a1, dog))
    assertEquals(GameCore.decide(log, 2), GateDecision.Abstain(AbstainReason.Unconfirmed(1)))
  }

  property("no-lone-sign: a signature ALWAYS has ≥ MinCorroboration distinct backers") {
    forAll(genLog) { (log: Vector[Event]) =>
      GameCore.decide(log, log.size) match
        case GateDecision.Sign(v) =>
          GameCore.distinctBackers(log, v) >= GameCore.MinCorroboration
        case GateDecision.Abstain(_) => true
    }
  }

  property("a single-agent field never signs, whatever it asserts and however often") {
    val genSingleAgentLog =
      for
        n <- Gen.choose(1, 8)
        cand <- genCandidate
        agent <- genAgent
      yield (1 to n).map(seq => mkAssert(seq, agent, cand)).toVector
    forAll(genSingleAgentLog) { (log: Vector[Event]) =>
      GameCore.decide(log, log.size) == GateDecision.Abstain(AbstainReason.Unconfirmed(1))
    }
  }

  property("a signature is only ever the slot's clean, single-True value (never a wrong value)") {
    forAll(genLog) { (log: Vector[Event]) =>
      val t = GameCore.slot(log, log.size)
      GameCore.decide(log, log.size) match
        case GateDecision.Sign(v) =>
          (Testimony.corner(t) == Belnap.True) && (t.cardinality == 1) && (t.value == Some(v))
        case GateDecision.Abstain(_) => true
    }
  }
