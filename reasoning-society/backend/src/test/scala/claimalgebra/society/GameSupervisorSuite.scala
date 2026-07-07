package claimalgebra.society

import cats.effect.{Deferred, IO, Ref, Resource}
import cats.syntax.all.*
import munit.CatsEffectSuite

/** The single-game supervisor's restart mechanics, proven on a SYNTHETIC game program
  * (Deferred/Ref, no real actors, no sleep) so the cancel → reset → fork ordering, the no-stacking
  * guarantee, and the teardown-on-cancel signal are asserted deterministically. The end-to-end
  * teardown of REAL actors over the shared transport is exercised in
  * [[GameSupervisorSocietySuite]].
  *
  * The synthetic game acquires a `Resource` (the stand-in for the game's ActorSystem) that:
  *   - on ACQUIRE records a `start-N` marker in the shared "transport log", bumps the in-flight
  *     count (tracking its running peak), and completes the per-restart "reached in-flight" signal;
  *   - on RELEASE records a `release-N` marker and drops the in-flight count — the completed-on-
  *     release signal that proves cancellation tore the game down (no leak).
  *
  * Then it blocks on `IO.never`, so the game stays running until the supervisor cancels it — which
  * is exactly when the release finalizer fires. `resetState` clears the shared log, standing in for
  * the real replay-log clear + oracle reset.
  */
class GameSupervisorSuite extends CatsEffectSuite:

  final private case class Harness(
      games: GameSupervisor,
      log: Ref[IO, Vector[String]], // the shared transport log resetState clears
      released: Ref[IO, Int], // completed-on-release count — teardown-on-cancel witness
      inFlight: Ref[IO, Int], // game bodies currently past acquire (running)
      maxInFlight: Ref[IO, Int], // the peak of `inFlight` — 1 iff no two games ever stacked
      starts: Ref[IO, Int], // total game-body acquisitions
      nextStarted: Ref[IO, Option[Deferred[IO, Unit]]] // a per-restart "reached in-flight" signal
  ):
    /** Restart and deterministically await the fresh game reaching its in-flight region — no sleep.
      * (Sequential-only: it relies on there being no other game racing for the single signal.)
      */
    def restartAndAwaitStart: IO[Unit] =
      for
        d <- Deferred[IO, Unit]
        _ <- nextStarted.set(Some(d))
        _ <- games.restart
        _ <- d.get
      yield ()

  private def harness: IO[Harness] =
    for
      log <- Ref[IO].of(Vector.empty[String])
      released <- Ref[IO].of(0)
      inFlight <- Ref[IO].of(0)
      maxInFlight <- Ref[IO].of(0)
      starts <- Ref[IO].of(0)
      nextStarted <- Ref[IO].of(Option.empty[Deferred[IO, Unit]])
      playGame = Resource
        .make(
          for
            n <- starts.updateAndGet(_ + 1)
            _ <- log.update(_ :+ s"start-$n")
            live <- inFlight.updateAndGet(_ + 1)
            _ <- maxInFlight.update(_ max live)
            sig <- nextStarted.getAndSet(None)
            _ <- sig.traverse_(_.complete(()).void)
          yield n
        )(n => released.update(_ + 1) *> inFlight.update(_ - 1) *> log.update(_ :+ s"release-$n"))
        .surround(IO.never[Outcome])
      resetState = log.set(Vector.empty)
      games <- GameSupervisor.make(playGame, resetState)
    yield Harness(games, log, released, inFlight, maxInFlight, starts, nextStarted)

  // Poll the in-flight count with a fiber yield (no sleep) until it settles at `n`.
  private def awaitInFlight(h: Harness, n: Int): IO[Unit] =
    h.inFlight.get.flatMap {
      case m if m == n => IO.unit
      case _ => IO.cede *> awaitInFlight(h, n)
    }

  test("restart forks exactly one running game over a cleared log") {
    harness
      .flatMap { h =>
        (for
          _ <- h.restartAndAwaitStart
          log <- h.log.get
          inFlight <- h.inFlight.get
          starts <- h.starts.get
          released <- h.released.get
        yield
          assertEquals(starts, 1, clue("exactly one game body ran"))
          assertEquals(inFlight, 1, clue("exactly one game is running"))
          assertEquals(released, 0, clue("nothing was torn down — the game is still alive"))
          assertEquals(log, Vector("start-1"), clue("the fresh game emitted over an empty log"))
        )
          .guarantee(h.games.shutdown)
      }
  }

  test(
    "a second restart cancels the first: one running game, prior torn down, log reset (no stack)"
  ) {
    harness
      .flatMap { h =>
        (for
          _ <- h.restartAndAwaitStart // game 1
          _ <- h.restartAndAwaitStart // game 2 — cancels 1, clears the log, forks 2
          inFlight <- h.inFlight.get
          maxInFlight <- h.maxInFlight.get
          released <- h.released.get
          starts <- h.starts.get
          log <- h.log.get
        yield
          assertEquals(starts, 2, clue("two games were forked in total"))
          assertEquals(inFlight, 1, clue("only ONE game runs at a time — no stacking"))
          assertEquals(maxInFlight, 1, clue("the two games never overlapped"))
          assertEquals(
            released,
            1,
            clue("the first game's resource (its actors) was released on cancel — no leak")
          )
          // If the order were reset→cancel→fork, the first game's `release-1` marker would survive
          // after `start-2`; that it does not proves cancel(+teardown) ran BEFORE the reset, so the
          // old game can contaminate neither the shared log nor the new game.
          assertEquals(
            log,
            Vector("start-2"),
            clue("the log was reset before the second game — no leftover from the first")
          )
        )
          .guarantee(h.games.shutdown)
      }
  }

  test("concurrent restarts never stack two games and settle on one (mutex-serialized)") {
    harness
      .flatMap { h =>
        (for
          // Hammer the supervisor with concurrent restarts; the mutex must serialize them so no two
          // games are ever alive at once.
          _ <- List.fill(6)(h.games.restart).parSequence_
          _ <- awaitInFlight(h, 1) // the last-forked game reaches its in-flight region and stays
          inFlight <- h.inFlight.get
          maxInFlight <- h.maxInFlight.get
        yield
          assertEquals(inFlight, 1, clue("exactly one game runs after the storm"))
          assertEquals(
            maxInFlight,
            1,
            clue("no two games were ever in-flight at once — the mutex serialized the restarts")
          )
        )
          .guarantee(h.games.shutdown)
      }
  }

  test("shutdown cancels the running game and is idempotent") {
    harness
      .flatMap { h =>
        for
          _ <- h.restartAndAwaitStart
          _ <- h.games.shutdown
          _ <- h.games.shutdown // idempotent — no running game, a no-op
          inFlight <- h.inFlight.get
          released <- h.released.get
        yield
          assertEquals(inFlight, 0, clue("the game was torn down"))
          assertEquals(released, 1, clue("the game's resource was released exactly once"))
      }
  }
