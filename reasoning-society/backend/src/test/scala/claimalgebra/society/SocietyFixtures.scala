package claimalgebra.society

import claimalgebra.Lineage
import org.scalacheck.{Arbitrary, Gen}

/** Shared builders and generators for the pure game-core suites. The self-type gives access to
  * `fail`, so the id smart constructors are exercised through their real validating path (a blank —
  * which never occurs from these fixed pools — would `fail` the suite rather than admit a bad id).
  * Generators are constrained at generation time and draw ids from small pools so the corners (a
  * lone backer, rival candidates, a glut) actually appear.
  */
trait SocietyFixtures:
  self: munit.FunSuite =>

  // Small fixed pools — enough agents for the ≥2 floor, enough candidates for rivals.
  val agentPool: List[AgentId] = List("a1", "a2", "a3").map(mkAgent)
  val candidatePool: List[Answer] = List("dog", "cat", "bird").map(mkAnswer)
  val questionPool: List[QuestionId] = List("q1", "q2").map(mkQuestion)

  // A small term pool for the clarification events — a couple of challengeable terms.
  val termPool: List[Term] = List("alive", "animal").map(mkTerm)

  // Terms whose VALUE collides with a candidate label (e.g. "dog") — a definition's term/meaning is
  // now critical (a grounded answer's `governing` cites terms), so the belief-inertness
  // generator must exercise a definition that LOOKS like a candidate value, to confirm it still
  // never becomes a phantom backer or corroboration of that candidate.
  val collidingTermPool: List[Term] = candidatePool.map(c => mkTerm(c.value))

  def mkAgent(raw: String): AgentId = AgentId.from(raw).fold(fail(_), identity)
  def mkAnswer(raw: String): Answer = Answer.from(raw).fold(fail(_), identity)
  def mkQuestion(raw: String): QuestionId = QuestionId.from(raw).fold(fail(_), identity)
  def mkTerm(raw: String): Term = Term.from(raw).fold(fail(_), identity)
  // The nth game of a session (n ≥ 1) — `first` then `n-1` `next` bumps.
  def mkGame(n: Int): GameId = (1 until math.max(n, 1)).foldLeft(GameId.first)((g, _) => g.next)
  def mkLineage(raw: String): Lineage =
    Lineage.from(raw).fold(fail(s"blank lineage: $raw"))(identity)

  // Event builders — timestamp defaults to the seq so a generated log is monotone and readable.
  def mkAssert(seq: Int, agent: AgentId, candidate: Answer, content: String = "?"): Event =
    Event.Assert(seq, seq.toLong, agent, candidate, content)
  def corroborate(seq: Int, agent: AgentId, candidate: Answer): Event =
    Event.Corroborate(seq, seq.toLong, agent, candidate, "seconded")
  def refute(seq: Int, agent: AgentId, candidate: Answer): Event =
    Event.Refute(seq, seq.toLong, agent, candidate, "no")
  def strike(seq: Int, agent: AgentId, candidate: Answer): Event =
    Event.Strike(seq, seq.toLong, agent, candidate, "struck")
  def questionAsked(seq: Int, agent: AgentId, question: QuestionId): Event =
    Event.QuestionAsked(seq, seq.toLong, agent, question, "is it an animal?")
  def answerGiven(
      seq: Int,
      question: QuestionId,
      oracle: OracleAnswer,
      governing: List[Term] = Nil
  ): Event =
    Event.AnswerGiven(seq, seq.toLong, question, oracle, governing)
  def clarificationRequested(seq: Int, question: QuestionId, term: Term): Event =
    Event.ClarificationRequested(seq, seq.toLong, question, term)
  def definitionGiven(
      seq: Int,
      agent: AgentId,
      question: QuestionId,
      term: Term,
      meaning: String = "the agreed meaning"
  ): Event =
    Event.DefinitionGiven(seq, seq.toLong, agent, question, term, meaning)
  def definitionRemembered(
      seq: Int,
      term: Term,
      origin: DefinitionProvenance,
      meaning: String = "the agreed meaning"
  ): Event =
    Event.DefinitionRemembered(seq, seq.toLong, term, meaning, origin)

  val genAgent: Gen[AgentId] = Gen.oneOf(agentPool)
  val genCandidate: Gen[Answer] = Gen.oneOf(candidatePool)
  val genQuestion: Gen[QuestionId] = Gen.oneOf(questionPool)
  val genTerm: Gen[Term] = Gen.oneOf(termPool)
  val genOracle: Gen[OracleAnswer] = Gen.oneOf(OracleAnswer.values.toList)

  /** A belief-inert vocabulary event at an arbitrary high `seq` — a challenge, a this-game
    * definition, OR a RECALLED definition (persistent memory seeded into a fresh game,
    * two-tier-reset-design). Its `seq` is irrelevant to belief (each projects to nothing), so it
    * never collides with a base log's contiguous `seq`s in a meaningful way. Meanings include the
    * empty string: an agent that cannot crisply define its term is itself diagnostic
    * (clarification-feature §2). The recalled variant draws an `origin` provenance with a `None` /
    * `Some(game)` gameId, so the belief-inertness property covers a recalled definition regardless
    * of its stamped origin.
    */
  val genInertClarification: Gen[Event] =
    for
      question <- genQuestion
      // Draw from the challengeable terms UNION the candidate-colliding terms, so a definition on a
      // term like "dog" (a candidate label) is exercised for belief-inertness.
      term <- Gen.oneOf(termPool ++ collidingTermPool)
      agent <- genAgent
      // Meanings include a candidate-value-shaped string ("dog") and the empty string: an agent that
      // cannot crisply define its term is itself diagnostic (clarification-feature §2).
      meaning <- Gen.oneOf("living creature currently alive", "any living tissue", "dog", "")
      seq <- Gen.choose(1000, 9999)
      originSeq <- Gen.choose(1, 60)
      gameId <- Gen.oneOf(None, Some(GameId.first), Some(GameId.first.next))
      variant <- Gen.oneOf("challenge", "given", "remembered")
    yield variant match
      case "challenge" => clarificationRequested(seq, question, term)
      case "given" => definitionGiven(seq, agent, question, term, meaning)
      case _ =>
        definitionRemembered(
          seq,
          term,
          DefinitionProvenance(agent, question, originSeq, gameId),
          meaning
        )

  /** One event at a given `seq`, weighted toward the belief-moving variants so most logs reach a
    * decision, with the belief-inert control events mixed in.
    */
  def genEvent(seq: Int): Gen[Event] =
    val genAssert = genAgent.flatMap(a => genCandidate.map(c => mkAssert(seq, a, c)))
    val genCorroborate = genAgent.flatMap(a => genCandidate.map(c => corroborate(seq, a, c)))
    val genRefute = genAgent.flatMap(a => genCandidate.map(c => refute(seq, a, c)))
    val genStrike = genAgent.flatMap(a => genCandidate.map(c => strike(seq, a, c)))
    val genAsked = genAgent.flatMap(a => genQuestion.map(q => questionAsked(seq, a, q)))
    val genAnswered = genQuestion.flatMap(q => genOracle.map(o => answerGiven(seq, q, o)))
    Gen.frequency(
      5 -> genAssert,
      3 -> genCorroborate,
      2 -> genRefute,
      1 -> genStrike,
      1 -> genAsked,
      1 -> genAnswered,
      1 -> Gen.const(Event.GateAbstain(seq, seq.toLong, "watching"))
    )

  /** A log with contiguous 1-based `seq`s (so a positional prefix `take(k)` is the events with
    * `seq ≤ k`, the frontend's playhead semantics).
    */
  val genLog: Gen[Vector[Event]] =
    Gen.choose(0, 14).flatMap { n =>
      (1 to n).foldLeft(Gen.const(Vector.empty[Event])) { (accGen, seq) =>
        for
          acc <- accGen
          event <- genEvent(seq)
        yield acc :+ event
      }
    }

  given Arbitrary[Vector[Event]] = Arbitrary(genLog)
