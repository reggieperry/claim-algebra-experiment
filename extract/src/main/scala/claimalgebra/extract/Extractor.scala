package claimalgebra.extract

import cats.effect.IO
import claimalgebra.*

/** The LLM-backed leaf node — the only place a guess can enter the pipeline
  * (wiring-the-falsification-rig.html Figure 3). It makes one bounded, structured call and then
  * GROUNDS the result mechanically: a value is admitted only if the model's cited span occurs
  * verbatim in the corpus AND decodes to a figure. The grade's corner is decided by grounding, not
  * by the model's self-reported confidence — there is no confidence on the wire to trust.
  *
  * A grounded extraction is a `True` leaf citing its span; everything else — a hallucinated
  * citation, a span that does not decode, or a failed call — is the gap `N`, fail-closed. The seam
  * is transport-agnostic: it depends on the [[LlmCall]] facade, so the same grounding is exercised
  * against a deterministic stub in tests and the live SDK adapter in a run.
  */
final class Extractor(llm: LlmCall[ExtractionDto], corpus: Corpus, rubric: String):

  /** Extract one figure: `ask` names what to find (trusted), the corpus is appended as untrusted
    * user data. Returns a grounded leaf or the gap, in `IO`.
    */
  def extract(ask: String): IO[Testimony[Money]] =
    llm.call(rubric, s"$ask\n\nDocument:\n${corpus.text}").map {
      case Right(dto) => ground(dto)
      case Left(_) => Extractor.gap // any call error is a gap — never a guessed value
    }

  /** The mechanical grounding, pure and total: the span must be a non-blank citation that occurs in
    * the corpus and decodes to a figure; only then is the value admitted, with the span as its
    * provenance. Any failure short-circuits to the gap.
    */
  private def ground(dto: ExtractionDto): Testimony[Money] =
    val grounded =
      for
        quote <- Option(dto.quote) // lift a possibly-null Jackson field at the boundary
        lineage <- Lineage.from(quote)
        if corpus.containsFigure(quote)
        money <- Money.parse(quote)
      yield Testimony.leaf(money, Prov.single(lineage))
    grounded.getOrElse(Extractor.gap)

object Extractor:

  /** The fail-closed result: no candidate on either channel — the gap `N`. */
  val gap: Testimony[Money] = Testimony.gap

  /** The fixed, trusted system prompt: how to extract and what to return. Held apart from the
    * corpus (which rides the user message), and fed whole — never pre-digested — so the narrowing
    * is the schema, not a truncated rubric. Untrusted corpus text is never interpolated here.
    */
  val defaultRubric: String =
    """You read one figure from a financial document and cite where you read it.
      |Return only the exact span — the figure as it appears in the document (for example
      |"$1,234,567"), with no surrounding words. Do not compute, infer, or reformat the figure;
      |quote it verbatim. If the document does not state the figure, cite nothing.""".stripMargin
