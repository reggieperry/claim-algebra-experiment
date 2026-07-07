package claimalgebra.society

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, CallError, LlmCall}

import scala.concurrent.duration.*

/** The launchable entry point: wire the [[ActorSystem]], the diverse [[AgentActor]] cohort, the
  * [[LogActor]], and an [[Oracle]]; play one game; emit the ordered event log to stdout (the SSE
  * transport is slice 3). Real Haiku is gated behind `RUN_LIVE_SOCIETY` so the DEFAULT run is
  * hermetic and free — a canned stub cohort with a scripted oracle that plays a short game to a
  * signature, so `sbt run` demonstrates the machinery without a billed call.
  *
  * {{{
  *   sbt "reasoningSociety/run"                    # hermetic canned demo (default)
  *   RUN_LIVE_SOCIETY=1 sbt "reasoningSociety/run" # live Haiku agents + console oracle (billed)
  * }}}
  * The live path needs `ANTHROPIC_API_KEY` in the environment (API-key auth only).
  */
object RunSociety extends IOApp.Simple:

  def run: IO[Unit] =
    if sys.env.contains("RUN_LIVE_SOCIETY") then live else hermetic

  /** Live: one shared Anthropic call for every agent (the diversity is in the system prompts, not
    * the client), a console oracle, the default config.
    */
  private def live: IO[Unit] =
    AnthropicLlmCall.clientResource.use { client =>
      val llm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      IO.println(
        "Live reasoning society (Haiku). Think of something; answer the questions y/n/?."
      ) *>
        Society
          .play(
            AgentStrategy.cohort,
            _ => llm,
            Oracle.console,
            EventSink.stdout,
            Society.defaultConfig
          )
          .flatMap(report)
    }

  /** Hermetic: a canned three-agent cohort that narrows to "apple", the gate abstaining at one
    * backer (unconfirmed) then signing at two — the same event shape the tests pin.
    */
  private def hermetic: IO[Unit] =
    val demoConfig =
      SocietyConfig(maxRounds = 6, roundTimeout = 2.seconds, hardDeadline = 30.seconds)
    val scripts: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
      "driller" -> List(Right(StubLlm.move("assert", "apple", "a common fruit"))),
      "splitter" -> List(
        Right(StubLlm.move("propose", "", "Is it a fruit?")),
        Right(StubLlm.move("corroborate", "apple", "agreed"))
      ),
      "skeptic" -> List(Right(StubLlm.pass))
    )
    for
      stubs <- scripts.toList.traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      llmById = stubs.toMap
      oracle <- Oracle.scripted(List(OracleAnswer.Yes))
      llmFor = (id: AgentId) => llmById.getOrElse(id.value, fallbackStub)
      _ <- IO.println(
        "Hermetic reasoning-society demo (no live model). Set RUN_LIVE_SOCIETY=1 for Haiku.\n"
      )
      outcome <- Society.play(AgentStrategy.cohort, llmFor, oracle, EventSink.stdout, demoConfig)
      _ <- report(outcome)
    yield ()

  private val fallbackStub: LlmCall[AgentMoveDto] =
    new LlmCall[AgentMoveDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
        IO.pure(Right(StubLlm.pass))

  private def report(outcome: Outcome): IO[Unit] = outcome match
    case Outcome.Signed(answer) => IO.println(s"\n== SIGNED: ${answer.value} ==")
    case Outcome.Inconclusive =>
      IO.println("\n== INCONCLUSIVE (no signature within the round budget) ==")
