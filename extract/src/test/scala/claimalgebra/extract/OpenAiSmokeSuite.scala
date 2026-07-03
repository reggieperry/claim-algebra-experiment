package claimalgebra.extract

import munit.CatsEffectSuite

/** A LIVE smoke test of the OpenAI SDK adapter — a real, BILLED OpenAI call, so the whole suite is
  * gated: ignored unless `RUN_LIVE_OPENAI` is set, which keeps `sbt check` hermetic. Run it with:
  *
  * {{{RUN_LIVE_OPENAI=1 sbt "testOnly *OpenAiSmokeSuite"}}}
  *
  * (needs `OPENAI_API_KEY` in the environment). It confirms the one thing the hermetic tests
  * cannot: the carrier's structured round-trip — the SDK derives a JSON schema from
  * [[ExtractionDto]] via `responseFormat` and deserializes the response into it — and that a real
  * structured GPT call cites a span that grounds and decodes to the expected figure. The mirror of
  * [[LlmSmokeSuite]] for the GPT provider; grounding itself is proven hermetically elsewhere.
  */
class OpenAiSmokeSuite extends CatsEffectSuite:

  override def munitIgnore: Boolean = !sys.env.contains("RUN_LIVE_OPENAI")

  private val corpus =
    Corpus("The borrower reported Total debt of $1,234,567 as of the period end.")
  private val ask = "the company's total debt"

  test("a live OpenAI structured call returns a parsed ExtractionDto whose cited span grounds") {
    OpenAiLlmCall.clientResource.use { client =>
      val llm = OpenAiLlmCall(client, classOf[ExtractionDto])
      val userMessage = s"$ask\n\nDocument:\n${corpus.text}"
      llm.call(Extractor.defaultRubric, userMessage).map {
        case Left(err) =>
          fail(s"live OpenAI structured call did not return a DTO (schema / call failed): $err")
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
