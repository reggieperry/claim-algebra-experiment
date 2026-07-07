package claimalgebra.society

/** One agent's move for a round — the DOMAIN move, decoded and re-validated from the boundary
  * [[AgentMoveDto]]. A closed `enum`, so the LogActor's projection into an [[Event]] is
  * exhaustively checked. Two layers (brief §2): the four belief-moving moves ([[Assert]] /
  * [[Corroborate]] / [[Refute]] plus the value-less [[Pass]]) and the control move [[Propose]] (a
  * question, belief-inert).
  *
  * `Pass` is a genuine, reported non-move — an agent that reasoned and had nothing to add. It POSTS
  * (it counts toward the round barrier, so a round can complete and sign), but it moves no belief.
  * This is distinct from a FAILED call, which does not post at all (attrition — actor-abstraction
  * §9).
  */
enum AgentMove:
  case Assert(candidate: Answer, content: String)
  case Corroborate(candidate: Answer, note: String)
  case Refute(candidate: Answer, note: String)
  case Propose(question: String)
  case Pass

object AgentMove:

  /** Total, fail-closed decode of the boundary carrier into a domain move (scala-llm.md:
    * re-validate every parsed field before the algebra trusts it). `Some(move)` is a well-formed
    * move (including an explicit `Pass`); `None` is a MALFORMED output — an unknown action, or an
    * assert/corroborate/ refute with a blank candidate, or a propose with blank text. The caller
    * (the [[AgentActor]]) treats `None` as an abstention (no post), so a garbled output can neither
    * inject a guessed belief nor complete the round barrier.
    */
  def parse(dto: AgentMoveDto): Option[AgentMove] =
    Option(dto.action).map(_.trim.toLowerCase) match
      case Some("assert") => candidate(dto).map(c => AgentMove.Assert(c, text(dto)))
      case Some("corroborate") => candidate(dto).map(c => AgentMove.Corroborate(c, text(dto)))
      case Some("refute") => candidate(dto).map(c => AgentMove.Refute(c, text(dto)))
      case Some("propose") => question(dto).map(AgentMove.Propose(_))
      case Some("pass") => Some(AgentMove.Pass)
      case _ => None

  /** Project a move into the log event the LogActor appends, given the event's assigned `seq`.
    * `Pass` projects to nothing (`None`) — it is a reported non-move, not a logged event. The
    * returned builder is `seq`-closed and takes only the display timestamp (assigned at append
    * time).
    */
  def event(agent: AgentId, move: AgentMove, seq: Int): Option[Long => Event] =
    move match
      case AgentMove.Assert(c, content) =>
        Some(ts => Event.Assert(seq, ts, agent, c, content))
      case AgentMove.Corroborate(c, note) =>
        Some(ts => Event.Corroborate(seq, ts, agent, c, note))
      case AgentMove.Refute(c, note) =>
        Some(ts => Event.Refute(seq, ts, agent, c, note))
      case AgentMove.Propose(q) =>
        QuestionId
          .from(s"q$seq")
          .toOption
          .map(qid => ts => Event.QuestionProposed(seq, ts, agent, qid, q))
      case AgentMove.Pass => None

  private def candidate(dto: AgentMoveDto): Option[Answer] =
    Option(dto.candidate).flatMap(Answer.from(_).toOption)

  private def question(dto: AgentMoveDto): Option[String] =
    Option(dto.text).map(_.trim).filter(_.nonEmpty)

  private def text(dto: AgentMoveDto): String =
    Option(dto.text).map(_.trim).getOrElse("")
