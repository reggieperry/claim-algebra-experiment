# The reveal-the-set cell — separating channel from decoder (item 3)

**The committee's decisive separating experiment, chosen over the confounded feasibility staircase. Item 2 refuted the pure-capacity framing and left the stronger-closer null as a JOINT channel+decoder ceiling; this cell isolates the two by collapsing the decoder's job. Reveal the eight-word target set to the society (so the open ~10–13-bit search becomes the true 3-bit identification) and re-run at p = 0.7 against the withheld-set baseline. If revealing the set unblocks correct signing, the open search was the binding constraint (decoder); if it does not, the channel binds even the collapsed 3-bit task.** 2026-07-09. Grounded in the capacity decomposition (`capacity-estimate.md`) and the stronger-closer null (D-Sonnet 0/64 at p = 0.7).

---

## The question

The stronger-closer null (W = S1 = D ≈ 0 at p = 0.7) has two candidate causes the capacity pass could not separate from the data alone: the **channel** (1.9 bits at p = 0.7, reasoner-independent) and the **decoder** (the society searches the open everyday-noun space because the protocol withholds the target set; endgame weak). The p = 1.0 corner already shows the decoder binds *there* (6/52 with a perfect channel). This cell tests whether the decoder's *open search* is what binds at p = 0.7: remove it, and see if the win rate moves.

## Design (the stronger-closer template, one variable changed)

Seam-gated (`corroborationSigns = false`), p = 0.7, `"correlated"` model, k = 1, `maxRounds = 16`, dev-8 targets, N = 64/arm, all-**Sonnet** society (the D allocation — the best decoder the trial found; Opus added nothing). Two arms, one surface, differing only in the independent variable:

| arm | society sees the candidate set? |
|---|---|
| **A — withheld** | no (current behaviour; reproduces the D-Sonnet null) |
| **B — revealed** | yes — a trusted preamble to every agent's system prompt: *"the hidden thing is exactly one of these eight, and no other: dog, apple, chair, spoon, book, tree, cup, shoe; identify which one"* |

The reveal is a **trusted** injection of the SET, never the target — the target stays sealed in the `ExperimentOracle`, so grounding/grading are unchanged (`TargetMatch`, exact on the dev-8 words). Per-arm logs archived; `w` recorded; the surface stamp differs by the preamble (the IV) and is printed.

## Pre-registration (fixed before the run)

1. **Primary contrast.** B's correct-rate vs A's, Newcombe hybrid-score CI on the difference. **Success = the CI excludes 0** (revealing the set changes the win rate).
2. **The reading.**
   - **B > A (reveal unblocks):** the open search / set-ignorance was the binding constraint at p = 0.7 — the null was **decoder-bound**, and a decoder that does not have to search the open space wins where the withheld one cannot. The stronger-closer null was capability/protocol, not channel.
   - **B ≈ A ≈ 0 (reveal does not help):** collapsing the search to eight candidates does not unblock — the blocker survives the collapse, so it is the **channel** (1.9 bits binds even the 3-bit task at n = 16, finite-blocklength) or a deep decoding limit independent of search width. This confirms the channel term of the decomposition binds.
3. **A reproduces the null.** A (withheld, all-Sonnet) should land near the stronger-closer D-Sonnet 0/64; a materially different A means drift, and the contrast carries that caveat.
4. **Fail-open is expected in B and is informative.** With the search collapsed, the society reaches guesses; at p = 0.7 a corrupted "yes" to a wrong candidate is a fail-open, a true "yes" to the right one is a win. So B's split (correct vs fail-open) is the channel made visible on the collapsed task — report both.
5. **One point, N = 64/arm floor**, Newcombe/top-up discipline; power resolves an unblock of the size the p = 1.0 corner suggests (~12%) but not a small gradient.

## Acceptance

- `RunRevealSet` compiles under `-Werror`/Scalazzi; `sbt check` green; adversarially verified before the live spend.
- Both arms under one invocation; the reveal preamble is a system-prompt preamble (trusted, set-only); startup logs the allocation and which arm is revealed; per-path split + `w` printed; logs archived.
- The run lands in `fallible-oracle-results.md` as its own dated section under the threats structural rule.

*End — 2026-07-09.*
