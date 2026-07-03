package claimalgebra.extract

import cats.effect.IO
import claimalgebra.*

/** The con-channel extractor — the supersession counterpart of [[Extractor]]. It makes one bounded,
  * structured call asking the model to cite a span asserting the balance-sheet figure is superseded
  * by the notes, and (separately) a span withdrawing or disputing that supersession. Each cited
  * span is GROUNDED verbatim against the corpus before it is admitted, exactly as a figure is — a
  * hallucinated citation is dropped, fail-closed.
  *
  * The result is a value-empty meta-claim whose Belnap CORNER carries the signal: a grounded
  * supersede span with no withdrawal is `True` (a clean supersession), both grounded is `Glut` (a
  * contested one), neither is the gap `N`. The topology reads only the corner ([[CreditNetwork]]
  * resolves a `True` supersession and falls through on a `Glut`), so this never puts a value on the
  * wire — only the structural signal a value-only reader cannot represent.
  */
final class SupersessionExtractor(llm: LlmCall[SupersessionDto], corpus: Corpus, rubric: String):

  /** Ground the two cited spans into the supersession meta-claim, in `IO`. Any call error is the
    * gap (no supersession), fail-closed.
    */
  def extract: IO[Testimony[Unit]] =
    llm.call(rubric, s"Document:\n${corpus.text}").map {
      case Right(dto) => ground(dto)
      case Left(_) => SupersessionExtractor.none
    }

  /** The mechanical grounding, pure and total: the supersede span (if it grounds) is the
    * pro-channel support, the withdrawal span (if it grounds) the con-channel. An ungrounded or
    * absent span contributes nothing, so a citation that points nowhere cannot manufacture a
    * corner.
    */
  private def ground(dto: SupersessionDto): Testimony[Unit] =
    Testimony.single((), groundSpan(dto.supersedeSpan), groundSpan(dto.withdrawSpan))

  // A supersession span is TEMPORAL-RETRACTION evidence (G3): a restatement asserted or withdrawn.
  // So a contested supersession's con-channel carries that kind, and κ̂ routes the conflict to the
  // deal lead (Routing). The kind is a pure decoration — it does not change the grounded corner.
  private def groundSpan(span: String): Prov =
    Option(span)
      .filter(corpus.containsSpan)
      .flatMap(s => Lineage.from(s, Kind.TemporalRetraction))
      .fold(Prov.zero)(Prov.single)

object SupersessionExtractor:

  /** No supersession on either channel — the gap `N`. Value-less, so keyed by the unit. */
  val none: Testimony[Unit] = Testimony.gap

  /** The fixed, trusted system prompt. The corpus rides the user message; never interpolated here.
    */
  val defaultRubric: String =
    """You read a financial document and report whether one figure has been SUPERSEDED by another.
      |Cite the exact span — verbatim, with no surrounding words — that states the balance-sheet total
      |debt has been superseded or restated by the notes, if any (the "supersedeSpan"); and separately
      |cite the exact span that WITHDRAWS or DISPUTES that supersession, if any (the "withdrawSpan").
      |Do not infer or paraphrase; quote each span as it appears. If there is no such span, cite
      |nothing for it.""".stripMargin
