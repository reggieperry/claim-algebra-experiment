# Build brief — the dev-ledger and gate harness for claim-algebra-lab

**For Claude Code. This installs the evidence half of the trust kernel around the repo's development: a claim ledger, a mechanical gate at commit, the librarian rule over live knowledge, and the demotion of generative review to testimony. The discipline kit (already installed, Scala rules included) is the process half; this brief builds the four pieces the kit does not supply.** 2026-07-08. Grounded in the fallible-oracle results (E2: the seam closes when the sign path is checked; E3: generative confirmers are correlated — joint error 0.167 same-model and cross-family, 0 mechanical) and the glut-laundering incident (a refuted claim left live in project memory).

The repo runs Opus 4.8 at xhigh with ultracode. Nothing here changes that. The harness gates artifacts, not process: it is indifferent to model, effort level, and orchestration shape. Two of its rules exist *because* of that configuration and are marked below.

---

**Execution scope.** This document's executable steps are §0 and §2–§5, verified by §8 items 2–4. `ledger-audit.py` is **supplied alongside this brief** — place it at `ledger/audit.py` verbatim; do not reconstruct it (it is fixture-tested; a rewrite would be an unverified lookalike). Out of scope for this run: the §1 port (own brief, source attached there), §8 item 1 (the port's acceptance), §8 item 5 (an event observed in operation, not a task), and §9 until §8 items 2–4 pass. Do not attempt out-of-scope items from this document.

## 0. Baseline tag — before anything else

Tag the current state so the whole harness can be rolled back in one move.

```
git tag -a pre-harness-baseline -m "State before dev-ledger/gate harness. Kit installed, Scala rules present. Roll back to this tag to remove the harness."
git push origin pre-harness-baseline
```

Record the tag name in the ledger's bootstrap entry (§2). The harness is additive — a `ledger/` directory, hooks, and skill-description edits — so rollback is checkout of the tag plus removal of the additions (§7). Do not proceed until the tag exists on the remote.

## 1. The differential gate — absent from this tree, ported as build item 2

`sdlc-gate.py` is not in the lab: its scanners are Python-bound (ruff, mypy, bandit, pytest markers, Python suppression comments, `uv run` invocation), so excluding it from a Scala repo was correct. Its engine, however — the baseline/diff worktree model, the (file, error-code) multiset identity, rename tracking, relocation-advisory downgrade, the waiver system, the verdict logic, roughly 470 of its 800 lines — is language-agnostic and ports untouched. The port is a scanner swap, done at the kit level as a scanner-plugin layer (one engine, per-toolchain scanners) rather than a lab fork, and the gate stays a Python tool that shells out to sbt the way it shells out to uv.

**The scanner specification lives in the kit's own Scala rules; the port consumes it rather than inventing one.** `scala-testing.md` §"Anti-weakening (what the differential gate forbids)" enumerates the test-weakening vectors: the munit skip forms (`.ignore`, `assume(false, ...)`, `munitIgnore`, a removed registration, a commented-out body — the framework is munit, so no scalatest forms); assertion sites counted as `assertEquals`/`assert`/`assertEqualsDouble`/`intercept`/labeled `:|` conjuncts, no net drop per file across the rename map; ScalaCheck parameter weakening (`minSuccessfulTests` lowered, `maxDiscardRatio` raised, a generator widened past its corners); a committed `forAllNoShrink`; a failure downgraded to a log; a pinned regression example deleted; and a scoverage coverage differential against merge-base, which the rule text already expects. `scala-security.md` names the lint-and-security scanners: WartRemover plus Scalafix `DisableSyntax` — the wart set (`Null`, `AsInstanceOf`, `IsInstanceOf`, `JavaSerializable`, `OptionPartial`/`TryPartial`/`EitherProjectionPartial`, `Var`, `Throw`) already covers the casts and partial accessors previously listed here as additions — and OWASP `sbt-dependency-check` failing on findings, which closes the security category previously recorded open. Suppression scan: bare `@nowarn` → the BLANKET key, filtered `@nowarn("...")` → targeted keys (the scope-broadening catch works verbatim), plus `@SuppressWarnings` and `// scalafix:off`.

