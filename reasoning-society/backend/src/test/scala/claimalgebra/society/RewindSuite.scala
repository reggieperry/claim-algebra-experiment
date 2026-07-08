package claimalgebra.society

/** B2 (recovery-and-endgame): the pure rewind helpers — the round-boundary snap and the LogState
  * rehydrate. The snap MUST go to before the poisoning `QuestionAsked` (not the `AnswerGiven`), or
  * `pendingQuestion` never re-surfaces the poisoned question; it is fail-closed on an invalid
  * `toSeq`.
  */
class RewindSuite extends munit.FunSuite with SocietyFixtures:

  private val splitter = mkAgent("splitter")
  private val driller = mkAgent("driller")
  private def qid(raw: String): QuestionId = QuestionId.from(raw).fold(fail(_), identity)
  private def term(raw: String): Term = Term.from(raw).fold(fail(_), identity)

  private val q1 = qid("q1")
  private val q2 = qid("q2")
  // A game: q1 proposed → asked → a term defined in that round → answered No, then q2 proposed.
  private val log = Vector[Event](
    Event.QuestionProposed(1, 1L, splitter, q1, "Is it alive?"),
    Event.QuestionAsked(2, 2L, splitter, q1, "Is it alive?"),
    Event.DefinitionGiven(3, 3L, splitter, q1, term("alive"), "metabolizing"),
    Event.AnswerGiven(4, 4L, q1, OracleAnswer.No, List(term("alive"))),
    Event.QuestionProposed(5, 5L, driller, q2, "Is it a fruit?")
  )

  test(
    "rewindPrefix snaps to before the poisoning QuestionAsked, keeping the proposal re-askable"
  ) {
    val prefix = LogState.rewindPrefix(log, 4) // the human clicks the AnswerGiven at seq 4
    // Only the proposal survives — the QuestionAsked, the round's DefinitionGiven, and the AnswerGiven
    // are all dropped, so the question re-enters askQuestion and A3's definition can regenerate.
    assertEquals(prefix, Vector[Event](Event.QuestionProposed(1, 1L, splitter, q1, "Is it alive?")))
    assert(!prefix.exists { case _: Event.QuestionAsked => true; case _ => false })
    assert(!prefix.exists { case _: Event.AnswerGiven => true; case _ => false })
    assert(!prefix.exists { case _: Event.DefinitionGiven => true; case _ => false })
  }

  test("rewindPrefix is fail-closed — a toSeq naming no AnswerGiven leaves the log unchanged") {
    assertEquals(LogState.rewindPrefix(log, 99), log) // out of range
    assertEquals(LogState.rewindPrefix(log, 1), log) // seq 1 is a proposal, not an answer
  }

  test(
    "resumed rehydrates the prefix: log is the prefix, budget continues from the answers, closed"
  ) {
    val prefix = LogState.rewindPrefix(log, 4)
    val state = LogState.resumed(prefix)
    assertEquals(state.log, prefix)
    assertEquals(state.roundsUsed, 0, clue("no answers remain in the prefix → budget from 0"))
    assertEquals(state.phase, Phase.Playing)
    assert(state.barrier.closed)
    assertEquals(LogState.resumed(log).roundsUsed, 1, clue("one AnswerGiven in the full log"))
  }
