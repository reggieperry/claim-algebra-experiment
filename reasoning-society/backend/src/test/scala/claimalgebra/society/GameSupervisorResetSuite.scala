package claimalgebra.society

import cats.effect.{Deferred, IO, Ref}
import cats.syntax.all.*
import claimalgebra.calculus.Status
import munit.CatsEffectSuite

/** The two-tier reset's DEFINITIONS ROUND-TRIP over the real [[GameSupervisor]] wiring, on a
  * SYNTHETIC game program (no real actors, no sleep) that stands in for a played game: on start it
  * records the seed it was handed, writes its working log — the recalled seed replayed as
  * belief-inert [[Event.DefinitionRemembered]] at the head (as [[LogActor.seedDefinitions]] would),
  * then this game's own events — and parks. `harvest`/`clearWorking` are the REAL `RunServer`
  * wiring over the working `logRef`, so the cancel → harvest → clear → fork spine is exercised
  * end-to-end for the persistent tier.
  *
  * It pins the invariants slice 2 owns: New Game carries the definitions and drops all working
  * evidence (3, 5); harvest-then-clear fails closed so nothing is lost on a harvest failure (4);
  * Full Reset clears both and re-blanks the session (6); and a definition's origin never drifts
  * across generations (8). The mechanical no-stack spine and the real-actor teardown live in
  * [[GameSupervisorSuite]] / [[GameSupervisorSocietySuite]].
  */
