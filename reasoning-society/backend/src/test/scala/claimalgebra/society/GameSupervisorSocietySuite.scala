package claimalgebra.society

import cats.effect.std.Supervisor
import cats.effect.{Deferred, IO, Ref}
import cats.syntax.all.*
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

import scala.concurrent.duration.*

/** The reset mechanics end to end over REAL actors and the real shared transport — the same
  * [[GameSupervisor]] + [[TopicSink]] + [[RemoteOracle]] wiring `RunServer` uses. It proves the
  * properties the synthetic [[GameSupervisorSuite]] cannot: that cancelling an in-flight game tears
  * down its real [[ActorSystem]] (LogActor + AgentActors) so it can post nothing more, and that a
  * New Game refills the shared replay log from `seq 1` with no event from the cancelled game.
  *
  * The scripted cohort proposes a question and then PARKS on the [[RemoteOracle]] (which never
  * auto-answers), so each game is genuinely in flight — mid-park — when the next New Game cancels
  * it. A signalling sink completes a `Deferred` when a `QuestionAsked` is emitted, so the test
  * awaits the parked state deterministically (no sleep).
  */
class GameSupervisorSocietySuite extends CatsEffectSuite:

  private val config =
    SocietyConfig(maxRounds = 6, roundTimeout = 1.hour, hardDeadline = 1.minute)

  // The round timeout never fires; rounds close on full-cohort report (deterministic).
  private def noTimeout(supervisor: Supervisor[IO]): Scheduler = new Scheduler:
    def submit(action: IO[Unit]): IO[Unit] = supervisor.supervise(action).void
    def arm(delay: FiniteDuration)(action: IO[Unit]): IO[Unit] = IO.unit

  // The splitter proposes a bisecting question; the others pass. Round one completes, the gate
  // abstains (no hypothesis), the question is asked, and the game parks on the silent oracle.
  private val proposeThenPark: Map[String, List[Either[CallError, AgentMoveDto]]] = Map(
    "splitter" -> List(Right(StubLlm.move("propose", "", "Is it alive?"))),
    "driller" -> List(Right(StubLlm.pass)),
    "skeptic" -> List(Right(StubLlm.pass))
  )

  private val passStub: LlmCall[AgentMoveDto] = new LlmCall[AgentMoveDto]:
    def call(systemPrompt: String, userMessage: String): IO[Either[CallError, AgentMoveDto]] =
      IO.pure(Right(StubLlm.pass))

  private def scriptedLlms(
      scripts: Map[String, List[Either[CallError, AgentMoveDto]]]
  ): IO[AgentId => LlmCall[AgentMoveDto]] =
    scripts.toList
      .traverse((id, script) => StubLlm.scripted(script).map(id -> _))
      .map(pairs => pairs.toMap)
      .map(byId => (agent: AgentId) => byId.getOrElse(agent.value, passStub))

  private def isQuestionAsked(event: Event): Boolean = event match
    case _: Event.QuestionAsked => true
    case _ => false

  /** A sink that appends to the shared replay log AND republishes to the Topic (as [[TopicSink]]
    * does), then fires the current "a question was asked" signal — so the test can await the parked
    * state without sleeping.
    */
  private def signallingSink(
      base: EventSink,
      askedSignal: Ref[IO, Option[Deferred[IO, Unit]]]
  ): EventSink = event =>
    base.emit(event) *> (
      if isQuestionAsked(event) then
        askedSignal.getAndSet(None).flatMap(_.traverse_(_.complete(()).attempt.void))
      else IO.unit
    )

  test(
    "a New Game tears the old game's actors down and refills the shared log from seq 1 (no leak)"
  ) {
    for
      topicAndSink <- TopicSink.make
      (base, _, logRef) = topicAndSink
      oracle <- RemoteOracle.make
      askedSignal <- Ref[IO].of(Option.empty[Deferred[IO, Unit]])
      sink = signallingSink(base, askedSignal)
      llmFor <- scriptedLlms(proposeThenPark)
      playSeeded = (seed: List[Definition]) =>
        Society.play(AgentStrategy.cohort, llmFor, oracle, sink, config, noTimeout, seed = seed)
      memory <- DefinitionMemory.make
      gameCounter <- Ref[IO].of(GameId.first)
      // The propose-then-park cohort establishes no definitions, so the harvest is empty and the
      // fresh game's seed is empty — game two refills the log from seq 1, byte-identical.
      harvest = (_: GameId) => logRef.get.map(Definitions.established)
      clearWorking = logRef.set(Vector.empty[Event]) *> oracle.reset
      games <- GameSupervisor.make(
        playSeeded,
        _ => IO.never[Outcome],
        IO.pure(Vector.empty[Event]),
        memory,
        gameCounter,
        harvest,
        clearWorking,
        _ => IO.unit
      )

      // Await a helper that installs a fresh signal, New-Games, and blocks until the new game parks.
      awaitAsked = (d: Deferred[IO, Unit]) => askedSignal.set(Some(d)) *> games.newGame *> d.get
      firstAsked <- Deferred[IO, Unit]
      _ <- awaitAsked(firstAsked) // game 1 runs to the parked state
      firstLog <- logRef.get

      secondAsked <- Deferred[IO, Unit]
      _ <- awaitAsked(secondAsked) // New Game: cancel game 1, clear the log, fork game 2 → it parks
      secondLog <- logRef.get
      _ <- games.shutdown
    yield
      // Game 1 emitted a contiguous opening from seq 1 and reached the asked-and-parked state.
      assertEquals(firstLog.map(_.seq), Vector(1, 2, 3), clue("game 1 numbered its own log from 1"))
      assert(firstLog.exists(isQuestionAsked), clue("game 1 asked its question and parked"))

      // The New Game cleared the shared log and the SECOND game refilled it from seq 1 — the
      // cancelled game's events are gone (else the log would be six events, or its seqs
      // non-contiguous), and its torn-down actors posted nothing into the new game.
      assertEquals(
        secondLog.map(_.seq),
        Vector(1, 2, 3),
        clue("the New Game reset the shared log and the fresh game refilled it from seq 1")
      )
      assertEquals(
        secondLog.count(isQuestionAsked),
        1,
        clue("exactly one game is producing events — no cross-game contamination")
      )
  }
