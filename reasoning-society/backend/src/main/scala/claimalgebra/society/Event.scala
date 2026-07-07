package claimalgebra.society

/** The fields every reasoning event carries — the frontend `EventBase` (model/event.ts). `seq` is
  * the 1-based, contiguous global serialization order the log assigns (the single point where the
  * fold learns the order to consume); `timestamp` is display-only.
  */
sealed trait EventMeta:
  def seq: Int
  def timestamp: Long

/** The single ordered stream the fold folds — the Scala mirror of the frontend `ReasoningEvent`
  * union (model/event.ts), the same nine variants with the same payloads so the log serializes to
  * matching JSON in a later slice. A closed `enum`, so a `match` over it is exhaustively checked
  * and an impossible combination (a [[GateSign]] with a `questionId`) cannot be constructed
  * (scala-types.md: make illegal states unrepresentable — stronger than a uniform
  * `agentId: Option`).
  *
  * Two layers of claim (brief §2): [[AnswerGiven]] is the ORACLE (ground truth), while [[Assert]] /
  * [[Corroborate]] / [[Refute]] / [[Strike]] move HYPOTHESES (what the answer is). The question and
  * gate events are the society's control flow, logged so replay and the graph stay rich but
  * projecting to no belief.
  *
  * `agentId` is present on exactly the six variants that carry one (matching the frontend), NOT a
  * uniform optional field; the enum makes that structural. The [[agentId]] read below gives the
  * uniform `Option[AgentId]` view a caller wants, over the precise per-variant fields.
  */
enum Event extends EventMeta:

  /** An agent proposes hypothesis `candidateId`, introducing it with `content`. Pro-support. */
  case Assert(seq: Int, timestamp: Long, agent: AgentId, candidateId: Answer, content: String)

  /** An agent adds support to an existing hypothesis. Pro-support (a second backer, or the same one
    * again — the no-lone-sign floor counts DISTINCT agents, so a repeat does not lift the floor).
    */
  case Corroborate(seq: Int, timestamp: Long, agent: AgentId, candidateId: Answer, note: String)

  /** An agent (or the human/oracle seam) contradicts a live hypothesis. Con-support — folded
    * against a prior assertion of the same value it yields a glut (a real conflict).
    */
  case Refute(seq: Int, timestamp: Long, agent: AgentId, candidateId: Answer, note: String)

  /** An agent strikes a hypothesis whole — a retraction of the slot's operative with no
    * replacement. Reads Superseded (struck).
    */
  case Strike(seq: Int, timestamp: Long, agent: AgentId, candidateId: Answer, note: String)

  /** A question is proposed but not yet asked — logged for replay; belief-inert. */
  case QuestionProposed(
      seq: Int,
      timestamp: Long,
      agent: AgentId,
      questionId: QuestionId,
      content: String
  )

  /** A question is put to the oracle — the current question; belief-inert. */
  case QuestionAsked(
      seq: Int,
      timestamp: Long,
      agent: AgentId,
      questionId: QuestionId,
      content: String
  )

  /** The oracle answers a question (`yes`/`no`/`unknown`). No agent — the oracle is outside the
    * actor graph (actor-abstraction §3: the observer gets no vote). Belief-inert: the raw reply
    * moves no candidate (the contradiction it implies enters as a [[Refute]]).
    */
  case AnswerGiven(seq: Int, timestamp: Long, questionId: QuestionId, answer: OracleAnswer)

  /** The gate abstained, with a human-readable reason — logged control flow; belief-inert. */
  case GateAbstain(seq: Int, timestamp: Long, reason: String)

  /** The gate signed a hypothesis — logged control flow; belief-inert (the sign is a READING of the
    * fold, honest only when the fold agrees, so it never itself moves belief).
    */
  case GateSign(seq: Int, timestamp: Long, candidateId: Answer)

  /** The uniform "who spoke" read — `Some` on the six agent-bearing variants, `None` on the oracle
    * and gate events. The floor and any router consult this; it is deliberately a READING, so the
    * per-variant fields stay precise (an [[AnswerGiven]] structurally cannot carry an agent).
    */
  def agentId: Option[AgentId] = this match
    case Assert(_, _, a, _, _) => Some(a)
    case Corroborate(_, _, a, _, _) => Some(a)
    case Refute(_, _, a, _, _) => Some(a)
    case Strike(_, _, a, _, _) => Some(a)
    case QuestionProposed(_, _, a, _, _) => Some(a)
    case QuestionAsked(_, _, a, _, _) => Some(a)
    case AnswerGiven(_, _, _, _) => None
    case GateAbstain(_, _, _) => None
    case GateSign(_, _, _) => None
