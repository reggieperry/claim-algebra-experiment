---
paths:
  - "**/*.scala"
  - "**/*.sc"
  - "build.sbt"
---

# Scala LLM boundary (structured output)

The typed contract between the experiment and the model — the single place the harness's reliability is bought, and the only place the pure algebra touches the outside world. Sources: the Anthropic Java SDK (`com.anthropic:anthropic-java`), reached over JVM interop; the Anthropic Messages API structured-outputs documentation (`outputConfig`, the JSON-schema limitations); and cats-effect `IO` for the effect boundary. The model is Claude, called through the official Java SDK; the model id is configured per node, defaulting to `claude-opus-4-8`.

> See `scala-types.md` for the output as a typed contract (the case class the schema is derived from), `scala-concurrency.md` for wrapping the blocking SDK call in `IO` and putting a timeout on it, `scala-errors.md` for surfacing validation failures as typed values rather than thrown exceptions, `scala-modules.md` for keeping the SDK off the algebra's dependency surface, and `craft-abstraction.md` for the schema as a specification the call is written against.

> **Verify SDK specifics against the version resolved by `sbt`.** The Java SDK evolves and the JVM-interop surface is verbose; treat the method names below as a guide and confirm them against the resolved artifact (`sbt "show dependencyTree"`, the Javadoc, or the SDK README) before relying on them — do not compose interop calls from memory. `outputConfig(Class[T])`, `StructuredMessage[T]`, and `AnthropicOkHttpClient.fromEnv()` are the names this rule turns on; the rest of the builder surface shifts between releases.

## The schema is the contract, and it is language-independent

- **The structured-output schema — not the Scala type, not the Java class — is what this rests on.** The experiment's whole claim is that a typed contract on the wire changes outcomes; the guardrail therefore lives at the JSON schema the model is constrained to, which is independent of Scala, Java, or any host language. Define one type as the single source of truth for each LLM output and derive the wire schema and the parsed result from it, so they cannot drift.
- **One bounded call, one typed result.** A focused node makes a narrow, structured, validated call — no open-ended agent loop, no unbounded tool surface. The leverage over an open-ended Claude Code session is exactly this narrowing; keep the prompt and rubric fed whole (do not pre-digest them) and keep the schema as the thin typed boundary.
- **Feed the schema from a Java class the SDK can reflect.** The Java SDK derives the JSON schema from a class passed to `outputConfig(Class[T])` and returns a `StructuredMessage[T]`. Scala 3 `case class`es work as that carrier, but the SDK uses Jackson reflection — declare the carrier as a **top-level** class (not a nested or local one) and keep its fields to types Jackson maps cleanly. Treat that class as a boundary adapter, converted to a domain type immediately; the algebra core never imports it.

```scala
// The carrier IS the schema and the contract. A top-level class the Java SDK can reflect.
// Keep it at the boundary; convert to the domain Verdict before it reaches the algebra.
final case class VerdictDto(
    decision: String,        // constrained to a closed set — validate after parse
    reasons: java.util.List[String],
    severity: Int
)
```

## Bound everything, then validate again at the boundary

- **The schema constrains generation; it does not constrain a misbehaving model.** Constrain the wire schema where the SDK allows it — `enum` / `const` on closed sets, required vs optional fields, `additionalProperties: false` (the SDK adds this to every derived schema). But the structured-output schema does **not** enforce string-length, item-count, or numeric range: `minLength`/`maxLength`, `minItems` beyond 0/1, and `minimum`/`maximum`/`multipleOf` are stripped from the wire schema and are not enforced by the model. Recursive schemas and complex enum members are unsupported — keep the carrier flat.
- **Re-validate every parsed output against a total Scala check before the algebra trusts it.** Layer the bounds the schema cannot carry — closed-set membership, list size, numeric range, cross-field rules — as a pure validating function from the boundary DTO to the domain type. This is the deterministic replica of the constraints JSON Schema dropped, and it is where the experiment's determinism is bought: the same output must always validate to the same domain value. Return the result as `Either` / `Validated`, never by throwing.

