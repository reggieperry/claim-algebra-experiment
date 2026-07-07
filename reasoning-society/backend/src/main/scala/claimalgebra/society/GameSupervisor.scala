package claimalgebra.society

import cats.effect.std.Mutex
import cats.effect.{Fiber, IO, Ref}
import cats.syntax.all.*

/** The single-game supervisor: at most ONE game fiber is alive at a time, and [[restart]] is the
  * one clean way to (re)start a game. It is what makes `POST /start` a real restart rather than a
  * second game forked onto the same event log.
  *
  * [[restart]] runs UNDER A MUTEX and, in order:
  *
  *   1. cancels the running game fiber and AWAITS its cancellation. The game's [[ActorSystem]] is a
  *      `Resource` scoped INSIDE [[Society.play]]'s `.use`, so cancelling the fiber unwinds that
  *      `use` and releases the actor `Supervisor` — which cancels every actor loop (the single
  *      [[LogActor]], the [[AgentActor]]s) and every supervised side fiber (the round-timeout and
  *      the oracle round-trip). No actor loop and no supervised fiber outlives the cancelled game
  *      (scala-concurrency.md — `Fiber.cancel` completes only after the finalizers run).
  *   2. resets the shared transport state (`resetState`, composed by the caller from the replay-log
  *      clear and the oracle's pending-map reset). This runs AFTER the cancellation completes, so a
  *      late in-flight emit cannot land after the clear, and no event or pending question from the
  *      old game can bleed into the new one.
  *   3. forks exactly ONE fresh game and stores its handle.
  *
  * The mutex serializes restarts: two `POST /start`s in flight cannot stack two games or race the
  * reset — the second waits for the first to install its game, then tears it down and installs its
  * own.
  */
final class GameSupervisor private (
    playGame: IO[Outcome],
    resetState: IO[Unit],
    running: Ref[IO, Option[Fiber[IO, Throwable, Outcome]]],
    lock: Mutex[IO]
):

  /** Cancel the running game (tearing its actors down), reset the shared state, then fork exactly
    * one fresh game. Serialized by the mutex; returns once the fresh game has been forked.
    */
  def restart: IO[Unit] =
    lock.lock.surround {
      for
        previous <- running.getAndSet(None)
        _ <- previous.traverse_(_.cancel) // await the old game's teardown — no leaked actor fibers
        _ <- resetState // the old game is gone: clear the log and reset the oracle
        fiber <- playGame.start // exactly one fresh game
        _ <- running.set(Some(fiber))
      yield ()
    }

  /** Cancel the running game and forget it — the graceful stop counterpart to [[restart]] (and the
    * hook a test uses to release its forked game). Idempotent; serialized by the same mutex.
    */
  def shutdown: IO[Unit] =
    lock.lock.surround(running.getAndSet(None).flatMap(_.traverse_(_.cancel)))

object GameSupervisor:

  /** Build a supervisor over the re-runnable `playGame` program and the `resetState` effect the
    * caller composes (the replay-log clear and the oracle reset). No game is running until the
    * first [[GameSupervisor.restart]].
    */
  def make(playGame: IO[Outcome], resetState: IO[Unit]): IO[GameSupervisor] =
    for
      running <- Ref[IO].of(Option.empty[Fiber[IO, Throwable, Outcome]])
      lock <- Mutex[IO]
    yield new GameSupervisor(playGame, resetState, running, lock)
