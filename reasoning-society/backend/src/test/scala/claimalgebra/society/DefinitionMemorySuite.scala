package claimalgebra.society

import munit.CatsEffectSuite

/** The session-scoped persistent definitions store (two-tier-reset-design). Its contract:
  * `remember` harvests the just-finished game's established definitions and stamps each with the
  * current game (a `None` origin filled, a carried `Some(g)` PRESERVED so provenance never drifts —
  * invariant 8); `recall` returns them in first-seen order; `clear` empties (the Full Reset half,
  * invariant 5); re-remembering the same set is idempotent under latest-wins; and the normalized
  * [[Term]] is the key, so "Alive" and "alive" do not fork.
  */
class DefinitionMemorySuite extends CatsEffectSuite with SocietyFixtures:

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val q1 = mkQuestion("q1")
  private val alive = mkTerm("alive")
  private val animal = mkTerm("animal")

  // A game-1 working log establishing two definitions (this-game `DefinitionGiven` → gameId None).
  private val game1Log = Vector(
    definitionGiven(1, a1, q1, alive, "any living tissue"),
    definitionGiven(2, a2, q1, animal, "of the animal kingdom")
  )

  test("recall on a fresh store is empty") {
    DefinitionMemory.make.flatMap(_.recall).assertEquals(Nil)
  }

  test("remember harvests the established definitions and stamps gameId None → Some(current)") {
    for
      mem <- DefinitionMemory.make
      _ <- mem.remember(mkGame(1), game1Log)
      recalled <- mem.recall
    yield
      assertEquals(recalled.map(_.term.value), List("alive", "animal"), clue("first-seen order"))
      assertEquals(
        recalled.map(_.meaning),
        List("any living tissue", "of the animal kingdom")
      )
      assertEquals(
        recalled.map(_.provenance.gameId),
        List(Some(mkGame(1)), Some(mkGame(1))),
        clue("a None origin is stamped with the current game")
      )
  }

  test(
    "a carried Some(g) is PRESERVED across a later remember — provenance never drifts (inv. 8)"
  ) {
    // A definition RECALLED into game 2 (its origin already stamped Some(game1)); harvesting game 2
    // must keep game1 as the origin, not overwrite it with game2.
    val carried = DefinitionProvenance(a1, q1, 9, Some(mkGame(1)))
    val game2Log = Vector(definitionRemembered(1, alive, carried, "any living tissue"))
    for
      mem <- DefinitionMemory.make
      _ <- mem.remember(mkGame(2), game2Log)
      recalled <- mem.recall
    yield
      assertEquals(recalled.map(_.provenance.gameId), List(Some(mkGame(1))))
      assertEquals(recalled.map(_.provenance.seq), List(9), clue("the whole origin is preserved"))
  }

  test("clear empties the store (the Full Reset half — inv. 5)") {
    for
      mem <- DefinitionMemory.make
      _ <- mem.remember(mkGame(1), game1Log)
      _ <- mem.clear
      recalled <- mem.recall
    yield assertEquals(recalled, Nil)
  }

  test("re-remembering the same set is idempotent under latest-wins") {
    for
      once <- DefinitionMemory.make
      _ <- once.remember(mkGame(1), game1Log)
      onceRecall <- once.recall
      twice <- DefinitionMemory.make
      _ <- twice.remember(mkGame(1), game1Log)
      _ <- twice.remember(mkGame(1), game1Log)
      twiceRecall <- twice.recall
    yield assertEquals(twiceRecall, onceRecall)
  }

  test("a later game's redefinition supersedes the recalled meaning (latest-wins by term)") {
    // Game 1 establishes "alive"; game 2 (its log recalls "alive", then redefines it via a challenge).
    val game2Log = Vector(
      definitionRemembered(
        1,
        alive,
        DefinitionProvenance(a1, q1, 1, Some(mkGame(1))),
        "any living tissue"
      ),
      definitionGiven(5, a2, q1, alive, "a living creature currently alive")
    )
    for
      mem <- DefinitionMemory.make
      _ <- mem.remember(mkGame(1), game1Log)
      _ <- mem.remember(mkGame(2), game2Log)
      recalled <- mem.recall
    yield
      assertEquals(
        recalled.map(_.term.value),
        List("alive", "animal"),
        clue("first-seen order kept")
      )
      val aliveDef = recalled.find(_.term == alive)
      assertEquals(aliveDef.map(_.meaning), Some("a living creature currently alive"))
      assertEquals(
        aliveDef.flatMap(_.provenance.gameId),
        Some(mkGame(2)),
        clue("the redefinition's origin is game 2 — it was newly established there")
      )
  }

  test("the normalized Term is the key — 'Alive' and 'alive' do not fork") {
    val logN = Vector(
      definitionGiven(1, a1, q1, mkTerm("Alive"), "first"),
      definitionGiven(2, a2, q1, mkTerm("alive"), "second")
    )
    for
      mem <- DefinitionMemory.make
      _ <- mem.remember(mkGame(1), logN)
      recalled <- mem.recall
    yield
      assertEquals(recalled.map(_.term.value), List("alive"), clue("one key, not two"))
      assertEquals(recalled.map(_.meaning), List("second"), clue("latest wins"))
  }
