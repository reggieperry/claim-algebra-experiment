package claimalgebra.society
package experiment

/** Slice 0 (fallible-oracle): the mechanical adjudicator + the non-leak property of the sealed
  * truth marker. `classify` is a pure fold over the log; `TargetRegistered` must never reach the
  * agents.
  */
class AdjudicationSuite extends munit.FunSuite with SocietyFixtures:

  private val apple = mkAnswer("apple")
  private val dog = mkAnswer("dog")

  // A minimal well-formed experiment log: the sealed truth at the head, then a terminal move.
  private def log(sign: Option[Answer]): Vector[Event] =
    val head = Event.TargetRegistered(1, 1L, apple, 42L)
    sign match
      case Some(c) => Vector(head, Event.GateSign(2, 2L, c))
      case None => Vector(head, Event.GateAbstain(2, 2L, "budget exhausted"))

  test("classify — a sign equal to the sealed truth is SignCorrect") {
    assertEquals(Adjudication.classify(log(Some(apple))), Right(PrimaryOutcome.SignCorrect))
  }

  test("classify — a sign DIFFERENT from the truth is SignWrong (the fail-open)") {
    assertEquals(Adjudication.classify(log(Some(dog))), Right(PrimaryOutcome.SignWrong))
  }

  test("classify — no terminal sign is Abstain") {
    assertEquals(Adjudication.classify(log(None)), Right(PrimaryOutcome.Abstain))
  }

  test("classify — fail-closed: a log with no TargetRegistered cannot be adjudicated") {
    val orphan = Vector[Event](Event.GateSign(1, 1L, apple))
    assert(
      Adjudication.classify(orphan).isLeft,
      clue("no truth marker → Left, never a silent Abstain")
    )
  }

  test("the sealed truth NEVER reaches the agent-facing view — the non-leak property") {
    // A log carrying ONLY the truth marker: the target's string must not surface in the agent prompt.
    val secret = mkAnswer("zzqqxx-secret-target")
    val view = GameView.from(Vector(Event.TargetRegistered(1, 1L, secret, 7L)))
    assert(!view.render.contains("zzqqxx"), clue(view.render))
    assertEquals(view.transcript, Nil)
    assertEquals(view.hypotheses, Nil)
    assertEquals(view.definitions, Nil)
  }

  test(
    "signPath attributes the sign: OracleConfirmed for a lone confirmed winner, BackerQuorum for ≥2"
  ) {
    val driller = mkAgent("driller")
    val splitter = mkAgent("splitter")
    val head = Event.TargetRegistered(1, 1L, apple, 42L)
    // one backer + a ground-truth Yes → signed via the oracle-confirmed disjunct (the k-gated path).
    val oracleSigned = Vector[Event](
      head,
      mkAssert(2, driller, apple),
      Event.GuessAnswered(3, 3L, apple, OracleAnswer.Yes),
      Event.GateSign(4, 4L, apple)
    )
    // two distinct backers → signed via the structural quorum (k-invariant).
    val backerSigned = Vector[Event](
      head,
      mkAssert(2, driller, apple),
      Event.Corroborate(3, 3L, splitter, apple, "agreed"),
      Event.GateSign(4, 4L, apple)
    )
    assertEquals(Adjudication.signPath(oracleSigned), Some(SignPath.OracleConfirmed))
    assertEquals(Adjudication.signPath(backerSigned), Some(SignPath.BackerQuorum))
    assertEquals(
      Adjudication.signPath(Vector(head)),
      None,
      clue("an unsigned game has no sign path")
    )
  }
