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
  *   definition_remembered   {seq, timestamp, type:"definition_remembered", term, meaning, origin}
  *   answer_given            {seq, timestamp, type:"answer_given",      questionId, answer, governing?}
  *   gate_abstain            {seq, timestamp, type:"gate_abstain",      reason}
  *   gate_sign               {seq, timestamp, type:"gate_sign",         candidateId}
  *   convergence_warning     {seq, timestamp, type:"convergence_warning", roundsWithoutConsolidation, glutPersistence}
  *   guess_answered          {seq, timestamp, type:"guess_answered",      candidateId, answer}
  * }}}
  *
  * The opaque id types (and `term`) serialize as their bare string value; `answer` is the lowercase
  * oracle token `yes` / `no` / `unknown` (the TS `Answer` set). `governing` is an ARRAY of term
  * strings, OMITTED when empty (a non-clarified answer keeps the pre-clarification `answer_given`
  * shape byte-identical). `clarification_requested` carries NO `agentId` (the human's move);
  * `definition_given` does (the asking agent). `definition_remembered` carries NO top-level
  * `agentId` (its author spoke in a prior game); instead `origin` is a nested object `{gameId?,
  * agentId, questionId, seq}` — the provenance of the meaning, with `gameId` OMITTED when it is not
  * yet stamped (`None`). Hand-written (no derivation) so the field names and order are pinned by
  * the golden tests rather than inferred from the case-class shape.
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
    case Event.DefinitionRemembered(seq, timestamp, term, meaning, origin) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "definition_remembered".asJson,
        "term" -> term.value.asJson,
        "meaning" -> meaning.asJson,
        "origin" -> originJson(origin)
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
    // The lifecycle markers (hypothesis-lifecycle §A/§B) — belief-inert trace of a retirement /
    // resurrection. Minimal frames now (the frontend `ReasoningEvent` union member lands in slice
    // 3); shape mirrors `gate_sign` — seq, timestamp, type, candidateId.
    case Event.Retired(seq, timestamp, candidate) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "retired".asJson,
        "candidateId" -> candidate.value.asJson
      )
    case Event.Resurrected(seq, timestamp, candidate) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "resurrected".asJson,
        "candidateId" -> candidate.value.asJson
      )
    // The convergence flag (librarian-convergence-monitor) — belief-inert; carries the STRUCTURAL
    // evidence (rounds-without-consolidation, glut-persistence) and NO semantic diagnosis (no
    // candidateId, no reason string). Minimal frame now (the frontend `ReasoningEvent` union member
    // lands in a later slice, as it did for the lifecycle markers).
    case Event.ConvergenceWarning(seq, timestamp, roundsWithoutConsolidation, glutPersistence) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "convergence_warning".asJson,
        "roundsWithoutConsolidation" -> roundsWithoutConsolidation.asJson,
        "glutPersistence" -> glutPersistence.asJson
      )
    // The guess-to-oracle answer (B1) — belief-inert; carries the guessed candidate and the oracle's
    // reply token (yes|no|unknown). Shape mirrors gate_sign plus the answer token.
    case Event.GuessAnswered(seq, timestamp, candidate, answer) =>
      Json.obj(
        "seq" -> seq.asJson,
        "timestamp" -> timestamp.asJson,
        "type" -> "guess_answered".asJson,
        "candidateId" -> candidate.value.asJson,
        "answer" -> answerToken(answer).asJson
      )
  }

  /** The nested `origin` object on a `definition_remembered` frame — the provenance of a recalled
    * meaning. `gameId` is OMITTED when the provenance is not yet stamped (`None`, the current
    * not-yet-persisted game); when present it leads, then `agentId`, `questionId`, `seq`. Fields in
    * this fixed order so the golden test pins the shape the slice-3 decoder matches.
    */
  private def originJson(origin: DefinitionProvenance): Json =
    val stamped =
      List(
        "agentId" -> origin.agent.value.asJson,
        "questionId" -> origin.questionId.value.asJson,
        "seq" -> origin.seq.asJson
      )
    val fields = origin.gameId match
      case Some(g) => ("gameId" -> g.value.asJson) :: stamped
      case None => stamped
    Json.obj(fields*)

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

/** The `POST /rewind` command (B2, recovery-and-endgame) — the human flips ONE poisoned early
  * answer. `toSeq` is the `AnswerGiven` event seq the human clicked; the BACKEND snaps it to the
  * round boundary ([[LogState.rewindPrefix]]), never trusting the client to compute the snap.
  * Untrusted input: a non-positive seq is a [[DecodingFailure]], surfaced as a 400.
  */
final case class RewindCommand(toSeq: Int)

object RewindCommand:
  given decoder: Decoder[RewindCommand] = Decoder.instance { cursor =>
    cursor.get[Int]("toSeq").flatMap { toSeq =>
      if toSeq >= 1 then Right(RewindCommand(toSeq))
      else Left(DecodingFailure(s"toSeq must be a positive event seq: $toSeq", cursor.history))
    }
  }
