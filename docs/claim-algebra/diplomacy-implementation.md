# Diplomacy agent game — implementation spec

A build spec for a Claude Code instance to start implementing a game of **Diplomacy played by seven LLM agents**, on the claim-algebra substrate, with the **actor model** as the communication layer. Design and rationale: `diplomacy-agent-game.html`; the parent framework: `verifiable-claim-algebra.html` (§8).

This spec is the *what and how to build*; the HTML is the *why*. Read both before starting.

## 0. The spine (do not lose it)

- **Two regimes meet at adjudication.** The **substrate** (board, orders, adjudication) is a clean verification-semiring instance: deterministic, fail-closed, auditable — *no agent can hallucinate the board or make an illegal move*. The **diplomacy** (negotiation) leaves the clean semiring: a promise is a claim whose verification is *deferred*, and its confidence is a subjective-logic *reputation* supplied by game theory, not by the algebra.
- **The adjudicator is the verification oracle and the spine.** It is a pure, deterministic function and must be **DATC-correct**. Everything else trusts it. Build it first, with no LLM in sight.
- **The LLM is called in exactly two bounded places** per power per phase: `negotiate` and `chooseOrders`. It may lie in the first; it cannot falsify the board or the rules in the second.
- **Fail-closed everywhere.** Illegal order → Hold (the per-order annihilator). Missing/crashed power → all Holds (NMR). Half-resolved board → never observed (the adjudicator is the serialization window).

**Non-goals (v1):** a graphical board; the full 17-province-coast map nuance can come after a small-map variant works; tournament infrastructure; training/RL. Get one full game between seven scripted-then-LLM agents to a solo or draw, auditable end to end.

## 1. Tech and layout

- **Language:** Scala 3. **Actors:** Apache Pekko Typed (or Akka Typed). The substrate types are pure and framework-free; only the communication layer depends on Pekko.
- **Modules:** `engine` (pure: board, orders, adjudication, DATC), `claims` (the `Claim[K, A]` types + the two semirings), `trust` (subjective-logic opinions + reputation), `agents` (the `PowerAgent` trait + a scripted impl + an LLM impl behind a provider seam), `game` (the actors + the loop + the Chronicle).
- **LLM provider:** behind a thin adapter (one `LlmCall` seam), API-key auth, model/provider configurable — do not hard-wire a vendor.

## 2. Milestones (dependency order — each ships and is testable)

| # | Milestone | Done when |
|---|---|---|
| **M0** | **Adjudicator + map.** Pure `adjudicate`, the province graph, the legality checker. | A DATC subset passes (see §6); given any order set on the standard map, the resolved board is correct. **No LLM, no actors.** |
| **M1** | **Game loop with scripted agents.** The actor topology, the phase state machine, the Chronicle, NMR/illegal fail-closed. | Seven scripted (deterministic, no-LLM) agents play a full game Spring 1901 → solo/draw; the Chronicle replays it deterministically. |
| **M2** | **LLM order selection.** Swap one or more scripted agents for an LLM `chooseOrders` (no negotiation yet). | An LLM agent plays legal orders for a full game; illegal suggestions are dropped to Hold; the game still terminates. |
| **M3** | **Negotiation + reputation.** LLM `negotiate`, the PressRouter, `Deal` claims, the subjective-logic reputation update from promise-vs-action. | Agents exchange deals, keep/break them, and reputations move measurably; a betrayal is visible in the Chronicle as a broken promise. |
| **M4** | **Full seven-LLM game + audit.** All seven LLM-backed; tournament-ready. | A complete game between seven LLM powers, fully replayable, with a per-deal kept/broken ledger. |

## 3. Data model (build these types)

```scala
enum Power  { case England, France, Germany, Italy, Austria, Russia, Turkey }
enum Kind   { case Army, Fleet }
enum Season { case Spring, Fall }
enum Phase  { case Movement, Retreat, Build }

final case class ProvId(code: String)                      // e.g. "mun", "gal", "nth" (sea), "stp/nc" (coast)
final case class UnitAt(power: Power, kind: Kind, prov: ProvId)

enum Order:
  case Hold(at: ProvId)
  case Move(from: ProvId, to: ProvId, viaConvoy: Boolean = false)
  case Support(at: ProvId, target: Order)                  // target is a Hold or a Move
  case Convoy(fleet: ProvId, army: Order.Move)
  // retreat/build phases reuse: Retreat(from,to), Disband(at), Build(at, Kind)

final case class BoardState(units: Set[UnitAt], owners: Map[ProvId, Power], year: Int, season: Season, phase: Phase)

enum Result { case Succeeded, Bounced, SupportCut, Dislodged, Disbanded, Illegal }
final case class Outcome(order: Order, result: Result)
final case class Outcomes(perOrder: Map[(Power, Order), Outcome], dislodged: Set[UnitAt])

// Claim types (specialize Claim[K, A] from the parent framework)
type StateClaim = Claim[Verified, UnitAt]                  // K = verification (Boolean); verify() vs the engine
type OrderClaim = Claim[Verified, Order]                   // legality-checked
final case class Deal(from: Power, to: Power, iPledge: Set[Order], iExpect: Set[Order])
type DealClaim  = Claim[Opinion, Deal]                     // K = trust; UNRESOLVED-as-fact until adjudication

// Trust layer (subjective logic)
final case class Opinion(b: Double, d: Double, u: Double, base: Double):  // b+d+u = 1
  def expected: Double = b + base * u
  def fuse(ev: Opinion): Opinion                           // cumulative fusion (corroborate)
  def discount(via: Opinion): Opinion                      // trust through a third party
final case class Reputation(of: Map[Power, Opinion]):
  def observe(p: Power, promised: Set[Order], actual: Set[Order]): Reputation
```

