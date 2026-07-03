package claimalgebra.extract

import cats.effect.IO
import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.{
  CitationsConfigParam,
  ContentBlockParam,
  DocumentBlockParam,
  Message,
  MessageCreateParams,
  Model,
  TextBlockParam,
  TextCitation
}

import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

/** A 0-indexed character range into the document text we sent — the citation's location. */
final case class CharRange(start: Int, end: Int)

/** A claim the model made, paired with the verbatim span the platform cited for it: `value` is the
  * model's claim text, `citedText` is the exact source span (extracted by the API, so it cannot be
  * fabricated), `range` its char offsets. The harness's OWN type — the SDK `Message` is parsed into
  * this at the boundary and never reaches the algebra.
  */
final case class CitedClaim(value: String, citedText: String, range: CharRange)

/** The Anthropic Citations boundary — a NON-structured Messages call (a document block with
  * `citations: enabled`, plus the ask; no `outputConfig`), because citations and structured outputs
  * are incompatible (a 400). The raw `Message` is parsed into owned [[CitedClaim]]s here.
  * Claude-only, API-key auth via the shared client. Failures become typed [[CallError]]s, sanitized
  * to a class name; the blocking call is lifted with `IO.blocking` and bounded by a timeout
  * (scala-concurrency.md).
  */
final class AnthropicCitations(
    client: AnthropicClient,
    model: Model,
    maxTokens: Long,
    timeout: FiniteDuration
):

  /** Ask one item with citations enabled over `docText` (titled `docId`); return the cited claims.
    */
  def cite(
      rubric: String,
      ask: String,
      docText: String,
      docId: String
  ): IO[Either[CallError, List[CitedClaim]]] =
    val document = ContentBlockParam.ofDocument(
      DocumentBlockParam
        .builder()
        .textSource(docText)
        .citations(CitationsConfigParam.builder().enabled(true).build())
        .title(docId)
        .build()
    )
    val question = ContentBlockParam.ofText(TextBlockParam.builder().text(ask).build())
    val params = MessageCreateParams
      .builder()
      .model(model)
      .maxTokens(maxTokens)
      .system(rubric) // the fixed rubric; the untrusted document rides the user message
      .addUserMessageOfBlockParams(List(document, question).asJava)
      .build()
    // Parse INSIDE IO.blocking: the SDK's required-field accessors throw on a malformed wire field,
    // so a parse-throw must be captured by `.attempt` and mapped to a typed CallError (→ the B1 gap),
    // never escape as an uncaught IO crash.
    IO.blocking(AnthropicCitations.parse(client.messages().create(params)))
      .timeout(timeout)
      .attempt
      .map {
        case Right(claims) => Right(claims)
        case Left(_: TimeoutException) => Left(CallError.Timeout)
        case Left(t) => Left(CallError.Transport(t.getClass.getSimpleName))
      }

object AnthropicCitations:

  val DefaultMaxTokens: Long = 1024L
  val DefaultTimeout: FiniteDuration = 60.seconds

  def apply(
      client: AnthropicClient,
      model: Model = AnthropicLlmCall.DefaultModel,
      maxTokens: Long = DefaultMaxTokens,
      timeout: FiniteDuration = DefaultTimeout
  ): AnthropicCitations =
    new AnthropicCitations(client, model, maxTokens, timeout)

  /** Parse the `Message` into owned [[CitedClaim]]s: each text block's text is the claimed value,
    * paired with each of its CHARACTER-location citations (`cited_text` + range). Blocks without
    * citations, and non-character (page/block) citations, are dropped. An SDK required-field
    * accessor may throw on a malformed wire field, so this runs inside the caller's `IO.blocking`,
    * where such a throw is captured into a `CallError` (the gap), never an uncaught crash.
    */
  def parse(message: Message): List[CitedClaim] =
    message.content().asScala.toList.flatMap { block =>
      block.text().toScala.toList.flatMap { tb =>
        val value = tb.text()
        tb.citations().toScala.fold(List.empty[TextCitation])(_.asScala.toList).flatMap { tc =>
          tc.charLocation().toScala.map { cl =>
            CitedClaim(
              value,
              cl.citedText(),
              CharRange(cl.startCharIndex().toInt, cl.endCharIndex().toInt)
            )
          }
        }
      }
    }
