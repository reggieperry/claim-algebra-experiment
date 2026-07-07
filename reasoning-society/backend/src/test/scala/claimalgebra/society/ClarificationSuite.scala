package claimalgebra.society

import claimalgebra.BlockReason
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

  test(
    "a definition whose term/meaning value-collides with a candidate stays belief-inert (no phantom backer)"
  ) {
    // A definition given by a2 whose TERM ("dog") and MEANING ("dog") both equal the candidate label
    // must not become a second backer of the "dog" hypothesis — dog is still lone-backed by a1.
    val dogTerm = mkTerm("dog")
    val log = Vector(
      mkAssert(1, a1, dog),
      definitionGiven(2, a2, q1, dogTerm, "dog")
    )
    assertEquals(
      GameCore.project(log).size,
      1,
      clue("only a1's assert projects; the definition does not")
    )
    assertEquals(
      GameCore.decide(log, log.size),
      GateDecision.Abstain(AbstainReason.Unconfirmed(1)),
      clue("the definition does not back or corroborate 'dog' — still a lone backer")
    )
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

  // --- two-tier reset: recalled definitions are belief-inert (invariants 1-2) ---

  test("a recalled definition projects to nothing (belief-inert at the projection)") {
    val origin = DefinitionProvenance(a1, q1, 9, Some(mkGame(1)))
    assertEquals(GameCore.project(List(definitionRemembered(1, alive, origin))), Nil)
  }

  test("seed-invariance: a game seeded with K recalled definitions begins at gap, K=0-identical") {
    // Two definitions recalled from game 1, seeded at the HEAD of game 2's log (seq 1..2).
    val seed = Vector(
      definitionRemembered(
        1,
        alive,
        DefinitionProvenance(a1, q1, 21, Some(mkGame(1))),
        "any living tissue"
      ),
      definitionRemembered(
        2,
        animal,
        DefinitionProvenance(a2, q1, 22, Some(mkGame(1))),
        "of the animal kingdom"
      )
    )
    val empty = Vector.empty[Event]

    // Belief-inertness: the seeds add NO evidence, so belief begins EXACTLY where an unseeded game does.
    assertEquals(GameCore.project(seed), Nil, clue("seeds project to nothing"))
    assertEquals(
      GameCore.belief(seed, seed.size),
      GameCore.belief(empty, 0),
      clue("belief begins at gap regardless of how many definitions are seeded")
    )
    assertEquals(GameCore.decide(seed, seed.size), GameCore.decide(empty, 0))
    assertEquals(
      GameCore.nextMove(seed, seed.size, roundComplete = true),
      GameCore.nextMove(empty, empty.size, roundComplete = true)
    )
    assertEquals(
      GameCore.decide(seed, seed.size),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Gap)),
      clue("a gap — no hypothesis on the table")
    )

    // Seed-visibility (invariant 2): the K definitions are established vocabulary from question one.
    assertEquals(Definitions.established(seed).map(_.term.value), List("alive", "animal"))
    assertEquals(GameView.from(seed).definitions.map(_.term.value), List("alive", "animal"))
    assertEquals(
      GameView.from(seed).definitions.map(_.meaning),
      List("any living tissue", "of the animal kingdom")
    )
  }

  test("this-game-wins: a this-game redefinition supersedes a recalled definition in the read") {
    // "alive" recalled from game 1, then CHALLENGED and redefined this game → the this-game meaning
    // wins in `established` (latest-wins at the recalled term's first-seen position), the recalled
    // meaning retained as a challengeable trace in `from`.
    val log = Vector(
      definitionRemembered(
        1,
        alive,
        DefinitionProvenance(a1, q1, 9, Some(mkGame(1))),
        "recalled: any living tissue"
      ),
      clarificationRequested(2, q1, alive),
      definitionGiven(3, a2, q1, alive, "this game: a living creature currently alive")
    )
    val established = Definitions.established(log)
    assertEquals(
      established.map(_.term.value),
      List("alive"),
      clue("first-seen (recalled) position")
    )
    assertEquals(
      established.find(_.term == alive).map(_.meaning),
      Some("this game: a living creature currently alive"),
      clue("the this-game redefinition supersedes the recalled meaning")
    )
    // The recalled meaning is still recoverable from the raw chain, and its origin gameId is retained.
    assertEquals(Definitions.from(log).count(_.term == alive), 2)
    assertEquals(
      Definitions.from(log).headOption.flatMap(_.provenance.gameId.map(_.value)),
      Some(1),
      clue("the recalled definition keeps its origin game")
    )
    // Belief is untouched — a redefinition never routes through refute/strike.
    assertEquals(GameCore.project(log), Nil)
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

  test("Term.from normalizes for grounding — case-folds and collapses internal whitespace") {
    // A challenge on "Alive" and a stored definition of "alive" must be the SAME key (else a
    // governing/definition lookup misses on case), so the value is case-folded and whitespace-collapsed.
    assertEquals(Term.from("Alive").map(_.value), Right("alive"))
    assertEquals(Term.from("  Living   THING  ").map(_.value), Right("living thing"))
    assertEquals(Term.from("ALIVE"), Term.from("alive"), clue("case-insensitive terms are one key"))
    assertEquals(
      Term.from("living  thing"),
      Term.from("Living Thing"),
      clue("collapsed-whitespace, case-folded terms are one key")
    )
  }
