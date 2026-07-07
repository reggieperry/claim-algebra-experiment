package claimalgebra.society

import claimalgebra.BlockReason

/** The Oracle→Refute barrier, red-first (forward-carry safety item 1). The sharp CWS hole: a fresh
  * human answer is BELIEF-INERT (it projects to nothing), so a candidate the answer contradicts
  * still reads as cleanly signable — the gate WOULD sign it. The structural guard is that the
  * answer opens a NEW round, and [[GameCore.nextMove]] abstains on an INCOMPLETE round, so the
  * candidate cannot sign until the agents have reacted (and emitted any `Refute`, which gluts →
  * Conflict → blocked).
  *
  * This pins the pure logic the LogActor's round-barrier wiring realizes ([[LogActor.onAnswered]]
  * opens the new round; [[SocietyGameSuite]] proves the effectful wiring end to end).
  */
class OracleRefuteBarrierSuite extends munit.FunSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val dog = mkAnswer("dog")
  private val q1 = mkQuestion("q1")

  test("a fresh human 'no' cannot sign a 2-backer candidate before the agents react") {
    // A live, 2-distinct-backer, clean-True candidate — the gate WOULD sign it.
    val backed = Vector(mkAssert(1, a1, dog), corroborate(2, a2, dog))
    assertEquals(
      GameCore.decide(backed, backed.size),
      GateDecision.Sign(dog),
      clue("two distinct backers, clean True, cardinality 1 → the gate signs")
    )

    // The human answers 'no' — which OPENS a new round. The raw answer is belief-inert, so `decide`
    // (which knows nothing of rounds) STILL wants to sign: this is exactly the CWS hole.
    val afterAnswer =
      backed ++ Vector(questionAsked(3, a2, q1), answerGiven(4, q1, OracleAnswer.No))
    assertEquals(
      GameCore.decide(afterAnswer, afterAnswer.size),
      GateDecision.Sign(dog),
      clue("the raw answer moved no belief — the bare gate would still sign")
    )

    // The structural guard: the round the answer opened is INCOMPLETE (the agents have not reported
    // since the answer), so nextMove ABSTAINS — it never signs on a partial round.
    assertEquals(
      GameCore.nextMove(afterAnswer, afterAnswer.size, roundComplete = false),
      Move.Abstain,
      clue("incomplete round after the answer → Abstain, never Sign")
    )

    // Once the agents REACT — refuting the contradicted candidate — dog is BOTH-asserters-withdrawn:
    // a1 and a2 each asserted then refuted it (self-withdrawal), giving two standing refuters and no
    // pro-author standing behind. That is a DEFEATED claim, not a live glut, so the hypothesis-
    // lifecycle predicate RETIRES it (masks both channels) rather than jamming the gate on a false
    // glut. The gate then abstains for the CORRECT reason — no live hypothesis (Gap) — still never a
    // signature. (Pre-lifecycle this read Conflict; the safety, "never sign the contradicted
    // candidate," is preserved either way.)
    val afterRefute = afterAnswer ++ Vector(refute(5, a1, dog), refute(6, a2, dog))
    assertEquals(
      GameCore.decide(afterRefute, afterRefute.size),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Gap)),
      clue("both asserters self-withdrew → dog retired (defeated) → Gap, never a sign")
    )
    assertEquals(
      GameCore.nextMove(afterRefute, afterRefute.size, roundComplete = true),
      Move.Abstain
    )
  }

  test("an answer the agents cannot map abstains — it never no-ops into a sign") {
    // The human says 'unknown'; no agent refutes; the candidate is only lone-backed. The gate must not
    // sign (the no-lone-sign floor holds), and the incomplete round holds regardless.
    val log = Vector(
      mkAssert(1, a1, dog),
      questionAsked(2, a2, q1),
      answerGiven(3, q1, OracleAnswer.Unknown)
    )
    assertEquals(
      GameCore.decide(log, log.size),
      GateDecision.Abstain(AbstainReason.Unconfirmed(1)),
      clue("a lone backer never signs")
    )
    assertEquals(GameCore.nextMove(log, log.size, roundComplete = false), Move.Abstain)
  }
