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
    Option(dto.text).map(_.trim).filter(_.nonEmpty).filterNot(isEitherOr)

  /** A structural, fail-closed guard on a proposed question (A3, recovery-and-endgame): drop an
    * EITHER/OR question — one whose surface form joins a second interrogative clause with "or" ("…
    * or is it …", "… or are they …") — because a plain "yes" to it is uninterpretable, the
    * apple-log failure. Dropping is fail-closed: the proposer posts nothing (an abstention, exactly
    * as a malformed move), never a fabricated or wrong sign. Kept deliberately CONSERVATIVE — it
    * matches only "or" immediately followed by an interrogative verb, so a SET-MEMBERSHIP question
    * ("… glass, ceramic, or stone?", where "yes" means one of them) is NOT dropped; over-rejecting
    * useful questions would cost only recall, but is still avoided. The residue (an either/or with
    * no repeated verb) is left to the prompt contract — a prompt cannot be proven by the fold, so
    * best-effort is the honest ceiling under a cheap model. Structural only (surface tokens, no
    * meaning judged), so it stays out of the librarian and the gate.
    */
  private val eitherOr =
    raw"\bor\s+(is|are|am|does|do|did|was|were|can|could|has|have|will|would|should)\b".r

  private def isEitherOr(question: String): Boolean =
    // `Locale.ROOT` so the casing is locale-deterministic (determinism-first): a Turkish-locale JVM
    // otherwise maps I/i differently and could shift which questions match.
    eitherOr.findFirstIn(question.toLowerCase(java.util.Locale.ROOT)).isDefined

  private def text(dto: AgentMoveDto): String =
    Option(dto.text).map(_.trim).getOrElse("")
