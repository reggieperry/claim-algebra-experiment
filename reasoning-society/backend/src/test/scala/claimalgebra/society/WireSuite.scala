package claimalgebra.society

/** The wire contract (scala-testing: pin the exact serialization). One golden per event variant —
  * the precise JSON string the frontend `ReasoningEvent` decoder must accept — plus the
  * oracle-token mapping and the fail-closed decode of an untrusted `POST /answer` body. If a field
  * name, order, or discriminator drifts here, a golden breaks and the frontend contract is
  * renegotiated deliberately.
  */
class WireSuite extends munit.FunSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val a3 = mkAgent("a3")
  private val dog = mkAnswer("dog")
  private val q1 = mkQuestion("q1")
  private val alive = mkTerm("alive")

  private def json(event: Event): String = Wire.encode(event).noSpaces

  test("assert encodes to its exact wire JSON") {
    assertEquals(
      json(Event.Assert(1, 2L, a1, dog, "hi")),
      """{"seq":1,"timestamp":2,"type":"assert","agentId":"a1","candidateId":"dog","content":"hi"}"""
    )
  }

  test("corroborate encodes to its exact wire JSON") {
    assertEquals(
      json(Event.Corroborate(3, 4L, a2, dog, "seconded")),
      """{"seq":3,"timestamp":4,"type":"corroborate","agentId":"a2","candidateId":"dog","note":"seconded"}"""
    )
  }

  test("refute encodes to its exact wire JSON") {
    assertEquals(
      json(Event.Refute(5, 6L, a3, dog, "no")),
      """{"seq":5,"timestamp":6,"type":"refute","agentId":"a3","candidateId":"dog","note":"no"}"""
    )
  }

  test("strike encodes to its exact wire JSON") {
    assertEquals(
      json(Event.Strike(7, 8L, a1, dog, "struck")),
      """{"seq":7,"timestamp":8,"type":"strike","agentId":"a1","candidateId":"dog","note":"struck"}"""
    )
  }

  test("question_proposed encodes to its exact wire JSON") {
    assertEquals(
      json(Event.QuestionProposed(9, 10L, a2, q1, "Is it an animal?")),
      """{"seq":9,"timestamp":10,"type":"question_proposed","agentId":"a2","questionId":"q1","content":"Is it an animal?"}"""
    )
  }

  test("question_asked encodes to its exact wire JSON") {
    assertEquals(
      json(Event.QuestionAsked(11, 12L, a2, q1, "Is it an animal?")),
      """{"seq":11,"timestamp":12,"type":"question_asked","agentId":"a2","questionId":"q1","content":"Is it an animal?"}"""
    )
  }

  test("answer_given encodes to its exact wire JSON (lowercase oracle token, no agentId)") {
    // The empty-governing case: a non-clarified answer OMITS the governing field, so its wire shape
    // is byte-identical to the pre-clarification contract (backward-compatible, additive).
    assertEquals(
      json(Event.AnswerGiven(13, 14L, q1, OracleAnswer.Yes)),
      """{"seq":13,"timestamp":14,"type":"answer_given","questionId":"q1","answer":"yes"}"""
    )
  }

  test("clarification_requested encodes to its exact wire JSON (no agentId — the human's move)") {
    assertEquals(
      json(Event.ClarificationRequested(19, 20L, q1, alive)),
      """{"seq":19,"timestamp":20,"type":"clarification_requested","questionId":"q1","term":"alive"}"""
    )
  }

  test("definition_given encodes to its exact wire JSON (the asking agent + term + meaning)") {
    assertEquals(
      json(Event.DefinitionGiven(21, 22L, a2, q1, alive, "a living creature currently alive")),
      """{"seq":21,"timestamp":22,"type":"definition_given","agentId":"a2","questionId":"q1","term":"alive","meaning":"a living creature currently alive"}"""
    )
  }

  test(
    "definition_remembered encodes to its exact wire JSON (nested origin with a stamped gameId)"
  ) {
    val origin = DefinitionProvenance(a2, q1, 21, Some(GameId.first))
    assertEquals(
      json(Event.DefinitionRemembered(1, 2L, alive, "a living creature currently alive", origin)),
      """{"seq":1,"timestamp":2,"type":"definition_remembered","term":"alive","meaning":"a living creature currently alive","origin":{"gameId":1,"agentId":"a2","questionId":"q1","seq":21}}"""
    )
  }

  test("definition_remembered OMITS origin.gameId when it is not yet stamped (None)") {
    val origin = DefinitionProvenance(a2, q1, 21, None)
    assertEquals(
      json(Event.DefinitionRemembered(1, 2L, alive, "a living creature currently alive", origin)),
      """{"seq":1,"timestamp":2,"type":"definition_remembered","term":"alive","meaning":"a living creature currently alive","origin":{"agentId":"a2","questionId":"q1","seq":21}}"""
    )
  }

  test("answer_given with a governing definition encodes the governing array (a clarified answer)") {
    assertEquals(
      json(Event.AnswerGiven(23, 24L, q1, OracleAnswer.No, List(alive))),
      """{"seq":23,"timestamp":24,"type":"answer_given","questionId":"q1","answer":"no","governing":["alive"]}"""
    )
  }

  test("gate_abstain encodes to its exact wire JSON") {
    assertEquals(
      json(Event.GateAbstain(15, 16L, "watching")),
      """{"seq":15,"timestamp":16,"type":"gate_abstain","reason":"watching"}"""
    )
  }

  test("gate_sign encodes to its exact wire JSON") {
    assertEquals(
      json(Event.GateSign(17, 18L, dog)),
      """{"seq":17,"timestamp":18,"type":"gate_sign","candidateId":"dog"}"""
    )
  }

  test("retired encodes to its exact wire JSON (the lifecycle trace marker)") {
    assertEquals(
      json(Event.Retired(25, 26L, dog)),
      """{"seq":25,"timestamp":26,"type":"retired","candidateId":"dog"}"""
    )
  }

  test("resurrected encodes to its exact wire JSON (the recovery trace marker)") {
    assertEquals(
      json(Event.Resurrected(27, 28L, dog)),
      """{"seq":27,"timestamp":28,"type":"resurrected","candidateId":"dog"}"""
    )
  }

  test(
    "convergence_warning encodes to its exact wire JSON (structural counts, NO candidate/reason)"
  ) {
    // The non-convergence flag carries the structural evidence ONLY — no candidateId, no reason
    // string — so the wire shape cannot leak a semantic diagnosis.
    assertEquals(
      json(Event.ConvergenceWarning(29, 30L, 5, 4)),
      """{"seq":29,"timestamp":30,"type":"convergence_warning","roundsWithoutConsolidation":5,"glutPersistence":4}"""
    )
  }

  test("guess_answered encodes to its exact wire JSON (candidate + lowercase oracle reply token)") {
    assertEquals(
      json(Event.GuessAnswered(31, 32L, dog, OracleAnswer.No)),
      """{"seq":31,"timestamp":32,"type":"guess_answered","candidateId":"dog","answer":"no"}"""
    )
    assertEquals(
      json(Event.GuessAnswered(33, 34L, dog, OracleAnswer.Yes)),
      """{"seq":33,"timestamp":34,"type":"guess_answered","candidateId":"dog","answer":"yes"}"""
    )
  }

  test("the oracle token is the lowercase yes/no/unknown of the TS Answer set") {
    assertEquals(Wire.answerToken(OracleAnswer.Yes), "yes")
    assertEquals(Wire.answerToken(OracleAnswer.No), "no")
    assertEquals(Wire.answerToken(OracleAnswer.Unknown), "unknown")
  }

  test("answerFromToken parses the closed set (case-insensitive, trimmed) and fails closed else") {
    assertEquals(Wire.answerFromToken("yes"), Right(OracleAnswer.Yes))
    assertEquals(Wire.answerFromToken("  NO "), Right(OracleAnswer.No))
    assertEquals(Wire.answerFromToken("Unknown"), Right(OracleAnswer.Unknown))
    assert(Wire.answerFromToken("maybe").isLeft)
    assert(Wire.answerFromToken("").isLeft)
  }

  test("AnswerCommand decodes a valid body and rejects a bad token or a missing field") {
    import io.circe.Json
    def obj(fields: (String, String)*): Json =
      Json.obj(fields.map((k, v) => k -> Json.fromString(v))*)
    val decoded = AnswerCommand.decoder
      .decodeJson(obj("questionId" -> "q1", "answer" -> "yes"))
      .map(c => (c.questionId.value, c.answer))
    assertEquals(decoded, Right(("q1", OracleAnswer.Yes)))
    assert(
      AnswerCommand.decoder.decodeJson(obj("questionId" -> "q1", "answer" -> "maybe")).isLeft,
      "an answer outside the closed set is rejected"
    )
    assert(
      AnswerCommand.decoder.decodeJson(obj("answer" -> "yes")).isLeft,
      "a missing questionId is rejected"
    )
  }
