# Trio — implementation spec

**A build spec for a Claude Code instance implementing Trio: Diplomacy's smallest variant, played by three LLM agents on the claim-algebra substrate, instrumented as a measurement apparatus from the first commit. This supersedes the seven-power draft of this file (which remains in git history); the seven-power design's rationale and trust mathematics stay canonical in `diplomacy-agent-game.html`, and the standard map remains the expansion target once a Trio result earns it. The rule set is `trio-rules.html` — the constitution; where this spec and the rules disagree, the rules win. Why any of this: the Diplomacy program brief.** 2026-07-10.

## 0. The spine (unchanged from the seven-power draft, quoted because it must not be lost)

- **Two regimes meet at adjudication.** The substrate (board, orders, adjudication) is the consistent, glut-free fragment of the algebra: deterministic, fail-closed, auditable — no agent can hallucinate the board or make an illegal move. Negotiation stays inside the algebra: a promise is a claim whose verification is deferred (now formalized as the commitment kind — `claim-calculus.html` Appendix A, `claim-algebra.html` §9.4), and trust is the graded bilattice opinion updated by resolutions.
- **The adjudicator is the verification oracle and the spine.** Pure, deterministic, correct against the Trio-relevant DATC subset. Build it first, with no LLM in sight.
- **The LLM is called in exactly two bounded places** per power per phase: `negotiate` and `chooseOrders`. It may lie in the first; it cannot falsify the board in the second.
- **Fail-closed everywhere.** Illegal/unparseable/missing order → Hold (rules R4.2). Ill-formed press blocks → ignored and logged. Half-resolved board → never observed.

**Non-goals (v1):** the standard seven-power map; fleets, coasts, convoys; a graphical board beyond a debug SVG; RL or self-play training; rating chases. One instrumented Trio game between three scripted-then-LLM agents, replayable byte-identically, with a per-commitment kept/broken record — then the D-cells.

## 1. Tech and layout

