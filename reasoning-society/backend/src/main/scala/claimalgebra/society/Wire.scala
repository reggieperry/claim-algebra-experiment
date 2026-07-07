package claimalgebra.society

import cats.syntax.all.*
import io.circe.syntax.*
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

/** The JSON wire format for the reasoning-society event log — the exact shape the browser decoder
  * consumes (the TypeScript `ReasoningEvent` union in `frontend/src/model/event.ts`). This object
  * is the ONE place the contract lives (craft-complexity: the knowledge in a single module), so the
  * frontend decoder in slice 3b can be made to agree against it.
  *
  * Every event is a JSON object with, in order, `seq` (number), `timestamp` (number), a `type`
  * discriminator, then that variant's payload — matching the TS union member for member:
  *
  * {{{
  *   assert                  {seq, timestamp, type:"assert",            agentId, candidateId, content}
  *   corroborate             {seq, timestamp, type:"corroborate",       agentId, candidateId, note}
  *   refute                  {seq, timestamp, type:"refute",            agentId, candidateId, note}
  *   strike                  {seq, timestamp, type:"strike",            agentId, candidateId, note}
  *   question_proposed       {seq, timestamp, type:"question_proposed", agentId, questionId,  content}
  *   question_asked          {seq, timestamp, type:"question_asked",    agentId, questionId,  content}
  *   clarification_requested {seq, timestamp, type:"clarification_requested", questionId, term}
  *   definition_given        {seq, timestamp, type:"definition_given",  agentId, questionId, term, meaning}
  *   answer_given            {seq, timestamp, type:"answer_given",      questionId, answer, governing?}
  *   gate_abstain            {seq, timestamp, type:"gate_abstain",      reason}
  *   gate_sign               {seq, timestamp, type:"gate_sign",         candidateId}
  * }}}
  *
  * The opaque id types (and `term`) serialize as their bare string value; `answer` is the lowercase
  * oracle token `yes` / `no` / `unknown` (the TS `Answer` set). `governing` is an ARRAY of term
  * strings, OMITTED when empty (a non-clarified answer keeps the pre-clarification `answer_given`
  * shape byte-identical). `clarification_requested` carries NO `agentId` (the human's move);
  * `definition_given` does (the asking agent). Hand-written (no derivation) so the field names and
  * order are pinned by the golden tests rather than inferred from the case-class shape.
  */
