# The claim algebra — document map

The verifiable claim algebra is a graded distributive bilattice — Belnap's four values via a for/against twist over the free provenance semiring ℕ[X] — carried as the typed, fail-closed, trust-pluggable contract on the wire between agents. The documents here develop it, prove it, test it, and apply it. This map says what each one is, the order to read them in, and which are current versus superseded, so the set reads as one connected body of work rather than a folder of files.

The core of what these documents describe is implemented in the Scala library module [`claim-algebra/`](../../claim-algebra) — the bilattice, the ℕ[X] provenance semiring, the `Testimony`/`Claim` types, the calculus, and the acceptance gate.

## The algebra — read in this order

1. **[`claim-algebra.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra.html)** — **start here.** The canonical note: the inter-agent message as a bilattice-graded object, presented as a three-level tower (category → algebra → Scala type) and worked through credit analysis, cost routing, and Diplomacy. It consolidates the two earlier drafts; everything else companions it.
2. **[`claim-algebra-general.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra-general.html)** — the same design in pictures: the candidate-map carrier (G1), the never-stored rendered grade (G2), kinded citations (G3), the four combiners, and the gate. Read alongside the canonical note.
3. **[`claim-algebra-foundations.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra-foundations.html)** — the formal corpus: 123 properties stated as theorems with proofs, and the **relaxation table** — the essential-versus-relaxable contract between the mathematics and the code. The reference when deciding what an implementation may vary.
4. **[`claim-calculus.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-calculus.html)** — the operational meta-theory: a small reduction system over evidence-event sequences, with eight proved meta-theorems (determinacy, normalization, confluence, fail-closed propagation, and more) and two deliberate non-theorems. The semantics behind the Ledger fold and the signing gate.

## The experiment

The algebra was put to a falsification test — a controlled three-arm experiment (naive prose, disciplined baseline, claim algebra) over a nine-node credit-analysis topology with a sealed fault key, measuring confidently-wrong-at-signature (CWS) as the headline metric against an F1–F8 fault matrix. The harness wired it through a sealed-key boundary, a `Testimony`/`Claim` type layer, and transport-agnostic node seams; its running results and validity audit are summarized in the findings below.

## Around the algebra

- **[`claim-algebra-novelty.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra-novelty.html)** — a citation-verified prior-art assessment: the novelty is a synthesis of settled parts, not an invention.
- **[`diplomacy-agent-game.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/diplomacy-agent-game.html)** — the algebra's third, adversarial instance (seven LLM powers playing Diplomacy over a trust semiring).
- **[`trio-rules.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/trio-rules.html)** — the Trio game constitution: the minimal Diplomacy variant (three powers, thirteen provinces, six supply centers, armies only) reduced to the smallest board on which a promise can matter — map, orders, adjudication, and the commitment grammar the program measures.
- **[`diplomacy-program-brief.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/diplomacy-program-brief.html)** — the Diplomacy program brief: motivated deception meets the ledger. Why Trio, the D0–D4 experiment ladder, the commitment calculus (promises as a claim kind, discharged at their horizon), and the pre-registered predictions.
- **[`july-1914-claim-ledger.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/july-1914-claim-ledger.html)** — an interactive replay of the July Crisis as a fold over evidence events: a 1914 board, the bilattice diamond (position = rendered grade, badge = structural corner), and a steppable decision ledger. Six claims exercise the machinery natively — scope Ambiguity (the blank cheque), a glut with a CWS signature (the Serbian reply), a clean supersession (Russian mobilization), an absorbing strike (the Lichnowsky telegram), a decade-old commitment glut (Italy), and the betrayal corner (Belgium). Adversarially verified for history, algebra faithfulness, and code; self-contained, no external dependencies.
- **[`claim-algebra-law-audit-checklist.md`](claim-algebra-law-audit-checklist.md)** — the law & property audit checklist, reconciled against the algebra (§§3–5) and the calculus (§§4–8): every law/theorem tagged by whether the formalization backs it, which test covers it, and the two deliberate non-theorems not to test. Audited against the suite 2026-07-01 — coverage complete.

## Superseded drafts — kept for history

These predate the canonical note and are retained only for design history. **Read [`claim-algebra.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra.html) instead.**

- **[`verifiable-claim-algebra.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/verifiable-claim-algebra.html)** — Revision 1, the original single-channel semiring design.
- **[`claim-algebra-belnap.html`](https://reggieperry.github.io/claim-algebra-experiment/docs/claim-algebra/claim-algebra-belnap.html)** — Revision 2, which completed Rev 1 into the two-channel Belnap bilattice. Both are consolidated into the canonical note.

## Findings worth knowing

- **Structuring beats prose live, but the bilattice does not isolate a measurable edge over a fair control** — its value is parsimony and disclosure, not accuracy (findings 5 and 7).
- **How well each model exercises the bilattice.** The multi-reader panel's conflict cross-examination across model tiers: surfacing a document-internal Conflict is frontier-grade and self-grounding-only — **Opus 3/3, GPT-5.4 full 2/3, Sonnet 0/3, the cheap tiers 0**; the Citations API converges on the operative value regardless of tier (recall, not conflict); the catch is probabilistic (the multi-reader panel study, Run 10).
