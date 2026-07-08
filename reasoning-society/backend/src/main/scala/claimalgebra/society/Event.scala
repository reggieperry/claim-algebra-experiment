package claimalgebra.society

/** The fields every reasoning event carries — the frontend `EventBase` (model/event.ts). `seq` is
  * the 1-based, contiguous global serialization order the log assigns (the single point where the
  * fold learns the order to consume); `timestamp` is display-only.
  */
sealed trait EventMeta:
  def seq: Int
  def timestamp: Long

/** The single ordered stream the fold folds — the Scala mirror of the frontend `ReasoningEvent`
  * union (model/event.ts), the same variants with the same payloads so the log serializes to
  * matching JSON in a later slice. A closed `enum`, so a `match` over it is exhaustively checked
  * and an impossible combination (a [[GateSign]] with a `questionId`) cannot be constructed
  * (scala-types.md: make illegal states unrepresentable — stronger than a uniform
  * `agentId: Option`).
  *
  * Two layers of claim (brief §2): [[AnswerGiven]] is the ORACLE (ground truth), while [[Assert]] /
  * [[Corroborate]] / [[Refute]] / [[Strike]] move HYPOTHESES (what the answer is). The question,
  * gate, and clarification events are the society's control flow, logged so replay and the graph
  * stay rich but projecting to no belief. The clarification pair — [[ClarificationRequested]] and
  * [[DefinitionGiven]] — negotiates the shared vocabulary a grounded answer is grounded to
  * (clarification-feature): also belief-inert, definitions being grounding context, not hypotheses.
  *
  * `agentId` is present on exactly the variants that carry one (matching the frontend), NOT a
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

  /** The human CHALLENGED a question's `term` before answering — "what do you mean by `term`?"
    * (clarification-feature §1) — pausing grounding until the asking agent defines it. The HUMAN's
    * move, so NO agent: the human/oracle seam sits outside the actor graph, exactly like
    * [[AnswerGiven]] (actor-abstraction §3). Belief-inert: a challenge moves no hypothesis; it is
    * logged so replay reconstructs the negotiation at the playhead (§4).
    */
  case ClarificationRequested(seq: Int, timestamp: Long, questionId: QuestionId, term: Term)

  /** The ASKING agent supplied the `meaning` of a challenged `term` (clarification-feature §2): the
    * proposing agent must state what it meant, and if it cannot crisply define its own question
    * that is itself diagnostic. Belief-inert: a definition is grounding context/audit, NEVER a
    * hypothesis about the answer — it enters the ledger as a [[Definition]] CLAIM (read by
    * [[Definitions]]), available to every agent, not as evidence for the answer-slot. Carries an
    * `agent` (unlike the human's challenge).
    */
  case DefinitionGiven(
      seq: Int,
      timestamp: Long,
      agent: AgentId,
      questionId: QuestionId,
      term: Term,
      meaning: String
  )

  /** The oracle answers a question (`yes`/`no`/`unknown`). No agent — the oracle is outside the
    * actor graph (actor-abstraction §3: the observer gets no vote). Belief-inert: the raw reply
    * moves no candidate (the contradiction it implies enters as a [[Refute]]).
    *
    * `governing` is the ADDITIVE clarification reference (clarification-feature §4): the term(s)
    * whose established [[Definition]] the grounded answer was grounded to, so the answer records
    * WHAT it was grounded to. Empty on a non-clarified answer — which omits the field on the wire,
    * keeping the pre-clarification `answer_given` shape byte-identical. Belief-inert either way:
    * the reference decorates the record, it never moves a candidate.
    */
  case AnswerGiven(
      seq: Int,
      timestamp: Long,
      questionId: QuestionId,
      answer: OracleAnswer,
      governing: List[Term] = Nil
  )

  /** A definition RECALLED from persistent memory into a fresh game (two-tier-reset-design): the
    * session's established vocabulary, replayed at the HEAD of the new game's log so agents ground
    * to it from question one without re-litigating what a term means. It carries `origin`
    * provenance — which game, agent, and question first established the meaning — NEVER a
    * current-game exchange, and has NO agent in the who-spoke read (its author spoke in a prior
    * game). Belief-inert like the clarification pair, and a DISTINCT variant, not a re-emitted
    * [[DefinitionGiven]]: the cross-game question-id collision is guaranteed (both games mint
    * `q1`), so a recalled definition must be structurally excluded from the governing-term read and
    * the ordering gate. It projects to nothing ([[GameCore.project]]); it enters the vocabulary
    * read as a [[Definition]] claim ([[Definitions.from]]).
    */
  case DefinitionRemembered(
      seq: Int,
      timestamp: Long,
      term: Term,
      meaning: String,
      origin: DefinitionProvenance
  )

  /** The gate abstained, with a human-readable reason — logged control flow; belief-inert. */
  case GateAbstain(seq: Int, timestamp: Long, reason: String)

  /** The gate signed a hypothesis — logged control flow; belief-inert (the sign is a READING of the
    * fold, honest only when the fold agrees, so it never itself moves belief).
    */
  case GateSign(seq: Int, timestamp: Long, candidateId: Answer)

  /** The librarian RETIRED a defeated hypothesis to trace (hypothesis-lifecycle §A/§B): its pro
    * channel has no live support (every pro-author self-withdrew) and its con channel carries ≥ 2
    * standing refutations, so it is a *defeated* claim, not a live glut. Off the live board, kept
    * as citable trace — a marker, NOT the masking authority: masking is the recomputed
    * [[GameCore.retiredCandidates]] predicate, and this event only mirrors it for the audit/UI
    * trace ([[GameCore.reconcileRetirements]] keeps the two in sync). Belief-inert — no agent (the
    * librarian reads the channel balance the agents produced; it makes no domain judgment), and it
    * projects to nothing ([[GameCore.project]]), so it can never move a hypothesis or invent a
    * backer.
    */
  case Retired(seq: Int, timestamp: Long, candidateId: Answer)

  /** The librarian RESURRECTED a previously-retired hypothesis (hypothesis-lifecycle §B, recovery):
    * fresh live support arrived above an agent's latest refutation, so the retirement predicate no
    * longer holds and the claim returns to the live board. Retirement is to trace, never deletion,
    * so this is a re-fold consequence, not an un-delete. Belief-inert exactly as [[Retired]] is —
    * no agent, projects to nothing.
    */
  case Resurrected(seq: Int, timestamp: Long, candidateId: Answer)

  /** The librarian raised a NON-CONVERGENCE flag (librarian-convergence-monitor): the search is not
    * converging — a purely STRUCTURAL fact about the belief-state history ([[Convergence]]), read
    * with zero understanding of what any claim means. It carries only the structural evidence —
    * `roundsWithoutConsolidation` (how many recent rounds passed with no candidate accumulating
    * durable, signable support) and `glutPersistence` (the longest run of rounds one live glut sat
    * unresolved) — and NO semantic diagnosis: no candidate name, no "the answer is wrong". The
    * detect/diagnose split (§C, retirement): the librarian detects THAT the search is stuck; the
    * human or the agents diagnose WHAT is wrong. Belief-inert exactly as the lifecycle markers are
    * — no agent (the librarian counts structure, it makes no domain judgment), and it projects to
    * nothing ([[GameCore.project]]), so it can never move a hypothesis, invent a backer, or change
    * the gate/sign decision. It is a request for help, never permission to guess.
    */
  case ConvergenceWarning(
      seq: Int,
      timestamp: Long,
      roundsWithoutConsolidation: Int,
      glutPersistence: Int
  )

  /** The society POSED A GUESS to the oracle — "is it <candidate>?" — and got `answer` (B1,
    * recovery-and-endgame). The endgame move: when the search stalls on a lone, unconfirmed
    * candidate, the society asks the oracle directly rather than dying blank. Belief-inert — it
    * moves no hypothesis itself ([[GameCore.project]] drops it); the WORK is done by two structural
    * folds over these events. A `No` UNIONS the candidate into the masking authority
    * ([[GameCore.maskedCandidates]]), dropping it from the slot, `decide`, and the live board —
    * never a `Refute`, which would glut the whole slot's channel TOTALS and deadlock all future
    * signing. A `Yes` relaxes ONLY the no-lone-sign floor inside [[GameCore.decide]], so the
    * ground-truth confirmation substitutes for the missing second backer while `Gate.accept` still
    * gates on `corner = True ∧ cardinality = 1` from the LIVE evidence. `Unknown` neither masks nor
    * confirms. No agent — the oracle is not an agent; the guess is posed by the society from the
    * gate's own clean winner, never an agent's free text.
    */
  case GuessAnswered(seq: Int, timestamp: Long, candidateId: Answer, answer: OracleAnswer)

  /** The uniform "who spoke" read — `Some` on the seven agent-bearing variants (including
    * [[DefinitionGiven]], the asking agent's move), `None` on the human's challenge, the recalled
    * definition (its author spoke in a prior game — the origin agent is provenance, not a this-game
    * speaker), the oracle, and the gate events. The floor and any router consult this; it is
    * deliberately a READING, so the per-variant fields stay precise (an [[AnswerGiven]]
    * structurally cannot carry an agent).
    */
  def agentId: Option[AgentId] = this match
    case Assert(_, _, a, _, _) => Some(a)
    case Corroborate(_, _, a, _, _) => Some(a)
    case Refute(_, _, a, _, _) => Some(a)
    case Strike(_, _, a, _, _) => Some(a)
    case QuestionProposed(_, _, a, _, _) => Some(a)
    case QuestionAsked(_, _, a, _, _) => Some(a)
    case DefinitionGiven(_, _, a, _, _, _) => Some(a)
    case ClarificationRequested(_, _, _, _) => None
    case DefinitionRemembered(_, _, _, _, _) => None
    case AnswerGiven(_, _, _, _, _) => None
    case GateAbstain(_, _, _) => None
    case GateSign(_, _, _) => None
    // The librarian's lifecycle markers carry no agent — it reads the channel balance and files the
    // consequence; the retirement is a structural fact about the log, not one agent's move.
    case Retired(_, _, _) => None
    case Resurrected(_, _, _) => None
    // The convergence flag likewise carries no agent — it counts structural evidence over the
    // belief-state history, a fact about the log, not any agent's move.
    case ConvergenceWarning(_, _, _, _) => None
    // The guess is posed by the society to the oracle, not by an agent — no agent.
    case GuessAnswered(_, _, _, _) => None
