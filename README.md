# claim-algebra-experiment

A verifiable **claim algebra** for inter-agent messages — a small, lawful object that carries a value
together with everything a downstream agent needs to trust or distrust it: where it came from, how
strongly it is supported, whether anything refutes it, and whether it reproduces against its sources.
The premise is that language models are not trustworthy — they produce confidently-wrong outputs — so
the question is what agents should *say to each other* given that. This repository is the algebra, its
operational calculus, and a live testbed that measures whether the structure actually buys anything.

It is research code, developed in the open method it documents: every substantive claim about the
system is checked mechanically before it is believed, and the review discipline travels with the repo
(`.claude/`, `ledger/`).

**Read online** — three rendered pages (GitHub Pages):
- **[Building JARVIS using a game of twenty questions](https://reggieperry.github.io/claim-algebra-experiment/docs/reasoning-society/building-jarvis.html)** — the essay: five measured findings about when to believe an AI. *Start here.*
- **[Fail-open risk in AI self-verification](https://reggieperry.github.io/claim-algebra-experiment/docs/reasoning-society/fallible-oracle-report.html)** — the engineering report.
- **[The numbers behind the experiments](https://reggieperry.github.io/claim-algebra-experiment/docs/reasoning-society/statistics-visual-guide.html)** — the visual guide to the statistics.

## Components (the sbt modules)

The build is multi-module; packages stay `claimalgebra.*` across modules, and the hard seam is
pure-versus-effectful — the library carries no cats-effect and no SDK.

| Module | Dir | Role | Depends on |
|---|---|---|---|
| **claim-algebra** | `claim-algebra/` | The pure library: the graded distributive bilattice (Belnap's four values — true, false, both/glut, neither/gap — over a for/against twist), the free provenance semiring ℕ[X] whose grade is rendered on demand rather than stored, the acceptance `Gate`, and the operational **calculus** (`claimalgebra.calculus` — the event-fold `Ledger` and the four-state read). cats-core + `algebra` only, and domain-neutral by a committed gate (`scripts/library-neutrality.sh`). | — |
| **extract** | `extract/` | Shared model/grounding infrastructure: the `LlmCall` facade + Anthropic/OpenAI adapters, the pure `Corpus` grounder, the domain value types, and the extractors. Effectful (cats-effect + both SDKs). | claim-algebra |
| **reasoning-society** | `reasoning-society/backend/` | The reasoning-society testbed (the "auditable society of minds") and the **fallible-oracle program**: a society of small LLM agents that plays Twenty Questions with its reasoning visible — competing hypotheses, grades rising and falling, contradictions held explicit — and a gate that refuses to guess until confidence is earned. Emits an ordered event log; belief state is a pure fold over it. | claim-algebra, extract |
| *(its UI)* | `reasoning-society/frontend/` | The React + Vite observability UI — a pure reader of the backend's event log. | — (TypeScript) |
| **gate** | `gate/` | The Scala differential gate (anti-weakening): blocks regressions versus the merge-base (net-new lint findings, new suppressions, deleted or skipped tests, coverage drops). A standalone fat jar; a clean-room port of a Go original not included here. | — |

Also in the tree: `ledger/` — the dev-ledger commit-path harness (a claim ledger over this repo's own
development; a "done" report is an *unverified* claim, and only a mechanical check signs); `.claude/`
— the coding and review discipline (craft-\* language-neutral + the scala-\* overlay, plus the review
workflows), which travels with the repo; `docs/` — the algebra, calculus, and experiment write-ups.

## Build and test

Scala 3 (LTS) + sbt.

```bash
sbt check              # scalafmt + scalafix (Scalazzi) + library-neutrality + the full suite
sbt test               # munit + ScalaCheck (property/law-first)
sbt gate/assembly      # the standalone differential-gate.jar
```

`sbt check` is the one gate the build must pass — formatting, the Scalazzi/Scala-3 scalafix rules,
the library-neutrality gate (`claim-algebra` stays domain-neutral), then the law-first suites — 880
tests in all (claim-algebra 514, extract 29, reasoning-society 276, gate 61). The frontend has its
own checks: `cd reasoning-society/frontend && npm ci && npm run build` (and `npm run check`).

## Run the Twenty Questions observability tool

Two parts joined by an event log — the backend produces claims, the browser only observes them.

```bash
# backend: the SSE server that runs the society and streams its event log
sbt "reasoningSociety/runMain claimalgebra.society.RunServer"

# frontend: the observability UI (a pure viewer of the log)
cd reasoning-society/frontend && npm install && npm run dev
```

The fallible-oracle experiment mains live in `claimalgebra.society.experiment` (e.g. `RunRevealSet`,
`RunComposedCell`, `RunStrongerCloser`) for reproducing the measured results. An `ANTHROPIC_API_KEY`
in the environment is required for the live agents (API-key auth only); `reasoning-society/README.md`
and `reasoning-society/frontend/README.md` carry the details.

## Git hooks (dev-ledger + secret scan)

The commit-path gate and the ledger live in `.githooks/` and `ledger/` but are wired into no clone's
config by default. To enable them:

```bash
scripts/setup-hooks.sh    # sets core.hooksPath to .githooks
```

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
- `verify-results-check-design.md` — the verify-results check: a munit suite (in `sbt check`) recomputes each headline result table from a committed receipt under `receipts/`, so the published numbers reproduce on any machine, and the dev-ledger signs the tables only when they do.

## Provenance

Developed privately 2026-06-24 → 2026-07-09; the internal commit dates and ordering are preserved in
this filtered public derivative, and the first public commit is this one. Those pre-publication
timestamps are local, self-consistent, ordinary evidence of when the work was done — not notarized
proof; the repository's *public* timestamps begin at this push.

## License

Code is licensed under **Apache-2.0** (see `LICENSE`); its explicit patent grant makes the timestamped
disclosure legible as a defensive publication. The research documents under `docs/` may alternatively
be used under **CC-BY-4.0** — cite the repository and tag.
