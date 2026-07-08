export const meta = {
  name: 'adversarial-verify',
  description: 'Adversarially verify a change to the fail-closed core for reachable fail-opens, then a verdict',
  whenToUse: 'Before committing a change that signs/admits a value or touches grounding/the gate. Pass the change description + the files as args.',
  phases: [{ title: 'Hunt' }, { title: 'Verdict' }],
}

const TARGET = typeof args === 'string' ? args : JSON.stringify(args ?? {}, null, 2)

const CONTEXT = `CHANGE UNDER REVIEW:
${TARGET}

Read the changed code and the relevant .claude/rules/ (esp. scala-security.md, scala-errors.md) and
docs/ findings. The CARDINAL RULE is FAIL-CLOSED — never sign, admit, or return a wrong, partial, or
ungrounded value; an error must degrade to a gap/abstention, never to a confident wrong result and never
to an uncaught crash that bypasses the gap.

MAPPING-VERIFICATION RULE: when the change's safety rests on a claim that a code construct IS some
spec/algebra concept (e.g. "this floor discharges the verify conjunct", "this event is belief-inert"),
VERIFY that mapping by reading the construct's actual definition and the parameters/guards it is wired
with — never infer it from the spec's shape, and never accept the change's own framing of it. Reviews
silently REPRODUCE the author's mapping error otherwise.`

phase('Hunt')
const [failOpen, correctness] = await parallel([
  () =>
    agent(`You hunt a REACHABLE FAIL-OPEN — a path that signs/admits a wrong, partial, or ungrounded value. ${CONTEXT}
Try hard with concrete inputs; trace each candidate through the actual code; probe boundary/offset/cardinality/parse-mispairing/error paths. Report only genuinely reachable fail-opens; concede if you find none.`, {
      label: 'hunt:fail-open',
      phase: 'Hunt',
    }),
  () =>
    agent(`You audit CORRECTNESS and the project's constraints. ${CONTEXT}
Check: error paths gap rather than crash or sign; the change preserves prior guarantees and tests; any throw is captured into a typed error. Report fail-closed-SAFE recall regressions (they block nothing, never sign wrong) SEPARATELY from real bugs. Be concrete; cite line behavior.`, {
      label: 'hunt:correctness',
      phase: 'Hunt',
    }),
])

phase('Verdict')
const verdict = await agent(
  `Synthesize an adversarial verdict on the change. ${CONTEXT}

=== FAIL-OPEN HUNT ===
${failOpen}

=== CORRECTNESS AUDIT ===
${correctness}

Produce a clear PROSE verdict (no JSON). Open with exactly one line: "VERDICT: MERGE_SAFE" or
"VERDICT: FIX_NEEDED". MERGE_SAFE only if there is NO reachable way to produce a wrong/partial/ungrounded
result and no error path bypasses the gap. List any required fix concretely with its triggering input;
report fail-closed-safe recall regressions without blocking. If the verdict rests on any code↔spec
mapping, confirm it against the code's actual wiring or mark it UNVERIFIED — do not anchor on the change's
own framing. (Caveat: this hunt and audit are the SAME model reasoning from one framing — agreement is
correlated, not independent; a clean verdict resting on one unverified mapping is not safe.)`,
  { label: 'verdict', phase: 'Verdict', effort: 'high' }
)

return verdict
