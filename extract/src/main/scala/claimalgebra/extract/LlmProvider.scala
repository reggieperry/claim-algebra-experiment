package claimalgebra.extract

import cats.effect.{IO, Resource}
import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.Model
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel

/** A model provider behind the [[LlmCall]] facade: it builds a structured `LlmCall[A]` for any
  * boundary carrier, hiding which SDK (Anthropic vs OpenAI) and which client and model are in play.
  * This is what lets callers run Claude or GPT through the same code — the caller holds an
  * `LlmProvider`, not a provider-specific client. `name` records the provider and model for a
  * trial.
  *
  * Acquire one via the `Resource` factories (each owns its SDK client's lifecycle); the
  * experiment's arms stay Anthropic-only by construction and do not use this seam.
  */
trait LlmProvider:
  def name: String
  def llm[A](carrier: Class[A]): LlmCall[A]

object LlmProvider:

  /** Claude over an already-acquired client (lets a caller share one client across seams). */
  def anthropicOver(client: AnthropicClient, model: Model): LlmProvider =
    new LlmProvider:
      val name: String = s"anthropic:$model"
      def llm[A](carrier: Class[A]): LlmCall[A] = AnthropicLlmCall(client, carrier, model)

  /** GPT over an already-acquired client. */
  def openaiOver(client: OpenAIClient, model: ChatModel): LlmProvider =
    new LlmProvider:
      val name: String = s"openai:$model"
      def llm[A](carrier: Class[A]): LlmCall[A] = OpenAiLlmCall(client, carrier, model)

  /** Claude via the Anthropic SDK (default the dated Haiku 4.5). */
  def anthropic(model: Model = AnthropicLlmCall.DefaultModel): Resource[IO, LlmProvider] =
    AnthropicLlmCall.clientResource.map(anthropicOver(_, model))

  /** GPT via the OpenAI SDK (default the dated cheap tier). */
  def openai(model: ChatModel = OpenAiLlmCall.DefaultModel): Resource[IO, LlmProvider] =
    OpenAiLlmCall.clientResource.map(openaiOver(_, model))
