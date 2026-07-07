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
    assertEquals(
      json(Event.AnswerGiven(13, 14L, q1, OracleAnswer.Yes)),
      """{"seq":13,"timestamp":14,"type":"answer_given","questionId":"q1","answer":"yes"}"""
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
