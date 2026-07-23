# Build Brief — Twenty Questions as a window into a reasoning society

**For Claude Code, building on a headless workstation, viewed remotely.**
*2026-07-06 · a build spec, not a finished design — architecture is prescribed, behavior and visuals are yours to discover*
*rev: added §1.5, the actor-model communication substrate (Hewitt/Agha) — the second half of the foundation alongside the fold*
*rev 2026-07-06 — added §1.5, the actor-model communication substrate (Hewitt/Agha), as the second half of the foundation*

---

## 0. What this is, in one paragraph

Build a browser-based **observability tool for a reasoning system**, that happens to be watching a game of Twenty Questions. A small society of LLM agents tries to guess what the human is thinking of; the human answers yes/no; the agents post **claims** to a shared **ledger** governed by a claim algebra; and the whole point — the thing that makes this different from a chatbot — is that the human **watches the society think**: competing hypotheses, grades rising and falling, contradictions (gluts) forming, and a gate that refuses to guess until confidence is earned. It is a debugger / mission-control display for a mind, not a consumer app. The reference class is an oscilloscope or a time-travel debugger, not a Twenty Questions website.

This is a **personal research toy**, built for delight and learning, **not** a product and **not** connected to any employer system (see §7).

---

## 1. The one critical architectural decision (get this right; everything hangs off it)

**The entire UI is a pure function of `(event_log, playhead_position)`.**

Every claim, answer, and state-change is appended to a single ordered **event log**. The current belief state — which hypotheses exist, their grades, their epistemic state — is computed by a **pure fold** over the event log up to the playhead. Every UI panel is a **view of the fold at the playhead**. Nothing holds its own mutable state on the side.

This is not a UI convenience; it is the foundation, and it delivers three of the four hard requirements *for free*:
- **Replay** is just "evaluate the fold up to time T" — scrubbing the timeline re-runs the fold. You are not recording video; you are replaying the log.
- **Live mode is a special case of replay** — it is replay with the playhead pinned to the latest event, auto-advancing as new events arrive. **Do not build two systems.** Build replay; live is "follow the head."
- **Layering new panels is free** — a new view (memory, graph) is just another pure reader of the same log. This is why "layer each component from the beginning" works.

