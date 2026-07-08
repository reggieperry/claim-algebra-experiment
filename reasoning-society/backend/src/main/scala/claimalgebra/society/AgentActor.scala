package claimalgebra.society

import cats.effect.IO
import claimalgebra.extract.LlmCall

/** One reasoning agent's fixed identity, role label, and system prompt. The `id` is the ROUTING
  * identity the log dedups and the floor counts (actor-abstraction §9): one [[AgentActor]] holds
  * exactly one [[AgentStrategy]], so it can only ever post under its own id — the no-lone-sign
  * floor's identity contract (a Sybil cannot satisfy the ≥2-distinct-backer floor with one real
  * actor). The system prompt is the fixed, trusted rubric fed whole to the model; the game state
  * rides the user message, never spliced into the prompt (scala-security.md).
  */
final case class AgentStrategy(id: AgentId, label: String, systemPrompt: String)

object AgentStrategy:

  private def make(rawId: String, label: String, prompt: String): Option[AgentStrategy] =
    AgentId.from(rawId).toOption.map(id => AgentStrategy(id, label, prompt))

  private val outputContract: String =
    """Reply with a single structured move. Fields:
      |- action: one of "assert", "corroborate", "refute", "propose", "pass".
      |- candidate: the hypothesis label (for assert/corroborate/refute); leave blank otherwise.
      |- text: the note, content, or — for "propose" — the yes/no question to ask.
      |A proposed question MUST be a SINGLE yes/no proposition, answerable plainly "yes" or "no".
      |NEVER ask an either/or ("is it A, or is it B?") — a "yes" to it is uninterpretable; ask about
      |ONE attribute at a time. (A set-membership question — "is it made of glass, ceramic, or stone?"
      |— is fine: "yes" means one of them, "no" means none.)
      |Use the EXACT same candidate label when you mean the same hypothesis, so the society can agree.""".stripMargin

  /** The FIXED, trusted system prompt for a clarification-define call (clarification-feature §2).
    * The agent that proposed a question is asked to state what it meant by a challenged term. It is
    * a distinct rubric from the move contract (the output is a definition, not a move). The
    * untrusted question text and term ride the USER message, never this prompt (scala-security.md).
    */
  val definePrompt: String =
    """You are a member of a reasoning society playing Twenty Questions. You proposed a yes/no
      |question, and a human has challenged one term in it — they want to know what you meant before
      |they answer. Define the term in the EVERYDAY, common-sense way a player would use it to
      |DISTINGUISH one candidate hidden-thing from another in THIS game — NOT a maximal technical or
      |textbook definition. For "alive", the useful sense is "a living or once-living thing — a plant,
      |animal, or a part of one — as opposed to a manufactured object or a mineral", NOT a biology test
      |of active metabolism that would exclude a picked fruit. Keep it to one crisp sentence so it
      |grounds every future use; if you cannot state it crisply, that itself signals the question was
      |ill-formed. Reply with a single field:
      |- meaning: your one-sentence definition of the challenged term.""".stripMargin

  /** The broad category-splitter — proposes bisecting yes/no questions to halve the space. */
  private val splitter: Option[AgentStrategy] = make(
    "splitter",
    "category-splitter",
    s"""You are the CATEGORY-SPLITTER in a society playing Twenty Questions to identify a hidden thing.
       |Your job is to narrow the space: propose a single broad yes/no question that roughly HALVES
       |the remaining possibilities, given the question/answer history — bisect on ONE attribute at a
       |time, never an either/or. Prefer "propose". Only assert a hypothesis once the answers strongly
       |point at one thing.
       |
       |$outputContract""".stripMargin
  )

  /** The specifics-driller — asserts concrete hypotheses consistent with the answers. */
  private val driller: Option[AgentStrategy] = make(
    "driller",
    "specifics-driller",
    s"""You are the SPECIFICS-DRILLER in a society playing Twenty Questions. Your job is to commit to a
       |concrete guess: assert the single most likely specific hypothesis consistent with the
       |question/answer history, or corroborate an existing hypothesis you agree with. Prefer "assert"
       |or "corroborate". Propose a question only when you have no concrete guess.
       |
       |$outputContract""".stripMargin
  )

  /** The contrarian/skeptic — refutes hypotheses inconsistent with the accumulated Q&A. */
  private val skeptic: Option[AgentStrategy] = make(
    "skeptic",
    "contrarian-skeptic",
    s"""You are the SKEPTIC in a society playing Twenty Questions. Your job is to CHALLENGE: if a
       |hypothesis on the table is inconsistent with the question/answer history, refute it (action
       |"refute", naming the exact candidate). If nothing is inconsistent, pass, or propose a sharp
       |disconfirming question. Never assert a new hypothesis — you test, you do not guess.
       |
       |$outputContract""".stripMargin
  )

  /** The default diverse cohort (three distinct roles, three distinct ids and prompts). */
  val cohort: List[AgentStrategy] = List(splitter, driller, skeptic).flatten

