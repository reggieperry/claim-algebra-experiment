package claimalgebra.society

import cats.effect.{IO, IOApp, Ref, Resource}
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
      forks <- gameProgram(sink, oracle)
      memory <- Resource.eval(DefinitionMemory.make)
      gameCounter <- Resource.eval(Ref[IO].of(GameId.first))
      // Harvest the finishing game's established definitions off the working replay log; the
      // origin-preserving stamp/merge is DefinitionMemory.remember's (run after the awaited cancel).
      harvest = (_: GameId) => logRef.get.map(Definitions.established)
      // Clear the working scope a reset wipes: empty the replay log and drop every pending oracle
      // question, so no event or stale question id from a cancelled game survives.
      clearWorking = logRef.set(Vector.empty[Event]) *> oracle.reset
      // B2: reset the working scope TO A PREFIX (the SSE mirror reflects the rewound log) and reset
      // the oracle's pending map (else a question parked at rewind wedges the resumed game on a stale
      // Deferred the deterministically-minted re-asked id collides on).
      rewindWorking = (prefix: Vector[Event]) => logRef.set(prefix) *> oracle.reset
      games <- Resource.eval(
        GameSupervisor.make(
          forks.seeded,
          forks.resumed,
          logRef.get,
          memory,
          gameCounter,
          harvest,
          clearWorking,
          rewindWorking
        )
      )
      routes =
        SocietyRoutes(topic, logRef, oracle, games.newGame, games.fullReset, games.rewindTo)
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(bindHost)
        .withPort(bindPort)
        .withHttpApp(routes.orNotFound)
        .build
    yield games).use { games =>
      IO.println(
        "reasoning-society transport listening on http://0.0.0.0:8080  " +
          "(GET /events, POST /answer, POST /start, POST /reset)"
      ) *> games.newGame *> IO.never
    }

  /** The two fork channels a game needs: `seeded` starts a fresh game seeded with recalled
    * definitions (New Game / Full Reset), `resumed` re-forks the SAME game over a rewind PREFIX
    * injected as the initial log (B2). Both share the cohort / model / oracle / sink / config.
    */
  final case class GameForks(
      seeded: List[Definition] => IO[Outcome],
      resumed: Vector[Event] => IO[Outcome]
  )

  /** Build the game fork channels, real or hermetic. Live acquires the shared Anthropic client as a
    * Resource (held for the server's lifetime so a New Game or a rewind can re-run); hermetic needs
    * no resource, so a re-run rebuilds fresh stub cursors.
    */
  private def gameProgram(sink: EventSink, oracle: Oracle): Resource[IO, GameForks] =
    if sys.env.contains("RUN_LIVE_SOCIETY") then live(sink, oracle)
    else Resource.pure(hermetic(sink, oracle))

  private def live(sink: EventSink, oracle: Oracle): Resource[IO, GameForks] =
    AnthropicLlmCall.clientResource.map { client =>
      val llm = AnthropicLlmCall(client, classOf[AgentMoveDto])
      val definer = AnthropicLlmCall(client, classOf[DefinitionDto])
      def play(seed: List[Definition], initial: LogState): IO[Outcome] =
        Society.play(
          AgentStrategy.cohort,
          _ => llm,
          oracle,
          sink,
          Society.defaultConfig,
          definerFor = _ => definer,
          seed = seed,
          initial = initial
        )
      GameForks(
        seeded = seed => play(seed, LogState.initial),
        resumed = prefix => play(Nil, LogState.resumed(prefix))
      )
    }

  private def hermetic(sink: EventSink, oracle: Oracle): GameForks =
    def play(seed: List[Definition], initial: LogState): IO[Outcome] =
      val config =
        SocietyConfig(maxRounds = 6, roundTimeout = 30.seconds, hardDeadline = 10.minutes)
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
        outcome <- Society.play(
          AgentStrategy.cohort,
          llmFor,
          oracle,
          sink,
          config,
          seed = seed,
          initial = initial
        )
      yield outcome
    GameForks(
      seeded = seed => play(seed, LogState.initial),
      resumed = prefix => play(Nil, LogState.resumed(prefix))
    )

  private val fallbackStub: LlmCall[AgentMoveDto] =
    new LlmCall[AgentMoveDto]:
      def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
        IO.pure(Right(StubLlm.pass))
