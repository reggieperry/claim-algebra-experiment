package claimalgebra.extract

import munit.CatsEffectSuite

/** A LIVE smoke test of the SDK adapter — it makes a real, BILLED Anthropic call, so the whole
  * suite is gated: ignored unless `RUN_LIVE_LLM` is set, which keeps `sbt check` hermetic. Run it
  * with:
  *
  * {{{RUN_LIVE_LLM=1 sbt "testOnly *LlmSmokeSuite"}}}
  *
  * (needs `ANTHROPIC_API_KEY` in the environment). It confirms the one thing the hermetic tests
  * cannot: the carrier's Jackson round-trip — the SDK derives a schema from [[ExtractionDto]] and
  * deserializes the response into it — and that a real structured call cites a span that grounds
  * and decodes to the expected figure. Grounding itself is already proven hermetically in
  * [[ExtractorSuite]], so this asserts the live boundary, not the algebra.
  */
class LlmSmokeSuite extends CatsEffectSuite:

  override def munitIgnore: Boolean = !sys.env.contains("RUN_LIVE_LLM")

  private val corpus =
    Corpus("The borrower reported Total debt of $1,234,567 as of the period end.")
  private val ask = "the company's total debt"

  test("a live structured call returns a parsed ExtractionDto whose cited span grounds") {
    AnthropicLlmCall.clientResource.use { client =>
      val llm = AnthropicLlmCall(client, classOf[ExtractionDto])
      val userMessage = s"$ask\n\nDocument:\n${corpus.text}"
      llm.call(Extractor.defaultRubric, userMessage).map {
        case Left(err) =>
          fail(
            s"live structured call did not return a DTO (Jackson round-trip / call failed): $err"
          )
        case Right(dto) =>
          val quote = dto.quote
          assert(
            corpus.containsFigure(quote),
            s"model cited a non-figure or hallucination: [$quote]"
          )
          assertEquals(
            Money.parse(quote).map(_.toCents),
            Some(123456700L),
            s"cited quote: [$quote]"
          )
      }
    }
  }
