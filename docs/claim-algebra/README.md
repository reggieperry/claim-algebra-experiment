# The claim algebra — document map

The verifiable claim algebra is a graded distributive bilattice — Belnap's four values via a for/against twist over the free provenance semiring ℕ[X] — carried as the typed, fail-closed, trust-pluggable contract on the wire between agents. The documents here develop it, prove it, test it, and apply it. This map says what each one is, the order to read them in, and which are current versus superseded, so the set reads as one connected body of work rather than a folder of files.

The implementation these describe is [`../../src/main/scala/claimalgebra/`](../../src/main/scala/claimalgebra); the project's status and conventions are in [`../../CLAUDE.md`](../../CLAUDE.md).

## The algebra — read in this order

1. **[`claim-algebra.html`](claim-algebra.html)** — **start here.** The canonical note: the inter-agent message as a bilattice-graded object, presented as a three-level tower (category → algebra → Scala type) and worked through credit analysis, cost routing, and Diplomacy. It consolidates the two earlier drafts; everything else companions it.
2. **[`claim-algebra-general.html`](claim-algebra-general.html)** — the same design in pictures: the candidate-map carrier (G1), the never-stored rendered grade (G2), kinded citations (G3), the four combiners, and the gate. Read alongside the canonical note.
3. **[`claim-algebra-foundations.html`](claim-algebra-foundations.html)** — the formal corpus: 123 properties stated as theorems with proofs, and the **relaxation table** — the essential-versus-relaxable contract between the mathematics and the code. The reference when deciding what an implementation may vary.
4. **[`claim-calculus.html`](claim-calculus.html)** — the operational meta-theory: a small reduction system over evidence-event sequences, with eight proved meta-theorems (determinacy, normalization, confluence, fail-closed propagation, and more) and two deliberate non-theorems. The semantics behind the Ledger fold and the signing gate.

## The experiment

5. **[`falsifying-the-claim-algebra.html`](falsifying-the-claim-algebra.html)** — the design: the nine-node credit topology, the three arms (naive prose, disciplined baseline, claim algebra), the confidently-wrong-at-signature (CWS) headline metric, and the F1–F8 fault matrix.
6. **[`falsification-experiment.md`](falsification-experiment.md)** — the build-shape outline behind that design (the pre-registration-grade detail).
7. **[`wiring-the-falsification-rig.html`](wiring-the-falsification-rig.html)** — the rig architecture: a boxes-and-arrows view of the built harness — the sealed-key boundary, the `Testimony`/`Claim` type layer, the node seams.
8. **[`experiment-findings.md`](experiment-findings.md)** — the running results and validity-audit log. **Read this for what the experiment actually established.**

## Around the algebra

- **[`library-overview.html`](library-overview.html)** — the plain-language plain-language overview (non-technical).
- **[`claim-algebra-novelty.html`](claim-algebra-novelty.html)** — a citation-verified prior-art assessment: the novelty is a synthesis of settled parts, not an invention.
- **[`diplomacy-agent-game.html`](diplomacy-agent-game.html)** — the algebra's third, adversarial instance (seven LLM powers playing Diplomacy over a trust semiring).
- **[`claim-object-activation-design-brief.md`](claim-object-activation-design-brief.md)** — a design brief for activating the *full* claim object (all four coordinates, the glut, the confidence/verification axes) in an early-stage, multi-document deal corpus. Its credit-workbench Scenario-A design of record is [`../credit-deal-workbench/in-flight-deal-design.md`](../credit-deal-workbench/in-flight-deal-design.md).

## Superseded drafts — kept for history

These predate the canonical note and are retained only for design history. **Read [`claim-algebra.html`](claim-algebra.html) instead.**

- **[`verifiable-claim-algebra.html`](verifiable-claim-algebra.html)** — Revision 1, the original single-channel semiring design.
- **[`claim-algebra-belnap.html`](claim-algebra-belnap.html)** — Revision 2, which completed Rev 1 into the two-channel Belnap bilattice. Both are consolidated into the canonical note.

## Sibling lines

- **[`../credit-deal-workbench/`](../credit-deal-workbench)** — the N=1 product specialization (the live tool over real EDGAR deals) and its findings.
- **[`../actors/`](../actors)** — the actor-model canon for the eventual general agent system (not the experiment).

## Findings worth knowing

- **Structuring beats prose live, but the bilattice does not isolate a measurable edge over a fair control** — its value is parsimony and disclosure, not accuracy ([`experiment-findings.md`](experiment-findings.md), findings 5 and 7).
- **How well each model exercises the bilattice.** The multi-reader panel's conflict cross-examination across model tiers: surfacing a document-internal Conflict is frontier-grade and self-grounding-only — **Opus 3/3, GPT-5.4 full 2/3, Sonnet 0/3, the cheap tiers 0**; the Citations API converges on the operative value regardless of tier (recall, not conflict); the catch is probabilistic. The table and the analysis are in [`../credit-deal-workbench/live-demo-findings.md`](../credit-deal-workbench/live-demo-findings.md) — **Run 10**.