class GameSupervisorResetSuite extends CatsEffectSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val q1 = mkQuestion("q1")
  private val alive = mkTerm("alive")
  private val animal = mkTerm("animal")
  private val dog = mkAnswer("dog")

  /** The DefinitionRemembered head a real seeded game emits ([[LogActor.seedDefinitions]]) — so a
    * round-tripped game's working log carries the recalled definitions the NEXT harvest re-reads
    * (their origin preserved verbatim, which is how invariant 8 holds across generations).
    */
  private def seedHead(seed: List[Definition]): Vector[Event] =
    seed.zipWithIndex.map { case (d, i) =>
      Event.DefinitionRemembered(i + 1, (i + 1).toLong, d.term, d.meaning, d.provenance)
    }.toVector

  final private case class Harness(
      games: GameSupervisor,
      logRef: Ref[IO, Vector[Event]],
      memory: DefinitionMemory,
      seeds: Ref[IO, Vector[List[Definition]]],
      started: Ref[IO, Option[Deferred[IO, Unit]]]
  ):
    /** New Game (or Full Reset), then deterministically await the fresh game reaching its body — no
      * sleep. Sequential-only (one game races the single signal at a time).
      */
    def newGameAndAwait: IO[Unit] = awaitAfter(games.newGame)
    def fullResetAndAwait: IO[Unit] = awaitAfter(games.fullReset)

    private def awaitAfter(reset: IO[Unit]): IO[Unit] =
      for
        d <- Deferred[IO, Unit]
        _ <- started.set(Some(d))
        _ <- reset
        _ <- d.get
      yield ()

    /** The seed handed to the most recently forked game — what a New Game replayed into it. */
    def lastSeed: IO[List[Definition]] = seeds.get.map(_.lastOption.getOrElse(Nil))

    /** The current game's working log (the seed head + this game's own events). */
    def workingLog: IO[Vector[Event]] = logRef.get

  /** A supervisor whose synthetic game, on start, records its seed, writes its working log (the
    * recalled seed at the head, then this game's own events drawn from `perGame` by a start
    * cursor), signals it reached the body, and parks. `perGame(i)` is the i-th forked game's own
    * events.
    */
  private def harness(perGame: List[Vector[Event]]): IO[Harness] =
    for
      logRef <- Ref[IO].of(Vector.empty[Event])
      memory <- DefinitionMemory.make
      gameCounter <- Ref[IO].of(GameId.first)
      seeds <- Ref[IO].of(Vector.empty[List[Definition]])
      started <- Ref[IO].of(Option.empty[Deferred[IO, Unit]])
      cursor <- Ref[IO].of(0)
      playSeeded = (seed: List[Definition]) =>
        for
          _ <- seeds.update(_ :+ seed)
          idx <- cursor.getAndUpdate(_ + 1)
          thisGame = perGame.lift(idx).getOrElse(Vector.empty[Event])
          _ <- logRef.set(seedHead(seed) ++ thisGame)
          sig <- started.getAndSet(None)
          _ <- sig.traverse_(_.complete(()).void)
          outcome <- IO.never[Outcome]
        yield outcome
      // The REAL RunServer wiring: harvest the working log's established definitions; clear the log.
      harvest = (_: GameId) => logRef.get.map(Definitions.established)
      clearWorking = logRef.set(Vector.empty[Event])
      games <- GameSupervisor.make(playSeeded, memory, gameCounter, harvest, clearWorking)
    yield Harness(games, logRef, memory, seeds, started)

  test(
    "New Game carries the definition into game two's seed; game one's evidence does not (inv. 3, 5)"
  ) {
    // Game 1 asserts `dog` (working evidence) AND establishes `alive` (a definition). New Game must
    // seed game 2 with `alive` (origin Some(game1)) but leave `dog` — and every working backer —
    // behind.
    val game1 = Vector(
      mkAssert(1, a1, dog),
      definitionGiven(2, a1, q1, alive, "a living creature currently alive")
    )
    harness(List(game1, Vector.empty)).flatMap { h =>
      (for
        _ <- h.newGameAndAwait // boot: game 1 writes its log (seed empty)
        _ <- h.newGameAndAwait // New Game: harvest game 1, fork game 2 seeded with `alive`
        seed2 <- h.lastSeed
        game2Log <- h.workingLog
      yield
        // The definition carried, origin stamped Some(game1) (invariant 5).
        assertEquals(
          seed2.map(_.term.value),
          List("alive"),
          clue("alive carried into game 2's seed")
        )
        assertEquals(
          seed2.map(_.provenance.gameId),
          List(Some(mkGame(1))),
          clue("origin stamped with game 1")
        )
        // It heads game 2's log as a belief-inert DefinitionRemembered, origin Some(game1).
        assertEquals(
          game2Log.headOption.collect { case Event.DefinitionRemembered(_, _, t, _, o) =>
            (t.value, o.gameId)
          },
          Some(("alive", Some(mkGame(1)))),
          clue("a DefinitionRemembered heads game 2's log, origin Some(game1)")
        )
        // Invariant 3: game 1's Assert(dog) is GONE — no candidate, no backer toward the floor.
        assertEquals(
          GameCore.belief(game2Log, game2Log.size).status,
          Status.Missing,
          clue("game 2 begins blank — no prior-game candidate reaches its belief")
        )
        assertEquals(
          GameCore.distinctBackers(game2Log, dog),
          0,
          clue("a prior-game backer does not count toward game 2's no-lone-sign floor")
        )
      ).guarantee(h.games.shutdown)
    }
  }

  test(
    "Full Reset clears the definitions: the next game's seed is empty, belief blank (inv. 6)"
  ) {
    val game1 = Vector(definitionGiven(1, a1, q1, alive, "a living creature currently alive"))
    harness(List(game1, Vector.empty, Vector.empty)).flatMap { h =>
      (for
        _ <- h.newGameAndAwait // game 1 establishes alive
        _ <- h.newGameAndAwait // New Game → game 2 recalls alive (memory now holds it)
        recalledMid <- h.memory.recall
        _ <- h.fullResetAndAwait // Full Reset → memory + working cleared, fresh empty-seed game
        recalledAfter <- h.memory.recall
        seedAfter <- h.lastSeed
        logAfter <- h.workingLog
      yield
        assertEquals(
          recalledMid.map(_.term.value),
          List("alive"),
          clue("New Game kept the definition")
        )
        assertEquals(recalledAfter, Nil, clue("Full Reset emptied persistent memory"))
        assertEquals(
          seedAfter,
          Nil,
          clue("the post-reset game's seed is empty — byte-identical to the first game")
        )
        assertEquals(
          GameCore.belief(logAfter, logAfter.size).status,
          Status.Missing,
          clue("belief blank after Full Reset")
        )
      ).guarantee(h.games.shutdown)
    }
  }

  test(
    "Full Reset resets the counter — a post-reset game-1 definition re-stamps Some(1)"
  ) {
    // After New Games advance the counter, Full Reset sets it back to `first`; a definition
    // established in the post-reset game harvests to Some(1) again, and the pre-reset `alive` is gone.
    val g1 = Vector(definitionGiven(1, a1, q1, alive, "living"))
    val postReset = Vector(definitionGiven(1, a1, q1, animal, "of the animal kingdom"))
    harness(List(g1, Vector.empty, postReset, Vector.empty)).flatMap { h =>
      (for
        _ <- h.newGameAndAwait // boot: game 1 establishes alive
        _ <- h.newGameAndAwait // New Game → game 2 recalls alive (counter advances to 2)
        _ <-
          h.fullResetAndAwait // Full Reset → memory + counter cleared; the fresh game is "game 1"
        _ <- h.newGameAndAwait // New Game → harvest the post-reset game's `animal`
        recalled <- h.memory.recall
      yield
        assertEquals(
          recalled.map(_.term.value),
          List("animal"),
          clue("only the post-reset definition — the pre-reset `alive` was cleared")
        )
        assertEquals(
          recalled.map(_.provenance.gameId),
          List(Some(mkGame(1))),
          clue("the counter reset to first — the post-reset definition stamps Some(1), not Some(3)")
        )
      ).guarantee(h.games.shutdown)
    }
  }

  test(
    "harvest failure fails closed: newGame raises, the working log is intact, nothing lost (inv. 4)"
  ) {
    // Game 1 establishes `alive`; a harvest armed to raise makes the New Game that would bank it fail
    // BEFORE clearWorking — the working log keeps `alive`, memory is untouched, and a retry (harvest
    // healthy) recovers it. Nothing lost.
    val boom = new RuntimeException("harvest boom")
    val game1 = Vector(definitionGiven(1, a1, q1, alive, "a living creature currently alive"))
    val perGame = List(game1, Vector.empty[Event])
    for
      logRef <- Ref[IO].of(Vector.empty[Event])
      memory <- DefinitionMemory.make
      gameCounter <- Ref[IO].of(GameId.first)
      started <- Ref[IO].of(Option.empty[Deferred[IO, Unit]])
      cursor <- Ref[IO].of(0)
      raiseHarvest <- Ref[IO].of(false)
      playSeeded = (seed: List[Definition]) =>
        for
          idx <- cursor.getAndUpdate(_ + 1)
          thisGame = perGame.lift(idx).getOrElse(Vector.empty[Event])
          _ <- logRef.set(seedHead(seed) ++ thisGame)
          sig <- started.getAndSet(None)
          _ <- sig.traverse_(_.complete(()).void)
          outcome <- IO.never[Outcome]
        yield outcome
      harvest = (_: GameId) =>
        raiseHarvest.get.flatMap { raise =>
          if raise then IO.raiseError(boom) else logRef.get.map(Definitions.established)
        }
      clearWorking = logRef.set(Vector.empty[Event])
      games <- GameSupervisor.make(playSeeded, memory, gameCounter, harvest, clearWorking)
      awaitStart = (io: IO[Unit]) =>
        Deferred[IO, Unit].flatMap(d => started.set(Some(d)) *> io *> d.get)
      _ <- awaitStart(games.newGame) // boot: game 1 establishes alive
      _ <- raiseHarvest.set(true) // arm the failure
      result <-
        games.newGame.attempt // the New Game that would harvest `alive` RAISES before clearing
      logAfterRaise <- logRef.get
      memAfterRaise <- memory.recall
      _ <- raiseHarvest.set(false) // heal the harvest and retry
      _ <- awaitStart(games.newGame) // retry recovers `alive`
      memAfterRetry <- memory.recall
      _ <- games.shutdown
    yield
      assert(result.isLeft, clue("newGame raised when harvest failed"))
      assertEquals(
        logAfterRaise.collect { case Event.DefinitionGiven(_, _, _, _, t, _) => t.value }.toList,
        List("alive"),
        clue("the working log was NOT cleared — alive is intact, nothing lost")
      )
      assertEquals(
        memAfterRaise,
        Nil,
        clue("nothing was banked — memory untouched by the failed harvest")
      )
      assertEquals(
        memAfterRetry.map(_.term.value),
        List("alive"),
        clue("a retry with a healthy harvest recovers the definition — nothing was lost")
      )
  }

  test(
    "origin never drifts across generations: a game-1 definition stays Some(game1) (inv. 8)"
  ) {
    // Game 1 establishes alive (None → Some(1) on the first New Game). Game 2 re-establishes nothing;
    // the SECOND New Game harvests the recalled alive and must PRESERVE Some(1), not re-stamp Some(2).
    val game1 = Vector(definitionGiven(1, a1, q1, alive, "a living creature currently alive"))
    harness(List(game1, Vector.empty, Vector.empty)).flatMap { h =>
      (for
        _ <- h.newGameAndAwait // boot: game 1 establishes alive
        _ <-
          h.newGameAndAwait // New Game: harvest game 1 → alive stamped Some(1); game 2 recalls it
        seed2 <- h.lastSeed
        _ <- h.newGameAndAwait // New Game: harvest game 2 (only recalls alive) → origin PRESERVED
        seed3 <- h.lastSeed
        recalled <- h.memory.recall
      yield
        assertEquals(
          seed2.map(_.provenance.gameId),
          List(Some(mkGame(1))),
          clue("first stamp: Some(1)")
        )
        assertEquals(
          seed3.map(_.provenance.gameId),
          List(Some(mkGame(1))),
          clue("still Some(1) after a second generation — origin never drifts")
        )
        assertEquals(recalled.map(_.provenance.gameId), List(Some(mkGame(1))))
      ).guarantee(h.games.shutdown)
    }
  }
