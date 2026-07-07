# reasoning-society

A claim-algebra-governed **society of LLM agents that reasons with its work always showing** —
competing hypotheses, grades rising and falling, contradictions held explicit rather than averaged
away, and a gate that refuses to guess until confidence is earned. A **personal research project**
(public/synthetic data, personal hardware and accounts only — brief §7).

- **Architecture of record:** [`docs/reasoning-society/auditable-society-of-minds-v1.md`](../docs/reasoning-society/auditable-society-of-minds-v1.md)
- **First buildable window:** [`docs/reasoning-society/twenty-questions-build-brief.md`](../docs/reasoning-society/twenty-questions-build-brief.md) — a browser observability tool ("an oscilloscope for a mind") watching a small society play Twenty Questions.

## Two parts

| Dir | Stack | Role |
|---|---|---|
| [`backend/`](backend/) | Scala 3 + cats-effect (sbt subproject `reasoningSociety`) | Runs the agent society; emits the ordered **event log**; belief state is a pure fold over it (the claim-calculus `Ledger`). |
| [`frontend/`](frontend/) | React + Vite | The observability UI — a **pure viewer** of the event log the backend emits. |

The seam between them is the event log: the backend *produces* claims, the interface only
*observes* them — the observer gets no vote. Live mode is replay with the playhead pinned to the
head, so there is one system, not two.

## Foundations it builds on (already shipped in this repo)

- The **fold** is the claim-calculus `Ledger` (`claim-algebra` module) — belief state is a pure
  function of `(event_log, playhead)`.
- The **actor substrate** is the mailbox design in [`docs/actors/mailbox-abstraction.md`](../docs/actors/mailbox-abstraction.md),
  lightly implemented on `cats.effect.std.Queue` (address, mailbox, one-at-a-time,
  send/create/designate) — no Akka/Pekko.
- The **gate** and the four-state read are `claimalgebra.Gate` and `claimalgebra.calculus`.

## Status

Scaffold — both trees are laid and building; behavior (the event model, the fold, the actors, the
agents; the UI panels) lands in the Build 1 slice.
