package claimalgebra.extract

import cats.effect.{IO, Resource}
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.{MessageCreateParams, Model, StructuredMessage}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** The live [[LlmCall]], backed by the official Anthropic Java SDK (`com.anthropic:anthropic-java`)
  * over JVM interop — the one boundary where the experiment touches the model, kept behind the
  * facade so the algebra never sees the SDK. API-key auth only: [[AnthropicOkHttpClient.fromEnv]]
  * reads `ANTHROPIC_API_KEY`, never a literal and never an OAuth token. The structured call
  * constrains the model to the carrier's schema and parses the response into `A`; the system prompt
  * and user message are passed as the SDK's separate roles, so corpus data never reaches the system
  * prompt.
  *
  * Every failure becomes a typed [[CallError]] (the extractor turns each into a gap, fail-closed),
  * and the detail is sanitized to a class name so no payload or secret leaks (scala-errors.md). The
  * blocking SDK call is lifted with `IO.blocking` and bounded by a timeout (scala-concurrency.md).
  *
  * Confirmed end-to-end against the live API with Haiku 4.5 (see `LlmSmokeSuite`): the structured
  * round-trip works with the boundary DTO declared as a JAVA class — a Scala case class hides its
  * field from the SDK's Jackson — and the system prompt must use the top-level `system` parameter,
  * not a system *message* (which the API rejects with a 400). The hermetic stub covers the
  * grounding; the live path is smoke-tested behind `RUN_LIVE_LLM`.
  */
final class AnthropicLlmCall[A](
    client: AnthropicClient,
    carrier: Class[A],
    model: Model,
    maxTokens: Long,
    timeout: FiniteDuration
) extends LlmCall[A]:

  def call(systemPrompt: String, userMessage: String): IO[Either[CallError, A]] =
    val params = MessageCreateParams
      .builder()
      .model(model)
      .maxTokens(maxTokens)
      .system(
        systemPrompt
      ) // the TOP-LEVEL system parameter; a system *message* is rejected with 400
      .addUserMessage(userMessage)
      .outputConfig(
        carrier
      ) // a Class[A] turns the builder structured: StructuredMessageCreateParams[A]
      .build()
    IO.blocking(client.messages().create(params))
      .timeout(timeout)
      .attempt
      .map {
        case Right(message) => parse(message)
        case Left(_: TimeoutException) => Left(CallError.Timeout)
        case Left(t) => Left(CallError.Transport(t.getClass.getSimpleName))
      }

  /** Pull the first structured text block's parsed value out of the message; absent ⇒ `Malformed`.
    */
  private def parse(message: StructuredMessage[A]): Either[CallError, A] =
    message
      .content()
      .asScala
      .iterator
      .flatMap(block => block.text().toScala)
      .map(textBlock => textBlock.text())
      .nextOption()
      .toRight(CallError.Malformed("no structured text block in response"))

object AnthropicLlmCall:

  /** The experiment default: the dated Haiku 4.5 id, pinned for reproducibility (scala-llm.md). */
  val DefaultModel: Model = Model.CLAUDE_HAIKU_4_5_20251001

  /** Per-call bounds, held fixed and recorded per trial; identical across the arms. */
  val DefaultMaxTokens: Long = 1024L
  val DefaultTimeout: FiniteDuration = 60.seconds

  /** The retry budget, set EXPLICITLY rather than riding the SDK default, so the lever is recorded
    * and a run is reproducible from its own inputs (scala-llm.md). Client-level, so it governs
    * every call through this shared client — including the Citations path.
    */
  val DefaultMaxRetries: Int = 2

  /** One shared client from the environment (`ANTHROPIC_API_KEY`), released on exit — acquire and
    * release as a `Resource` on the blocking pool (scala-concurrency.md), with the retry budget set
    * explicitly on the builder.
    */
  val clientResource: Resource[IO, AnthropicClient] =
    Resource.make(
      IO.blocking(AnthropicOkHttpClient.builder().fromEnv().maxRetries(DefaultMaxRetries).build())
    )(c => IO.blocking(c.close()))

  /** A structured caller for carrier `A` over a shared client, defaulting to the experiment's model
    * and per-call bounds.
    */
  def apply[A](
      client: AnthropicClient,
      carrier: Class[A],
      model: Model = DefaultModel,
      maxTokens: Long = DefaultMaxTokens,
      timeout: FiniteDuration = DefaultTimeout
  ): AnthropicLlmCall[A] =
    new AnthropicLlmCall[A](client, carrier, model, maxTokens, timeout)
