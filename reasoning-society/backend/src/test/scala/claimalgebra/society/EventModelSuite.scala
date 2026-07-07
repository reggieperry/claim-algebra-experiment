package claimalgebra.society

/** The event model's negative space (scala-types.md): the id smart constructors reject the illegal
  * input, and the uniform `agentId` read matches the per-variant fields. The types carry the wire
  * shape; these pin what the constructors do with a bad value.
  */
class EventModelSuite extends munit.FunSuite with SocietyFixtures:

  test("AgentId.from rejects a blank or whitespace-only label") {
    assert(AgentId.from("").isLeft)
    assert(AgentId.from("   ").isLeft)
    assertEquals(AgentId.from("  a1  ").map(_.value), Right("a1")) // trimmed
  }

  test("Answer.from rejects a blank label and trims") {
    assert(Answer.from("").isLeft)
    assert(Answer.from("\t\n").isLeft)
    assertEquals(Answer.from(" dog ").map(_.value), Right("dog"))
  }

  test("QuestionId.from rejects a blank label") {
    assert(QuestionId.from("").isLeft)
    assertEquals(QuestionId.from("q1").map(_.value), Right("q1"))
  }

  test("agentId is Some on the six agent-bearing variants") {
    val a = mkAgent("a1")
    val c = mkAnswer("dog")
    val q = mkQuestion("q1")
    assertEquals(Event.Assert(1, 0L, a, c, "x").agentId, Some(a))
    assertEquals(Event.Corroborate(1, 0L, a, c, "x").agentId, Some(a))
    assertEquals(Event.Refute(1, 0L, a, c, "x").agentId, Some(a))
    assertEquals(Event.Strike(1, 0L, a, c, "x").agentId, Some(a))
    assertEquals(Event.QuestionProposed(1, 0L, a, q, "x").agentId, Some(a))
    assertEquals(Event.QuestionAsked(1, 0L, a, q, "x").agentId, Some(a))
  }

  test("agentId is None on the oracle and gate events") {
    val q = mkQuestion("q1")
    val c = mkAnswer("dog")
    assertEquals(Event.AnswerGiven(1, 0L, q, OracleAnswer.Yes).agentId, None)
    assertEquals(Event.GateAbstain(1, 0L, "watching").agentId, None)
    assertEquals(Event.GateSign(1, 0L, c).agentId, None)
  }

  test("EventMeta exposes seq and timestamp uniformly over the abstract Event type") {
    val events: List[Event] = List(
      mkAssert(3, mkAgent("a1"), mkAnswer("dog")),
      answerGiven(7, mkQuestion("q1"), OracleAnswer.Unknown)
    )
    assertEquals(events.map(_.seq), List(3, 7))
    assertEquals(events.map(_.timestamp), List(3L, 7L))
  }

  test("OracleAnswer is exactly the three oracle replies") {
    assertEquals(
      OracleAnswer.values.toList,
      List(OracleAnswer.Yes, OracleAnswer.No, OracleAnswer.Unknown)
    )
  }
