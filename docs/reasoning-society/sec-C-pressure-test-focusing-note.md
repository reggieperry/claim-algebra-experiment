# Focusing note — the decisive question for the §C pressure-test

**For the committee pressure-testing the §C gate-scoping decision.**
*2026-07-07 · a sharpening note · read alongside `hypothesis-lifecycle-decision-record.md`. Purpose: reduce the B-vs-A decision to the ONE question that settles it, so the adjudication targets the decisive point instead of litigating §C in general.*

---

## The whole decision reduces to one question

Findings H1 and H2 together claim the §C design space is empty: the aggressive (soleView) §C **over-signs** (signs around a contested rival — H1), and any HOLD-respecting §C is **redundant** (ignores only already-masked retired candidates — H2). If both hold, §C has no valid form and the answer is **(A) skip §C**.

The only way **(B) build a safe §C** survives is if H2 is false — and H2 is false **iff** the steelman can construct:

> **A THIRD notion of "ignorable rival" — a rule for which candidates §C is allowed to sign around — that is (1) NOT identical to slice-1's retirement predicate (self-withdrawal + ≥2 standing refuters), and (2) NOT a semantic "does this evidence contradict this assertion?" judgment (forbidden to the librarian by Caution 1), and (3) purely structural (a rule over the log/channels), and (4) such that signing around such a rival never signs around a genuinely contested hypothesis (respects the HOLD path, Caution 2).**

**That is the entire adjudication.** Do not argue about §C in general. Ask only: *does such a third notion exist?* If the steelman constructs one that survives prosecution, it **is** the safe non-redundant §C → build it (B). If the steelman cannot, H2 is airtight → §C is dead → skip it (A).

## Why this is the right reduction

- Slice 1 already retires the *defeated* candidates (no live support) and masks them. The standard gate already holds on *contested* candidates (live support both sides). So the only role a distinct §C could play is to sign around some **third category** of rival — one that is neither "already retired" (slice 1 handles it) nor "genuinely contested" (must HOLD).
- Either that third category exists as a clean structural rule, or it doesn't. If it exists, §C is real. If it doesn't, every §C is either over-signing (treats a contested rival as ignorable — H1) or redundant (only ignores what's already retired — H2). There is no third outcome. So constructing-or-failing-to-construct the third notion is **necessary and sufficient** to decide.

## Traps the adjudicator must reject (a "constructed safe §C" that does any of these is NOT safe)

1. **Smuggled semantic judgment.** Any rule that decides an un-retired glut is "really defeated" by inspecting whether the evidence contradicts the assertion — even dressed up structurally — is the Caution-1 violation. Reject. (Test: could a non-generative librarian evaluate this rule with zero semantic understanding of the claims' content? If it needs to know what the claims *mean*, reject.)
2. **Contested-rival sign.** Any rule that, on the H1 adversarial case (apple-tree clean + 2 backers; plant-or-fungus with a silent-but-standing asserter X and 2 refuters), signs apple-tree while X still stands behind plant-or-fungus. That is signing around a live glut. Reject.
3. **Redundant dressing.** Any rule whose set of "ignorable rivals" is actually equal to slice-1's retired set (just computed differently). That is H2 — adds nothing. Reject as not-a-reason-to-build (fold it into slice 1 if the computation is cleaner, but it is not a distinct §C).
4. **Threshold fiddling.** Any rule that is slice-1 with a different backer count or refuter count. That is a parameter change to slice 1, not a new mechanism. Reject as not-§C.

A surviving §C must pass ALL of: structural (trap 1), HOLD-respecting (trap 2), non-redundant (trap 3), genuinely distinct (trap 4).

## The metamorphic check to run on any candidate rule (borrow slice-1's method)

Slice 1 proved non-generativity with a metamorphic test (relabel/permute claim *contents* without changing channel structure → decision must be invariant). Apply the same to any proposed §C rule: **if changing the semantic content of the claims (while holding the pro/con channel structure fixed) changes whether §C fires, the rule is making a semantic judgment → trap 1 → reject.** This is a mechanical discriminator between "structural rule" and "smuggled judgment" and should be the first filter on any steelman construction.

## Expected outcome (stated so it can be falsified, not to pre-judge)

I expect the steelman to **fail** to construct a surviving third notion, because "defeated but not by slice-1's predicate" seems to necessarily require either a semantic contradiction judgment (trap 1) or a weaker structural predicate that over-fires (trap 2) — which is exactly the H1/H2 pincer. If that expectation holds, **(A) skip §C** is correct and slice 1 is the complete safe fix. But this is stated as a falsifiable prediction, not a conclusion — if the steelman constructs a rule that passes all four traps and the metamorphic check, that rule refutes H2 and **(B)** is correct. Let the check decide.

## What does NOT depend on this

Per the decision record: slice 1 (self-withdrawal retirement, committed, MERGE_SAFE) is the working fix and holds regardless. The remaining *build* work (retirement markers on the wire, agents-see-retired, frontend replay) proceeds either way. This note only settles whether a **distinct §C mechanism** is additionally warranted — a scope question, not a blocker on the delivered fix.

*End of focusing note — 2026-07-07.*
