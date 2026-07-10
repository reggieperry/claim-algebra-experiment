# An Auditable Society of Minds — architecture record

**A claim-algebra-governed network of LLM agents that reasons, remembers, and improves — with its work always showing, and a human at the center supplying what no structure can.**

*Version 1.1 · 2026-07-07 · supersedes Version 1 (2026-07-06), which superseded Version 0*

---

## Changelog — what moved from v0 to v1, and why

v0 described the constitutional framework, the fluid solving layer, and the problem-solving loop. v1 folds in five structural advances worked out since, quarantines two elegant-but-unbuilt hypotheses, and demotes two worked examples to illustrative appendices. Every change is marked below so the trail stays legible.

**(v1.1, 2026-07-07) Clarifications the Twenty-Questions build surfaced — validated against a real game log:**
- **Librarian operates at two scopes** (§I.1): consolidation/forgetting run *within* a task (live hypothesis retirement), not only *after* it. The librarian is the non-generative maintainer of the live/trace boundary at every scope.
- **Real glut vs. defeated claim — the overloaded "glut" disambiguated** (§I.1, §I.2): "asserted-and-refuted" was conflating two opposite states. A **real glut** has *both channels live* (current support AND refutation — genuine tension → the gate HOLDS). A **defeated claim** has *no live pro support + standing con* (no tension → retire it). Retirement fires on a **pure channel-asymmetry predicate** (no live pro support + standing con), **not** on "detecting agreement" — it doesn't matter *why* support vanished, only that it did, which keeps detection a clean non-generative rule. Split: agents produce the channel state (grounded reasoning); the librarian *reads the balance and files the retirement* (mechanical, never a judge); retire to *trace* (recoverable), off the live board.
- **Gate scoping** (§I.2): the gate evaluates the *leading live candidate*, not global conflict-freeness — a defeated/abandoned hypothesis must not block signing a well-supported live one (backstop for the case where it isn't cleanly retired).
- These are **clarifications, not reversals** — they make explicit a scope the librarian's v1 definition implied but did not state, and disambiguate a primitive ("glut") that was silently carrying two meanings. Together they close the specific gate-jamming failure the build exhibited (a correctly-abandoned hypothesis holding the gate hostage for 50 events while the correct answer sat corroborated and unsigned). *Validated against the log for the retirement path; the gate-scoping backstop and the real-glut HOLD case are justified but were not exercised by that particular log — noted honestly.*

**Structural advances folded into the architecture (settled enough to commit):**
1. **The two-`h` split** — grounding-confidence and predictive-confidence are different functions; v0 treated `h` as one thing. (§I.2, §III)
2. **`h` as method-reliability** — a claim is graded by the *demonstrated, ground-truth-validated track record of the verification method that checked it*. The rule is constitutional; the reliability table is written by outcomes. This is the sharpest advance and it resolves the fixed-vs-learned tension. (§I.2, §I.5)
3. **The verification-methods memory tier** — librarians remember *methods and their reliability*, not just facts and validated relationships. New tier, absent from v0. (§I.1)
4. **The interaction loop as first-class architecture** — the human/system loop, with the four-move terminal model (adjust / follow-up / related / satisfied), promoted to a core section equal in standing to the reasoning core. v0 under-specified this. (§II — new)
5. **The frontier boundary** — the system reasons truthfully over what exists and honestly *bounds* where the known runs out, handing the human a precise edge rather than a false "impossible." (§IV)

**Held as unvalidated hypothesis, NOT finding (elegant, believed, entirely unbuilt):**
- **The killer-usage bet** — verification-layer-at-the-signature as the highest-value application. A strategic hypothesis. (Appendix B)
- **Validated meta-pattern transfer** — the system learning *which abstractions transfer* from track record. The most speculative idea here; conjectured capability, unbuilt, untested. (Appendix C)

**Demoted to illustrative appendix (worked examples that *taught* us things, not components):**
- The markets analysis (taught the two-`h` split and the in-sample/out-of-sample discipline). (Appendix A.1)
- The legal-question walkthrough (showed the loop on a groundable domain; surfaced the settled-vs-apparently-settled strain). (Appendix A.2)

**New risk created by these advances (named, not solved):**
- The methods tier is now **the highest-leverage thing in the system to be wrong about** — a corrupted verification method silently mis-certifies everything it touches. Strictest validation, most essential provenance. (§V, risk 7)

---

## Preface — what this is and what it is not

This is the design record for a network of many LLM agents that communicate not in prose but in **claims** — typed, provenance-carrying assertions governed by the verifiable claim algebra and its four-valued (Belnap) calculus. The organizing goal is not raw capability but **auditable collective reasoning**: a system whose every belief traces to its evidence, whose contradictions are explicit rather than averaged away, which refuses to assert what it cannot ground — and which is used *by a human, in a continuous loop*, not launched and awaited.

Framing commitments, unchanged from v0 and critical:

1. **It is a society, not a brain.** The unit is an LLM (itself a whole model), so the right reference class is an organization, a court, or a scientific community — distributed *social* cognition. Neuroscience analogies are generative scaffolding, never evidence.
2. **The differentiator is auditability, not scale.** More agents is not the key variable and past a point introduces failure modes (§V). The value is that collective belief is legible and revisable.
3. **Storage is not generation.** Memory is a persisted ledger, never a group of generative agents, because generators confabulate. Agents *manage* memory; they are not *made* the memory.
4. **(v1) The human is in the loop, not behind it.** The system does the tireless, auditable, honestly-graded part and *refuses to fake the rest*, handing the human exactly the claims that need taste, context, and accountable stakes. The seam between machine reasoning and human judgment is the product, not a limitation. (§II)

The principle that determines what is fixed and what is fluid, unchanged:

> **The constitution test.** *Could the agents, by self-organizing this, make it easier to sign a falsehood?* If yes, it is constitutional — fixed and out of their reach. If no, let it self-organize. This is the fail-closed philosophy applied to the architecture itself.

---

## Part I — The constitutional framework (fixed)

The constants. Infrastructure, not problem-solving; fixed because if the solving agents could rewrite them, the system could lie to itself.

### I.0 The Claim Ledger (the non-generative substrate)

The **Claim Ledger** is the durable, structured, *non-generative* store that is simultaneously the shared workspace and the memory. Not an agent; cannot hallucinate; a record.

**What it holds.** Every claim, carrying its **value**, its **provenance** (lineage in the free provenance semiring ℕ[X], so any trust measure is a homomorphism off the single stored lineage), its **epistemic state** (the Belnap corner: `Resolved`, `Missing`, `Conflict`/glut, `Superseded`), its **grade** (see §I.2), and its **trace** (struck/superseded predecessors, retained for audit, never deleted).

**How it works.** Claims combine by the algebra's operations — corroboration (⊕), conjunction (⊗), refutation, strike, supersede. **Fail-closed is the annihilator law `0 ⊗ x = 0`**, a theorem, and because homomorphisms preserve zero it holds under every confidence model. Single-writer-per-slot serialized. The ledger persists across tasks, serving as working memory (active task) and long-term memory (episodic and semantic record).

### I.1 The Librarian agents (memory processes) — now three tiers

The *processes* of memory over the non-generative *store*. Fixed, because memory management is infrastructure that cannot be reinvented per task without losing persistence.

**Sub-roles (retrieve / consolidate / forget) — unchanged from v0:**

- **Retrieval librarians (the index).** Surface relevant *prior claims/relationships/methods* by content address. Strict anti-confabulation guarantee: return **pointers to real stored items with original provenance**, never a generated paraphrase; return **`Missing`** on no match; retrieval is **itself gated** into the three-branch match decision (§III).
- **Consolidation librarians (encoding — the hard problem).** After a task resolves, compress episodic detail into durable structure **without fabricating** — structured, provenance-preserving consolidation, *not* lossy summarization. Re-weight, link, archive claims *as claims*; resolve superseded-as-trace vs. live-belief.
- **Forgetting.** Principled decay via the supersede/strike machinery: a retired claim is a citable trace, not a deletion. Forgetting = decay of live weight over time (the credibility/method-reliability rules run longitudinally) while the trace persists. Forgetting and reliability-learning are the same mechanism viewed over time.

**(v1.1) Consolidation and forgetting operate at TWO scopes — clarification the Twenty-Questions build revealed.** v1 originally described the librarian as a *cross-task* memory manager ("after a task resolves"). The same consolidation/forgetting *functions* also operate *within a task*, on the live working board, as the task runs — most importantly **hypothesis retirement**: when the solving agents establish that one hypothesis subsumes or obsoletes another (e.g. an oracle fact kills a disjunct — "plant: YES" retires "plant *or fungus*"), the superseded hypothesis is **retired to trace, removed from the live board** so agents no longer attack it, and kept recoverable. This is not a new role — it is exactly "resolve superseded-as-trace vs. live-belief" (consolidation) and "decay via supersede/strike to citable trace" (forgetting), applied to *working* memory rather than only to post-task *long-term* memory. So the librarian's unified definition is: **the non-generative maintainer of the live/trace boundary at every scope** — continuously within a task, and in consolidation after it.

**The mechanism — channel asymmetry, not "agreement" (the clean formulation).** Retirement is triggered by a **pure structural predicate over the claim's two channels**, not by detecting that agents "agree." A hypothesis is **defeated — retire it — when its pro channel has no *live* support (every supporter has withdrawn, or the only support predates and is defeated by subsequent evidence) AND its con channel carries standing refutation.** This distinguishes two states the overloaded "glut" was conflating: a **real glut** has **both channels live** (current support *and* current refutation — genuine tension; the gate must HOLD); a **defeated claim** has **no live pro support + standing con** (no tension — the claim is simply beaten; retire it). The channels *already encode* the difference; the fix is reading the balance rather than collapsing both into "asserted-and-refuted." (In the plant-or-fungus log: "plant or fungus" had one pro entry, made *before* the "plant: YES" evidence that defeated it and then *withdrawn by its own author's* refutation, against standing con — no live support, so *defeated*, not contested.) This formulation needs **no** agreement-detection and **no** grounds-consistency check — it does not matter *why* support vanished, only that it did — which keeps detection a clean, non-generative predicate.

