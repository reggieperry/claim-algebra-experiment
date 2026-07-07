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
    hypotheses: List[(Answer, Int)]
):

  /** Render the state as a plain, readable brief for the model. */
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
    s"$qa\n\n$hs"

object GameView:

  /** Project the ordered log into the agent's read: match each asked question with its oracle
    * answer, and tally each hypothesis's DISTINCT backers (assert + corroborate, deduplicated by
    * agent — the same distinct-agent count the no-lone-sign floor uses, read from the events, never
    * from provenance, actor-abstraction §9).
    */
  def from(log: Vector[Event]): GameView =
    val answers: Map[String, OracleAnswer] =
      log.collect { case Event.AnswerGiven(_, _, qid, ans) => qid.value -> ans }.toMap

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

    val hypotheses: List[(Answer, Int)] =
      backers.toList.map((c, agents) => c -> agents.size).sortBy((_, n) => -n)

    GameView(transcript, hypotheses)

  private def add(
      acc: Map[Answer, Set[AgentId]],
      candidate: Answer,
      agent: AgentId
  ): Map[Answer, Set[AgentId]] =
    acc.updated(candidate, acc.getOrElse(candidate, Set.empty) + agent)