If components hold their own ad-hoc state instead of deriving from the fold, replay becomes impossible to bolt on and layering becomes a tangle. **Log everything; derive everything; keep the fold pure.** (This mirrors the system's own philosophy: the reasoning produces claims, the interface only observes them — the observer gets no vote.)

---

## 1.5 The communication substrate — the actor model (Hewitt, extended by Agha)

The fold (§1) is *what the log means*. The actor model is *how messages get into it*. These are the two halves of the foundation and belong together. Agents communicate on an **actor substrate**: adopt this as **design principles, not a production framework** (see the caution at the end — do not pull in Akka for a 5-agent toy).

**The model.** Each agent is an **actor** with an **address** and a **mailbox** (a message queue). On receiving a message, an actor may do exactly three things: **send** messages to actors it knows, **create** new actors, and **designate** how it will handle the *next* message (change its own behavior/stance). Actors **share no mutable state** and communicate **only** by asynchronous message-passing — no back channels. Each actor processes its mailbox **one message at a time**.

**Why this substrate specifically (not generic concurrency):**
- **The three actor primitives are the agents' three needs.** Agents *send* claims; the system *creates* agents (spin up a specialist when a problem demands — the fluid solving layer); an agent *designates its next behavior* when it updates its stance as evidence arrives. Send / create / designate = assert / recruit / revise.
- **Agha's mailbox IS the algebra's serialization requirement.** The claim algebra's single-writer-per-slot discipline (the operational counterpart of Non-theorem 5.2) is operationally a mailbox: messages to a slot arrive asynchronously and are processed in a serialized order. Adopting Agha's mailbox semantics does not add a foreign concept — it **formalizes a discipline the architecture already assumed**. You are naming the substrate the algebra was implicitly requiring.
- **Message-only communication ENFORCES the trust firewall.** The epistemic model is that agents are untrusted generators who may influence each other *only* through claims that `h` can grade. An actor literally cannot affect another except by sending a message — and here a message is a claim, and a claim gets graded. "No shared state, communicate only by messages" is simultaneously the actor model's core tenet and the trust model's core tenet; the substrate choice *enforces* the epistemics rather than merely coexisting with them.

**How actors and the fold meet — the key convergence.** Actor message-passing is asynchronous and has no global order (mailboxes serialize *per actor*, not globally); the fold needs a *definite* ordered sequence to be a pure function. These are reconciled by the **event log as the global serialization point**: actors send claims asynchronously, and the log is where those messages are **assigned a sequence number** and become the single ordered stream the fold consumes. Agha's mailbox serializes per actor; the event log serializes globally; the fold gives the ordered stream meaning. So the actor substrate does not complicate the fold — it **explains where the fold's ordered input comes from.** Actors provide concurrency, the log provides order, the fold provides meaning.

**Scale rationale (why specify it now, for only 3–5 agents).** Same logic as the fold: architect for the destination so the increment is free. Build on actor principles — addresses, mailboxes, message-only, one-at-a-time — and scaling from 5 agents to hundreds becomes "create more actors," with the substrate already handling the concurrency. Build ad-hoc agent communication instead, and reaching hundreds means rewriting the substrate. Bake the principles in now; scale later is additive.

**Caution — principles, not a framework, for the toy.** The actor *model* is a set of rules; honor them with a **light implementation** — an actor is roughly a small entity with an address, a mailbox (queue), a one-message-at-a-time processing loop, and the send/create/designate operations. That is a small amount of code, not an industrial actor runtime. Adopting a production actor framework (Akka, etc.) for a 5-agent toy would be exactly the over-engineering this project has avoided throughout. Keep the discipline; skip the machinery; upgrade only if and when scale genuinely demands it.

---

## 2. The game model

**System guesses; human is the oracle.** The human thinks of a thing; the agents ask questions and try to identify it; the human answers. The human's job is dead simple (answer yes/no/unknown), and the society does the epistemic work you want to watch.

**Two layers of claim — this distinction is the design act:**
- **Evidence** — the human's answers, entering as *grounded facts*: `("is it alive?", yes)`. High grounding-confidence, because the oracle is ground truth. These are the grounds.
- **Hypotheses** — agent claims about *what the answer is*: "the answer is 'dog'", "the answer is in category animal". Graded by how well they fit the accumulated evidence. These are what compete.

**The gate** signs a guess only when one hypothesis is `Resolved` (fits evidence, uncontradicted), `cardinality = 1` (one candidate dominates — rivals refuted or far behind), and grade ≥ threshold θ. Until then it **abstains** — asks another question rather than guessing. Watching the gate *hold back* is a core delight; do not let agents guess prematurely.

**Where conflict / gluts come from (need at least one; both is better):**
- **Agent disagreement** (build first — it is inherent): different agents favor different hypotheses given the same evidence. Natural competing-candidate structure.
- **Contradiction detection** (add as the first "oh, cool" feature): if the human's answers are mutually inconsistent, or an agent asserts something a prior answer refutes, the system surfaces a **glut** (`Conflict`) rather than plowing ahead. Watching it say "these answers conflict" is the auditable-reasoning magic.

**Agents: few, cheap, and deliberately diverse.** 3–5 agents for the first build. Use **cheap models (Haiku-tier)** — the whole thesis is that the architecture makes weak agents trustworthy, so weak agents are the honest test, not a compromise. **Diversity is required, not optional**: give agents different strategies/personalities (one asks broad category-splitting questions, one drills specifics, one plays contrarian). This is simultaneously (a) more fun to watch, (b) the natural mitigation for the monoculture failure mode, and (c) what makes the disagreement productive. If all agents are the same model with the same prompt, they agree on everything and the game is boring — that boringness *is* monoculture, made visible.

---

## 3. The claim / event data model (make this concrete first)

Before any UI, define the event and claim structures. Sketch (adapt as you build):

- **Event** (appended to the log): `{ seq, timestamp, type, agent_id?, payload }` where `type ∈ {assert, corroborate, refute, strike, question_proposed, question_asked, answer_given, gate_abstain, gate_sign }`.
- **Claim / hypothesis** (derived state): `{ id, content, provenance (lineage of supporting/opposing event seqs), corner (Resolved|Missing|Conflict|Superseded), grade }`.
- **Provenance** is the lineage — which events (which agents, which answers) support or oppose this claim. Grade is computed from provenance, **not** self-reported by an agent (an agent's own confidence is just another claim and is not trusted). Keep the grade function simple in v1: supported-by-consistent-evidence → higher; contradicted → Conflict; thinly-supported → low.

Log-everything means: every question proposed (even ones not chosen), every answer, every grade change, every abstain decision, is an event. That is what makes replay and the graph rich later.

---

## 4. The interface — four requirements, laid out

Screen regions (all are views of the fold at the playhead):

- **Belief state (center, the centerpiece).** The live candidate hypotheses, each a row with a **grade bar** and a **color for its Belnap corner** (green = Resolved, amber = Conflict/glut, grey = Missing/unresolved). Sorted by grade. **Animates** as evidence arrives — candidates rise, fall, get struck. This is "what does the society currently believe," and watching it move is the story.
- **Event stream (right, the play-by-play).** Scrolling claims as posted: `[Agent 3] asserts: 'dog' (grade Med) ← [alive:yes, four-legs:yes]`. Color-coded by event type (assert / corroborate / refute / strike). Chronological.
- **Transport / timeline (bottom, the comprehension tool).** The DVR: scrubber, play / pause, **step one event**, speed control, playhead. **Live = playhead pinned to head.** This governs the whole UI above — everything renders at the playhead's time. This is how a human actually comprehends a process too fast and parallel to follow live, so it is not optional and it is not "phase 2 if time" — it comes early (see build order).
- **Memory panel (toggleable).** The persistent ledger / three tiers (facts established; validated relationships; later, verification-method reliability) as a **browsable store**, distinct from the live belief state. Thin in v1 (shows what this game established); its real payoff is across multiple games, when you watch it *retrieve* from a prior game.
- **Society navigator (left / top, the scale-survival mechanism).** At 3–5 agents: a simple list; clicking an agent filters all panels to its claims. Designed to grow into a **node-graph** at higher agent counts — agents as nodes, claim-relationships as edges (corroborate pulls together, refute pushes apart) — so that **monoculture becomes literally visible** (all agents collapse into one agreeing blob = echo chamber; distinct camps = healthy disagreement). Build the list first; architect so the graph is a drop-in replacement reading the same log.
- **The human's action (prominent).** The current **question** — and *which agent proposed it and why* — plus the yes / no / unknown input. The human is the oracle; this is where they act.

---

## 5. Build order (each stage is runnable and watchable; nothing is throwaway)

**Build 1 — the observability shell against a MOCK event log.**
- Event log + pure fold + playhead. Get the foundation exactly right.
- Panels: belief state + event stream + transport + the human Q&A region.
- Drive it with a **hand-written / scripted mock log** that shows off good moments: a candidate collapse, a glut forming, the gate abstaining then signing.
- **Why mock first:** debug the *interface and the fold* completely separately from *agent behavior*. The mock log also lets you deliberately inject the interesting cases (glut, monoculture, dramatic collapse) to make sure the UI *shows* them well before you are at the mercy of what real agents do. Replay works from day one because it is built on the fold.

**Build 2 — layer in the remaining views.** Memory panel, then the society navigator (list, architected for the graph). New components reading the same log — no rebuild.

**Build 3 — swap the mock agents for real ones.** Cheap, diverse LLM agents emitting real events into the log. Now it is live and real, and everything built to visualize it already works. Add contradiction-detection here or just after.

Architect for all four requirements from the start (log everything, pure fold). Build the *views* in this order so the first running thing is watchable soonest and every addition makes it *more* comprehensible.

---

## 6. Stack and environment (specific to a headless workstation viewed remotely)

- **Single-page app: React (component structure fits "every panel is a view of the fold") + SVG (grade bars and the node-graph — crisp, animatable, inspectable).** Transport is plain controls over the playhead.
- **The agents (LLM calls) run in a separate local process** that appends to the event log; the browser is a **pure viewer** of that log. This separation mirrors the system's philosophy and lets you mock the agents in Build 1.
- **The agents are actors (§1.5), lightly implemented.** Each agent = an entity with an address, a mailbox, a one-message-at-a-time loop, and send/create/designate. In the mock-log build (Build 1) there are no live actors yet — the scripted log stands in — but define the actor abstraction early so Build 3's real agents drop into it and scaling is additive. Light code, not a framework.
- **Headless build machine — critical:** the workstation has no browser in use; the UI is viewed from a laptop over the network. So:
  - **Bind the dev server to `0.0.0.0`, not `localhost`** (Vite: `server.host: true` or `--host`), so the laptop can reach it over the LAN/SSH tunnel.
  - Note the workstation's port and reach it from the laptop (direct LAN IP, or `ssh -L` tunnel forwarding the dev-server port).
  - **Do not rely on opening a browser on the build machine.** Any "take a screenshot to critique" step won't work headlessly — critique visually **from the laptop browser** instead, or add a lightweight way to dump state.
  - Running under **tmux over SSH** is right — keep the dev server (and the agent process) in named tmux windows so they survive disconnects; document the window layout in the repo README so a reconnect is trivial.
- **Vite** for the dev server (fast HMR, trivial `--host`). Keep the whole thing dependency-light; this is a toy to iterate on, not a product to harden.
- **No browser storage APIs** for anything critical — the event log is the source of truth and should live in the running process / on disk, not in localStorage.

---

## 7. Environment & IP discipline (non-negotiable for this project)

This is **personal research**, and its provenance must stay clean:
- Build entirely on the **workstation (personal hardware)**, on **personal accounts**, using **public or synthetic data only**. Twenty Questions needs no real-world data, so this is easy to honor — keep it that way.
- **Nothing from any employer system, engagement, or client touches this.** Do not develop it on, or copy it to, any enterprise/work environment. The general architecture here is the asset with independent provenance; keeping it walled off is what preserves that.
- Log everything anyway (for replay) — the version history and commit trail also serve as a clean, timestamped provenance record of independent development.

---

## 8. Aesthetic direction (an instrument, not a marketing page)

This is a tool for watching a mind think for hours, so design it like scientific/mission-control instrumentation, **not** a friendly consumer app:
- **Data is the only bright thing.** Everything structural (frame, labels, chrome) recedes; the grades, the claims, the state colors are what carry visual weight. Think oscilloscope trace on a dark field, or a debugger, or an air-traffic display.
- Dark, dense, information-first. High information density is a *feature* here, not a smell — this is an expert instrument.
- The Belnap corner colors (Resolved / Conflict / Missing / Superseded) are the core semantic palette and should be instantly distinguishable at a glance, because spotting a glut form is a primary act.
- Motion is meaningful, not decorative: a grade bar moving, a candidate being struck, the playhead advancing — animation encodes *state change*, never ornament.
- Legibility of dense live text (the event stream) and crisp small type for data matter more than display flourish. Pick a strong monospace or technical face for the data; keep it consistent.
- (Consult the frontend-design skill for execution, but hold the brief: this is an instrument. Avoid the friendly-SaaS defaults; the subject — a society reasoning transparently — is the source of the look.)

---

## 9. What is prescribed vs. what is yours to discover

**Prescribed (getting these wrong is costly):** the fold-as-single-source-of-truth (§1); the actor-model substrate as principles — addresses, mailboxes, message-only, one-at-a-time — with the event log as the global serialization point (§1.5); live-as-a-special-case-of-replay (§1); the evidence-vs-hypothesis two-layer claim model (§2); grade computed from provenance, never self-reported (§3); log-everything (§1, §3); the headless/remote environment specifics (§6); the IP discipline (§7).

**Yours (and the human's) to discover — this is the fun:** exactly how agents phrase and choose questions; the grade function's precise form; how contradiction-detection feels; the exact visual treatment; agent personalities/strategies; how many agents before the graph earns its place. Build the mock-log shell, get it running and watchable from the laptop, then *play* — run it, watch what surprises you, and follow the surprising thing. The point of this phase is contact with a mechanism that talks back, not hitting a spec.

---

## 10. First move

Build 1, against a mock log, with a hand-written scenario that shows a candidate field narrowing, a glut forming and being flagged, and the gate abstaining then signing — so the first thing viewable from the laptop is a *society visibly reasoning toward a guess*, scrubberable from the first run. Then hand it back to the human to watch, react, and steer.

*End of build brief — 2026-07-06.*
