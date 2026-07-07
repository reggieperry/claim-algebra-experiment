package claimalgebra.society

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, CallError, LlmCall}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*

import scala.concurrent.duration.*

/** The launchable transport server: an ember HTTP server that streams the live event log over SSE
  * and accepts the human oracle's answers over `POST /answer`, plus one game wired to a
  * Topic-backed sink and the [[RemoteOracle]]. Bound to `0.0.0.0:8080` for headless/remote reach.
  *
  * Real Haiku agents are gated behind `RUN_LIVE_SOCIETY` (the live path needs `ANTHROPIC_API_KEY`,
  * API-key auth only), so the DEFAULT run is hermetic and free — a canned stub cohort narrowing to
  * "apple" that streams events with no network, and whose one question waits for the browser to
  * answer over `POST /answer`.
  *
  * {{{
  *   sbt "reasoningSociety/runMain claimalgebra.society.RunServer"
  *   RUN_LIVE_SOCIETY=1 sbt "reasoningSociety/runMain claimalgebra.society.RunServer"   # billed
  *   # then: open http://localhost:8080/events (SSE); POST /answer {"questionId":"q1","answer":"yes"}
  * }}}
  */
object RunServer extends IOApp.Simple:

  private val bindHost = ipv4"0.0.0.0"
  private val bindPort = port"8080"

  def run: IO[Unit] =
    (for
      topicAndSink <- Resource.eval(TopicSink.make)
      (sink, topic, logRef) = topicAndSink
      oracle <- Resource.eval(RemoteOracle.make)
      playGame <- gameProgram(sink, oracle)
      // Reset the shared transport state a restart clears: empty the replay log and drop every
      // pending oracle question, so no event or stale question id from a cancelled game survives.
      resetState = logRef.set(Vector.empty[Event]) *> oracle.reset
      games <- Resource.eval(GameSupervisor.make(playGame, resetState))
      routes = SocietyRoutes(topic, logRef, oracle, games.restart)
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(bindHost)
        .withPort(bindPort)
        .withHttpApp(routes.orNotFound)
        .build
    yield games).use { games =>
      IO.println(
        "reasoning-society transport listening on http://0.0.0.0:8080  " +
          "(GET /events, POST /answer, POST /start)"
      ) *> games.restart *> IO.never
    }

  /** Build the "play one game" program, real or hermetic. Live acquires the shared Anthropic client
    * as a Resource (held for the server's lifetime so a restart can re-run); hermetic needs no
    * resource, so a re-run rebuilds fresh stub cursors.
    */
  private def gameProgram(sink: EventSink, oracle: Oracle): Resource[IO, IO[Outcome]] =
    if sys.env.contains("RUN_LIVE_SOCIETY") then live(sink, oracle)
    else Resource.pure(hermetic(sink, oracle))

  private def live(sink: EventSink, oracle: Oracle): Resource[IO, IO[Outcome]] =
    AnthropicLlmCall.clientResource.map { client =>
      val llm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      Society.play(
        AgentStrategy.cohort,
        _ => llm,
        oracle,
        sink,
        Society.defaultConfig,
        definerFor = _ => definer
      )
    }

  private def hermetic(sink: EventSink, oracle: Oracle): IO[Outcome] =
    val config = SocietyConfig(maxRounds = 6, roundTimeout = 30.seconds, hardDeadline = 10.minutes)
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
      llmFor = (id: AgentId) => llmById.getOrElse(id.value, fallbackStub)
      outcome <- Society.play(AgentStrategy.cohort, llmFor, oracle, sink, config)
    yield outcome

  private val fallbackStub: LlmCall[AgentMoveDto] =
    new LlmCall[AgentMoveDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
        IO.pure(Right(StubLlm.pass))
