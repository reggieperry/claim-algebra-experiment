package claimalgebra.society

import cats.effect.{Deferred, IO, Ref}
import munit.CatsEffectSuite

/** B2 (recovery-and-endgame): the supervisor's `rewindTo` orchestration over the shared cancel →
  * reset → fork spine. It snapshots the finishing game's log, snaps `toSeq` to the round boundary
  * ([[LogState.rewindPrefix]]), resets the working scope TO the prefix, and re-forks the SAME game
  * resumed over it — and it SKIPS harvest (else the poisoning definition is re-banked) and does NOT
  * bump the game counter (a rewind is the same game). Stubs capture what each channel saw.
  */
class GameSupervisorRewindSuite extends CatsEffectSuite:

  private def mkAgentId(raw: String): AgentId = AgentId.from(raw).fold(fail(_), identity)
  private def qid(raw: String): QuestionId = QuestionId.from(raw).fold(fail(_), identity)

  // A game: q1 proposed(1) / asked(2) / answered No(3), then q2 proposed(4).
  private val fullLog = Vector[Event](
    Event.QuestionProposed(1, 1L, mkAgentId("splitter"), qid("q1"), "Is it alive?"),
    Event.QuestionAsked(2, 2L, mkAgentId("splitter"), qid("q1"), "Is it alive?"),
    Event.AnswerGiven(3, 3L, qid("q1"), OracleAnswer.No),
    Event.QuestionProposed(4, 4L, mkAgentId("driller"), qid("q2"), "Is it a fruit?")
  )

  test(
    "rewindTo snaps to the round boundary, resumes the prefix, skips harvest, does not bump the counter"
  ) {
    for
      memory <- DefinitionMemory.make
      gameCounter <- Ref[IO].of(GameId.first)
      harvested <- Ref[IO].of(0) // count harvest calls
      resumedWith <- Ref[IO].of(Option.empty[Vector[Event]])
      rewoundWith <- Ref[IO].of(Option.empty[Vector[Event]])
      seededStarted <- Deferred[IO, Unit]
      resumedStarted <- Deferred[IO, Unit]
      playSeeded = (_: List[Definition]) => seededStarted.complete(()).void *> IO.never[Outcome]
      playResumed = (prefix: Vector[Event]) =>
        resumedWith.set(Some(prefix)) *> resumedStarted.complete(()).void *> IO.never[Outcome]
      harvest = (_: GameId) => harvested.update(_ + 1).as(List.empty[Definition])
      rewindWorking = (prefix: Vector[Event]) => rewoundWith.set(Some(prefix))
      games <- GameSupervisor.make(
        playSeeded,
        playResumed,
        IO.pure(fullLog), // snapshotLog — the finishing game's log
        memory,
        gameCounter,
        harvest,
        IO.unit, // clearWorking (unused on the rewind path)
        rewindWorking
      )
      _ <- games.newGame // fork the first game
      _ <- seededStarted.get
      harvestAfterStart <- harvested.get
      counterAfterStart <- gameCounter.get
      _ <- games.rewindTo(3) // the human flips the answer at seq 3
      _ <- resumedStarted.get
      resumed <- resumedWith.get
      rewound <- rewoundWith.get
      harvestAfterRewind <- harvested.get
      counterAfterRewind <- gameCounter.get
      _ <- games.shutdown
    yield
      // Snapped to before QuestionAsked(2): only QuestionProposed(1) survives (re-askable).
      val expectedPrefix = Vector[Event](fullLog.head)
      assertEquals(resumed, Some(expectedPrefix), clue("playResumed forks the snapped prefix"))
      assertEquals(
        rewound,
        Some(expectedPrefix),
        clue("rewindWorking resets to the snapped prefix")
      )
      assertEquals(
        harvestAfterRewind,
        harvestAfterStart,
        clue("rewind SKIPS harvest — no re-poison")
      )
      assertEquals(counterAfterRewind, counterAfterStart, clue("rewind does NOT bump the counter"))
  }
