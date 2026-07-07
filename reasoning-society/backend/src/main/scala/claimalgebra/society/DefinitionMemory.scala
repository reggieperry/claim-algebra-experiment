package claimalgebra.society

import cats.effect.{IO, Ref}

import scala.collection.immutable.VectorMap

/** The session-scoped PERSISTENT definitions store (two-tier-reset-design §Types) — the first real
  * memory tier. Working memory is the per-game log, cleared each New Game; this store survives a
  * New Game (harvested from the finishing game, replayed into the next as belief-inert
  * [[Event.DefinitionRemembered]] events) and is cleared only on a Full Reset.
  *
  * Belief-inert BY TYPE (invariant 2): the store is a `Map[Term, Definition]` — it structurally
  * cannot represent a hypothesis about the answer, so no persistence slip can leak a prior game's
  * belief into the next. Keyed by the NORMALIZED [[Term]] so "Alive" and "alive" do not fork; the
  * concrete map is a `VectorMap` so `recall` yields first-seen (first-established) order with
  * latest-wins values.
  */
final class DefinitionMemory private (ref: Ref[IO, Map[Term, Definition]]):

  /** The established definitions, in first-seen order — the seed replayed into a fresh game. */
  def recall: IO[List[Definition]] = ref.get.map(_.values.toList)

  /** Harvest the definitions established by the just-finished game and merge them into the store
    * (called on New Game, BEFORE the working log is cleared — harvest-then-clear fails closed,
    * invariant 4). Each harvested entry's provenance is stamped `gameId None → Some(current)`; a
    * carried `Some(g)` is PRESERVED, so a definition's origin never drifts across generations
    * (invariant 8 — provenance stability). Merge is latest-wins keyed by the normalized term: a
    * this-game redefinition supersedes the recalled meaning at the term's first-seen position, and
    * re-remembering the same set is idempotent.
    */
  def remember(current: GameId, log: Vector[Event]): IO[Unit] =
    val harvested = Definitions.established(log).map(stamp(current))
    ref.update(store => harvested.foldLeft(store)((acc, d) => acc.updated(d.term, d)))

  /** Clear the persistent store — the Full Reset half (invariant 5: definitions are lost ONLY on a
    * Full Reset, never on a New Game).
    */
  def clear: IO[Unit] = ref.set(VectorMap.empty)

  /** Stamp a harvested definition's origin game: fill `None → Some(current)`, PRESERVE a carried
    * `Some(g)`. A recalled definition (unredefined this game) keeps its original game; a this-game
    * definition (`gameId = None` from [[Definitions.from]]) is stamped to the current game.
    */
  private def stamp(current: GameId)(d: Definition): Definition =
    d.provenance.gameId match
      case Some(_) => d
      case None => d.copy(provenance = d.provenance.copy(gameId = Some(current)))

object DefinitionMemory:
  /** An empty persistent store. The `VectorMap` preserves first-seen key order under latest-wins
    * updates, so `recall` is deterministic.
    */
  def make: IO[DefinitionMemory] =
    Ref.of[IO, Map[Term, Definition]](VectorMap.empty).map(new DefinitionMemory(_))
