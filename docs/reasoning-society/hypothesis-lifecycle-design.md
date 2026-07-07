# Hypothesis lifecycle — design of record (channel-asymmetry retirement)

The realization design for the hypothesis-lifecycle fix; `hypothesis-lifecycle-instructions.md` (rev 2,
channel-asymmetry) is the requirements. Settled 2026-07-07 by two design committees — the second re-run
against rev 2's reformulation. This changes what the gate signs — the fail-closed core — so the design
is dominated by not signing a falsehood.

> **Update (2026-07-07) — §C gate scoping was subsequently DROPPED.** An independent pressure-test found
> the §C described below (the `soleView` leading-candidate evaluation) either signs *around* a contested
> hypothesis (a reachable fail-open, violating the review addendum's HOLD path) or reduces to the masking
> predicate (dead weight). Slice 1's self-withdrawal retirement is the complete safe fix on its own; the
> remaining work (slice 2/3) is the visible half — the LogActor marker wiring, agents-see-retired, and the
> frontend replay — with **no gate change**. The §C material below and in the slice plan is retained for
> the record but is not built. See `hypothesis-lifecycle-decision-record.md` for the findings and the
> decision.

## The decision: self-withdrawal, not channel-recency

rev 2 retires a hypothesis when its pro channel has no live support and its con channel has standing
refutation. The realization question is what "no live support" means structurally. Two candidates:

- **Channel-recency** — no pro entry survives the latest refutation (`max pro seq < max con seq`).
  **Rejected.**
- **Self-withdrawal** — every agent that asserted B has since refuted it (its latest stance on B is
  against). **Chosen.**

Channel-recency is a reachable confidently-wrong-at-signature. Take the real glut rev 2 says to HOLD: X
asserts B, Y and Z refute, X stays silent. X's assertion is neither withdrawn (X never refuted it) nor
evidence-defeated (no oracle contradicts it), so it is live support and B must be held. Channel-recency
(`max pro < max con`, two refuters) retires it — masking B's con, letting a clean-but-wrong rival
collapse to cardinality one and sign, with no backstop. Self-withdrawal holds it (X still stands behind
B). The asymmetry is total: over-retirement enables a wrong sign; under-retirement is merely cautious
and is backstopped by gate scoping. Fail-closed dominance settles it.

## The predicate (a pure fold over the log)

Per candidate B, the latest seq at which each agent took a pro (Assert/Corroborate) or con (Refute)
stance on B:

- `standsBehind(a)` — a's latest stance on B is pro (still supporting).
- `standsAgainst(a)` — a's latest stance on B is con (a standing refuter).
- **Retire B iff** B has pro-authors, **every** pro-author self-withdrew (none `standsBehind`), and
  **≥ 2** distinct standing refuters.

The ≥2 threshold is symmetric with the no-lone-sign floor — one lone (possibly-hallucinated) refutation
never retires, mirroring one lone assertion never signing. Fires plant-or-fungus at e-20 (skeptic e-19 +
the driller's self-withdrawal e-20); `≥1` would misfire at e-19. "Evidence-defeated" is **not** read
semantically — the oracle answer is belief-inert, and reading its meaning would be the generative
judgment rev 2 forbids; the agents already expressed the defeat by refuting, so only the structural
stances are consulted.

## Masking, retirement, and gate scoping

- **Masking is the pure predicate recomputed on read** — `retiredCandidates(log.take(upTo))`, threaded
  through `slot`/`belief`/`decide`, drops both channels of a retired candidate before the existing
  projection. No stored status, no drift; prefix replay is correct for free (∅ at e-19, {plant-or-fungus}
  at e-20). `project ≡ maskedProject(_, ∅)`, so the no-retirement path is byte-identical to HEAD.
- **`Retired` / `Resurrected` are belief-inert events** (`project → Nil`, `agentId → None`) — the audit
  and UI trace, not the masking source. `reconcileRetirements` (single-writer LogActor) keeps them in
  sync with the predicate.
- **Gate scoping (§C)** — because the corner reads slot totals, evaluate the leading candidate on a
  single-candidate sub-Testimony view: sign B iff B is the unique leader, B's own channels read True
  (nobody refuted B itself), and B has ≥2 backers. §C never lowers the floor and never signs a candidate
  carrying its own con; it only prevents a different candidate's con from holding the leader hostage.
  Retirement and §C ship together — under-retirement is only *useful* because §C backstops it.

## Fail-closed invariants (the adversarial-verify confirms)

1. **Never retire live surviving pro support** — `(∃a. standsBehind(a)) ⟹ B ∉ retired`. Plus explicit
   real-glut HOLD tests (the log has none): re-defended (X asserts, Y+Z refute, X corroborates after);
   **author-silent, refuted-by-others** (the anti-CWS pin); single-refuter.
2. **Conservative** — `retired ⊆ { B : ≥2 standing refuters ∧ every pro-author self-withdrew }`;
   exactly-one refuter never retires.
3. **Structural / non-generative** — metamorphic: replacing every note/content string and every oracle
   answer in a log leaves `retiredCandidates` unchanged.
4. **Reversible** — a fresh Assert/Corroborate above an agent's latest refutation resurrects B; events
   are never deleted (log only grows; the markers project to Nil).
5. **Sign floor + leading path intact** — no-retirement `decide` is byte-identical to HEAD; a retired
   candidate never signs; masking removes a jamming con, never adds pro, never lowers `MinCorroboration`
   (apple-tree signs at e-66 on its own two backers, not because a rival was masked).

## Slice plan

1. **Detection predicate + retirement (pure core).** `stances`/`standsBehind`/`standsAgainst`/`defeated`/
   `retiredCandidates`, `maskedProject`, `reconcileRetirements` (all pure in `GameCore`); the
   `Retired`/`Resurrected` backend enum variants (project → Nil); thread `retired` through
   `slot`/`belief`/`decide`. Tests: plant-or-fungus retires at e-20, the three real-glut HOLD mirrors,
   invariants 2–4. Hermetic.
2. **Gate scoping (§C) + LogActor wiring.** `leadingCandidate`/`soleView`/§C in `decide`;
   `reconcileRetirements` into the single-writer LogActor. Tests: the full plant-or-fungus replay signs
   apple-tree at e-66; invariant 5 regression.
3. **Agents see the retired board + frontend + recovery.** `GameView` drops retired candidates from
   agents' live targets (collapses the wasted re-refutations); the `Retired`/`Resurrected` mirror in the
   frontend event union + the fold masking (replay scrubs to e-20 and e-66); the recovery/resurrection
   integration path.

## Notes

- Detection is purely structural — no agreement, grounds, or semantic-contradiction check, per rev 2's
  "what NOT to build."
- Conservative under-retires genuine third-party evidence-defeat when the asserter stays silent — a
  deliberate, faithful choice (holding possible dissent), backstopped by §C. Do not "fix" it by reading
  oracle-answer content — that reintroduces the forbidden generative judgment.
- Safety is conditional on the grounded-refutations fix (sequenced after this one): ≥2 assumes
  trustworthy refutations. A self-withdrawal plus one bad refuter could retire wrongly — reversible, and
  out of scope per rev 2's sequencing. Noted, not blocking.