**Sequencing.** The gate uniquely guards the checks themselves: every other check verifies the work, this one verifies that the checks were not weakened — and under ultracode, weakening is the move available to an autonomous workflow facing a failing check. Until the port lands, `signed` means "passed the checks as they existed at commit," and the bootstrap entry (§2) records that interim guarantee. Do not block the ledger on the port: wire §2–§5 now; port the gate second. The port is outside this document's jurisdiction: the gate's runnable copy installs to `~/.claude/discipline/sdlc-gate.py` (user scope, per the kit's `install.sh`), and the editable source is `reference/sdlc-gate.py` in the kit tree — neither is in this repo. Do not reconstruct the gate from the description above — a from-scratch lookalike is exactly the unverified artifact this harness exists to prevent. Build item 2 receives its own brief and runs in the kit's source directory — where `reference/sdlc-gate.py` and the kit's tooling live — not in this repo; the lab receives the ported gate afterward through a kit update.

**Acceptance of the port** is four probe commits on a scratch branch, each of which must be blocked: (1) add a bare `@nowarn`; (2) add an `.ignore`-marked test; (3) remove one assertion from an existing test; (4) lower `minSuccessfulTests` in one suite's `scalaCheckTestParameters`.

## 2. The dev-ledger

A repo-visible record of claims about the repo's own development. Distinct from the game's belief ledger: same concept, disjoint subject matter, separate store. State this in the README so no future instance folds one into the other.

**Location.** `ledger/claims.jsonl` (append-only) and `ledger/trace/` (retired entries). Repo-visible, not buried in tooling.

**Schema.** One JSON object per line:

```json
{"id": "clm-0001",
 "ts": "2026-07-08T00:00:00Z",
 "sha": "<commit under claim>",
 "subject": "<file, module, or invariant>",
 "claim": "test suite passes | types check | no verification-surface weakening | invariant <name> holds | <free text>",
 "source": "claude-code | subagent | human",
 "kind": "assertion | testimony | refutation",
 "about": "<claim id this testimony/refutation targets; null for assertions>",
 "check": "scala-suite | typecheck | sdlc-gate | scrub-gate | none",
 "status": "unverified | signed | refuted | retired",
 "discharged_by": {"check": "...", "run": "<log ref>", "ts": "..."},
 "supersedes": null,
 "trace_reason": null}
```

**Status semantics.** `unverified` — asserted, no check has run; the honest "I don't know," on the record. `signed` — a mechanical check discharged it; `discharged_by` is mandatory. `refuted` — a check failed it; the entry stays on the record. `retired` — superseded or defeated; the entry moves to `ledger/trace/` with `trace_reason` and a pointer (`retired_by`) to what defeated it. **Entries are immutable: a transition is a new appended entry whose `supersedes` names its predecessor** (unverified → a signed or refuted successor; a revised assertion supersedes its earlier form), and the current status of a claim is the head of its supersedes chain. The only removal is retirement — a verbatim move of the entry to `trace/`. Never edit a line; never delete outside retirement. `ledger-audit.py` enforces all of this mechanically (schema, signed-implies-mechanical-discharge, testimony-never-signs, chain legality, immutability against git history, trace integrity) and warns on the glut signal — a live claim with a standing refutation and no successor or retirement.

**Bootstrap entry.** `clm-0000`: records the harness installation, the baseline tag from §0, the jurisdiction boundary (checks govern from this commit forward; the bootstrap commits precede it), and the interim guarantee: until the differential gate ports (§1), `signed` attests "passed the checks as they existed at commit," not "passed checks the signer could not weaken." Supersede this entry when the port lands.

## 3. The gate as claim-discharge — hook placement is a hard requirement

Wire the checks into a pre-commit (or pre-merge) hook that the commit path physically passes through:

1. On commit, run the mapped checks: test suite → behavioral claims; compiler → type claims; the differential gate, once ported (§1), invoked at its installed path `~/.claude/discipline/sdlc-gate.py` per the kit README → the no-weakening claim; `scrub-gate.sh` (if the lab grows one) → the no-private-identifiers claim.
2. Every check that passes appends a `signed` entry naming itself in `discharged_by`. Every check that fails appends a `refuted` entry **and blocks the commit**.
3. A "done" report from any model — orchestrator or subagent — creates an `unverified` assertion. It never creates a `signed` one. No path exists from generation to signature except through a check.
4. The hook's last step runs `python3 ledger/audit.py --root .` and blocks on any hard violation — the ledger's own invariants are themselves a gated check on every commit.

**Ultracode rule (placement).** The session runs autonomous, long-horizon, multi-stage workflows. The gate must therefore be a hook in the commit path, never an advisory step in a prompt or skill: an orchestrated workflow that can commit must be structurally unable to commit around the gate. If any automation commits via a route that bypasses hooks, close that route before enabling it.

**Insertion mechanics.** All writers append through one helper (`ledger/append`) that validates the schema and assigns id and timestamp; retirement runs through `ledger/retire <id> <reason> <refuting-id>`. Five writers: the hook (auto-claims the standard set — suite, types, no-weakening — adjudicating each in the same pass, and sweeps pending `unverified` claims whose `check` names a runnable check; the sole writer of `signed`); Claude Code (assertions only, per a CLAUDE.md instruction: one `unverified` entry per claim on any done-report, `check: "none"` when no check exists); plan approval (plan claims as bulk assertions, panel verdicts as testimony, changed plans as supersession); review skills and workflow verifiers (testimony and refutation only); the human (`source: "human"`, anything except signatures). Forgery guard: the hook validates the ledger diff on every commit — any new `signed` entry it did not itself write blocks the commit. Sha timing: pre-commit has no commit sha, so refutations write immediately at block time (parent sha plus patch-id), and `signed` entries write post-commit when the sha exists, staged by the post-commit step.

## 4. The demotion rule — generative review testifies, never signs

Measured basis: E3's joint error for two generative confirmers is 0.167 whether same-model or cross-family; the mechanical check's is 0. Correlated confirmation is not verification.

Covered by name: the kit's `pr-review` skill, the kit's `deep-reason` skill, and **dynamic-workflow verification subagents**. Ultracode fans substantive tasks out across verifying subagents; those verifiers are all the same model, which is the 2-backer configuration at orchestration scale — it will read as independent verification and is not.

Mechanics, in order of what actually enforces. The **structural** enforcement is §3's schema: nothing acquires `status: "signed"` without a `discharged_by` naming a mechanical check, so testimony physically cannot sign regardless of what any prompt says. The **prose** rule lands in the repo's CLAUDE.md (project scope), one line covering all three named reviewers: "Review output — pr-review, deep-reason, and dynamic-workflow verification passes — is testimony in the dev-ledger; only mechanical checks sign." Do not edit skill files under `~/.claude` from this brief: that is user scope, and the policy would leak into unrelated repos. Dynamic-workflow verifiers have no editable description at all; the CLAUDE.md line and the hook are what govern them. Review output appends `kind: "testimony"` entries, or `kind: "refutation"` when a reviewer finds a defect — generative refutations are welcome and actionable; they block nothing by themselves but are exactly what the checks should then confirm.

## 5. The librarian rule — retired claims leave the live board

Motivating incident: the refuted glut-laundering claim sat live in project knowledge while its refutation lived elsewhere. The rule, over both stores:

- **Ledger:** when a later refutation defeats a signed or unverified claim, the defeated entry moves to `ledger/trace/` with `trace_reason` pointing at the refuting entry's id. The live file never carries a claim whose defeat is on the record.
- **Kit memories (`memories/`):** when a ledger refutation contradicts a memory file, the memory moves to `memories/trace/` with a one-line header naming the refuting ledger id. Never edited silently; never left standing.

Retirement is mechanical where possible (the channel test: a standing refutation and no surviving support) and otherwise a human call recorded in `trace_reason`. Recovery is allowed — a traced entry can return via a new entry that cites it — but the traced original is immutable.

## 6. What does not change

- **Model, effort, ultracode.** Opus 4.8, xhigh, ultracode stay exactly as configured. The harness is generation-agnostic: it gates commits and claims, not reasoning depth or orchestration shape. A single-window edit and a five-subagent workflow meet the same gate.
- **The kit's rules and guides.** Untouched. They define what "done" asserts; this harness records and adjudicates those assertions.
- **The game's belief ledger and the algebra code.** Untouched. The dev-ledger is about them, not part of them.
- **The floor discipline.** No check threshold is lowered anywhere in this build. Checks are added, never weakened — the standing rule.

## 7. Rollback

`git checkout pre-harness-baseline` restores the pre-harness tree. Concretely, removal means: delete `ledger/`, remove the hook wiring, revert the skill-description lines from §4, and (if built) revert the §1 adapter commits. The tag from §0 is the authoritative reference for what "before" was. Ledger contents accumulated before a rollback should be archived, not destroyed — they are a record of what was claimed, even if the harness is withdrawn.

## 8. Acceptance

The harness is accepted when all of the following are observed, not argued:

1. **The gate bites** (acceptance of build item 2, the §1 port). The three probe commits each produce a blocked commit and a `refuted` ledger entry naming the check. Items 2–5 do not wait on this one.
2. **The structural invariants hold, mechanically.** `python3 ledger/audit.py --root . --report` exits 0 on the live ledger: every `signed` entry names a mechanical check with a run reference, testimony never signs, chains are legal, committed lines are immutable, trace entries carry reasons and pointers — and the UNVERIFIED map prints. The audit ships with the harness (`ledger/audit.py`) and is fixture-tested: a clean ledger passes, and each violation class trips its named check.
3. **Demotion is real.** A test PR carrying approving testimony from `pr-review` and from a workflow verification subagent, but with a failing check, is blocked — approval did not sign.
4. **The librarian fires.** One superseded claim is retired to `ledger/trace/` with a pointer, and the live file no longer carries it.
5. **The kernel moment** (observed in operation after this run; not an execution-time task). The first natural occurrence of: a model reports done, a check refutes it, the refutation lands in the ledger, the commit is blocked. When that entry exists, the harness is operating on real work — a "done" that was not the model's to give, caught by a check that was not the model's to pass.

Log the acceptance run and record its ledger ids in the README. The harness is then live, and every friction it produces from that point is data — either about the repo, or about the kit's transfer, and both are wanted.

## 9. Extraction into the kit — after acceptance, in the kit's tree, not this one

This document is the build: executing §0–§5 installs the harness here, directly, by hand. The reusable installer is not a lab artifact. The kit's source directory — where `install.sh`, `scrub-gate.sh`, and `reference/sdlc-gate.py` live — is the home for anything meant to install into a *next* repo, and it already contains the installer the harness should extend. After §8 items 2–4 pass, the extraction is a kit-directory task, in a session rooted there: fold the harness (the ledger schema and `clm-0000` template, the hook, `ledger/audit.py`, the CLAUDE.md demotion line, the trace directories) into the kit's install path — extending `install.sh` or adding `install-harness.sh` beside it — and extend the kit's self-audit with a verify step that checks the §8 items mechanically. This repo contributes the reference implementation to copy from; it does not gain an installer of its own. Requirements carried over: idempotent (proven by re-running against this repo as a no-op) and hook-is-a-hook (self-test: a deliberately failing commit on a scratch branch is blocked). The installer-as-first-customer pattern — its "installed per brief" claim entering as the new ledger's first working entry, discharged or refuted by the verify step — applies to those future installs, where the ledger starts empty; here the ids are already taken by the direct build. Scope guard: mechanize the sections named above and nothing more; a config framework or interactive menu is gold-plating and should be cut.

*End — 2026-07-08.*
