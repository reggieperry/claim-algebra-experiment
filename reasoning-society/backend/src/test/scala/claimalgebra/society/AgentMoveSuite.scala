package claimalgebra.society

/** The boundary decode `AgentMove.parse` — the total, fail-closed re-validation of the model's
  * structured output (scala-llm.md). A malformed move must never inject a guessed belief: an
  * unknown action, or an assert/corroborate/refute with a blank candidate, decodes to `None` (the
  * caller treats it as an abstention — no post). An explicit `pass` is a genuine reported non-move.
  */
class AgentMoveSuite extends munit.FunSuite:

  import StubLlm.move

  private def parsed(dto: AgentMoveDto): Option[AgentMove] = AgentMove.parse(dto)

  test("a well-formed assert decodes to Assert with the candidate and content") {
    assertEquals(
      parsed(move("assert", "dog", "woof")).collect { case AgentMove.Assert(c, t) => (c.value, t) },
      Some(("dog", "woof"))
    )
  }

  test("a well-formed refute decodes to Refute with the candidate") {
    assertEquals(
      parsed(move("refute", "cat", "not a cat")).collect { case AgentMove.Refute(c, _) => c.value },
      Some("cat")
    )
  }

  test("a corroborate decodes to Corroborate") {
    assertEquals(
      parsed(move("corroborate", "dog", "seconded")).collect { case AgentMove.Corroborate(c, _) =>
        c.value
      },
      Some("dog")
    )
  }

  test("a propose decodes to Propose with the question text") {
    assertEquals(
      parsed(move("propose", "", "Is it an animal?")).collect { case AgentMove.Propose(q) => q },
      Some("Is it an animal?")
    )
  }

  test("an explicit pass decodes to Pass — a reported non-move, never None") {
    assertEquals(parsed(move("pass")), Some(AgentMove.Pass))
  }

  test("assert with a blank candidate is malformed → None (fail-closed, no guessed hypothesis)") {
    assertEquals(parsed(move("assert", "   ", "x")), None)
  }

  test("refute with a blank candidate is malformed → None") {
    assertEquals(parsed(move("refute", "", "x")), None)
  }

  test("an unknown action is malformed → None") {
    assertEquals(parsed(move("guess", "dog", "x")), None)
  }

  test("propose with blank text is malformed → None") {
    assertEquals(parsed(move("propose", "", "   ")), None)
  }

  // A3 (recovery-and-endgame): a structural, fail-closed either/or guard. A question that joins a
  // second interrogative clause with "or" ("… or is it …") is dropped, because a plain "yes" to it is
  // uninterpretable — the apple-log failure. Dropping is fail-closed (no post, never a wrong sign).
  test("propose of an EITHER/OR question is dropped → None (the apple-log ambiguity)") {
    assertEquals(
      parsed(
        move("propose", "", "Is it a single piece of food, or is it a mixture of components?")
      ),
      None
    )
  }

  test("propose of an 'or are they' either/or is dropped → None") {
    assertEquals(parsed(move("propose", "", "Is it one object, or are they several?")), None)
  }

  // The guard is deliberately CONSERVATIVE: a SET-MEMBERSHIP question ("… glass, ceramic, or stone?",
  // where "yes" means one of them) is NOT an either/or and must survive — over-rejecting it would cost
  // recall on the useful material scan.
  test("propose of a SET-MEMBERSHIP question survives — 'yes' means one of the set") {
    assertEquals(
      parsed(move("propose", "", "Is it made of glass, ceramic, or stone?")).collect {
        case AgentMove.Propose(q) => q
      },
      Some("Is it made of glass, ceramic, or stone?")
    )
  }

  test("propose with 'or' before a non-verb (a colour set) survives — not an either/or") {
    assert(parsed(move("propose", "", "Is it gold or silver-coloured?")).isDefined)
  }

  test("the action is case- and whitespace-tolerant") {
    assertEquals(
      parsed(move("  ASSERT ", "dog", "x")).collect { case AgentMove.Assert(c, _) => c.value },
      Some("dog")
    )
  }

  test("Pass projects to no event; a belief-moving move projects to one") {
    val agent = AgentId.from("a1").fold(e => fail(e), identity)
    assertEquals(AgentMove.event(agent, AgentMove.Pass, 1), None)
    assertEquals(
      AgentMove
        .event(agent, AgentMove.Assert(Answer.from("dog").fold(e => fail(e), identity), "woof"), 3)
        .map(mk => mk(0L)),
      Some(Event.Assert(3, 0L, agent, Answer.from("dog").fold(e => fail(e), identity), "woof"))
    )
  }
