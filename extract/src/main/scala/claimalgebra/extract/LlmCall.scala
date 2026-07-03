package claimalgebra.extract

import cats.effect.IO

/** The thin facade over one model call (scala-llm.md): a single bounded, structured call, with `A`
  * the parsed structured-output carrier. The system prompt is fixed and trusted; the user message
  * carries untrusted data (corpus text). They are SEPARATE parameters so data can never be spliced
  * into the system prompt — the prompt-injection seam scala-security.md closes.
  *
  * This is our own type, defined in our terms, so the extractor's grounding can be tested against a
  * deterministic stub (only mock what you own). The SDK-backed implementation is a boundary adapter
  * confirmed at first wiring (CLAUDE.md): the algebra never sees the SDK.
  */
trait LlmCall[A]:
  def call(systemPrompt: String, userMessage: String): IO[Either[CallError, A]]

/** Why a model call did not yield a usable structured result. The extractor treats every case as a
  * gap — fail-closed, never a guessed value. Carry no secret and no raw payload in the detail
  * (scala-errors.md): a sanitized, typed reason the pipeline can route and record.
  */
enum CallError:
  case Timeout
  case Transport(detail: String)
  case Malformed(detail: String)
