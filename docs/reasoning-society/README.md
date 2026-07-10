# docs/reasoning-society

The design, the experiment program, and the build record for the **reasoning-society** testbed — a
society of small LLM agents that plays Twenty Questions with its reasoning always visible (competing
hypotheses, grades rising and falling, contradictions held explicit rather than averaged away),
governed by the claim algebra's gate. The code lives in `reasoning-society/` (a Scala backend + a
React observability UI); this directory is everything written about it.

The founding premise is that language models are not trustworthy — they produce confidently-wrong
outputs — so the program measures **where confidence attaches when the checker itself is unreliable**
(the *fallible oracle*), and what a lawful gate does about it. The headline concern is *fail-open*: a
gate signing a wrong answer.

**▸ The essay — [building-jarvis.html](building-jarvis.html)** — *Building JARVIS using a game of twenty questions*: five measured findings about when to believe an AI, in narrative form. The best entry point; everything below is the record behind it.

## Start here

1. **[auditable-society-of-minds-v1.md](auditable-society-of-minds-v1.md)** — the architecture of record: what the society is and why.
2. **[twenty-questions-build-brief.md](twenty-questions-build-brief.md)** — the first buildable window: Twenty Questions as an oscilloscope for a reasoning society.
3. **[fallible-oracle-results.md](fallible-oracle-results.md)** — the measured results and the live threats-to-validity ledger. The single most important document here.
4. **[statistics-visual-guide.html](statistics-visual-guide.html)** and **[fallible-oracle-report.html](fallible-oracle-report.html)** — the two visual readers: the statistics explained for a general reader, and the engineering report on fail-open risk.

## The architecture and the build

- **[auditable-society-of-minds-v1.md](auditable-society-of-minds-v1.md)** — the architecture record.
- **[twenty-questions-build-brief.md](twenty-questions-build-brief.md)** — the build brief for the observability tool.
- **[build2-ui-design.md](build2-ui-design.md)** — the Build 2 UI design (the instrument's panels).
- **[fallible-oracle-experiment-design.md](fallible-oracle-experiment-design.md)** — the experiment design, testing, and analysis plan.
- **[fallible-oracle-build-plan.md](fallible-oracle-build-plan.md)** — the harness build plan: the sign paths, the redundancy/correlation curve, the (k, ρ) study.

## The experiment: results and reports

- **[fallible-oracle-results.md](fallible-oracle-results.md)** — every measured finding, arm by arm, with the threats-to-validity ledger kept live.
- **[fallible-oracle-report.html](fallible-oracle-report.html)** — "Fail-open risk in AI self-verification," the engineering report.
- **[statistics-visual-guide.html](statistics-visual-guide.html)** — "The numbers behind the experiments," a visual guide (Wilson intervals, power, correlation, channel capacity).
- **[fallible-oracle-interpretation-and-next-experiments.md](fallible-oracle-interpretation-and-next-experiments.md)** — interpretation and the questions that came next.

## The experiment cells (the individual studies)

- **[fallible-oracle-composed-cell-plan.md](fallible-oracle-composed-cell-plan.md)**, **[fallible-oracle-composed-cell-experiment.md](fallible-oracle-composed-cell-experiment.md)**, **[composed-cell-corrections-and-next-steps.md](composed-cell-corrections-and-next-steps.md)** — the composed cell (improved endgame × seam-gated × perfect oracle). Finding: the wins-versus-confident-wrongs coupling was a *seam-open artifact*, and fail-closed is productive, not merely safe.
- **[stronger-closer-trial-experiment.md](stronger-closer-trial-experiment.md)** — a stronger reasoner (Sonnet, then Opus) in the closing roles at a degraded oracle. Finding: the tier does not unblock; the bottleneck is the checker, not the reasoner.
- **[capacity-estimate.md](capacity-estimate.md)** — the channel-capacity estimate, committee-reconciled: refuted as a pure ceiling, kept as a channel + decoder decomposition.
- **[reveal-the-set-cell.md](reveal-the-set-cell.md)** — the decisive separating cell: revealing the candidate set unblocks at p = 0.7, so the null was *decoder*-bound, not channel-bound.
- **[next-work-capacity-and-feasibility.md](next-work-capacity-and-feasibility.md)** — the work order that framed those three studies.

## Feature build-notes and decision records

The "how it was built" trail — each a spec or a committee decision behind a slice of the tool:

- **[clarification-feature-instructions.md](clarification-feature-instructions.md)** — the clarification (two-move turn) feature.
- **[recovery-and-endgame-instructions.md](recovery-and-endgame-instructions.md)** — the recovery loop and the honest endgame (recovering from a poisoned premise; closing the guess).
- **[reset-and-definitions-instructions.md](reset-and-definitions-instructions.md)** and **[two-tier-reset-design.md](two-tier-reset-design.md)** — the two-tier reset and definitions-as-first-memory.
- **[grounded-refutations-instructions.md](grounded-refutations-instructions.md)** — grounded refutations (the hallucinated-Skeptic fix).
- **[glut-laundering-correction-instructions.md](glut-laundering-correction-instructions.md)** — the glut-laundering correction (use `conjoin`, never bare `derive`, on possibly-contested testimonies).
- **[librarian-convergence-monitor-instructions.md](librarian-convergence-monitor-instructions.md)** — the librarian convergence monitor (structural non-convergence detection).
- **[hypothesis-lifecycle-design.md](hypothesis-lifecycle-design.md)**, **[hypothesis-lifecycle-instructions.md](hypothesis-lifecycle-instructions.md)**, **[hypothesis-lifecycle-decision-record.md](hypothesis-lifecycle-decision-record.md)**, **[hypothesis-lifecycle-review-addendum.md](hypothesis-lifecycle-review-addendum.md)** — the hypothesis-lifecycle work (channel-asymmetry retirement + gate scoping §C) and its adversarial review.
- **[sec-C-pressure-test-focusing-note.md](sec-C-pressure-test-focusing-note.md)** — the focusing note for the §C pressure-test.
- **[unpinned-referent-decision-record.md](unpinned-referent-decision-record.md)** and **[unpinned-referent-algebra-validation.md](unpinned-referent-algebra-validation.md)** — the unpinned-referent (multi-slot) committee decision and its validation against the algebra.
