# The dev-ledger and gate harness

A repo-visible record of **claims about this repo's own development**, and a mechanical gate at commit that discharges them. Installed per `dev-ledger-gate-harness-build-brief.md`. The evidence half of the trust kernel: a model reporting "done" creates an *unverified* claim; only a mechanical check turns it into a *signed* one.

## Not the game's belief ledger

This is **distinct from the reasoning-society game's belief ledger** (`Testimony`, the algebra's four-state store). Same concept — claims with graded status — but **disjoint subject matter** (this repo's development, not a game's target) and a **separate store** (`ledger/claims.jsonl`, not the algebra's fold). Do not fold one into the other; they never share entries.

## Layout

- `ledger/claims.jsonl` — append-only, one JSON object per line. The live board.
- `ledger/trace/` — retired entries (one `<id>.jsonl` per retirement). Off the live board, still on the record.
- `ledger/audit.py` — the mechanical checks over the ledger (supplied verbatim; do not edit).
- `ledger/append` — the ONE schema-validating writer. Assigns id + timestamp. `echo '{...}' | ledger/append`.
- `ledger/retire <id> <reason> <refuting-id>` — the ONE sanctioned removal: move a defeated/superseded claim to `trace/`.
- `ledger/librarian` — reports contested claims (the glut signal) and retires superseded ones.
- `ledger/gate.py` — the commit-path gate (forgery guard, check-discharge, audit), called by the git hooks.

## Schema and status semantics

One object per line: `id, ts, sha, subject, claim, source, kind, about, check, status, discharged_by, supersedes, trace_reason`.

- `kind` — `assertion` (a claim), `testimony` (review output, never signs), `refutation` (a defect, `about` a claim).
- `status` — `unverified` (asserted, no check ran — the honest "I don't know"); `signed` (a mechanical check discharged it, `discharged_by` mandatory); `refuted` (a check failed it, stays on the record); `retired` (superseded or defeated, moved to `trace/`).
- **Entries are immutable.** A transition is a NEW appended entry whose `supersedes` names its predecessor; the current status of a claim is the head of its supersedes chain. Only `unverified` may be superseded in place (`unverified → signed | refuted | unverified`); a `signed` or `refuted` claim is never rewritten — it is defeated by a refutation and then retired. Never edit a line; never delete outside retirement.
- **Only a mechanical check signs.** `discharged_by.check` must be a real check (`scala-check`, `scala-suite`, `typecheck`, `sdlc-gate`, …) with a run reference — never a generative source (`none`, `pr-review`, `deep-reason`, `workflow-verify`, a model). The audit enforces this; the gate is the sole writer of `signed`.

## The immutability discipline for retirement

The audit holds every line ever **committed** to `claims.jsonl` to be byte-identical now, in live or trace. Because retirement rewrites the moved entry (adds `trace_reason` + `retired_by`), **retire a claim in the same commit that supersedes or defeats it** — not as a later edit to a long-committed line. Supersede-then-retire-then-commit is the batched form the librarian and the acceptance use.

## The demotion rule (generative review testifies, never signs)

Measured basis (fallible-oracle E3): two generative confirmers share a joint error of 0.167 whether same-model or cross-family; a mechanical check's is 0. Correlated confirmation is not verification. So **review output — `pr-review`, `deep-reason`, and dynamic-workflow verification passes — is `testimony` in this ledger; only mechanical checks sign.** The structural enforcement is the schema (nothing reaches `signed` without a mechanical `discharged_by`); the prose rule is one line in the repo CLAUDE.md. A reviewer that finds a defect appends `refutation` — welcome and actionable; it blocks nothing by itself, but is exactly what a check should then confirm.

## The librarian rule (retired claims leave the live board)

Motivating incident: a refuted glut-laundering claim sat live in project knowledge while its refutation lived elsewhere. So:
- **Ledger** — when a refutation defeats a claim (a standing refutation, no surviving support: the channel test), the defeated entry moves to `ledger/trace/` with `trace_reason` pointing at the refuting id. The live file never carries a claim whose defeat is on the record. `ledger/librarian` does this mechanically for superseded claims and reports contested ones.
- **Memories** — when a ledger refutation contradicts a kit memory file (`memories/`), the memory moves to `memories/trace/` with a header naming the refuting ledger id. (No `memories/` lives in this repo; the rule stands for wherever the memories do.)

Recovery is allowed — a traced entry can return via a new entry that cites it — but the traced original is immutable.

## Acceptance run (2026-07-09)

Observed, not argued (brief §8 items 2–4; items 1, 5 and §9 are out of scope for the install):

- **Item 2 — invariants hold mechanically.** `python3 ledger/audit.py --root . --report` exits 0 on the live ledger; all six hard checks (schema, signed, coherence, chains, trace, immutable) PASS and the UNVERIFIED map prints.
- **Item 3 — demotion is real.** On scratch branch `harness-accept-item3`, a commit carrying two approving testimonies (a `pr-review` and a workflow-verification subagent, both `about` the probe claim) plus a genuinely broken Scala file was **blocked** by the real `sbt check`; a `refuted` entry was recorded and the probe claim stayed `unverified` — the approval signed nothing. Branch discarded.
- **Item 4 — the librarian fires.** `clm-0001` (a draft assertion) was superseded by `clm-0002` and retired by `ledger/librarian --sweep` to `ledger/trace/clm-0001.jsonl` with `retired_by: clm-0002`; the live file no longer carries it; audit clean before and after (commit `af76e4b`).

Live ids after acceptance: `clm-0000` (bootstrap), `clm-0002` (the acceptance record). Retired: `clm-0001`. The harness is live; every friction it produces from here is data.

## The kernel moment (item 5 — observed in operation, not installed)

Still owed by operation, not by this install: the first natural occurrence of a model reporting done, a check refuting it, the refutation landing in the ledger, and the commit blocked. When that entry exists on real work, the harness is operating on a "done" that was not the model's to give.

## Rollback

`git checkout pre-harness-baseline` restores the pre-harness tree. Removal is: delete `ledger/`, revert the hook wiring (`.githooks/pre-commit` gate block, `.githooks/post-commit`), revert the CLAUDE.md demotion line. Accumulated ledger contents should be archived, not destroyed — they record what was claimed.
