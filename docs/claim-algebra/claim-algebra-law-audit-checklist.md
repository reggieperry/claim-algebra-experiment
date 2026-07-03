# Claim Algebra — Law & Property Audit Checklist (RECONCILED)

Reconciled against the source: `claim-algebra.html` §§3–5 (the tower, the algebraic layer, the
Scala type layer) and `claim-calculus.html` §§4–8 (operational meta-theory through adequacy).
Every item below is tagged by whether your actual formalization backs it.

> **Tracked in-repo, audited 2026-07-01.** The suite was audited against this checklist and the gaps
> closed: the calculus meta-theorems (non-fabrication Thm 6.2, determinacy/normalization 4.1/4.2,
> confluence-on-assertions 5.3) in `LedgerLawsSuite`; the `Testimony`-generator distribution in
> `CorroborationSuite`; the ¬-vs-`strike` supersession discrimination in `TestimonySuite`; noisy-OR
> non-distributivity in `ProvLawsSuite`; and the `Monomial` commutative monoid in `ProvLawsSuite`.
> Coverage is complete as of that date. The one correction the audit made to THIS document is the
> supersession item (§Tier 2): it is `refute` (¬), not `strike` — marked ✂️ below.
>
> **Updated 2026-07-02** after the adversarial validation of the activation design brief (see the
> errata header on `claim-object-activation-design-brief.md`): the corner-is-a-presence-read caution
> is made explicit (Tier 2), a third deliberate non-theorem is recorded (conflict-annihilation in
> `⊗ₖ`), and the ⚠️ extensions below now carry their validated status against the shipped carrier
> and the Scenario-A design of record. One leftover from the 2026-07-01 pass is also fixed: the
> extensions section still said supersession is `strike(old)`; it is `refute`, matching Tier 2.

### Legend
- ✅ **GROUNDED** — stated in the docs; exact reference given.
- ✏️ **REFRAMED** — the concept is in the docs, but my earlier checklist stated it imprecisely; corrected here.
- ➕ **ADDED** — a real property/theorem in the docs my earlier checklist omitted.
- ⚠️ **EXTENSION** — **not in the source docs**; a design addition from our conversation. Legitimate to build for the imperfect-reader / early-stage scenarios, but do **not** present it as one of the algebra's laws. Decide separately and give it its own tests.
- ✂️ **CORRECTED** — my earlier checklist got this wrong; the correction is here.

---

## The actual objects (from §5 — so the checks target real types)

- `Lev` — the per-channel lattice **L** (confidence carrier): sealed `Bot | Deg(p∈(0,1])`, fail-closed `deg`, `given Order[Lev]`. (Here, the fuzzy carrier: meet=min, join=max.)
- `Ev(pro: Lev, con: Lev)` — the **rendered** bilattice element, the twist `L ⊙ L`. Four ops `kmeet/kjoin/tmeet/tjoin` (componentwise) + `negate`; `corner → Belnap{Gap,True,False,Glut}`.
- `Prov` — the provenance semiring **ℕ[X]**: `given CommutativeRig[Prov]`, `zero`=gap (`0·x=0`), `one`=cites-nothing. **Not** `Set[Lineage]`.
- `Testimony[+A](value | candidateMap, provPro, provCon)` — value + two provenance polynomials. **The grade is RENDERED (`renderEv`/`evaluate`), not stored** — no second source of truth to drift. The shipped carrier generalizes `value: Option[A]` to a candidate map `A →₀ (Prov, Prov)` (rivals kept as ambiguity, cardinality ≥ 2).
- `Claim[A]` — a `Testimony` **refined by verification**; opaque, built only by running the verifier.
- Operations: `derive` (⊗ₖ), `truthMeet` (∧ₜ), `corroborate` (⊕ₖ), `refute` (¬), `strike` (withdrawal), `renderEv` (ν̂).

---

## Tier 0 — Meta-checks (gate everything below)

- ✅ **Generator coverage.** The docs say residual invariants are "carried by law and property tests," so this is doc-endorsed. Generators must hit gap `N`, glut `B`, `False`, and **multi-candidate ambiguity (cardinality ≥ 2)** — the last is required because the shipped carrier is the candidate map, not `Option[A]`. ➕ (the ambiguity-coverage requirement was missing before).
- ✅ **`Eq` correctness / significant fields.** Note the doc's design choice: the corner is read *structurally* from `provPro.isZero / provCon.isZero`, and the grade is *rendered*, not stored — so equality should be defined on the provenance polynomials, and the "no stored grade to drift" invariant is worth a property.
- ✅ `Cogen` where functions are generated; shrinks stay well-formed.

---

## Tier 1 — Library-standard typeclass laws (confirm the right instance)