```scala
import cats.data.ValidatedNec
import cats.syntax.all.*

enum Decision:
  case Pass, Block

// Total: a malformed model output becomes an accumulated error, not an exception.
def validate(dto: VerdictDto): ValidatedNec[String, Verdict] =
  val decision = dto.decision match
    case "pass"  => Decision.Pass.validNec
    case "block" => Decision.Block.validNec
    case other   => s"decision not in {pass, block}: $other".invalidNec
  val reasons =
    val rs = dto.reasons.asScala.toList
    if rs.nonEmpty && rs.sizeIs <= 10 then rs.validNec
    else s"reasons must hold 1..10 items, got ${rs.size}".invalidNec
  val severity =
    if (0 to 5).contains(dto.severity) then dto.severity.validNec
    else s"severity out of range 0..5: ${dto.severity}".invalidNec
  (decision, reasons, severity).mapN(Verdict.apply)
```

## Calling the model — a thin Scala facade over the Java SDK

- **Wrap the call in one `LlmCall` facade and keep the SDK behind it.** The Java SDK's builder surface (`MessageCreateParams.builder()`, `StructuredMessage[T]`, `java.util.List`, `CompletableFuture`) is Java-shaped and stays at the boundary. Expose one Scala-idiomatic method that takes the prompt and the carrier class and returns `IO[Either[CallError, A]]`; every node calls that, never the SDK directly. (`scala-modules.md` — the SDK is a boundary dependency, not an algebra dependency.)
- **Construct one client, share it, and run the blocking call on a blocking pool.** `AnthropicOkHttpClient.fromEnv()` builds a client with its own connection and thread pools — build it once at the edge, not per call. The synchronous `client.messages().create(params)` blocks; lift it with `IO.blocking(...)` (or the SDK's `async()` client adapted to `IO`) so it doesn't starve the cats-effect compute pool. (`scala-concurrency.md`.)
- **Put a timeout on every call and set retries explicitly.** The per-call latency and cost budget belong to the experiment, not the SDK default. Bound the `IO` with `.timeout(d)` and configure the client's `maxRetries` and request `timeout` through the builder rather than relying on the 10-minute default. A node that can hang has no place in a deterministic trial run.

```scala
import cats.effect.IO
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.{MessageCreateParams, Model}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

final class LlmCall(client: com.anthropic.client.AnthropicClient):
  // One bounded, structured, validated call. A is the domain type; Dto is its boundary carrier.
  def structured[Dto, A](
      prompt: String,
      carrier: Class[Dto],
      validate: Dto => Either[String, A],
      timeout: FiniteDuration = 60.seconds
  ): IO[Either[String, A]] =
    val params = MessageCreateParams.builder
      .model(Model.CLAUDE_OPUS_4_8)
      .maxTokens(1024L)
      .outputConfig(carrier)      // the schema is derived here — confirm the name against the resolved SDK
      .addUserMessage(prompt)
      .build()
    IO.blocking(client.messages().create(params))
      .timeout(timeout)
      .map(parseCarrier[Dto])     // pull the typed value out of StructuredMessage[Dto]
      .map(_.flatMap(validate))   // then the total Scala re-validation
      .handleError(t => Left(s"llm call failed: ${t.getMessage}"))
```

## Determinism and seeding — the experiment depends on it

- **Pin the model id and every sampling lever the SDK exposes; record them in the trial record.** The headline metric is a query over a fixed trial store; a silently floating model id or token budget makes two runs incomparable. Set the model id explicitly (`claude-opus-4-8` unless a node configures otherwise), hold `maxTokens` and any effort setting fixed per arm, and write the resolved values into each `{fault_id, …}` record so a trial is reproducible from its own row. Note that the API does not promise bit-identical output even at fixed settings — determinism here means *fixed, recorded inputs and a mechanical grader*, not a deterministic model.
- **Never put an LLM judge on the headline number.** Grading of the confidently-wrong-at-signature flag is mechanical, over the structured record — the model is the system under test, not the scorer. An `LlmCall` belongs in a node, never in the grader.

## Auth and prompt safety

- **API key only — never subscription OAuth.** Configure the client from the environment (`AnthropicOkHttpClient.fromEnv()` reads `ANTHROPIC_API_KEY`) or the `apiKey` builder setter; do not wire an OAuth/subscription token path. This is a settled decision for the coded harness, distinct from interactive Claude Code sessions. Keep the key in the environment, never in source, a test fixture, or a committed config.
- **Never interpolate untrusted content into the system prompt.** Corpus text, a faulted leaf's payload, and any upstream node's output are untrusted data — they go in a user-role message, never spliced into the system prompt or the rubric. The system prompt and rubric are fixed, trusted, and fed verbatim; mixing data into them both poisons the cache prefix and opens a prompt-injection seam that would corrupt the very fault key the experiment seals away.
