# Build 2 UI design — the reasoning-society instrument

A design of record (2026-07-06) for Build 2 (the memory panel + the society navigator + a layout
rearrange), synthesized from a five-facet research pass over HCI dense-dashboard principles and
production monitoring UIs. Every choice is grounded in a named system or principle (cited inline).
The invariant from Build 1 holds: every panel is a **pure reader** of a single
`(fold(log[0..playhead]), selection)` state; **live = `playhead == head`**.

## 0. The one interaction model

```
UIState = { playhead: EventIndex, selectedAgent: AgentId | null, expanded: Set<RowKey> }
render(panel) = f(fold(log[0..playhead]), selectedAgent)
```

Exactly **two global selectors** — **WHEN** (the playhead, from the transport) and **WHO** (the
selected agent, from the navigator) — composed over every panel (Baldonado et al.'s coordinated-
multiple-views single-model discipline; Datadog's "drill retains time/filter context"). Changing WHO
never resets WHEN. **Do not add a third global selector** (it would make two of Shneiderman's rungs
compete).

## 1. The rearranged layout — six regions

```
┌─ HEADER ───────────────────────────────────────────────────────────────────────┐
│ 20-Q: "is it a mammal?"   legend ▪Resolved ▪Conflict ▪Missing ▪Superseded         │
│ ● LIVE (pinned to head)   |   gate: last ✓ SIGN c#14   |   as-of  e-142 / 142     │
├─────────────┬──────────────────────────────────────────┬────────────────────────┤
│ SOCIETY NAV │ BELIEF STATE   (overview · dominant area) │ MEMORY   (calm rail)    │
│ (left rail) │   leading hypothesis — longest grade bar  │   as of e-142           │
│  ▪A1 Interr │   ▁▂▄▆ sparkline   ⊞ Resolved             │  ▸ FACTS          142   │
│  ▪A2 Skeptic│   rival — shorter bar   ⊟ Conflict        │  ▸ RELATIONSHIPS   38   │
│  ▪A3 Prober │   rival — shorter bar   ▫ Missing         │  ▸ METHODS          6   │
│  diversity: │   … stable vertical slots …               │   (accordion — still,   │
│   2 live ⚠1 ├──────────────────────────────────────────┤    keyed, in-place)     │
│  scoped: —  │ EVENT LOG   (detail · adjacent to belief) │                         │
│             │  0142 A3 ¬ c#17 "red"        ⊟ CONFLICT   │                         │
│             │  0141 A1 + c#16 "warm-blood" ⊞ RESOLVED   │                         │
│             │  0140 ?→ human: "yes"                     │                         │
├─────────────┴──────────────────────────────────────────┴────────────────────────┤
│ TRANSPORT — full width · histogram STACKED BY CORNER · click=seek · drag=brush    │
│ ◀◀  ▐ playhead ───────────────●══ head  ▶   [ N new ↓ jump-to-live ]              │
└──────────────────────────────────────────────────────────────────────────────────┘
```

Hierarchy, justified: **belief state is the overview and takes the dominant top-left/center area**
(Grafana Z-pattern, "general→specific, large→small," size encodes importance — it is the "current
verdict"). **Society navigator is the always-visible left overview rail** (Shneiderman mantra laid
out spatially; k9s/Datadog service-list position). **Event log sits directly beneath/adjacent to
belief** (Netdata proximity — the event→belief causal pair is the tightest-correlated). **Memory is
the calm right rail** — reference, "consulted not watched," visually quieter/typographic vs the
volatile live column (CMDB single-source-of-truth vs event-feed split). **Transport spans full width
at the bottom** — time is a global axis over every panel, never docked in one. **Header carries
visibility-of-system-status** (Nielsen #1): LIVE-vs-REPLAY, `as-of e-N/M`, last gate decision, and
the persistent corner legend (recognition-not-recall). WHO on the left edge, WHEN on the bottom edge
— orthogonal selectors on opposite edges.

## 2. Society navigator (list now; graph deferred)

At 3–5 agents a **dense list, not a graph** (node-link loses to a table below ~15–20 nodes; a 5-node
force layout is jitter). Model it on the **k9s resource table** — one dense monospace row per agent,
leading status band:

| col | content | encoding |
|---|---|---|
| ▪ | the agent's dominant epistemic corner now | the SAME four-corner palette (leading band) |
| id / role | `A3 · Skeptic` | text |
| +/¬/⊘ | asserted / refuted / superseded counts | right-aligned numerics (Tufte data-ink) |
| last spoke | `@e-138` or a tick | a silent/dead agent is visible at a glance |

Header carries a **diversity scalar** — the count of distinct live candidate positions — and badges a
**warning colour when all agents hold one position with no refutation** (the monoculture signal,
computable today, no graph needed).

**Click-to-filter = brushing-and-linking** (Heer & Shneiderman): selecting an agent scopes belief +
log + memory (transport untouched — WHO ⊥ WHEN). **Highlight over hide** — dim/desaturate the rest,
never delete (removal destroys the sense of the whole society, where the monoculture signal lives). A
persistent `scoped to: A3 ✕` chip prevents linked-view amnesia; click-empty / Esc clears; the
selection survives scrubbing. **Hue is reserved for corner** — bind an agent's scattered
contributions with a *secondary* channel (a consistent left-gutter tick/texture — Gestalt
similarity; lnav's per-source gutter).

**The graph is architected now, rendered later.** Store `agents = nodes, claim-relationships = edges`
from day one; the list is a node-table *view* of that model, and the selection abstraction is a set
of agent ids, so a future graph reuses the identical linking pipeline. **Earn the graph** only on a
trigger: node count > ~15–20, or the question shifts to "who talks to whom," or monoculture needs
spatial rendering (a single dense blob with corroboration edges and no refutation edges).

## 3. Memory panel — a still, browsable three-tier store

**A store is a different affordance, not a second log** (Backstage catalog / CMDB, not a tail). Live
tail moves and you watch it; memory is *still* and you *query* it. Rows are durable keyed entities
that **update in place** (pulse and settle), never append. If memory visibly scrolls, you built two
event logs.

Render as **three stacked independently-collapsible sections** (an accordion of catalogs), each with
a count badge — not tabs (the tiers must be simultaneously scannable). Overview = the three counts
(`142 · 38 · 6`); expand a row for provenance (details-on-demand). Thin in v1 per the brief (it shows
what *this* game established; its payoff is across games). Colour = corner, inherited verbatim.

- **Tier 1 — Facts established.** One row per durable fact-key (the human's grounded answers, and any
  gate-signed value): `[corner dot] statement … [grade micro-bar] [established @e-N]`. Sort by
  relevance; recency lives in the age column, not the order. A fact that later goes Conflict **greys
  with a "reopened" flag** rather than silently updating.
- **Tier 2 — Validated relationships.** `factA —relation→ factB [strength] [age]` — this IS the future
  edge-graph's edge list rendered as a browsable list first (Backstage typed relations). Thin in v1.
- **Tier 3 — Method reliability.** One calibration bar per method: `[method] [n signed / m held-up]
  [n = …]` — **show sample size beside the rate** so a 1/1 does not masquerade as certainty; low-n
  greys out. The quietest tier (reference-of-reference). Thin/placeholder in v1.

**Provenance for free from the fold.** Every fact has an exact birth index; render `established @e-N`
as a **seek-target** (click jumps the scrubber there — details-on-demand → jump). Because memory is
the same fold projected differently, it **re-derives as you scrub** (at playhead e-100 a fact born at
e-142 simply isn't there yet); a subtle `as of e-N` header caption ties it to the transport. Never
store the rendered grade — render from the fold each tick.

## 4. Cross-panel coordination — coordinated multiple views

- **WHEN — the shared playhead.** Grafana's global time picker made literal. **Unify the playhead
  with the log's follow-tail state**: the log scroll, the scrubber thumb, and live/paused are one
  variable — scrolling the log up enters replay, clicking-to-bottom re-pins to live; show the
  standard `N new events ↓ / jump-to-live` while detached (Loki). The transport **histogram is
  stacked by epistemic corner** (Kibana `log.level` colour; lnav) — Conflict bursts and gate-abstains
  become coloured spikes; click a bar to seek, drag to brush a range.
- **WHO — the agent filter.** Click scopes belief + log + memory simultaneously; playhead untouched.
- **Coordination rules (CMV):** highlight-over-hide everywhere; instant and reversible (click-empty
  clears); **filtering is a *view* over the fold, never a re-fold — the belief fold always runs over
  the full unfiltered log; muting an agent changes display only, never the computed beliefs** (the
  critical safety rule). Hover-to-correlate is the lightweight cross-highlight (hover a log row →
  light the candidate it moved and its memory fact).

## 5. Do / Don't / Defer

**DO** — reserve hue exclusively for epistemic corner + redundantly encode with a glyph
(colorblind-safe); make the grade bar the data-ink (strip bevels/shadows/boxed borders; 1px hairline
separators on dark); sparklines for grade/reliability history; small multiples for the 3–5 agents
(identical mini belief-tiles, shared scale — the cheap monoculture precursor); keep hypothesis
vertical order stable across scrubbing (fixed slots); make the fold nature legible (LIVE-vs-REPLAY,
`as-of e-N`, last gate decision persistent); fixed monospace log columns with verb glyphs
(`+` assert, `¬` refute, `?→` human, `✓` sign, `∅` abstain), truncate + expand-on-select.

**DON'T** — overload hue with agent identity/recency (use a secondary channel); inline full
provenance into any overview; let memory scroll/append like a log; hard-remove on filter (dim);
build per-agent panels (the navigator-as-filter replaces them); let a display filter touch the belief
fold; add a third global selector.

**DEFER (premature for a 3–5 agent toy)** — the node-graph (architect the node/edge model now, render
the list; ship the graph only on the §2 trigger); multi-select agents (ship single-selection, keep
the abstraction a set); force-directed layout / edge-bundling / cluster meta-nodes; method-reliability
richer than a calibration bar.

*Sources: Shneiderman (Visual Information-Seeking Mantra); Baldonado et al. + Heer & Shneiderman (CMV,
brushing-and-linking); Tufte (data-ink, sparklines, small multiples); pre-attentive attributes +
Gestalt; Nielsen heuristics; Grafana dashboard best-practices; Datadog (Service Map, Topology, Live
Tail, hover-correlate); Kibana Discover; lnav; Loki; k9s; Netdata; Backstage/CMDB catalog; Glasgow
graph-eval survey + data-to-viz on the node-link crossover.*
