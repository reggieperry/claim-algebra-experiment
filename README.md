# claim-algebra-experiment

A verifiable **claim algebra** for inter-agent messages — a small, lawful object that carries a
value together with everything a downstream agent needs to trust or distrust it: where it came from,
how strongly it is supported, whether anything refutes it, and whether it reproduces against its
sources. The premise is that language models are not trustworthy — they produce confidently-wrong
outputs — so the interesting question is what agents should *say to each other* given that. This
repository is the algebra, its operational calculus, and a live testbed that measures whether the
structure actually buys anything.

It is research code, developed in the open method it documents: every substantive claim about the
system is checked mechanically before it is believed, and the review discipline travels with the
repo (`.claude/`, `ledger/`).

## What is inside

- **The library** (`claim-algebra/`) — the pure core. A graded distributive bilattice (Belnap's four
  values — true, false, both/glut, neither/gap — over a for/against twist), a free provenance
  semiring ℕ[X] whose grade is *rendered* on demand rather than stored, and the acceptance `Gate`
  that signs a value only when it is unambiguously true, clears a threshold, and verifies. On top of
  that sits the operational **calculus** (`claimalgebra.calculus`): belief state is a pure fold over
  an append-only event log, with the meta-theorems proven as tests. Depends only on cats-core and
  `algebra` — no cats-effect, no SDK — so the pure/effectful seam is structural, and the library is
  domain-neutral by a committed gate (`scripts/library-neutrality.sh`).

- **The reasoning-society testbed** (`reasoning-society/`) and the **fallible-oracle program**
  (`docs/reasoning-society/`) — a society of small LLM agents that plays Twenty Questions with its
  reasoning always visible: competing hypotheses, grades rising and falling, contradictions held
  explicit rather than averaged away, and a gate that refuses to guess until confidence is earned.
  The experiment program stress-tests it against a *deliberately unreliable* oracle and reports what
  survives — including a negative result and a refuted-then-corrected one.

- **The differential gate** (`gate/`) — a self-contained Scala port of an anti-weakening gate that
  blocks regressions versus the merge-base (net-new lint findings, new suppressions, deleted or
  skipped tests, coverage drops). Ships as a standalone fat jar. It is a clean-room Scala port — the
  Go original it descends from is not included here.

- **The dev-ledger harness** (`ledger/`) — a claim ledger over this repo's own development, gated at
  commit: a "done" report is an *unverified* claim; only a mechanical check signs; review is
  testimony. See `ledger/README.md`.

## Run the Twenty Questions observability tool

Two parts joined by an event log — the backend produces claims, the browser only observes them.

```bash
# backend: the SSE server that runs the society and streams its event log
sbt "reasoningSociety/runMain claimalgebra.society.RunServer"

# frontend: the observability UI (a pure viewer of the log)
cd reasoning-society/frontend && npm install && npm run dev
```

An `ANTHROPIC_API_KEY` in the environment is required for the live agents (API-key auth only).
`reasoning-society/README.md` and `reasoning-society/frontend/README.md` carry the details.

## Reading map

**The algebra and its calculus** (`docs/claim-algebra/`)
- `claim-algebra.html` — the canonical note: the bilattice, the ℕ[X] provenance, the gate, worked end to end.
- `claim-calculus.html` — the operational calculus: the event fold and the four-state read.
- `claim-algebra-foundations.html` / `claim-algebra-general.html` — the formal foundations and the general structure.
- `claim-algebra-novelty.md` — how it sits against prior four-valued-logic and provenance work.

**The reasoning-society testbed and the fallible-oracle program** (`docs/reasoning-society/`)
- `auditable-society-of-minds-v1.md` — the architecture of record.
- `twenty-questions-build-brief.md` — the first buildable window (the observability tool).
- `fallible-oracle-experiment-design.md` — the experiment design.
- `fallible-oracle-results.md` — the measured results and the threats-to-validity ledger.
- `fallible-oracle-report.html` and `statistics-visual-guide.html` — the visual readers.

## Build and test

Scala 3 (LTS) + sbt.

```bash
sbt check     # scalafmt + scalafix (Scalazzi) + library-neutrality + the full suite
sbt test      # munit + ScalaCheck (property/law-first)
```

The public module set is `claim-algebra` (the pure library), `extract` (the shared LLM-call and
grounding layer), `reasoning-society` (the testbed), and `gate` (the differential gate) — 872 tests
in all (claim-algebra 514, extract 29, reasoning-society 268, gate 61).

## Git hooks (dev-ledger + secret scan)

The commit-path gate and the ledger live in `.githooks/` and `ledger/` but are wired into no clone's
config by default. To enable them:

```bash
scripts/setup-hooks.sh    # sets core.hooksPath to .githooks
```

## Provenance

Developed privately 2026-06-24 → 2026-07-09; the internal commit dates and ordering are preserved in
this filtered public derivative, and the first public commit is this one. Those pre-publication
timestamps are local, self-consistent, ordinary evidence of when the work was done — not notarized
proof; the repository's *public* timestamps begin at this push.

## License

Code is licensed under **Apache-2.0** (see `LICENSE`); its explicit patent grant makes the
timestamped disclosure legible as a defensive publication. The research documents under `docs/` may
alternatively be used under **CC-BY-4.0** — cite the repository and tag.
