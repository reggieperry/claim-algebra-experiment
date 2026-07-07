# Two-tier reset / definitions-as-memory — design of record

The realization design for the two-tier reset; `reset-and-definitions-instructions.md` is the
requirements it satisfies. Settled 2026-07-07 by a three-proposal design committee grounded in the
code and adversarially scored on faithfulness to the §7 acceptance criteria, fit to the current code,
fail-closed safety, and simplicity.

## The decision: Realization B, a persistent definitions store

The requirements doc prefers Option A — one append-only scoped log, folded within scope, never
deleting on New Game. The committee rejected it for this code. Option A forces a global sequence
(today `seq` is per-game: `LogActor` mints `seq = log.size + 1` over a fresh empty log each game),
which breaks the SSE dedup and the frontend refill-from-1; it replaces the clean cancel-and-refork
restart with a live-across-games actor plus scoping filters at six backend read sites and every
frontend working map; and its one advantage — keeping prior games for replay — is an explicit
non-goal (§6). The fail-closed surface it opens (a slip at any fold site leaks a prior game's
refutation into a live belief) is the hazard that dominates ties.

The current code — clean per-game teardown, per-game `seq`, a fresh empty `LogState` each game — is
already the two-scope shape. Realization B keeps all of it and adds a session-scoped persistent store
harvested on New Game and replayed into each fresh game as belief-inert events. Persistence is
belief-inert by type: the store is a `Map[Term, Definition]` that cannot represent a hypothesis.

## Two scopes

- **Working memory (game-scoped)** — unchanged: the per-game `LogActor` log, cleared and re-minted
  from `seq = 1` each game.
- **Persistent memory (session-scoped)** — new: a `Ref`-backed store of established definitions,
  harvested on New Game, cleared on Full Reset, replayed into each fresh game at the head of its log.

## Types (`…/claimalgebra/society/`)

- `GameId` — an opaque `Int` session counter (`first` / `next` / `value`; a monotone counter has no
  illegal state, so it takes no validating constructor).
- `DefinitionProvenance` grows `gameId: Option[GameId] = None` — filled `Some(g)` at the persistence
  boundary; `None` means the current, not-yet-persisted game.
- `Event.DefinitionRemembered(seq, timestamp, term, meaning, origin: DefinitionProvenance)` — a
  distinct belief-inert variant, not a re-emitted `DefinitionGiven`. It carries origin provenance
  (which game, agent, and question first established it), never a current-game exchange, and has no
  agent in the who-spoke read. It must be distinct because the cross-game question-id collision is
  guaranteed — both games mint `q1` — so a remembered definition has to be structurally excluded from
  `governingTerms`.
- `DefinitionMemory` — the store: `Ref[IO, Map[Term, Definition]]` with `recall` / `remember(current,
  log)` / `clear`. `remember` harvests `Definitions.established(log)`, stamps `gameId None →
  Some(current)` (a carried `Some(g)` is preserved so origin never drifts), keyed by the normalized
  term.

## The fold within scope

- **Belief:** `GameCore.project` gains one drop-case (`DefinitionRemembered → Nil`). Belief reads only
  the current game's working events; seeded definitions project to nothing, so belief begins at `gap`
  regardless of how many are seeded.
- **Vocabulary:** `Definitions.from` folds the new variant; `Definitions.established`'s latest-wins now
  merges recalled and this-game definitions, this-game winning — the supersession path. `GameView.from`
  already calls `Definitions.established`, so agents see carried definitions from question one with no
  new retrieval code.
- **Frontend fold:** a belief-inert `definition_remembered` case that does not touch
  `definitionsByQuestion` (a recalled definition belongs to no this-game question, so it must not
  perturb the ordering gate on a colliding qid); the definitions derivation folds it latest-wins; the
  who-spoke read returns `undefined`. Each is exhaustiveness-forced.

## Sequence and transport

`seq` stays per-game. After New Game the stream carries the current game only: the seeded
`DefinitionRemembered` events at `seq` 1..K, then the working events. Prior games' working events are
gone. New Game and Full Reset both reuse the frontend `reconnect()` — drop the array, reopen, catch up
from the fresh log. The only new frame on the wire is `definition_remembered`.

## Reset mechanics

`GameSupervisor` keeps the cancel-and-refork spine under its mutex and exposes two methods:

- `newGame` — cancel and await the old game, **harvest the definitions before clearing** the working
  log, bump the game counter, recall the seed, fork a fresh game seeded with it.
- `fullReset` — cancel, clear the working log, clear memory, reset the counter, fork a fresh game with
  an empty seed (byte-identical to the first game).

`Society.play` gains one default argument `seed: List[Definition] = Nil`, threaded into `LogDeps` and
emitted as `DefinitionRemembered` events at `onBegin` before round one. `seed = Nil` leaves `onBegin`
byte-identical to today, so the empty-memory path and every existing test are untouched.
`SocietyRoutes` maps `POST /start → newGame` and adds `POST /reset → fullReset`.

## Challenging a remembered definition

Reuses the shipped clarification path. A fresh game seeds `DefinitionRemembered(alive, D1)`; the human
challenges "alive"; the current question's proposer redefines it as D2; `Definitions.established`
latest-wins surfaces D2 to agents and the panel — a supersession. The D1 event stays in the log (trace
retained), and the next New Game harvests D2. Supersession stays at the definitions-read level
(latest-wins by term); it is never routed through refute or strike, so definitions stay belief-inert.
A graded superseded-definition con-channel chain is a future slice, not this pass.

## Frontend and transport

Additive and exhaustiveness-forced: the `definition_remembered` union member and decoder; one case each
in the fold, the definitions derivation, and the who-spoke read; a "recalled from game N" badge in the
definitions and memory panel rendered from `origin` (never the event `seq` — the visible audit
surface); a Full Reset button with a confirm and tooltip; a `postReset` client; New Game reuses
`postStart → reconnect`. One new route.

## Fail-closed invariants (the adversarial-verify confirms these)

1. **Belief-inertness and seed-invariance** — `project(DefinitionRemembered) = Nil`, and
   `belief(seed ++ working) = belief(working)` for any seed.
2. **The store cannot represent a hypothesis** — type-level, `Map[Term, Definition]`.
3. **New Game clears all working evidence** — no prior-game candidate, backer, or refutation reaches
   the next game's belief or no-lone-sign floor.
4. **Harvest-then-clear fails closed** — a harvest failure raises before the clear, so the log is
   intact and nothing is lost.
5. **No definition lost on New Game** — every established definition is harvested; loss occurs only on
   Full Reset.
6. **The empty-memory path is byte-identical to today.**
7. **Distinct-event integrity** — `DefinitionRemembered` is never a governing term (structurally), and
   the frontend case never writes the ordering-gate state.
8. **Provenance stability** — origin never drifts across generations (idempotent under latest-wins).
9. **Sequence and transport unchanged.**

## Slice plan

1. **Scope model + fold** — the machinery and the belief-inertness proof; no reset behavior change.
2. **Reset mechanics + Full Reset** — the two buttons' behavior, structurally.
3. **Frontend + challenge-a-remembered-definition** — the visible panel, the human override, and the
   payoff: game two does not re-ask what "alive" means.
