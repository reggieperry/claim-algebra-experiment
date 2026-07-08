package claimalgebra.society
package experiment

import cats.effect.IO
import claimalgebra.extract.LlmCall

/** A held-fixed, MODEL-BACKED [[TruthOracle]] for the OPEN (free-text) question-space of the live
  * game (fallible-oracle-build-plan §Decisions). A pre-registered property table cannot match
  * arbitrary agent-generated questions, so the experimenter's ground truth is a model call — held
  * FIXED across every sweep cell (same tier, same prompt), so any residual ground-truth error is a
  * CONSTANT confound, not a per-cell effect (threats-to-validity). Use a STRONGER tier than the
  * society under test so the ground truth is reliably correct; the error model then corrupts on top
  * of it.
  *
  * Fail-closed to `Unknown` (a genuine gap) on a model error or an unrecognized answer. The target
  * is TRUSTED (the experimenter's) so it rides the system prompt; the question is UNTRUSTED
  * (agent-generated) so it rides the user message (scala-security).
  */
final class ModelTruthOracle(call: LlmCall[TruthDto]) extends TruthOracle:
  def truth(target: Answer, question: Question): IO[OracleAnswer] =
    call.call(ModelTruthOracle.systemPrompt(target), question.text).map {
      case Right(dto) => ModelTruthOracle.parse(dto.answer)
      case Left(_) => OracleAnswer.Unknown
    }

object ModelTruthOracle:

  private def systemPrompt(target: Answer): String =
    s"""You are the ground-truth oracle for a game of twenty questions.
       |The hidden thing is EXACTLY: ${target.value}.
       |A player will ask a yes/no question about the hidden thing. Answer STRICTLY about whether
       |"${target.value}" has the property asked, using only well-known facts about "${target.value}".
       |Put exactly one word in the "answer" field: yes, no, or unknown. Use "unknown" only when the
       |property genuinely does not apply or is indeterminate for "${target.value}" — never as a hedge.""".stripMargin

  /** Fail-closed parse of the model's answer token to an [[OracleAnswer]]; anything unrecognized is
    * a gap.
    */
  def parse(raw: String): OracleAnswer =
    Option(raw).map(_.trim.toLowerCase) match
      case Some("yes") => OracleAnswer.Yes
      case Some("no") => OracleAnswer.No
      case _ => OracleAnswer.Unknown
