package claimalgebra.society

/** The read of the game an agent reasons over — the accumulated question/answer transcript and the
  * current live hypotheses with their distinct-backer counts. Built PURELY from the log
  * ([[GameView.from]]); rendered into the user message of the agent's bounded call (never the
  * system prompt — the fixed rubric is the strategy's system prompt,
  * scala-llm.md/scala-security.md). Our own type, so a stub agent in tests is driven
  * deterministically without parsing prose.
  */
final case class GameView(
    transcript: List[(String, OracleAnswer)],
    hypotheses: List[(Answer, Int)],
    definitions: List[Definition],
    roundsUsed: Int = 0,
    roundBudget: Int = 0
):

  /** Questions left before the give-up — the endgame clock. Zero budget means "not surfaced" (an
    * internal `from` call that only needs the hypotheses).
    */
  def roundsLeft: Int = if roundBudget <= 0 then 0 else math.max(0, roundBudget - roundsUsed)

  /** Render the state as a plain, readable brief for the model. The established definitions are
    * surfaced so an agent grounds every future use of a challenged term to the agreed meaning
    * (clarification-feature §3), rather than re-inventing it.
    */
  def render: String =
    val qa =
      if transcript.isEmpty then "No questions answered yet."
      else
        transcript
          .map((q, a) => s"- Q: $q -> ${a.toString.toUpperCase}")
          .mkString("Question/answer so far:\n", "\n", "")
    val hs =
      if hypotheses.isEmpty then "No hypotheses on the table yet."
      else
        hypotheses
          .map((h, n) => s"- \"${h.value}\" (backed by $n)")
          .mkString("Current hypotheses:\n", "\n", "")
    val ds =
      if definitions.isEmpty then "No definitions established yet."
      else
        definitions
          .map(d => s"- \"${d.term.value}\" means: ${d.meaning}")
          .mkString("Definitions established this game:\n", "\n", "")
    val clock =
      if roundBudget <= 0 then ""
      else
        val left = roundsLeft
        val urge =
          if left <= 3 then
            " FEW QUESTIONS REMAIN — if any hypothesis fits the answers so far, ASSERT your single" +
              " best guess NOW instead of asking another question; an unanswered guess scores nothing."
          else ""
        s"\n\nRound budget: $roundsUsed of $roundBudget questions used, $left left.$urge"
    s"$qa\n\n$hs\n\n$ds$clock"

object GameView:

  /** Project the ordered log into the agent's read: match each asked question with its oracle
    * answer, tally each hypothesis's DISTINCT backers (assert + corroborate, deduplicated by agent
    * — the same distinct-agent count the no-lone-sign floor uses, read from the events, never from
    * provenance, actor-abstraction §9), and carry the established definitions ([[Definitions]]) so
    * an agent grounds to the agreed vocabulary (clarification-feature §3).
    *
    * RETIRED candidates are dropped from the live targets (hypothesis-lifecycle §B): a hypothesis
    * the recomputed [[GameCore.retiredCandidates]] predicate has defeated is off the live board, so
    * agents stop attacking it — this collapses the wasted re-refutations of an already-abandoned
    * hypothesis. It is a DISPLAY/targets filter only: the retired candidate's events remain in the
    * log (citable trace), and belief/the gate are untouched (they mask through the same predicate).
    * A non-retired candidate — including a *contested* live glut, which must stay a target so the
    * disagreement is held, not silenced — is shown.
    */
  def from(log: Vector[Event], roundBudget: Int = 0, roundsUsed: Int = 0): GameView =
    val answers: Map[String, OracleAnswer] =
      log.collect { case Event.AnswerGiven(_, _, qid, ans, _) => qid.value -> ans }.toMap

    val transcript: List[(String, OracleAnswer)] =
      log.toList.collect {
        case Event.QuestionAsked(_, _, _, qid, content) if answers.contains(qid.value) =>
          content -> answers(qid.value)
      }

    val backers: Map[Answer, Set[AgentId]] =
      log.foldLeft(Map.empty[Answer, Set[AgentId]]) { (acc, event) =>
        event match
          case Event.Assert(_, _, agent, c, _) => add(acc, c, agent)
          case Event.Corroborate(_, _, agent, c, _) => add(acc, c, agent)
          case _ => acc
      }

    val masked: Set[Answer] = GameCore.maskedCandidates(log)
    val hypotheses: List[(Answer, Int)] =
      backers.toList
        .filterNot((c, _) => masked.contains(c))
        .map((c, agents) => c -> agents.size)
        .sortBy((_, n) => -n)

    GameView(transcript, hypotheses, Definitions.established(log), roundsUsed, roundBudget)

  private def add(
      acc: Map[Answer, Set[AgentId]],
      candidate: Answer,
      agent: AgentId
  ): Map[Answer, Set[AgentId]] =
    acc.updated(candidate, acc.getOrElse(candidate, Set.empty) + agent)
