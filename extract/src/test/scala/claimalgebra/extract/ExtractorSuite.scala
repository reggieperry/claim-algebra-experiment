package claimalgebra.extract

import cats.effect.{IO, Ref}
import claimalgebra.*
import munit.CatsEffectSuite

/** The grounding seam, exercised against a deterministic stub `LlmCall` (only mock what you own).
  * The model's proposal is admitted ONLY when its cited span occurs in the corpus AS A WHOLE FIGURE
  * and decodes; every other case is the gap, fail-closed. The model reports no confidence, so a
  * citation that is a hallucination, a fragment of a larger figure, or undecodable still grounds to
  * a gap — grounding, not the model, decides the corner.
  */
class ExtractorSuite extends CatsEffectSuite:

  private val corpus = Corpus("Total debt of $1,234,567 and EBITDA of $400,000 were reported.")
  private val rubric = Extractor.defaultRubric

  private def stub(canned: Either[CallError, ExtractionDto]): LlmCall[ExtractionDto] =
    new LlmCall[ExtractionDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, ExtractionDto]] =
        IO.pure(canned)

  private def extractorReturning(canned: Either[CallError, ExtractionDto]): Extractor =
    new Extractor(stub(canned), corpus, rubric)

  test("a cited span that exists and decodes grounds to a True leaf citing that span") {
    extractorReturning(Right(ExtractionDto("$1,234,567"))).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.True)
      assertEquals(t.value.map(_.toCents), Some(123456700L))
      assertEquals(Lineage.from("$1,234,567").map(Prov.single), Some(t.provPro))
    }
  }

  test("a hallucinated citation — a span not in the corpus — grounds to the gap") {
    extractorReturning(Right(ExtractionDto("$9,999,999"))).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
      assertEquals(t.value, None)
    }
  }

  test("a fragment of a larger figure is not a whole-figure citation — it grounds to the gap") {
    // "234,567" is a substring of "$1,234,567" but not a self-contained figure; admitting it would
    // sign a wrong value the verifier could not catch.
    extractorReturning(Right(ExtractionDto("234,567"))).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
      assertEquals(t.value, None)
    }
  }

  test("a span that is in the corpus but does not decode to a figure grounds to the gap") {
    extractorReturning(Right(ExtractionDto("Total debt"))).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
      assertEquals(t.value, None)
    }
  }

  test("a blank citation grounds to the gap") {
    extractorReturning(Right(ExtractionDto("   "))).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
      assertEquals(t.value, None)
    }
  }

  test("a failed call is fail-closed — the gap, never a guessed value") {
    extractorReturning(Left(CallError.Timeout)).extract("total debt").map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
      assertEquals(t.value, None)
    }
  }

  test("the corpus rides the user message, never the system prompt (no injection seam)") {
    for
      box <- Ref[IO].of(("", ""))
      capturing = new LlmCall[ExtractionDto]:
        def call(systemPrompt: String, userMessage: String): IO[Either[CallError, ExtractionDto]] =
          box.set((systemPrompt, userMessage)).as(Right(ExtractionDto("$1,234,567")))
      _ <- new Extractor(capturing, corpus, rubric).extract("total debt")
      seen <- box.get
    yield
      assertEquals(seen._1, rubric, "system prompt is exactly the fixed rubric")
      assert(seen._2.contains(corpus.text), "the corpus is in the user message")
      assert(!seen._1.contains(corpus.text), "the corpus is never in the system prompt")
  }
