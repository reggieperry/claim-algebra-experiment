package claimalgebra.society

/** The round identity the LogActor's barrier keys on — a runtime concept, NOT a persisted [[Event]]
  * field (events carry `seq`, not round). Every `Probe` carries it and every `Post` echoes it, so
  * the barrier counts DISTINCT current-round agents rather than raw messages (actor-abstraction
  * §9). Opaque over an `Int` minted internally, starting at [[first]] and advancing by [[next]];
  * never built from external input, so the constructor guards only the internal invariant (rounds
  * are ≥ 1).
  */
opaque type RoundId = Int

object RoundId:
  val first: RoundId = 1

  /** A round from a raw count — `≥ 1`, fail-closed to `None` below that (rounds are 1-based). */
  def from(raw: Int): Option[RoundId] = if raw >= 1 then Some(raw) else None

  extension (r: RoundId)
    def value: Int = r
    def next: RoundId = r + 1

/** The game's terminal outcome — the value [[Society.play]] returns. A [[Signed]] hypothesis is the
  * society's committed answer; [[Inconclusive]] is a clean give-up (no signature within the round
  * budget, or a round that never heard from its cohort) — a safe non-signature, never a wrong sign.
  */
enum Outcome:
  case Signed(answer: Answer)
  case Inconclusive

/** Whether the game is still running or has reached a terminal state. Once `Ended`, the LogActor
  * ignores every further message (a late post appends nothing and moves no belief).
  */
enum Phase:
  case Playing, Ended

/** A message to an [[AgentActor]]. The whole current game state rides in the `view`, so the agent
  * needs no accumulated memory — one probe, one bounded structured call, one post.
  */
enum ToAgent:
  case Probe(round: RoundId, view: GameView)

  /** Ask the PROPOSER of `questionId` to define the challenged `term` (clarification-feature §2):
    * the human challenged the term, so the agent that proposed the question must state what it
    * meant — if it cannot crisply define its own question, that is itself diagnostic. `question` is
    * the proposed text (so the agent defines the term IN CONTEXT). The agent makes ONE bounded
    * structured call and posts back a [[ToLog.Defined]]; a failed/malformed call posts nothing (the
    * human re-challenges or answers ungrounded — never a wedge, never a fabricated definition).
    */
  case Clarify(questionId: QuestionId, question: String, term: Term)

/** A message to the single-writer [[LogActor]] (the global serialization point). Every belief
  * change enters here as an actor-to-actor send — an agent's [[Post]], the oracle's [[Answered]]
  * reply (even the human's input enters as a send, actor-abstraction §8), or the round's
  * [[RoundTimeout]].
  */
enum ToLog:
  /** Start the game: register the agent cohort and open round one. */
  case Begin(agents: List[(AgentId, ActorRef[ToAgent])])

  /** An agent reports its move for `round` — the barrier records the agent and appends the move. */
  case Post(round: RoundId, agent: AgentId, move: AgentMove)

  /** The round's budget elapsed. Closes the round as complete-WITH-ATTRITION → Abstain, never Sign.
    */
  case RoundTimeout(round: RoundId)

  /** The human ANSWERED the current question — logged as `AnswerGiven` (with the `governing` terms
    * established for this question, derived from the log), then a NEW round opens so the agents
    * must react to the answer before the gate can sign (the forward-carry barrier, §9).
    */
  case Answered(question: QuestionId, answer: OracleAnswer)

  /** The human CHALLENGED the current question's `term` (clarification-feature §1) — grounding is
    * PAUSED: the LogActor logs `ClarificationRequested`, routes a [[ToAgent.Clarify]] to the
    * proposer, and re-asks the same question. NO `AnswerGiven` is emitted and the round does not
    * close on a challenge.
    */
  case Challenged(question: QuestionId, term: Term)

  /** The asking agent SUPPLIED the `meaning` of the challenged `term` (clarification-feature §2) —
    * the LogActor logs it as a `DefinitionGiven` claim (belief-inert), available to every agent for
    * the rest of the game. Carries the defining `agent` (the proposer) for provenance.
    */
  case Defined(question: QuestionId, agent: AgentId, term: Term, meaning: String)

  /** The oracle ANSWERED a guess (B1, recovery-and-endgame) — "is it `candidate`?" → `answer`. Its
    * own transport (NOT [[Answered]], which opens a new round): the LogActor logs the belief-inert
    * `GuessAnswered` and re-runs the gate — a `Yes` signs the confirmed winner (the floor relaxed
    * by the ground truth), a `No` masks the candidate off the fold and the give-up ladder
    * continues, an `Unknown` neither signs nor masks (the candidate is marked posed and never
    * re-guessed).
    */
  case GuessAnswered(candidate: Answer, answer: OracleAnswer)