- **Language:** Scala 3 (the repo's `3.3.x` LTS). **Actor layer: the repo's own** — `claimalgebra.society.{Actor, ActorSystem, Mailbox, Protocol, GameSupervisor}` — not Pekko. Rationale: it exists, it carried the entire fallible-oracle program (614 archived games), and one fewer dependency. The seven-power draft's Pekko choice predates this substrate's existence.
- **LLM provider:** the existing `extract` facade — `LlmProvider.openaiOver` / `anthropicOver` — already multi-provider and air-gap-ready (a locally served OpenAI-compatible endpoint slots in via a base-URL'd client). Do not add a second seam.
- **Modules** (new code under `reasoning-society` unless noted): `trio.engine` (pure: map, board, legality, adjudication — no actors, no LLM, no IO), `trio.commit` (the commitment kind per calculus Appendix A: types, lifecycle, the per-game ledger), `trio.game` (the actor topology, phase state machine, press router, Chronicle), `trio.agents` (the `PowerAgent` trait, a scripted implementation, the LLM implementation), `trio.experiment` (the D-cell mains, reusing `ConfigStamp`, `Archiver`, `Rate`, `ProportionDiff`).
- **Naming note:** `society.experiment.Adjudication` already exists — it is the fallible-oracle per-game `PrimaryOutcome` enum. The board adjudicator lives in `trio.engine` and never shadows it.

## 2. Two ledgers, one schema — do not conflate them

The **dev-ledger** (`ledger/claims.jsonl`, hooks, `audit.py`) tracks the *program's* claims — pre-registrations, results, findings — exactly as it does today; nothing here changes how it signs. The **game commitment ledger** is a *per-game* record in the same JSON schema, written by the game itself into the game's archive directory (e.g. `commitments-<game-id>.jsonl` beside the Chronicle), auditable by `audit.py` **once `audit.py` is extended for the commitment kind** — a small, additive M2 subtask with its own fixtures:

- Add `commitment` to `audit.py`'s `KINDS` (today `{assertion, testimony, refutation}`). This is *additive* — every existing dev-ledger entry validates identically, so the validator's behavior on the program ledger is unchanged.
- The required tuple is the real one — `id / ts / claim / source / kind / status` — kept mandatory; the field list is not shortened.
- Discharge uses `audit.py`'s **nested** `discharged_by` object, not a bare string: `discharged_by: { check: "trio-adjudicator", run: "<the adjudication event id>" }`; supersession and retirement (including either-party elimination) per rules R9.4/R9.2 and calculus Rule A.3.

So the accurate claim is **one new kind, one new check family (`trio-adjudicator`), one additive validator change, and zero changes to how anything signs** — not "zero new signing paths, schema unchanged". Game commitments never enter the repo's dev-ledger; program-level claims about games (a D-cell's result) do, as always.

## 3. Milestones (dependency order; each ships and is testable)

| # | Milestone | Done when |
|---|---|---|
| **M0** | **Engine.** The 13-province graph (rules R1.2 verbatim as data), `BoardState`, `legalize`, `adjudicate` for movement, retreats, and Winter adjustments. No LLM, no actors. | The Trio-relevant DATC subset passes (§6); property tests hold (§6); any order set on any reachable board resolves deterministically and purely. |
| **M1** | **Game loop, scripted agents.** Actor topology on the society substrate, the phase state machine (press → orders → adjudicate → retreats → Winter), the Chronicle, NMR/illegal fail-closed, the game header line (rules R11.3) in the `Archiver` convention. | Three scripted agents (built on the `StubLlm` pattern) play 1901 → cap or solo; the game replays byte-identically from the Chronicle; a deliberate NMR resolves to Hold. |
| **M2** | **Commitments.** `trio.commit`: the grammar of rules §9 parsed from press blocks, the lifecycle events (propose/accept/rescind/supersede/resolve), the per-game ledger, resolution wired into adjudication. | A scripted betrayal (pledge support, order the attack) lands as `refuted` with the adjudication receipt; a kept pledge lands `signed`; an unmet guard retires void; a post-acceptance rescind is unrepresentable (compile-time or rejected event, logged); the fixture set includes one VOID per reason (`elimination`, `solo-unmatured`, `cap`, `guard-unmet`) and the two-phase horizon rejection (R9.6); `audit.py` (extended per §2) passes on the game ledger. |
| **M3** | **LLM orders, then LLM press.** Swap scripted `chooseOrders` for the LLM behind the facade (orders only, no press); then enable LLM `negotiate` with the commitment blocks. Frames compiled per phase: board, history, standing commitments, reputation pairs — the reveal-cell lesson as a design rule. | An all-LLM game completes within the cap with zero illegal-order crashes (annihilator absorbs); commitments form, resolve, and the reputation pair moves; cost per game is measured and recorded. |
| **M4** | **D0 — the apparatus cell.** Not a demo: the pre-registered apparatus validation from the brief's ladder, run and written up in the house grammar. | D0's acceptance battery is green (engine DATC subset, betrayal registration, replay determinism, seeded reproducibility across two hosts); the D0 record is archived with stamps; the program's dev-ledger gains the D0 claim, discharged. |

D1–D3 follow as pre-registered cells per the brief; they are experiment specs, not milestones of this build.

## 4. Data model (build these types; armies only in v1)

```scala
enum Power  { case A, B, C }                       // Aval, Brum, Cind
final case class ProvId(code: String)               // "aval","nord","mer","quor",…
final case class UnitAt(power: Power, prov: ProvId) // Kind omitted in v1: armies only
                                                    // (reintroduce Kind with fleets on the standard map)
enum Order:
  case Hold(at: ProvId)
  case Move(from: ProvId, to: ProvId)
  case Support(at: ProvId, target: Order)           // target is Hold or Move (rules R4.1)

enum Season { case Spring, Fall }
final case class Horizon(season: Season, year: Int) // rules R9.6 bounds

final case class Commitment(
  id: CommitId, by: Power, to: Power,               // to = counterparty (R9.1); to != by
  pattern: Order,                                   // pattern over `by`'s own units (R9.1)
  horizon: Horizon, guard: Option[Order],           // an order by `to` at the same horizon (R9.3)
  state: CommitState                                // Proposed | Active | Kept | Broken | Void | Superseded(next: CommitId)
)                                                   // Void = retired unresolved with recorded reason (R9.2/R9.3): guard-unmet, elimination,
                                                    // solo-unmatured, or cap. Any game-end/elimination before the horizon voids — v1 is BLUNT,
                                                    // no causation (the causation model is the recorded v2). Horizon <= 2 phases ahead (R9.6).
                                                    // supersede is admissible only when next.to == to (R9.4)
```

The adjudicator: `adjudicate(board: BoardState, orders: Map[Power, Set[Order]]): (BoardState, Resolutions)` — pure, total, deterministic (rules §5). Commitment resolution consumes `Resolutions` and the submitted order sets (rules R9.2's legality-substitution point: match against post-annihilator submissions).

## 5. The Chronicle and the archive

Every event — press message (verbatim), commitment event, submitted orders, adjudication, retreat, adjustment — appends to the Chronicle in order. The game record is one directory per game under the archive convention the digest tooling already distills: header line first (`# arm=… cell=… seed=… outcome=… stamp=…`), Chronicle, commitment ledger. `ConfigStamp` covers: map version, rules version, press rounds `R`, message cap, seed, agent/model pins per power. The D-cell digests and the verify-suite pattern (the results-reproduce check) apply to Trio tables the week they exist — that is the point of instrumenting from commit one.

## 6. Engine acceptance (M0 gate)

- **DATC subset, armies-only.** The fixtures are the ground truth; **reconcile every case id against the DATC suite at build time — DATC is not vendored in this repo**, so the ids below are the intended coverage, not a verified transcription. The fleet/coast/convoy families (6.B coasts, 6.F/6.G convoys) are dropped entirely — no D-cell exercises them.
  - **6.A basic legality and movement**, army cases only. The illegal/unparseable checks that in Trio reduce to "not adjacent / no such unit" are the annihilator's job (R4.2), asserted by one explicit fixture (e.g. the 6.A.8-class illegality → Hold), not re-litigated case by case.
  - **6.C circular movement, 6.C.1–.3** — including **6.C.3**, the *disrupted*-circular case that R5.7's "unless broken by a standoff" exception governs (previously omitted).
  - **6.D supports and cuts, the army-only subset of 6.D.1–.34** — the heart of Trio.
  - **6.E head-to-head and beleaguered garrison, army cases.** Head-to-head is the early 6.E cases; the *beleaguered-garrison* cases begin at **6.E.7**, so the army-only members are **6.E.7 / .9 / .13** (and their head-to-head neighbours) — the earlier "6.E.1–.6, beleaguered garrison" label was wrong in both the index and the title.

  Each translated case is a fixture (board, orders, expected resolution); the M0 gate is the fixture suite passing, not this prose.
- **Property tests:** adjudication is a pure function (same inputs, same output, across JVM runs and hosts); at most one unit per province after every phase; no self-dislodgement (R5.5); every dislodged unit retreats legally or disbands; SC counts and unit counts agree after every Winter; the annihilator is total (any garbage order set resolves).
- **Map integrity:** the adjacency table in code equals rules R1.2 (a test literally diffs the two — the rules file is the source of truth; parse it or mirror it as a reviewed fixture).

## 7. Determinism, seeds, and cost

All agent randomness draws from the game seed (rules R11.1); paired-arm designs (D2's receipts twin) share seeds exactly as `RunRevealSet` did. Record wall-clock and token counts per phase in the Chronicle so the brief's cost estimates become measured constants by M3.

## 8. Build order (concrete first steps)

1. `trio.engine`: province graph as data + `BoardState` + `legalize` + movement `adjudicate` → DATC 6.A/6.D fixtures green.
2. Retreats and Winter → remaining fixtures + property tests → **M0**.
3. `trio.game`: actors on the society substrate, phase machine, Chronicle, header line → **M1** with scripted agents.
4. `trio.commit`: grammar, lifecycle, per-game ledger, resolution wiring → **M2** (the betrayal fixture is the milestone's soul).
5. `trio.agents`: LLM `chooseOrders` behind the facade, then `negotiate` with commitment blocks and compiled frames → **M3**.
6. `trio.experiment`: the D0 main, stamps, archive, digests → **M4**, then the D-cells per the brief.

Keep the engine pure and DATC-green at every step; it is the verification oracle the entire game — and the commitment calculus's adequacy claim — depends on.

*End — 2026-07-10. The constitution is `trio-rules.html`; the theory is §9.4 and Appendix A; the reasons are the brief. Build the referee first.*