**The decide/execute split (required to keep the librarian consistent with its own definition).** A v1 librarian operates on the agents' *conclusions*; it never makes substantive domain judgments. Hypothesis retirement respects this: the **solving agents** produce the channel state (assertions into the pro channel, refutations/withdrawals into the con channel — substantive, grounded, adversarially checkable reasoning); the **librarian DETECTS and EXECUTES** — a *mechanical, non-generative rule over the log/channels* recognizes the defeated-claim predicate (no live pro support + standing con) and retires the hypothesis to trace, **off the live board** so agents stop attacking it. The librarian **does not judge** anything (that would make it a domain judge, which no librarian is); it *reads the channel balance the agents produced and files the consequence* — the same anti-confabulation discipline as retrieval, a pure function over the log, never a generative opinion. Retirement is to **trace, not deletion** — recoverable if later evidence restores live support (resurrection path). The **Gate** then evaluates the *leading live candidate* on its own merits; a defeated/abandoned hypothesis must not block signing a well-supported live one, and a *real* glut (both channels live) must still be HELD (gate scoping — see §I.2).

**The three memory tiers the librarians maintain — (v1) the third is new:**

1. **Facts (episodic).** "Revenue was 12%," grounded to a source, at a point in time. What was concluded, when, with what provenance.
2. **Validated relationships (semantic).** "Momentum → alpha, out-of-sample record R"; "physical occupation → per se taking, *Loretto*." A pattern promoted from "asserted once" to "durable standing belief," when corroborated across many tasks, never refuted, stable over time. The grade/valuation machinery is the promotion criterion.
3. **(v1) Verification methods and their reliability.** A *method* — "to check a grounding claim, re-parse the cited span and re-match the value"; "to validate a predictive relationship, run the sealed-vault protocol with alpha-adjustment"; "to check a legal claim, verify binding authority exists, is in-jurisdiction, is still good law, and stands for the proposition" — is a **reusable asset with a track record**. This tier is the most general and highest-leverage: a fact concerns one company, a relationship one pattern, but a method applies to a whole *class* of claims. The librarians store, for each method, its **demonstrated reliability against ground truth**, scoped by claim-class and regime.

