package claimalgebra.extract

import cats.effect.IO
import claimalgebra.{Belnap, Kind, Testimony}
import munit.CatsEffectSuite

/** The con-channel extractor's grounding, proven hermetically: the corner it produces is decided by
  * GROUNDING the cited spans, not by the model's say-so. A grounded supersede span alone is the
  * clean supersession (True); a grounded withdrawal alongside it is the contested one (Glut);
  * nothing, or a hallucinated span that grounds nowhere, is the gap.
  */
class SupersessionExtractorSuite extends CatsEffectSuite:

  private val corpus = Corpus(
    """Balance sheet: total debt of $1,200,000.
      |Notes: total debt of $1,000,000. The balance-sheet figure has been superseded by the notes.
      |The restatement was subsequently withdrawn.""".stripMargin
  )

  // A neutral test-kind injected into the extractor, so the con-channel carries a kind κ̂ can read.
  // The extractor names no taxonomy of its own — the caller injects it (default `None`).
  private case object RetractionKind extends Kind

  private def stub(supersede: String, withdraw: String): SupersessionExtractor =
    val llm = new LlmCall[SupersessionDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, SupersessionDto]] =
        IO.pure(Right(new SupersessionDto(supersede, withdraw)))
    new SupersessionExtractor(
      llm,
      corpus,
      SupersessionExtractor.defaultRubric,
      Some(RetractionKind)
    )

  test("a grounded supersede span with no withdrawal is a clean supersession (corner True)") {
    stub("superseded by the notes", "").extract.map { t =>
      assertEquals(Testimony.corner(t), Belnap.True)
    }
  }

  test(
    "a grounded supersede span and a grounded withdrawal is a contested supersession (corner Glut)"
  ) {
    stub("superseded by the notes", "The restatement was subsequently withdrawn").extract.map { t =>
      assertEquals(Testimony.corner(t), Belnap.Glut)
    }
  }

  test("a contested supersession carries the injected kind on its con-channel — κ̂ reads it") {
    stub("superseded by the notes", "The restatement was subsequently withdrawn").extract.map { t =>
      assertEquals(Testimony.conflictKinds(t), Set[Kind](RetractionKind))
    }
  }

  test("no cited spans is the gap — no supersession") {
    stub("", "").extract.map(t => assertEquals(Testimony.corner(t), Belnap.Gap))
  }

  test("a hallucinated supersede span that grounds nowhere is rejected — the gap, fail-closed") {
    stub("merged with a subsidiary that does not appear", "").extract.map { t =>
      assertEquals(Testimony.corner(t), Belnap.Gap)
    }
  }

  test("a call error is the gap — no supersession, never a guessed corner") {
    val failing = new LlmCall[SupersessionDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, SupersessionDto]] =
        IO.pure(Left(CallError.Timeout))
    new SupersessionExtractor(failing, corpus, SupersessionExtractor.defaultRubric).extract
      .map(t => assertEquals(Testimony.corner(t), Belnap.Gap))
  }
