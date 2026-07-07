package claimalgebra.society

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

/** The clarification feature, slice 1 (the pure model + fold). The load-bearing safety is
  * belief-INERTNESS: a challenge, a definition, and an answer's governing reference are grounding
  * context/audit — they must NEVER move the answer-slot, so the fail-closed sign path (the Gate,
  * the no-lone-sign floor, the glut-on-contradiction) is provably unchanged by their presence. The
  * rest pins the [[Definitions]] projection (claim-with-provenance, latest-per-term) and the
  * [[GameView]] carrying the established vocabulary.
  */
class ClarificationSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val dog = mkAnswer("dog")
  private val q1 = mkQuestion("q1")
  private val alive = mkTerm("alive")
  private val animal = mkTerm("animal")

  // Splice belief-inert clarification events into a base log at arbitrary positions, preserving the
  // base's order and seqs — so the belief-moving events (and their per-seq provenance tokens) are
  // byte-identical between `base` and `merged`; only inert events are added.
  private def spliceInto(base: Vector[Event], inerts: List[Event]): Gen[Vector[Event]] =
    inerts.foldLeft(Gen.const(base)) { (accGen, inert) =>
      accGen.flatMap { acc =>
        Gen.choose(0, acc.size).map(i => (acc.take(i) :+ inert) ++ acc.drop(i))
      }
    }

  private val genBaseAndMerged: Gen[(Vector[Event], Vector[Event])] =
    for
      base <- genLog
      n <- Gen.choose(0, 5)
      inerts <- Gen.listOfN(n, genInertClarification)
      merged <- spliceInto(base, inerts)
    yield (base, merged)

  // --- the load-bearing safety: belief-inertness ---

  property(
    "clarification/definition events are belief-inert: project/belief/decide/nextMove unchanged"
  ) {
    forAll(genBaseAndMerged) { case (base, merged) =>
      // The projection is identical — the clarification pair adds NO evidence to the fold.
      assertEquals(GameCore.project(merged), GameCore.project(base))
      // The four-state read is identical.
      assertEquals(GameCore.belief(merged, merged.size), GameCore.belief(base, base.size))
      // The gate decision is identical — the fail-closed sign path is untouched.
      assertEquals(GameCore.decide(merged, merged.size), GameCore.decide(base, base.size))
      // The round move is identical.
      assertEquals(
        GameCore.nextMove(merged, merged.size, roundComplete = true),
        GameCore.nextMove(base, base.size, roundComplete = true)
      )
    }
  }

  property("setting AnswerGiven.governing leaves belief/decide/nextMove identical") {
    forAll(genLog) { (base: Vector[Event]) =>
      val withGoverning = base.map {
        case Event.AnswerGiven(s, t, q, a, _) => Event.AnswerGiven(s, t, q, a, List(alive, animal))
        case other => other
      }
      assertEquals(GameCore.project(withGoverning), GameCore.project(base))
      assertEquals(
        GameCore.belief(withGoverning, withGoverning.size),
        GameCore.belief(base, base.size)
      )
      assertEquals(
        GameCore.decide(withGoverning, withGoverning.size),
        GameCore.decide(base, base.size)
      )
      assertEquals(
        GameCore.nextMove(withGoverning, withGoverning.size, roundComplete = true),
        GameCore.nextMove(base, base.size, roundComplete = true)
      )
    }
  }

  test("the clarification pair projects to nothing (belief-inert at the projection)") {
    val events = List(
      clarificationRequested(1, q1, alive),
      definitionGiven(2, a1, q1, alive, "a living creature currently alive")
    )
    assertEquals(GameCore.project(events), Nil)
  }

  test("only the hypothesis-moving events project — the clarification pair adds nothing") {
    val mixed = Vector(
      mkAssert(1, a1, dog),
      clarificationRequested(2, q1, alive),
      definitionGiven(3, a1, q1, alive, "a living creature currently alive"),
      corroborate(4, a2, dog),
      answerGiven(5, q1, OracleAnswer.Yes, List(alive))
    )
    assertEquals(GameCore.project(mixed).size, 2, clue("assert + corroborate only"))
  }

  test("a full challenge→definition→grounded-answer exchange does not change the signed outcome") {
    // dog asserted by two distinct agents → Sign(dog). The interleaved clarification is inert.
    val clarified = Vector(
      mkAssert(1, a1, dog),
      clarificationRequested(2, q1, alive),
      definitionGiven(3, a1, q1, alive, "a living creature currently alive"),
      mkAssert(4, a2, dog),
      answerGiven(5, q1, OracleAnswer.Yes, List(alive))
    )
    val plain =
      Vector(mkAssert(1, a1, dog), mkAssert(4, a2, dog), answerGiven(5, q1, OracleAnswer.Yes))
    assertEquals(GameCore.decide(clarified, clarified.size), GateDecision.Sign(dog))
    assertEquals(
      GameCore.decide(clarified, clarified.size),
      GameCore.decide(plain, plain.size)
    )
  }

  // --- the events' identity ---

  test("ClarificationRequested carries no agent; DefinitionGiven carries the asking agent") {
    assertEquals(clarificationRequested(1, q1, alive).agentId, None)
    assertEquals(definitionGiven(2, a2, q1, alive).agentId, Some(a2))
  }

  // --- definitions as claims with provenance ---

  test("Definitions.from yields one claim per DefinitionGiven, in log order, with provenance") {
    val log = Vector(
      mkAssert(1, a1, dog),
      definitionGiven(2, a1, q1, alive, "a living creature currently alive"),
      clarificationRequested(3, q1, animal),
      definitionGiven(4, a2, q1, animal, "a member of the animal kingdom")
    )
    val defs = Definitions.from(log)
    assertEquals(defs.map(_.term.value), List("alive", "animal"))
    assertEquals(
      defs.map(_.meaning),
      List("a living creature currently alive", "a member of the animal kingdom")
    )
    assertEquals(
      defs.map(_.provenance),
      List(DefinitionProvenance(a1, q1, 2), DefinitionProvenance(a2, q1, 4))
    )
  }

  test("Definitions.established keeps the LATEST meaning per term, in first-seen order") {
    val log = Vector(
      definitionGiven(1, a1, q1, alive, "any living tissue"),
      definitionGiven(2, a2, q1, animal, "a member of the animal kingdom"),
      definitionGiven(3, a1, q1, alive, "a living creature currently alive") // supersedes the first
    )
    val established = Definitions.established(log)
    assertEquals(established.map(_.term.value), List("alive", "animal"), clue("first-seen order"))
    assertEquals(
      established.find(_.term == alive).map(_.meaning),
      Some("a living creature currently alive"),
      clue("the later definition of 'alive' is retained (accumulate/latest)")
    )
    // The full chain is still recoverable from `from` — supersession-as-recorded-chain is a later slice.
    assertEquals(Definitions.from(log).count(_.term == alive), 2)
  }

  // --- GameView carries the established vocabulary ---

  test("GameView accumulates the established definitions, latest per term") {
    val log = Vector(
      mkAssert(1, a1, dog),
      definitionGiven(2, a1, q1, alive, "any living tissue"),
      definitionGiven(3, a2, q1, alive, "a living creature currently alive")
    )
    val view = GameView.from(log)
    assertEquals(view.definitions.map(_.term.value), List("alive"))
    assertEquals(view.definitions.map(_.meaning), List("a living creature currently alive"))
  }

  test("GameView.definitions is empty when no definition was given") {
    assertEquals(GameView.from(Vector(mkAssert(1, a1, dog))).definitions, Nil)
  }

  // --- the term constructor's negative space (scala-types) ---

  test("Term.from rejects a blank label and trims") {
    assert(Term.from("").isLeft)
    assert(Term.from("   ").isLeft)
    assertEquals(Term.from("  alive  ").map(_.value), Right("alive"))
  }
