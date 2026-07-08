package claimalgebra.society

import cats.effect.std.Mutex
import cats.effect.{Fiber, IO, Ref}
import cats.syntax.all.*

/** The single-game supervisor: at most ONE game fiber is alive at a time. It exposes the two-tier
  * reset (two-tier-reset-design §Reset mechanics) — [[newGame]] and [[fullReset]] — over the shared
  * cancel → reset → fork spine that makes `POST /start` a real restart rather than a second game
  * forked onto the same event log. Both run UNDER A MUTEX, so two requests cannot stack two games
  * or race the reset.
  *
  * The two scopes:
  *   - WORKING memory (game-scoped) — the per-game event log and the oracle's pending map, wiped by
  *     [[clearWorking]] each reset.
  *   - PERSISTENT memory (session-scoped) — [[DefinitionMemory]], the established definitions. It
  *     survives a [[newGame]] (harvested from the finishing game, replayed into the next as the
  *     seed) and is cleared only on a [[fullReset]].
  *
  * The cancel → HARVEST → clear → fork order is the fail-closed spine (invariants 3, 4, 5):
  *   1. cancel the running game fiber and AWAIT its cancellation — the game's [[ActorSystem]] is a
  *      `Resource` scoped inside [[Society.play]]'s `.use`, so cancelling the fiber unwinds it and
  *      releases every actor loop and supervised side fiber. Nothing outlives the cancelled game
  *      (scala-concurrency.md — `Fiber.cancel` completes only after the finalizers run).
  *   2. HARVEST the finishing game's established definitions off the working log and merge them
  *      (origin-preserving) into persistent memory. This runs AFTER the awaited cancel — so no
  *      in-flight emit can race the read — and BEFORE the clear — so if [[harvest]] or
  *      [[DefinitionMemory.remember]] raises, [[newGame]] raises and [[clearWorking]] never runs:
  *      the working log and memory stay intact, nothing lost (invariant 4).
  *   3. clear the working scope ([[clearWorking]]: the replay-log clear + the oracle reset), so no
  *      event or pending question from the old game bleeds into the new one.
  *   4. bump the counter, recall the persistent seed, and fork exactly ONE fresh game seeded with
  *      it.
  */