- ✏️ **Per-channel carrier `Lev` is a bounded lattice.** (Earlier I said "each channel is a BoundedSemilattice"; more precisely, the carrier `L` = `Lev` is a lattice with `Order[Lev]`, meet/join, and `⊥`/`⊤`.) Run lattice + `Order` laws on `Lev`. `LatticeLaws` (absorption) + `OrderTests`.
- ✅ **Provenance semiring: `CommutativeRig[Prov]`.** This is verbatim in §5 (`given CommutativeRig[Prov]`). Run the **commutative Rig** suite: additive comm monoid `(+,0)`, multiplicative comm monoid `(×,1)`, **left+right distributivity** (confirm it's in the set), **`0` annihilates `×`** (`0·x=0`, the structural gap). *Library note:* these are in the `algebra`/`algebra-laws` modules, **not** cats-kernel — confirm the dependency, or this isn't actually being checked.
- ✅ **The four `Ev` operations are the bilattice.** Their laws are the §4.5 properties in Tier 2 (assoc/comm, interlacing, monotonicity). Check them on `Ev`, not on a stored channel.

---

## Tier 2 — The object's defining laws (from §4.5 + §5)

- ✅ ✂️ ➕ **Annihilation — TWO of them.** §4.5 "Annihilation (two)": `N ⊗ₖ x = N` **and** `F ∧ₜ x = F`, *each preserved by every channel homomorphism*. My earlier list had only the gap annihilator. At the `Testimony` level: `derive` combines both channels by `Prov.times` (·), so a zero pro-channel sinks to gap; `truthMeet` uses `+` on the con-channel so a refuted conjunct goes to `F`. Check both, and check homomorphism-preservation.
- ✅ **Associativity + commutativity of `⊗ₖ, ⊕ₖ, ∧ₜ, ∨ₜ`.** §4.5. (Doc note: "lost only for sequential value-dependent derivation" — see Tier 3 confluence.)
- ✅ **Homomorphic evaluation `ν̂` (the rendered grade).** §4.5 "Homomorphic evaluation" + §5 `Prov.evaluate`/`renderEv`: any `ν : X → L` extends **uniquely** to `ν̂ : ℕ[X] → L` (the GKT free-semiring universal property), applied per channel. Check `ν̂` is a semiring homomorphism (`ν̂(p+q)=ν̂p ⊕ ν̂q`, `ν̂(p·q)=ν̂p ⊗ ν̂q`, `ν̂(0)=⊥`, `ν̂(1)=⊤`) and that the target `L` is itself a lawful semiring/lattice. *This is the core mechanism — the grade is rendered by ν̂, not stored.*
- ✅ ✏️ **Idempotency is L-dependent; distributivity of L is mandatory.** §4.5 "Idempotency (per channel)": `a ⊕ₖ a = a` **iff L is idempotent** (min/max). A non-idempotent L (counting/probability) is a *valid* choice that rewards corroboration but is fooled by correlated sources. **The non-negotiable is distributivity of L** (it's what makes `ν̂` a homomorphism); the doc explicitly rules out noisy-OR `a+b−ab` as *non-distributive, hence an invalid `ν̂` target*. So: check distributivity of your chosen `L` always; check idempotency only if you *claim* an idempotent `L`; and verify you did **not** instantiate a non-distributive combinator. (Earlier I said "if idempotence fails, downgrade" — this is the precise version.)
- ✅ **Gap ≠ glut.** §4.5 `N ≠ B`, the two poles of `≤ₖ`. Structurally guaranteed by the independent pair `Ev(pro, con)` + `corner` reading both channels. Operational test: no combination identifies `N` and `B`; a property that catches "`con := ⊤ ⊖ pro`" collapse. ➕ **The corner is a presence read, never a magnitude read** (calculus Def 2.6 — model-free, `provPro.isZero`/`provCon.isZero`): any nonzero con blocks `True` however small (Thm 6.3), and a weakly supported unrefuted value is still `True`. Do not restate the corners as high/low quadrants (the 2026-07-02 validation caught exactly this gloss in the activation brief).
- ➕ **Knowledge-monotonicity.** §4.5: more evidence on either channel moves a value **up** `≤ₖ`; it never spuriously descends. (Missing from the earlier list.)
- ✅ **Interlacing / distributivity.** §4.5: each operation monotone w.r.t. the *other* order; the two lattices distribute. This is what validates the twist and the per-channel homomorphism theorem.
- ✅ ✂️ **Negation `refute` (¬) — involution.** §4.5 + §5 `negate`: `¬¬a = a`; `¬` inverts `≤ₜ`, **preserves** `≤ₖ`; De Morgan holds in the truth lattice; `¬` is a homomorphism of the knowledge lattice.
- ✅ ✂️ ➕ **Withdrawal `strike` — idempotent & absorbing (DISTINCT from ¬).** `claim-calculus` Prop 2.13 + Theorem 6.1: `strike(strike t) = strike t`; withdrawal is absorbing. The doc is explicit that deletion is modeled as `strike`, **not** `¬`, precisely because "a second refute would swap the channels back and resurrect the value." **This is the correction that matters most** — `refute` and `strike` are two different operations with two different laws; test both, and do **not** test `strike` for involution or `refute` for idempotence.
- ✅ ✂️ **Supersession = `refute`(old, kept on record) + fresh `T`.** Calculus Def 2.14 / Thm 6.3: `supersede(t, g) = (¬t, g)` — modeled as *two* claims, never one, the prior refuted onto the con-channel and kept, the amendment governing. It is `refute` (¬), **not** `strike` (which is *withdrawal*, above): the two coincide on a clean prior but diverge on a contested one — refute swaps the channels, strike clears pro (Remark 6.4: ¬ keeps the operative's con empty so it can still sign). Collapsing the pair into one testimony would put the prior's support on the con-channel and misread it as a glut. Test the pairing, the trace retention, and the ¬-vs-strike choice on a contested prior.
- ✅ ✏️ **Verification is a `Bool` predicate + opaque `Claim`.** §4.4/§5: `verify : Claim a → Bool`, a third orthogonal axis; `Claim` is opaque (only the verifier constructs it). ✂️ My earlier "`FAILED`-absorbing / `UNVERIFIED`-bottom lattice" was **invented** — the doc has a plain Boolean. Orthogonality (verification never feeds a channel) holds *by construction* (opacity), not as a lattice law.
- ✅ **The accept/gate policy.** §4.4: `accept(c) ⇔ corner(c)=T ∧ cardinality(c)=1 ∧ grade(c)≥θ ∧ verify(c)`. Property-test the gate: it signs iff all four hold (`True`, single candidate, cleared, verified).

---

## Tier 3 — Calculus meta-theorems (`claim-calculus` §§4–8)

These are the "eight proved meta-theorems." Property-test them against the evaluator (the doc calls it the workbench's Ledger fold + the Gate; adequacy, §8, is what licenses treating these as statements about the implementation).

- ✅ **Determinacy (Thm 4.1).** One-step reduction `⤳` is a total function. ➕
- ✅ **Strong normalization (Thm 4.2).** Evaluation halts in exactly `|E|` steps. ➕
- ✅ **Confluence on assertions (Thm 5.3).** For an assertion term, `belief(E)` depends only on the *multiset* of testimonies, not their order. (This is "confluence on the commutative fragment.")
- ✅ **Idempotent absorption of withdrawal (Thm 6.1).** (See `strike`, above.)
- ✅ **Non-fabrication (Thm 6.2).** If a term signs `v`, then `v` originates in `E`. ➕
- ✅ **Exclusivity (Thm 6.3).** A term never signs a value carrying any con-support; a glutted value is never signed. ➕ (This, plus 6.2 and 6.7, is what "subject reduction / safety" actually *is* in your §6 — replace the generic "reduction preserves well-formedness" I had.)
- ✅ **Fail-closed propagation / no gap-laundering (Thm 6.7).** If any leaf of a derivation is a gap, `corner(⟦D⟧)=N` at the root and the gate blocks it. Uses only `⊗ₖ`. *The spine — matched verbatim.*
- ✅ **Relative soundness (Thm 7.1) + conservativity (Cor 7.2).** The calculus does not amplify input unfaithfulness; it can only mask it by abstention. Failure localizes to oracle faithfulness, not the reduction. ➕
- ✅ **Adequacy (Prop 8.1).** The normal form relates the calculus to the implemented system. ➕
- ✅ Supporting: corroboration is a commutative monoid (Prop 2.10); permutation invariance of the fold (Lemma 5.2).

---

## The two DELIBERATE non-theorems — do **NOT** test for these

Testing for these would be testing for something your docs deliberately do not claim.

- **Full confluence is a non-theorem.** Confluence holds *only* on the commutative (assertion) fragment (Thm 5.3). The supersession/withdrawal fragment is **non-confluent by design** and must be **serialized per slot (single-writer discipline)** — that requirement is the operational counterpart of Remark 5.4. So test order-independence on assertions; test *single-writer serialization* on supersession, not confluence.
- **Absolute soundness is a non-theorem.** Only *relative* soundness is provable (Thm 7.1); absolute soundness would require governing an oracle the calculus does not control.
- **Conflict/ambiguity annihilation in composition is a non-theorem** (recorded 2026-07-02). The glut `B` is the *identity* of `⊗ₖ` (the knowledge meet's bounded semilattice), and `derive` combines con by joint use (`con = ca·cb`), so a one-sided con drops against a clean conjunct — a glut `⊗ₖ` a clean `True` renders clean, pinned verbatim in the suite. Fail-closed propagation (Thm 6.7) is **gap-only**; a conflicted or ambiguous input is blocked at the **gate** (Thm 6.3 + `cardinality = 1`), and any "non-Resolved input annihilates downstream" behavior is decision gating *above* the algebra (gate each input, propagate the gap — the brief's own §8 step 7 shape), never a law of the meet. Testing `⊗ₖ` for conflict-annihilation would contradict the checked identity law.

---

## ⚠️ Extensions from our design conversation (NOT in the source — decide separately)

These came out of the design brief and our discussion. They may be worth building for the
imperfect-reader and early-stage-corpus scenarios, but they are **additions to** the object, not
laws **of** it — give each its own justification and its own tests, and don't fold them in with §4.5.
Validated 2026-07-02 against the shipped carrier and the Scenario-A design of record
(`../credit-deal-workbench/in-flight-deal-design.md`); each item now carries its outcome.

- ⚠️ **The phantom-glut firewall** (extraction uncertainty must never become con-support). The docs' `confidence` is derivation strength rendered from provenance; they do **not** model independent-query reader disagreement as a distinct, quarantined thing. The nearest grounded notion is the candidate-map **ambiguity (cardinality ≥ 2)** and `cardinality = 1` in the gate — that is *evidential* rivalry, not *reader* noise. *Validated:* the firewall is **structural** in the shipped carrier — reader disagreement writes rival pro-keys via `corroborate`, and only `refute`/`strike`/`supersede` write the con-channel, so a phantom Belnap `Glut` is unreachable from the reading path. The validation's high finding cuts the other way too: **cross-source** disagreement is *also* ambiguity (rival pro-candidates), not a glut — the brief's "cross-source ⇒ glut" mapping is corrected in its errata header, and populating con from a rival positive assertion would reinstate the removed `corroborateDebt` refute-hack.
- ⚠️ **Per-source keying of N independent AI queries** (N reads of one clause collapse to one contribution). Related to, but not the same as, per-channel **idempotency** (which only collapses identical *provenance tokens*). *Adopted* by the design of record: `SourceEvidence.toTestimony` mints at most one pro-only token per document, with two one-line provable invariants (`cardinality ≤ 1`, `provCon.isZero`).
- ⚠️ **The multi-document corpus / version-precedence supersession** framing. The docs model supersession as `refute`(old, kept on record) `+` fresh `T`, **not** as version/precedence ranking over a document set. *Adopted* as the thin Scenario-A layer (N1–N4): precedence rank is computed by a pure policy at decision time, never stored, and a winner must strictly dominate — any tie or incomparability keeps the target a conflict.
- ⚠️ **The `verify()` multi-state lattice** (`VERIFIED/UNVERIFIED/FAILED`). The docs have a plain `Bool` predicate + opaque `Claim`. *Still an extension*, with a validation correction alongside it: verification does not **propagate** through composition — subject reduction preserves non-fabrication, exclusivity, and fail-closed propagation, never a verification status; verification is a gate conjunct plus construction-time opacity, and grounding buys non-fabrication, not correctness (the Run-10 CWS was carried by fully verified readings).
- ⚠️ **The `[0,1]` reader-confidence weight** (brief §4 / INV1(a) / §7.4). *Dropped* by the design of record: reader agreement is display-only exposure and can only withhold a signature (no-lone-sign), never confer support. No such weight can live in a channel — `ℕ[X]` coefficients are naturals — and the one lawful weighting seam is the trust valuation `ν(lineage)` supplied at render, model-parameterized and never stored.

---

## Bottom line

The core matched the source verbatim: `CommutativeRig[Prov]` with `0·x=0`, the homomorphic
`ν̂`/`renderEv` as the GKT universal property, `gap ≠ glut` as `N ≠ B`, and Theorem 6.7 as the
fail-closed spine. The material corrections are (1) two annihilators not one, (2) `strike` is a
separate idempotent-absorbing operation from involutive `¬`, (3) `verify` is a `Bool`+opaque-`Claim`
not a lattice, (4) idempotency is L-dependent while **distributivity is mandatory**, and (5) the
phantom-glut firewall and the corpus/precedence model are extensions from our conversation, not part
of your formalization. The 2026-07-02 brief validation added (6): the corner is a presence read,
conflict-annihilation in `⊗ₖ` is a non-law (the glut is the meet's identity; fail-closed is
gap-only, conflict blocks at the gate), and the confidence weight is dropped — the brief-side
corrections live in its errata header.