**The non-negotiable constraint (v1, sharpened):** every claim retains **which method verified it**, and validated relationships and methods retain full provenance — so a method (or relationship) later found faulty triggers a **retraction cascade** over everything it certified. This is the defense against institutional false memory, now at its most important altitude: a corrupted *method* is otherwise an unfindable, systemic lie factory (see §V, risk 7). Consolidation must never be lossy summarization, precisely because provenance is what makes a wrong method survivable instead of catastrophic. A brain cannot revise a false belief's downstream consequences; this ledger can — *only if* provenance survives consolidation.

### I.2 The Gate and `h` (the signing discipline) — (v1) substantially advanced

The **Gate** is the exogenous standard for what counts as signable — a fixed predicate, not an agent to be argued with. Its rule, generalized from the workbench:

> Sign iff `corner = Resolved (True) ∧ cardinality = 1 (unambiguous) ∧ grade ≥ θ ∧ verification-predicate holds`.

A gap does not sign. A glut does not sign. An ambiguity (two rival supported candidates) does not sign. **Support alone does not sign without sufficient grade** — this conjunct is where v1's advance lives.

**(v1.1) Gate scoping — evaluate the leading candidate, not global conflict-freeness.** The gate asks whether *the leading live candidate* is signable (Resolved, cardinality 1, grade ≥ θ, and not under *live two-channel contention*) — **not** whether the entire ledger is free of any conflict anywhere. Two refinements matter. First, distinguish a **real glut** (both channels live — genuine current support *and* refutation → HOLD, do not sign) from a **defeated claim** (no live pro support + standing con → not a real conflict; retire, per §I.1) — the overloaded "asserted-and-refuted" was conflating these. Second, a defeated/abandoned hypothesis must **not** block signing a *different, well-supported live* hypothesis; this is the backstop for the case where a defeated claim is *not* cleanly retired — even unretired, it cannot hold the gate hostage over the leading candidate. Combined with grounded refutations (a *weak* refutation can't block a well-grounded hypothesis), this makes the gate robust against being jammed by things that should have no power over the leading candidate — *bad* refutations, *defeated* hypotheses, or *false gluts* — while still correctly HOLDING on genuine live contention.

**(v1) Two distinct kinds of confidence — the two-`h` split.** v0 treated `h` as one function. It is two, because there are two kinds of claim:

- **Grounding-confidence** applies to claims about *what is* — "the clause says 3.0x," "the case exists and stands for X." Verified by a non-generative check (parser, computation) over present evidence. Strong, nearly mechanical, and — critically — **question-independent**: the same grounding method grades a never-seen question, because it evaluates *grounds*, not novelty.
- **Predictive-confidence** applies to claims about *what will be* — "this stock earns positive alpha," "this regulation will be held a taking." **Cannot come from grounding**, because nothing present "says" the future. It can come only from a **validated, out-of-sample track record** that predictions of this kind, from these features, proved right often enough — a *statistical* verification, decay-discounted, and utterly distinct from grounding.

Conflating the two was a real error (it is the market/credit lesson, Appendix A.1): grounding the *inputs* to a prediction certifies the features, never the forecast.

**(v1) `h` as method-reliability — the core formulation.** The grade a claim receives is **not** a field an agent fills in (self-reported confidence is just another untrusted claim — the generator that hallucinates a fact hallucinates its confidence with equal fluency). The grade is a **homomorphism `h : ℕ[X] → K` applied to the claim's *provenance*** — and `h` evaluates a lineage token by *the demonstrated reliability of the verification method that produced it*:

- a token from a **grounding check** the method-tier records as highly reliable → **high**;
- a token from a **recomputation** → **high**;
- a token from **corroboration**, weighted by whether the corroborators' lineages are genuinely **disjoint** (shared-provenance agreement is *not* independent evidence — this is the monoculture defense, §V risk 1, expressed formally as ⊕ that does not double-count shared root tokens);
- a token from a **validated predictive relationship**, graded by its out-of-sample track record, decay-discounted;
- an **in-sample-backtest** token → **low** (in-sample fit is not evidence — the discipline the whole system embodies);
- a **bare agent-assertion** token with no checkable ground → **low**, gate abstains.

**The firewall:** *the model contributes to the provenance; `h` assigns the confidence.* An agent can put a token in the lineage; it cannot control what that token *evaluates to*, because the homomorphism — not the agent — assigns the value. **The model asserts; it never certifies.**

**Why `h` is constitutional though it learns.** The apparent contradiction (v0 said `h` is fixed; v1 says `h` learns from method track records) resolves exactly as the credibility rule does (§I.5): **the *rule* is fixed — "grade every claim by its verification method's demonstrated reliability against ground truth" — and never changes and is out of the agents' reach. What is learned is the *reliability table*, and that is written by *outcomes*, not by the agents.** A method's grade rises only when its past certifications proved correct against later truth, which no agent controls. `h` gets to learn without the agents getting to lower the bar, because the thing being learned is set by the world.

**(v1) What K must be — open design requirement.** A single confidence chain `{⊥ < Low < Med < High}` cannot distinguish "High because recomputed" from "High because a possibly-correlated panel agreed." K must carry *how* the grade was earned, so the Gate can hold "computation-High" and "consensus-High" to different bars. This is "the core must know the strength of its own grounds" as a requirement on K's structure — **a genuine open problem, not solved here.**

**Fail-closed, with the right meaning.** A claim with only ungrounded tokens evaluates below θ → does not sign. `Missing` is the preserved zero. "No checkable grounds → I don't know" is not a rule enforced; it is what `h` *computes*. "I don't know is first-class and unfakeable" is literally "the absorbing zero, and homomorphisms preserve it."

**Why the Gate is constitutional.** If the agents could self-organize the *standard for what counts as proven*, they could lower it to sign what they want. The Gate must be out of reach of the agents it judges — the one component deliberately *not* intelligent, so it cannot be persuaded.

### I.3 The Adversarial function (the guaranteed skeptic) — unchanged from v0

Required as a *guaranteed function*, because self-organizing solving agents drift toward **consensus**, and corroboration reads consensus as evidence. An unopposed network's failure mode is **false confidence** — it converges on a shared hallucination and the Gate fires on it (§V risk 1).

- **Structural adversarialism** (in the substrate): any agent can refute; the glut holds contradiction; the Gate refuses to sign what is contested. The *safety net*.
- **Motivated adversarialism** (the red team): a standing skeptic *function* attacks whatever the network is converging on — refutations, counterexamples, alternative framings, correlated-error signatures. The *active force* the con-channel alone does not supply.

**Fixed as a function, fluid as an assignment.** *That* the role is filled is constitutional; *which* agents fill it **rotates**, to prevent the skeptics themselves becoming a monoculture. Two targets, across the whole loop: attack the **plan** during planning, attack the **claims** during verification. (Institutional lineage: *advocatus diaboli*, murder board, red team, adversarial review.)

### I.4 — (renumbered) see I.5.

### I.5 The Credibility rule and the Method-reliability rule (how trust is earned)

Two rules of the same shape, both constitutional-rule / fluid-table:

- **Credibility rule (sources).** Update per-*source* valuation ν̂ by whether an agent's claims were later confirmed or refuted — "agents corroborated together (by *independent* others) weighted together." Hebbian credibility.
- **(v1) Method-reliability rule.** Update per-*method* reliability by whether the method's certifications proved correct against later ground truth. A method's reliability is *itself* an out-of-sample track record, subject to the same sealed-vault discipline as any predictive claim, and it **decays** and is **scoped** (reliable for filings ≠ reliable for transcripts).

**Why the rules are constitutional though the tables are fluid.** *How* trust is earned is fixed; *who/what* is trusted changes. If agents could self-organize the rules for earning credibility or method-reliability, they would game them. **The rules are constant; the learned weights are fluid, and written by outcomes.**

**The pathology both rules must be designed against (open problem, not solved):** reward *calibrated independence*, not *conformity*. A reliable-but-contrarian source/method must gain weight; a popular-but-unreliable one must lose it. A naive "agreement = reliability" rule builds groupthink — the echo-chamber pathology. Designing the update to reward calibrated independence over agreement is the genuine research problem at the framework's heart.

---

### Summary of the constitution (v1)

| Fixed component | What it is | Why it cannot self-organize |
|---|---|---|
| **Claim Ledger** | Durable non-generative store = workspace + 3-tier memory | Rewritable memory confabulates; audit trail worthless |
| **Librarians** | Memory *processes* over facts, relationships, **methods** | Reinventing memory management per task destroys persistence |
| **Gate + `h`** | Exogenous signing predicate; `h` = grade-by-method-reliability | Agents would lower the bar / forge their own confidence |
| **Adversarial function** | Guaranteed motivated skepticism (rotating) | Unopposed agents converge on shared hallucinations |
| **Credibility & method-reliability rules** | Fixed rules updating fluid trust/reliability tables | Agents would game the rules for earning trust |

Everything not in this table is fluid.

---

## Part II — The interaction loop (first-class) — (v1) new, equal in standing to the core

The human is *in* the loop, not behind it. This layer is not a UI wrapper; it is where the human's irreplaceable acts enter, and it is as much the architecture as the reasoning core. The system does the tireless, auditable, honestly-graded part and refuses to fake the rest; the human supplies taste, context, and accountable stakes.

### II.1 Where the human enters — three points, different in kind

- **Framing — required.** The human sets the problem *and its stakes*; stakes set θ (the signing bar). The system cannot invent what matters or how sure it needs to be. *Taste about what matters.*
- **Steering — available.** The human *may* inject context the system lacks, and *does* feed judgment back into the ledger; the loop runs without it if the human is silent. *Taste as context.*
- **Deciding — irreplaceable.** The human judges the answer and, where stakes exist, acts — bearing consequences no architecture touches. *Taste as judgment under consequence.*

### II.2 The handoff — the crux, and the product the human experiences

At verification, the system partitions and presents **both piles, with provenance**:
- **Signable** (Resolved, cardinality 1, `h ≥ θ`, verified): "here is what I can stand behind, and here is exactly why each thing earns it."
- **Unresolved, over to you**: "here is what I cannot ground — the fact-specific, the ungroundable judgment, the decayed edge — and here is precisely why it is yours."

This is the JARVIS moment: not "here is my answer," but "here is what I know, here is what I can't know, your judgment goes *here specifically*." The handoff's *format* is where the whole system's honesty becomes usable or becomes a wall of hedged noise — an open design problem with real taste in it.

### II.3 The human's judgment re-enters as a typed claim

When the human decides "the AI narrative holds" / "that analogy is the right one," that judgment **becomes a new claim in the ledger — marked with its true provenance: human judgment** — usable, but honestly typed, not laundered into false certainty nor discarded. The human is a *source*, typed like any other, and over time (§II.5) the human's own reliability on a class of judgment is itself measured.

### II.4 The four-move terminal model — the loop's actual end each turn

The loop does not terminate in a *decision*; it terminates each turn in the human **judging the answer and selecting one of four moves**, three of which re-enter the loop at different depths, one of which completes it. The selection *is* the continuous application of the human's taste, and it is *also* the signal the system learns from.

| Move | Human's verdict | Routes to | Signal to the system |
|---|---|---|---|
| **Adjust** | You answered a slightly wrong question | Reframe (§III.1), memory warm, work reusable | The framing step was weak — front-load the missing frame next time |
| **Follow-up** | Right, but not resolved enough | Drill on a narrowed sub-claim, frame reused | The handoff was too terse — surface more grounding upfront |
| **Related** | Satisfying, and it sparked a new question | New pass, **inherited memory** | Success — the good move |
| **Satisfied** | Sufficient for my purpose | Loop completes (acting-in-world is a special case here) | Complete success |

**The distribution of the four moves is the system's report card.** Success is defined as: give answers good enough that the human's next move is usually *related* (satisfied and sparked) rather than *adjust* (misunderstood) or *follow-up* (under-shown) — and learn, from which move it draws, how to make that true more often. This makes the system improve at the *interaction*, not just the content.

---

## Part III — The problem-solving algorithm

The loop: **retrieve → plan → execute → verify → (branch) → consolidate**, closed by recurrence, wrapped by the interaction loop (Part II). It is a *loop, not a line* — and it mirrors deliberate human problem-solving and the scientific method.

**Step 1 — Retrieve (gated, three-branch).** Librarians query the three tiers. Match decision is gated: **strong** → retrieve and adapt; **none** → plan fresh; **partial/uncertain** → retrieve as a *hypothesis to test*, not a template. A partial match is *more* dangerous than none (it anchors the effort in a mistaken frame that feels justified by "experience"). The gate philosophy applied to memory.

**Step 2 — Plan.** Solving agents self-organize; on a match, adapt the retrieved process/method. **Memory informs planning; it never replaces it.** Adversaries attack the plan, including the Step-1 match decision.

**Step 3 — Execute.** Executor/specialist agents produce **claims with cited grounds** into the ledger — each carrying provenance, and ungroundable claims *flagged as such*, not suppressed. Execution *generates*; it does not verify.

**Step 4 — Verify / Gate (critical).** For each claim, find the verification method (from the methods tier), run it, and grade by **that method's demonstrated reliability** (§I.2). Grounding claims → grounding-`h`; predictive claims → predictive-`h` (out-of-sample track record, decay-discounted); bare assertions → low. The adversary hunts correlated-error signatures so ⊕ does not double-count shared-provenance agreement. *This is where the algebra acts.*

**Branch.** *Signable* → Consolidate. *Not signable* (Missing / Conflict / Ambiguous) → **loop back** to Plan (re-approach), Execute (narrower sub-task), or Retrieve (different prior) — until signable *or* the task is determined **unresolvable** and returned honestly. Fail-closed is the stopping rule: never loop forever, never force a bad answer.

**Handoff (Part II.2) and the human's move (Part II.4).** The partition is presented; the human judges and selects adjust / follow-up / related / satisfied. On satisfied-with-stakes, the human decides and acts; the decision re-enters as a typed human-judgment claim (§II.3).

**Step 5 — Consolidate (the write-back that makes it learn).** Write back: the process used, the signed claims, the **dead ends the adversaries found**, whether the retrieved memory **helped or misled**, and — crucially — update the three tiers: facts, validated relationships, and **method reliabilities**, all against whatever ground truth arrived. The human's move and (over time) the human's call are recorded too. Without this, the memory check permanently queries an empty shelf; with it, the system accumulates competence.

---

## Part IV — How memory, credibility, recurrence, and the frontier interlock

**The learning mechanisms are one system, viewed several ways:**
- **Adaptive credibility / method-reliability (§I.5)** = learning *who and what to trust* — updated at Verify and Consolidate.
- **Memory (§I.0–I.1)** = learning *what is true* — episodic → semantic promotion.
- **Recurrence (loop-back)** = feeding conclusions back to be re-worked.

Run together they produce what a stateless network cannot: a system that **accumulates knowledge, learns from its own history, and improves with experience** — while every accumulated belief keeps its provenance and can be traced and revised.

**(v1) The frontier boundary — the system's most useful act at the edge.** The system **reasons truthfully over what exists** and, where the known runs out, says so *honestly and precisely* — "unachievable by any method whose reliability I can ground" — rather than overclaiming "impossible." A claim about whether something *can be created* is a claim about a *future world*: ungroundable at the moment of asking, graded low, handed back. The honest limit is not a wall; it is a **map to exactly where human creation is required**, and drawing that border precisely is the most valuable thing the system can do at the frontier — it *aims* the human's irreplaceable act instead of leaving it vague. (Illustration: a correctly built system says "unsynthesizable by known methods — whether a new method exists is yours," not "impossible"; the human then changes what exists, and the world updates. The machine's rigor defines the shape of the hole; the human fills it.)

This is the whole project restated at the system level: not a brain, but **collective cognition that shows its work — and can therefore revise itself, and can therefore mark honestly the edge past which only a human can go.**

---

## Part V — Known risks and open problems

Carried forward and extended. Design targets, not solved features.

1. **Correlated errors / monoculture (the deepest threat).** Shared-base-model agents share failure modes; corroboration reads *echo* as *agreement* and the Gate fires hardest on a *unanimous shared hallucination*. Mitigations, all partial: enforced model/prompt diversity; an independence-weighting term in ⊕ (do not double-count shared-provenance lineage); the motivated adversary hunting correlated-error signatures. **Test first, empirically.**
2. **Fail-closed gridlock at scale.** Many stochastic agents → many gluts → a contradictory network signs nothing and tips from "cautious" to "inert." Relieved only by grade and reliability tie-breaking — so usefulness at scale depends on the credibility/method axes being right.
3. **Credibility/method-reliability conformity collapse.** If the update rewards agreement, it builds groupthink and suppresses the correct contrarian. Rewarding *calibrated independence* is unsolved.
4. **Consolidation without fabrication.** Structured, provenance-preserving compression — not lossy summarization — is the hard problem governing whether memory scales.
5. **Institutional false memory.** A wrong conclusion consolidated into semantic memory corrupts every future task that builds on it, with the authority of "established knowledge." Defense: provenance-preserving consolidation enabling retraction cascades.
6. **Cost and emergence.** Many agents is 100×+ one call. Two things must hold and neither is obvious: beat a *single frontier model at matched cost*, and be *superadditive* (solve what no individual agent can). Absent superadditivity it is expensive parallel search with a picker.
7. **(v1) Corrupted verification methods — the highest-leverage failure.** A wrong *fact* corrupts one claim; a wrong *relationship* one pattern; a wrong **method silently mis-certifies every claim it touches**, with the full authority of "verified." The methods tier therefore needs the *strictest* validation (a method's reliability is itself an out-of-sample track record, sealed-vault-disciplined) and the *most essential* provenance (so a faulty method triggers a retraction cascade over all it certified). This risk is *created by* v1's method-reliability advance and is not solved by it.
8. **(v1) The structure of K.** A scalar grade cannot distinguish confidence earned by computation from confidence earned by a possibly-correlated panel. K must encode *how* a grade was earned. Open.

---

## Appendix A — Worked examples (illustrative; not components)

These taught us things; they are not part of the system. Included so the reasoning is checkable.

### A.1 Markets (taught the two-`h` split and the in-sample/out-of-sample discipline)

Pointing the system at "will this stock make money" was a worked example, not a proposed trading system. What it taught: (a) the **two-`h` split** — grounding the *inputs* (revenue, moving averages) certifies features, never the *forecast*, which needs predictive-`h` from an out-of-sample track record; (b) the domain is a pure stress test of the *discipline*, because its dominant failure mode — trusting an overfit **in-sample backtest** — is exactly what the trustworthy core exists to prevent, so `h` must map in-sample fit low and survived-out-of-sample high; (c) the honest lesson on value: grounding-confidence (right for signatures) is worthless-for-edge where the grounded facts are public and priced, while *validated statistical relationships* among public facts are where real edge lives — the hardest-earned form of confidence, which is exactly what `h` certifies. Historical validation requires: chronological (never random) split, a **sealed vault** evaluated **once** against a **pre-registered** procedure, **point-in-time** features (no revised figures, no survivorship), **alpha-adjusted** grading (skill, not beta), and power-awareness. Every one of those is a mechanism for not upgrading in-sample fit to earned confidence — i.e., the trustworthy core's own discipline, practiced by hand.

### A.2 A legal question (showed the loop on a groundable domain)

"When can a government take a person's land" walked cleanly through the loop and showed: (a) legal authority has a **natural strength hierarchy** (constitution/statute > controlling in-jurisdiction case, still-good-law > persuasive > fact-specific application > unverifiable) that *is* a grading rubric for `h`; (b) the verification method — "does binding, in-jurisdiction, still-good-law authority actually stand for this proposition" — is exactly the check whose failure is the *Farris* fabricated-citation catastrophe; (c) the signable/unresolved partition maps onto the real structure of legal reasoning (determinate *rules*, indeterminate *application to facts*), so the system correctly signs black-letter rules and hands fact-specific application to the human. The **strain it surfaced**: distinguishing *genuinely settled* from *apparently settled* law (circuit splits, doctrines in flux, a case being stretched) is where the adversarial function is most critical and the human's jurisdictional judgment stays irreplaceable — the legal form of the correlated-error and decay problems combined.

**Cross-example lesson:** the trustworthy core is worth the *most* where being defensibly, verifiably correct *is the product* (law — a lawyer signs everything under Rule 11) and *least* where the edge is in unverifiable judgment (raw market prediction). Same discipline, opposite value, depending on whether a signature is on the line.

---

## Appendix B — Hypothesis: the killer usage (unvalidated)

**Status: strategic hypothesis, not a finding.** Derived from what the system is *uniquely* good at (manufacturing earned, auditable confidence over checkable claims, and refusing to fake it), the highest-value usage is conjectured to be the **trust layer between AI output and a human's liable signature** — the layer that sorts any AI-generated work into "grounded, signable" vs. "unverified, your judgment," with provenance showing. The wedge is conjectured to be **legal authority-verification** (pain acute, public, already-punished via sanctions; claims maximally groundable; signature mandatory under Rule 11), with the defensible slice being *support-and-settledness* verification, not existence-checking (table stakes). The category behind it — "infrastructure for signing AI-generated work" — is conjectured to be created at scale, now, by the very AI boom that makes generation free and trust scarce. **None of this is validated; it is a bet about where the built system would be most valuable, recorded to be tested, not believed.**

---

## Appendix C — Hypothesis: validated meta-pattern transfer (highly speculative, unbuilt)

**Status: conjectured capability, unbuilt, untested — the most speculative idea in this record.** The conjecture: the system can (1) *apply* known, stored methodologies to matching new questions — this is just the methods tier working, and is not speculative; and (2) *generate candidate cross-domain meta-patterns* ("this legal grounding is structurally like credit-clause grounding — apply that methodology?") as **ungrounded, low-graded claims**, and then **learn from outcomes which transfers are valid**, accumulating a library of *validated meta-patterns* whose "depth" is earned by demonstrated track record of successful transfer — exactly the out-of-sample discipline applied to abstraction itself. The **hard boundary that does not move**: the *first* trust of a *genuinely novel* abstraction, before any track record exists, remains the **human's act**, because trusting a novel transfer on no evidence is indistinguishable at the moment of creation from a beautiful hallucination — the exact thing the system exists to refuse. A correctly built system *proposes* novel transfers and *flags them unverified*; it does not confidently apply them. The value, if this works, is that the system **remembers and compounds validated human taste** until most questions no longer need fresh taste — and honestly flags the ones that still do. **This requires the system to exist and to have run enough to accumulate transfer track records; it is a direction, not a claim.**

---

## Appendix D — Lineage of the ideas (for the record)

- **Blackboard systems** (Hearsay-II, 1970s) — specialists posting hypotheses to a shared structure; this architecture is a blackboard with typed epistemic states, provenance, and a methods memory added.
- **Society of Mind** (Minsky, 1986) — intelligence from many interacting agents; here the agents are not simple, so the reference class is *social*, not *neural*.
- **The verifiable claim algebra & four-valued calculus** — the project's own substrate: commutative semiring with `UNRESOLVED` as absorbing zero; Belnap/Ginsberg four-valued states; fail-closed as the annihilator law; provenance via the free semiring ℕ[X] (Green–Karvounarakis–Tannen, PODS 2007).
- **Global Workspace Theory** (Baars; Dehaene) — generative scaffolding for the ledger-as-broadcast intuition only; contested, not evidence.
- **Institutional design** — *advocatus diaboli*, red teams, murder boards, adversarial peer review — the source of "skepticism must be assigned and structural."

*End of Version 1 record — 2026-07-06.*