final class GameSupervisor private (
    playSeeded: List[Definition] => IO[Outcome],
    playResumed: Vector[Event] => IO[Outcome],
    snapshotLog: IO[Vector[Event]],
    memory: DefinitionMemory,
    gameCounter: Ref[IO, GameId],
    harvest: GameId => IO[List[Definition]],
    clearWorking: IO[Unit],
    rewindWorking: Vector[Event] => IO[Unit],
    running: Ref[IO, Option[Fiber[IO, Throwable, Outcome]]],
    lock: Mutex[IO]
):

  /** The shared cancel → body → track spine (Rule of Three across [[newGame]] / [[fullReset]] /
    * [[rewindTo]]): under the mutex, cancel-and-await the running game (so nothing outlives it —
    * its [[ActorSystem]] `Resource` unwinds), run the transition `body` (which resets the working
    * scope and forks exactly ONE fresh fiber, and may consult whether a game `previous`ly ran),
    * then track the new fiber. All three transitions share this so a rewind is an honest sibling of
    * a reset.
    */
  private def transition(
      body: Option[Fiber[IO, Throwable, Outcome]] => IO[Fiber[IO, Throwable, Outcome]]
  ): IO[Unit] =
    lock.lock.surround {
      for
        previous <- running.getAndSet(None)
        _ <- previous.traverse_(_.cancel) // await teardown — no leaked actor fibers
        fiber <- body(previous)
        _ <- running.set(Some(fiber))
      yield ()
    }

  /** New Game: cancel and await the old game, HARVEST its definitions into persistent memory BEFORE
    * clearing the working log, then fork a fresh game seeded with the recalled definitions. The
    * definitions carry over (invariant 5); every working-scope candidate, backer, and refutation is
    * cleared (invariant 3). Serialized by the mutex.
    */
  def newGame: IO[Unit] = transition { previous =>
    for
      current <- gameCounter.get
      // Harvest-then-clear, fail-closed (invariant 4): read the finishing game's established
      // definitions off the working log (post-awaited-cancel — no in-flight emit races it) and merge
      // them origin-preserving into persistent memory. If this raises, clearWorking below never runs;
      // the working log and memory are intact and the operator can retry, losing nothing.
      harvested <- harvest(current)
      _ <- memory.remember(current, harvested)
      _ <- clearWorking // the old game is gone AND its definitions are banked: wipe the working log
      // Bump ONLY after a real prior game. Boot forks game `first` (so a game-1 definition stamps
      // origin Some(first)); every subsequent New Game advances the counter for the next harvest.
      _ <- previous.traverse_(_ => gameCounter.update(_.next))
      seed <- memory.recall // the persistent definitions replayed into the fresh game
      fiber <- playSeeded(seed).start // exactly one fresh game, seeded
    yield fiber
  }

  /** Full Reset: cancel and await the old game, clear the working log AND persistent memory, reset
    * the counter, then fork a fresh game with an EMPTY seed — byte-identical to the first game
    * (invariant 6). Definitions are lost ONLY here, never on a [[newGame]]. Serialized by the
    * mutex. It does NOT harvest — discarding the definitions is the point.
    */
  def fullReset: IO[Unit] = transition { _ =>
    for
      _ <- clearWorking // clear working scope: the replay log + the oracle's pending map
      _ <- memory.clear // clear persistent scope: the established definitions
      _ <- gameCounter.set(GameId.first) // back to a truly blank session
      fiber <- playSeeded(Nil).start // an empty seed — the first-game path
    yield fiber
  }

  /** Rewind (B2, recovery-and-endgame): the human flips ONE poisoned early answer and the game
    * re-folds the shorter log. Snapshot the finishing game's log (the SSE mirror, faithful
    * post-awaited-cancel), snap `toSeq` back to the round boundary before the poisoning
    * `QuestionAsked` ([[LogState.rewindPrefix]] — fail-closed to a no-op prefix on an invalid seq),
    * reset the working scope TO THAT PREFIX (`logRef.set(prefix) *> oracle.reset`), and fork the
    * SAME game resumed over the prefix. Three deliberate departures from [[newGame]]: it SKIPS
    * `harvest` (which reads the FULL old log and would re-bank the poisoning `DefinitionGiven` into
    * persistent memory, re-poisoning the game and voiding A3); it SKIPS the memory reseed; and it
    * does NOT bump `gameCounter` (a rewind is the SAME game). The `oracle.reset` inside
    * `rewindWorking` is mandatory — a question parked at rewind leaves a stale `Deferred` the
    * deterministically-minted re-asked id would collide on and await forever. Serialized by the
    * same mutex.
    */
  def rewindTo(toSeq: Int): IO[Unit] = transition { _ =>
    for
      full <-
        snapshotLog // the finishing game's log — the SSE mirror, complete after the awaited cancel
      prefix = LogState.rewindPrefix(full, toSeq)
      _ <- rewindWorking(prefix) // logRef.set(prefix) *> oracle.reset
      fiber <- playResumed(prefix).start // the SAME game, resumed over the truncated prefix
    yield fiber
  }

  /** Cancel the running game and forget it — the graceful stop counterpart (and the hook a test
    * uses to release its forked game). Idempotent; serialized by the same mutex.
    */
  def shutdown: IO[Unit] =
    lock.lock.surround(running.getAndSet(None).flatMap(_.traverse_(_.cancel)))

object GameSupervisor:

  /** Build a supervisor over the seeded game program and its two-scope collaborators. No game is
    * running until the first [[GameSupervisor.newGame]].
    *
    * @param playSeeded
    *   fork a fresh game seeded with the given recalled definitions ([[Society.play]] threaded with
    *   `seed`).
    * @param memory
    *   the session-scoped persistent definitions store.
    * @param gameCounter
    *   the session game counter (start `GameId.first`).
    * @param harvest
    *   read the finishing game's established definitions off the working log (the origin-preserving
    *   stamp/merge is [[DefinitionMemory.remember]]'s).
    * @param clearWorking
    *   wipe the working scope — the replay-log clear composed with the oracle reset.
    */
  def make(
      playSeeded: List[Definition] => IO[Outcome],
      playResumed: Vector[Event] => IO[Outcome],
      snapshotLog: IO[Vector[Event]],
      memory: DefinitionMemory,
      gameCounter: Ref[IO, GameId],
      harvest: GameId => IO[List[Definition]],
      clearWorking: IO[Unit],
      rewindWorking: Vector[Event] => IO[Unit]
  ): IO[GameSupervisor] =
    for
      running <- Ref[IO].of(Option.empty[Fiber[IO, Throwable, Outcome]])
      lock <- Mutex[IO]
    yield new GameSupervisor(
      playSeeded,
      playResumed,
      snapshotLog,
      memory,
      gameCounter,
      harvest,
      clearWorking,
      rewindWorking,
      running,
      lock
    )
