package claimalgebra.extract

import cats.effect.{IO, Resource}
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.{ChatCompletionCreateParams, StructuredChatCompletion}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** The live [[LlmCall]] backed by the official OpenAI Java SDK (`com.openai:openai-java`) — the
  * same model boundary as [[AnthropicLlmCall]], behind the same facade, so callers can run
  * GPT as well as Claude (and `SelfGrounder` becomes genuinely multi-provider). API-key auth only:
  * [[OpenAIOkHttpClient.fromEnv]] reads `OPENAI_API_KEY`, never a literal. The structured call
  * constrains the model to the carrier's JSON schema via `responseFormat(Class[A])` — OpenAI's
  * analogue of Anthropic's `outputConfig` — and parses the first choice's content into `A`; the
  * system prompt and user message ride the SDK's separate roles, so corpus data never reaches the
  * system prompt.
  *
  * Every failure becomes a typed [[CallError]] (the extractor turns each into a gap, fail-closed),
  * sanitized to a class name so no payload leaks (scala-errors.md). The blocking SDK call is lifted
  * with `IO.blocking` and bounded by a timeout (scala-concurrency.md). Hermetic stubs cover the
  * grounding; the live path is smoke-tested behind `RUN_LIVE_OPENAI`.
  */
final class OpenAiLlmCall[A](
    client: OpenAIClient,
    carrier: Class[A],
    model: ChatModel,
    maxTokens: Long,
    timeout: FiniteDuration
) extends LlmCall[A]:

  def call(systemPrompt: String, userMessage: String): IO[Either[CallError, A]] =
    val params = ChatCompletionCreateParams
      .builder()
      .model(model)
      .maxCompletionTokens(maxTokens)
      .addSystemMessage(systemPrompt) // the system role; corpus data rides the user message only
      .addUserMessage(userMessage)
      .responseFormat(carrier) // Class[A] turns the builder structured: StructuredChatCompletion[A]
      .build()
    IO.blocking(client.chat().completions().create(params))
      .timeout(timeout)
      .attempt
      .map {
        case Right(completion) => parse(completion)
        case Left(_: TimeoutException) => Left(CallError.Timeout)
        case Left(t) => Left(CallError.Transport(t.getClass.getSimpleName))
      }

  /** Pull the first choice's parsed content; a refusal or absent content is `Malformed`. */
  private def parse(completion: StructuredChatCompletion[A]): Either[CallError, A] =
    completion
      .choices()
      .asScala
      .iterator
      .flatMap(choice => choice.message().content().toScala)
      .nextOption()
      .toRight(CallError.Malformed("no structured content in OpenAI response"))

object OpenAiLlmCall:

  /** The cheap GPT tier, dated and pinned for reproducibility (mirrors the Haiku default). */
  val DefaultModel: ChatModel = ChatModel.GPT_5_4_MINI_2026_03_17

  /** Per-call bounds, held fixed and recorded per trial. More headroom than the Anthropic default
    * because GPT reasoning tokens count against `max_completion_tokens`; the experiment overrides
    * per arm and holds the budget identical across arms.
    */
  val DefaultMaxTokens: Long = 4096L
  val DefaultTimeout: FiniteDuration = 60.seconds

  /** The retry budget, set EXPLICITLY rather than riding the SDK default, so the lever is recorded
    * and a run is reproducible from its own inputs (scala-llm.md).
    */
  val DefaultMaxRetries: Int = 2

  /** One shared client from the environment (`OPENAI_API_KEY`), released on exit — acquire and
    * release as a `Resource` on the blocking pool (scala-concurrency.md), with the retry budget set
    * explicitly on the builder.
    */
  val clientResource: Resource[IO, OpenAIClient] =
    Resource.make(
      IO.blocking(OpenAIOkHttpClient.builder().fromEnv().maxRetries(DefaultMaxRetries).build())
    )(c => IO.blocking(c.close()))

  /** A structured caller for carrier `A` over a shared client, defaulting to the cheap GPT tier and
    * per-call bounds.
    */
  def apply[A](
      client: OpenAIClient,
      carrier: Class[A],
      model: ChatModel = DefaultModel,
      maxTokens: Long = DefaultMaxTokens,
      timeout: FiniteDuration = DefaultTimeout
  ): OpenAiLlmCall[A] =
    new OpenAiLlmCall[A](client, carrier, model, maxTokens, timeout)
