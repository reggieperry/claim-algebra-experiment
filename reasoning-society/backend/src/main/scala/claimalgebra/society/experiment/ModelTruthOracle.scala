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

  private def systemPrompt(target: Answer): String = systemPromptFor(target.value)

  /** The rubric with the target spliced in at each `$t` site. Factored out so the stamp can hash a
    * target-INDEPENDENT template (`stampTemplate`) rather than a per-target render.
    */
  private def systemPromptFor(t: String): String =
    s"""You are the ground-truth oracle for a game of twenty questions.
       |The hidden thing is a typical, representative $t.
       |A player asks a yes/no question about it. Answer about a typical $t: reply "yes"
       |if a typical $t has the property, "no" if it does not.
       |Category and kind questions ALWAYS have a definite answer — whether it is alive, a living
       |organism, an animal, a plant, a mammal, man-made, a tool, bigger than a breadbox, and the like.
       |Answer those "yes" or "no", never "unknown"; a dog is alive, a hammer is not an animal.
       |Reserve "unknown" ONLY for a property that genuinely varies from one $t to another
       |(an exact color, an exact weight) with no typical value.
       |Put exactly one word in the "answer" field: yes, no, or unknown.""".stripMargin

  /** A target-independent fingerprint of the oracle rubric for the config-surface stamp (the target
    * placeholder is factored out, so the stamp tracks a rubric edit, not the target).
    */
  val stampTemplate: String = systemPromptFor("<TARGET>")

  /** Fail-closed parse of the model's answer token to an [[OracleAnswer]]; anything unrecognized is
    * a gap.
    */
  def parse(raw: String): OracleAnswer =
    Option(raw).map(_.trim.toLowerCase) match
      case Some("yes") => OracleAnswer.Yes
      case Some("no") => OracleAnswer.No
      case _ => OracleAnswer.Unknown