/** An LLM-backed reasoning agent (actor-abstraction §2): on a [[ToAgent.Probe]] it makes ONE
  * bounded, structured call for its move, then posts the resulting claim to the LogActor tagged
  * with its OWN [[AgentId]] and the probe's round. A failed, timed-out, malformed, or raised call
  * recovers INSIDE `receive` to an abstention — NO post, never a fiber-killing throw
  * (actor-abstraction §9 G): a missing report is attrition the round barrier holds against, not a
  * wedge. Stateless: the full game view arrives on every probe, so the agent designates no changed
  * behavior (§4 — a stateless actor returns `unchanged`).
  */
final class AgentActor(
    context: ActorContext[ToAgent],
    strategy: AgentStrategy,
    llm: LlmCall[AgentMoveDto],
    definer: LlmCall[DefinitionDto],
    log: ActorRef[ToLog]
) extends Actor[ToAgent](context):

  def receive(message: ToAgent): IO[Actor[ToAgent]] = message match
    case ToAgent.Probe(round, view) =>
      llm.call(strategy.systemPrompt, view.render).attempt.flatMap {
        case Right(Right(dto)) =>
          AgentMove.parse(dto) match
            case Some(move) => postMove(round, move)
            case None => unchanged // malformed structured output → no post (abstention)
        case Right(Left(_)) => unchanged // a typed CallError → no post
        case Left(_) => unchanged // a raised call → recovered here, no post, actor lives
      }

    case ToAgent.Clarify(questionId, question, term) =>
      // Define the challenged term with ONE bounded structured call (clarification-feature §2). Same
      // failure-is-silence discipline as a probe: a malformed/typed-error/raised call posts NOTHING
      // (the human re-challenges or answers ungrounded — never a wedge, never a fabricated
      // definition). The untrusted question text + term ride the USER message (scala-security.md).
      definer.call(AgentStrategy.definePrompt, defineUserMessage(question, term)).attempt.flatMap {
        case Right(Right(dto)) =>
          Definition.meaningOf(dto) match
            case Some(meaning) => postDefinition(questionId, term, meaning)
            case None => unchanged // blank/malformed meaning → no post (fail-closed)
        case Right(Left(_)) => unchanged // a typed CallError → no post
        case Left(_) => unchanged // a raised call → recovered here, no post, actor lives
      }

  /** Post the move under THIS agent's own id and the probe's round — the id and round it cannot
    * forge. The message id is caller-minted and stable per (agent, round) (the dedup key, §5); a
    * blank id is structurally impossible (non-blank agent id, positive round), so the `Left` branch
    * is a fail-closed no-op rather than a partial-accessor throw.
    */
  private def postMove(round: RoundId, move: AgentMove): IO[Actor[ToAgent]] =
    MessageId.from(s"${strategy.id.value}-post-r${round.value}") match
      case Right(id) => send(log, id, ToLog.Post(round, strategy.id, move)) *> unchanged
      case Left(_) => unchanged

  /** Post the definition under THIS agent's own id — the proposer states what it meant.
    * Belief-inert at the LogActor (a `DefinitionGiven` claim, never a hypothesis). A blank message
    * id is structurally impossible (non-blank agent + question id), so the `Left` branch is a
    * fail-closed no-op.
    */
  private def postDefinition(qid: QuestionId, term: Term, meaning: String): IO[Actor[ToAgent]] =
    MessageId.from(s"${strategy.id.value}-define-${qid.value}") match
      case Right(id) => send(log, id, ToLog.Defined(qid, strategy.id, term, meaning)) *> unchanged
      case Left(_) => unchanged

  /** The user message for the define call — the untrusted proposed question and the challenged
    * term, kept OUT of the system prompt (scala-security.md: never splice untrusted content into
    * the trusted rubric).
    */
  private def defineUserMessage(question: String, term: Term): String =
    s"""Your question: "$question"
       |The challenged term: "${term.value}"
       |Define what you meant by "${term.value}".""".stripMargin
