# Hypothesis lifecycle — decision record: gate scoping (§C)

For the record. 2026-07-07. Captures the findings and the decision on whether to build §C gate scoping
(slice 2 of the hypothesis-lifecycle fix). Read alongside `hypothesis-lifecycle-design.md` (the design of
record), `hypothesis-lifecycle-instructions.md` (rev 2, the requirements), and
`hypothesis-lifecycle-review-addendum.md` (the safety review).

## What is built and safe (independent of this decision)

**Slice 1 — self-withdrawal retirement — is committed (`5f67dfc`) and adversarially verified MERGE_SAFE.**
It is the working fix for the plant-or-fungus jam:

- A candidate B is retired iff every agent that asserted B has since refuted it (self-withdrawal) and ≥2
  standing refuters. Retired candidates are masked out of the fold; the standard gate runs on the masked
  slot.
- Chosen over "channel-recency" (no pro after the latest con), which the design committee showed
  over-fires — it would retire an author-silent glut and let a wrong rival sign. Self-withdrawal holds
  that case.
- The adversarial verify, run with over-firing as the *primary* hunt per the review addendum, confirmed
  that retiring a still-defended candidate is structurally impossible, the predicate is non-generative (no
  semantic judgment — proven by a metamorphic test), masking cannot manufacture a sign, and the
  no-retirement path is byte-identical to before.

Slice 1 signs "apple tree" at e-66 (retirement clears the glut → the standard gate signs) and holds every
contested case. **This holds regardless of the §C decision below.**

## The question: build §C gate scoping?

The design of record specified §C as slice 2: evaluate the leading candidate on a single-candidate
sub-`Testimony` view (`soleView`) — sign B iff B is the unique leader, B's own channels read True, and B
has ≥2 backers — because the corner reads slot totals, so a con on any candidate jams the whole slot.
Stated purpose: a stale glut on an *abandoned* hypothesis must not block signing a well-supported live
one.

Examining §C against the review addendum's guidance — *"gate scoping … the adversarial log will exercise
the HOLD path that scoping must respect"* — surfaced a tension, stated as two findings.

## Finding H1 — the soleView-§C signs around a contested hypothesis

On the addendum's own adversarial case:

- apple-tree: 2 backers, unrefuted.
- plant-or-fungus: X asserts, Y and Z refute, **X stays silent** (never withdraws).

Slice 1 correctly **holds** plant-or-fungus (X still stands behind it — contested, not defeated), so its
con keeps the global corner Glut and the standard gate blocks. But soleView-§C would sign apple-tree
anyway (its own channels are clean, it is the unique leader, 2 backers). That is signing *around* a
genuinely contested hypothesis — the exact "does not respect the HOLD path" failure Caution 2 names.

## Finding H2 — a HOLD-respecting §C is redundant

The doc conditions §C on the other glut being on an **abandoned** hypothesis. "Abandoned" = no live
support = exactly slice 1's retirement predicate — and those candidates are already masked out. So a §C
that respects the HOLD path (never signs while a contested / live-support rival exists) ignores only
already-masked candidates and adds nothing. Making §C ignore *more* (an un-retired glut) requires deciding
an un-retired glut is "really defeated" — the semantic "does this evidence contradict this assertion?"
judgment that Caution 1 forbids the librarian. So: the aggressive §C over-signs; the safe §C is redundant.

## The methodological point (addendum Caution 3)

The design of record's §C came from a design committee — correlated Claude reasoning, not independent
validation. The addendum is explicit that confidence attaches at the *check*, not the *concurrence*.
Rather than build §C on the committee's say-so, or skip it on my own reasoning (which could be wrong, and
contradicts the committee), the findings above are being **independently pressure-tested** — one lens
steelmanning §C (trying to construct a safe, non-redundant version, which would refute H2), one
prosecuting, and an adjudicator judging whether any constructed "safe §C" is real or smuggles a
contested-rival sign / a semantic judgment.

## The options

- **(A) Skip §C.** Slice 1's retirement is the complete safe fix. Build the visible half — the retirement
  markers on the wire, agents-see-retired, and the frontend replay — as the remaining work. The gate stays
  the standard gate on the masked slot: signs apple-tree, holds contested rivals.
- **(B) Build a safe §C** — only if a HOLD-respecting, purely structural, value-adding version exists
  (refuting H2).
- **(C) Build the soleView-§C per the design of record** — accepting that it signs around contested rivals
  (contra the addendum).

## Preliminary recommendation

**(A) Skip §C.** Both findings point to it, and it aligns with the addendum's HOLD-path bar. The plant-or-
fungus fix is already delivered by slice 1; §C as designed adds an over-sign, and a safe §C adds nothing.

## Final decision — (A) SKIP §C

An independent pressure-test — one lens steelmanning §C (trying to construct a safe, non-redundant
version), one prosecuting, and an adjudicator — confirmed both findings against the code. **§C will not be
built.**

- **H1 confirmed.** Traced against `GameCore`: on the contested log, `defeated(plant-or-fungus)` is false
  (X `standsBehind` → not retired → correctly held as a Glut → the standard gate blocks), yet
  `soleView`-§C reads only apple-tree's own clean channels and signs it — signing around a live, held,
  contested glut. From the leader's isolated view a *defeated* rival's con and a *contested* rival's con
  are indistinguishable. Option C is a reachable fail-open. Rejected.
- **H2 confirmed, with a sharper conclusion.** The steelman *did* construct a HOLD-respecting,
  purely-structural §C (add: "the leader is the unique candidate any agent still stands behind"). But that
  clause *is* masking — it proves `Gate.accept(masked slot) ≡ §C(leader)`, so `soleView` does no work and
  the rival-liveness read does all of it. The apparent value-add is not a capability of gate-scoping; it
  is a gap in slice 1's *predicate* (`authors.nonEmpty` + `MinRefuters ≥ 2` conservatism). A `soleView` §C
  is either the H1 fail-open (without the clause) or dead weight (with it). Option B rejected.

**Slice 1's self-withdrawal retirement is the complete safe fix.** The canonical plant-or-fungus case
needs no §C — at e-20 the driller's self-withdrawal plus the skeptic gives two standing refuters,
retirement masks it, and the standard gate signs apple-tree at e-66 on its own two backers. The
design-of-record's claim that "retirement and §C ship together — under-retirement is only useful because
§C backstops it" does not survive the audit: retirement is useful on its own, and the only thing §C would
backstop is a marginal, fail-closed edge.

**The remaining work is the visible half, no gate change:** `reconcileRetirements` into the single-writer
LogActor, the belief-inert `Retired`/`Resurrected` markers on the wire, `GameView` dropping retired
candidates from agents' live targets, the frontend event union + fold masking, and the
recovery/resurrection path.

**One acknowledged residue, deferred by design.** Slice 1 leaves one jam unfixed: a *lone* self-withdrawal
(an agent asserts then refutes its own hypothesis with no second refuter) stays a Glut and the gate
abstains — `MinRefuters = 2` holds it. This is marginal and fail-closed (over-caution, never a wrong
sign). Fixing it means relaxing the retirement predicate (the `authors.nonEmpty` / con-only-phantom case,
and re-deciding the threshold), which the design gates on the grounded-refutations fix — sequenced after
this one, because ≥2 assumes trustworthy refutations. Address it there, through adversarial-verify — never
as `soleView` gate-scoping.

*Recorded 2026-07-07 after the independent check. The decision was not pre-empted before the check
completed.*