**The map.** Province graph: nodes are provinces (with type land/sea/coast and, for split-coast provinces, named coasts); edges are adjacencies typed by what can traverse them (army-land, fleet-sea, fleet-coast). Source the standard map from a known dataset; encode adjacencies as data, not code. **Builders/disbands** key off `owners` (SC control) vs unit count.

## 4. The Adjudicator contract (§0 spine — build first)

```scala
def adjudicate(orders: Map[Power, Set[Order]], s: BoardState): (BoardState, Outcomes)
```

- **Pure and total.** No I/O, no randomness; same inputs → same outputs (the replay guarantee).
- **Legality first.** `legalize(orders, board)` drops every illegal order to a `Hold` at that unit's province (the per-order annihilator) and records `Result.Illegal`. Adjudicate only legal orders.
- **Resolution rules** (the DATC content): support strength and **support-cutting**; **standoffs/bounces**; **dislodgement** and the **beleaguered-garrison** rule; **convoys** and convoy paradoxes; you cannot dislodge your own unit; a unit cannot move where it bounced. Retreat and build phases are separate `adjudicate` calls keyed on `phase`.
- **Acceptance:** a DATC subset (§6) passes; then the full DATC suite as M0 hardens.

## 5. The actors, the loop, and the fail-closed rules

Actors (Pekko Typed): **GameMaster** (phase clock + state machine), **Adjudicator** (the pure engine behind an actor, the serialization point), **PowerAgent ×7** (isolated, private state, LLM-backed), **PressRouter** (Deal routing; private + white/grey/black press), **Chronicle** (append-only provenance DAG). See `diplomacy-agent-game.html` §5–6 for the message protocol (`GMsg` / `AgentMsg` / `AdjMsg`) and the GameMaster behavior.

**The loop, per phase:** `PhaseStart` (board as StateClaims) → timed negotiation window (Deals via PressRouter; each agent updates reputation) → order deadline → private `OrdersFrom` submissions → GameMaster forwards the full set to the Adjudicator → atomic `Resolved` → broadcast `Adjudicated` (verified StateClaims) → each agent `reflect`s (promise-vs-action → reputation) → retreat/build sub-phases → victory/draw check → next phase.

**Fail-closed rules (non-negotiable):**
- **Illegal order → Hold** (`legalize`), recorded as `Illegal`.
- **No Moves Received → all Holds** for that power (`ordersOrHold`) on deadline or crash.
- **Serialization:** orders accumulate; the Adjudicator resolves **once, atomically**; no actor observes a partial board.
- **Supervision:** a crashed PowerAgent restarts from the Chronicle's public state, holds for the missed phase, resumes next phase. The game never blocks on one agent.

## 6. LLM-call contracts (the only two seams)

Both are bounded, structured, and behind the provider adapter. Inputs are the **verified** board + the agent's private reputation + its inbox; outputs are validated before use.

1. **`negotiate(board, reputation, inbox) -> Vector[Deal]`** — generate/respond to deals. Output: zero or more `Deal`s (each a structured `iPledge`/`iExpect` set of orders). Free-form rationale is allowed in the message text; only the structured pledge is adjudicated against later. *The model may be deceptive here — that is the game.*
2. **`chooseOrders(board, reputation, deals) -> Set[Order]`** — one order per owned unit. Output is **legality-checked**; illegal orders are dropped to Hold, so a hallucinated move can never execute. Prompt the model with the legal move set per unit to keep outputs in-bounds.

Reputation update (`reflect`) is **deterministic**, not an LLM call: compare each `Deal.iPledge` the agent was told about against the promiser's actual submitted orders (now visible post-adjudication), and `observe` a kept/broken outcome into the subjective-logic opinion.

## 7. Test fixtures and acceptance

- **Adjudicator:** the **DATC** suite [Kruijswijk]. Start with a subset — 6.A (basic moves), 6.B (coasts), 6.C (circular movement), 6.D (support-cutting), 6.E (head-to-head, beleaguered garrison), 6.F/6.G (convoys, paradoxes) — then the full suite for M0-complete.
- **Loop:** a scripted-agents full game replays byte-identically from the Chronicle (determinism); an NMR phase resolves to all-Holds; an illegal-order injection resolves to Hold.
- **Negotiation:** a scripted betrayal (pledge support, order an attack) registers as a broken promise and lowers the victim's opinion of the betrayer; a kept alliance raises it.
- **Acceptance (M4):** seven LLM powers play to a solo (18 SCs) or an agreed draw; the Chronicle yields a per-deal kept/broken ledger; the whole game replays deterministically from the log.

## 8. Build order (concrete first steps)

1. `engine`: province graph + `BoardState` + `legalize` + `adjudicate` (movement phase) → pass DATC 6.A–6.D.
2. Retreat and build phases → DATC where applicable; full-game state transitions.
3. `game`: the Pekko actors, the GameMaster state machine, the Chronicle, NMR/illegal fail-closed → M1 with scripted agents.
4. `agents`: the `PowerAgent` trait, a scripted impl, then the LLM impl behind the provider adapter → M2.
5. `trust` + PressRouter + `Deal` + `reflect` → M3.
6. Seven LLM powers, replay/audit tooling → M4.

Keep the `engine` pure and DATC-green at every step; it is the verification oracle the entire game — and the entire claim-algebra thesis — depends on.