object Wire:

  /** The lowercase wire token for an oracle reply (the TS `ANSWERS` set). */
  def answerToken(answer: OracleAnswer): String = answer match
    case OracleAnswer.Yes => "yes"
    case OracleAnswer.No => "no"
    case OracleAnswer.Unknown => "unknown"

  /** Parse an oracle token back to the domain reply, fail-closed on anything outside the closed set
    * (untrusted input from `POST /answer`). Case-insensitive, trims insignificant whitespace.
    */
  def answerFromToken(raw: String): Either[String, OracleAnswer] =
    raw.trim.toLowerCase match
      case "yes" => Right(OracleAnswer.Yes)
      case "no" => Right(OracleAnswer.No)
      case "unknown" => Right(OracleAnswer.Unknown)
      case other => Left(s"answer not in {yes, no, unknown}: $other")

  /** The wire encoder — the contract. One object per variant, fields in the documented order. */
  given eventEncoder: Encoder[Event] = Encoder.instance {
    case Event.Assert(seq, timestamp, agent, candidate, content) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "assert".asJson,
        "agentId" -> agent.value.asJson,
        "candidateId" -> candidate.value.asJson,
        "content" -> content.asJson
      )
    case Event.Corroborate(seq, timestamp, agent, candidate, note) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "corroborate".asJson,
        "agentId" -> agent.value.asJson,
        "candidateId" -> candidate.value.asJson,
        "note" -> note.asJson
      )
    case Event.Refute(seq, timestamp, agent, candidate, note) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "refute".asJson,
        "agentId" -> agent.value.asJson,
        "candidateId" -> candidate.value.asJson,
        "note" -> note.asJson
      )
    case Event.Strike(seq, timestamp, agent, candidate, note) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "strike".asJson,
        "agentId" -> agent.value.asJson,
        "candidateId" -> candidate.value.asJson,
        "note" -> note.asJson
      )
    case Event.QuestionProposed(seq, timestamp, agent, question, content) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "question_proposed".asJson,
        "agentId" -> agent.value.asJson,
        "questionId" -> question.value.asJson,
        "content" -> content.asJson
      )
    case Event.QuestionAsked(seq, timestamp, agent, question, content) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "question_asked".asJson,
        "agentId" -> agent.value.asJson,
        "questionId" -> question.value.asJson,
        "content" -> content.asJson
      )
    case Event.ClarificationRequested(seq, timestamp, question, term) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "clarification_requested".asJson,
        "questionId" -> question.value.asJson,
        "term" -> term.value.asJson
      )
    case Event.DefinitionGiven(seq, timestamp, agent, question, term, meaning) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "definition_given".asJson,
        "agentId" -> agent.value.asJson,
        "questionId" -> question.value.asJson,
        "term" -> term.value.asJson,
        "meaning" -> meaning.asJson
      )
    case Event.AnswerGiven(seq, timestamp, question, answer, governing) =>
      // `governing` is OMITTED when empty, so a non-clarified answer's wire shape is byte-identical
      // to the pre-clarification contract (additive, backward-compatible).
      val fields = List(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "answer_given".asJson,
        "questionId" -> question.value.asJson,
        "answer" -> answerToken(answer).asJson
      )
      val withGoverning =
        if governing.isEmpty then fields
        else fields :+ ("governing" -> governing.map(_.value).asJson)
      Json.obj(withGoverning*)
    case Event.GateAbstain(seq, timestamp, reason) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "gate_abstain".asJson,
        "reason" -> reason.asJson
      )
    case Event.GateSign(seq, timestamp, candidate) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "gate_sign".asJson,
        "candidateId" -> candidate.value.asJson
      )
  }

  /** Encode one event to its wire JSON. */
  def encode(event: Event): Json = event.asJson

/** The `POST /answer` command — the browser's answer to the current question. Decoded from
  * untrusted JSON and re-validated to the domain types at the boundary (scala-security: validate
  * untrusted input): a blank/missing id or an answer outside `yes|no|unknown` is a
  * [[DecodingFailure]], which the route surfaces as a 4xx rather than admitting a bad value.
  */
final case class AnswerCommand(questionId: QuestionId, answer: OracleAnswer)

object AnswerCommand:
  given decoder: Decoder[AnswerCommand] = Decoder.instance { cursor =>
    for
      questionRaw <- cursor.get[String]("questionId")
      answerRaw <- cursor.get[String]("answer")
      question <- QuestionId.from(questionRaw).leftMap(DecodingFailure(_, cursor.history))
      answer <- Wire.answerFromToken(answerRaw).leftMap(DecodingFailure(_, cursor.history))
    yield AnswerCommand(question, answer)
  }

/** The `POST /challenge` command — the browser's CHALLENGE of a term on the current question
  * ("define '<term>'", clarification-feature §1), before answering. Decoded from untrusted JSON and
  * re-validated to the domain types at the boundary (scala-security: validate untrusted input): a
  * blank/missing id or a blank term is a [[DecodingFailure]], surfaced as a 4xx rather than
  * admitting a bad value. The [[Term]] constructor NORMALIZES the term (trim/collapse/case-fold),
  * so the challenge grounds against the same key a stored definition uses.
  */
final case class ChallengeCommand(questionId: QuestionId, term: Term)

object ChallengeCommand:
  given decoder: Decoder[ChallengeCommand] = Decoder.instance { cursor =>
    for
      questionRaw <- cursor.get[String]("questionId")
      termRaw <- cursor.get[String]("term")
      question <- QuestionId.from(questionRaw).leftMap(DecodingFailure(_, cursor.history))
      term <- Term.from(termRaw).leftMap(DecodingFailure(_, cursor.history))
    yield ChallengeCommand(question, term)
  }
