export const meta = {
  name: 'committee-review',
  description: 'Multi-lens committee review of a design/decision/question, then a synthesized verdict',
  whenToUse: 'A verdict-shaped decision: a design doc, an ADR, "is X the right approach?", a strategic call. Pass the question/target (and any pointers) as args.',
  phases: [{ title: 'Review' }, { title: 'Synthesize' }],
}

// The target is whatever the caller passes as `args` (a string question, or an object with details).
const TARGET = typeof args === 'string' ? args : JSON.stringify(args ?? {}, null, 2)

const CONTEXT = `REVIEW TARGET / QUESTION:
${TARGET}

Ground yourself in the repo before judging: read the design doc / code under review, the project's
coding discipline in .claude/rules/ (craft-* + scala-*), and the relevant docs/ findings. The project's
cardinal rule is FAIL-CLOSED — never sign or admit a wrong/partial/ungrounded value. Secondary virtues:
deep, non-leaky abstraction (Ousterhout/Liskov) and honest reporting (do not manufacture an advantage;
the project has killed features for having no measurable edge).

MAPPING-VERIFICATION RULE: when the target claims a code construct IS some spec/algebra concept (e.g.
"the corroboration floor = the verify conjunct"), verify it against the construct's actual definition and
wiring — the parameters it is called with, the guards around it — never from the spec's shape alone, and
never by accepting the target's own framing. This is where a review silently reproduces the author's error.`

const LENSES = [
  ['safety', 'Judge ONLY safety and correctness. Is the fail-closed rule preserved? Any reachable path to a wrong/ungrounded result? Trace it concretely.'],
  ['design', 'Judge the abstraction and complexity (Ousterhout/Liskov). Is the seam deep and non-leaky, the right design? Is there a simpler or stronger alternative? Scope / YAGNI.'],
  ['adversary', "Attack it — including the target's OWN premises and every code↔spec mapping it asserts; do NOT refine within its framing. Find where it breaks or a claim is too strong, verify each asserted mapping against the code's actual wiring, give concrete counterexamples; concede where you cannot break it."],
]

phase('Review')
const reviews = await parallel(
  LENSES.map(([name, brief]) => () =>
    agent(`You are the ${name.toUpperCase()} reviewer. ${CONTEXT}\n\nYour lens: ${brief}\nBe concrete and decisive; ground every claim in the actual files.`, {
      label: `review:${name}`,
      phase: 'Review',
    })
  )
)

phase('Synthesize')
const verdict = await agent(
  `Synthesize the committee's verdict. ${CONTEXT}\n\n` +
    reviews.map((r, i) => `=== ${LENSES[i][0].toUpperCase()} REVIEW ===\n${r}`).join('\n\n') +
    `\n\nProduce a clear PROSE verdict (no JSON — schema synthesis has crashed before). Open with exactly
one line: "VERDICT: PROCEED" or "VERDICT: PROCEED WITH CHANGES" or "VERDICT: RECONSIDER". Then: (1) the
blocking concerns, if any; (2) the required changes, concretely; (3) the strongest alternative raised and
whether it beats the proposal; (4) any empirical unknown that should be spiked before committing; (5) any
conclusion resting on an unverified code↔spec mapping — flag it explicitly as needing a direct source
read. Do not certify a mapping none of the lenses checked against the code: these lenses are the SAME
model reasoning from one framing, so agreement is correlated, not independent validation. Weight
fail-closed safety first, then abstraction quality, then simplicity. Be decisive.`,
  { label: 'synthesize', phase: 'Synthesize', effort: 'high' }
)

return verdict
