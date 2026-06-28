# Workflows — the review discipline, made portable

These are the recurring multi-agent review patterns this project uses, saved as runnable workflows so
the method travels with the repo (not just the `.claude/rules/` coding discipline). They run via the
`Workflow` tool, which resolves a saved workflow by `name` from this directory.

- **`committee-review`** — a verdict-shaped decision (a design doc, an ADR, "is X the right approach?",
  a strategic call). Three independent reviewer lenses (safety / design / adversarial) → a synthesized
  PROSE verdict. Pass the question or target as `args`.
- **`adversarial-verify`** — before committing a change that signs/admits a value or touches grounding
  or the gate. A fail-open hunt + a correctness audit → a MERGE_SAFE / FIX_NEEDED verdict. Pass the
  change description and the files as `args`.

How to run (from a Claude Code session):

> "Run the `committee-review` workflow on `docs/credit-deal-workbench/<doc>.md`"
> "Run `adversarial-verify` on the change to `Workbench.groundByAnchor`"

Notes:
- Synthesis returns **prose**, not a schema — a strict-schema synthesizer has hit the structured-output
  retry cap and crashed; prose is robust.
- Running a saved workflow is invocable on an explicit request (it does not require ultracode to be on).
- These are templates: for a one-off with bespoke reviewer prompts, author a `Workflow` inline instead.

The working rhythm they serve: build a slice → `sbt check` green → adversarially verify → commit → push
on command (TDD red-first). See `CLAUDE.md` ("Working method").
