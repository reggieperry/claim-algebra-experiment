package claimalgebra.society

/** The agent identity the log routes on — Agha's mail target read as "who spoke". Per
  * actor-abstraction §9 an agent identity is ROUTING, not provenance: it never enters a claim's
  * `Lineage`/`Prov` (a re-asserting agent must not double-count its own support), and the
  * no-lone-sign floor counts DISTINCT agents FROM THE EVENTS, never from the algebra's provenance.
  * Opaque over a `String` behind a validating constructor, mirroring the frontend `AgentId`
  * (model/ids.ts): a blank label names no agent, so it is refused.
  */
opaque type AgentId = String

object AgentId:
  /** The only constructor. Fail closed: a blank (or whitespace-only) label is refused rather than
    * admitted as a nameless agent. Insignificant surrounding whitespace is trimmed.
    */
  def from(raw: String): Either[String, AgentId] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Left("agent id must be a non-blank label")
    else Right(trimmed)

  extension (a: AgentId) def value: String = a

/** The identity of a question the society puts to the oracle — the frontend `QuestionId`
  * (model/ids.ts). Carried on the question/answer events for replay and the current-question read;
  * belief-inert. Opaque over a `String` behind a validating constructor.
  */
opaque type QuestionId = String

object QuestionId:
  /** The only constructor. Fail closed: a blank tag identifies no question, so it is refused. */
  def from(raw: String): Either[String, QuestionId] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Left("question id must be a non-blank label")
    else Right(trimmed)

  extension (q: QuestionId) def value: String = q

/** A competing hypothesis — a candidate answer to the twenty-questions game, and the candidate
  * VALUE the single answer-slot ranges over (actor-abstraction §7: one `Testimony[Answer]` whose
  * candidate values are the rival hypotheses). On the wire it is the frontend `candidateId`
  * (model/ids.ts): the backend keys the slot by the hypothesis identity, `content` being
  * display-only. A sound `Map` key — opaque over a `String`, so it carries the underlying value
  * equality the candidate carrier needs. Opaque behind a validating constructor: a blank names no
  * hypothesis, so it is refused.
  */
opaque type Answer = String

object Answer:
  /** The only constructor. Fail closed: a blank (or whitespace-only) label names no hypothesis, so
    * it is refused. Insignificant surrounding whitespace is trimmed.
    */
  def from(raw: String): Either[String, Answer] =
    val trimmed = raw.trim
    if trimmed.isEmpty then Left("answer must be a non-blank label")
    else Right(trimmed)

  extension (a: Answer) def value: String = a

/** The oracle's verdict on a question — the frontend `Answer` (model/event.ts). `Unknown` is a
  * real, distinct reply (the human may not know), NEVER a stand-in for "unanswered"; an unanswered
  * question simply has no [[Event.AnswerGiven]] yet. Belief-inert on the wire: the raw yes/no/
  * unknown reply carries no candidate, so it moves no hypothesis directly — the human/oracle seam
  * translates it into an [[Event.Refute]] of the contradicted hypothesis when it refutes one
  * (matching the frontend fold, which records the answer but changes no candidate). Serializes
  * lowercase (`yes`/`no`/`unknown`) at the wire boundary (a later slice's concern).
  */
enum OracleAnswer:
  case Yes, No, Unknown
