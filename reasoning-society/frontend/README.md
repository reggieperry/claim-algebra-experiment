# reasoning-society / frontend

The observability UI for the [reasoning society](../README.md) — a **pure viewer** of the ordered
event log the [`backend/`](../backend) emits. An instrument, not a consumer app: dark, dense,
information-first; the data (grades, claims, Belnap-corner colors) is the only bright thing
(brief §8).

Stack: **React + Vite** (standard Vite React layout).

## Layout

    index.html             — Vite entry
    vite.config.ts         — bound to 0.0.0.0 for headless/remote viewing (brief §6)
    public/                — static assets served as-is
    src/
      main.tsx             — React entry
      App.tsx              — root: owns the single playhead + transport, wires the panels
      index.css, App.css   — base + instrument styling (the Belnap semantic palette)
      model/               — Event (discriminated union) + the fold's result types, branded ids
      fold/                — the pure fold: fold(events, playhead) → BeliefState, grade, gate read
      mock/                — the Build 1 scripted scenario (a hand-written Twenty Questions game)
      panels/              — BeliefState · EventStream · Transport · HumanAction
      view/                — pure display derivations (Belnap tone → colour)

Every panel is a **view of the fold at the playhead** — no panel holds its own mutable state
(brief §1). The event log is the single source of truth; the App root holds only the playhead and
the transport's controls, and every panel is a pure reader of `fold(MOCK_EVENTS, playhead)`.

## Run (headless workstation, viewed from a laptop — brief §6)

    npm install
    npm run dev            # binds 0.0.0.0:5173 (see vite.config.js)

Reach it from the laptop via the LAN IP (`http://<workstation-ip>:5173`) or an SSH tunnel
(`ssh -L 5173:localhost:5173 <workstation>`). Run under a named tmux window so it survives
disconnects. `npm run build` produces a static `dist/`. `npm run check` runs the strict gate
(prettier + eslint `--max-warnings 0` + tsc + vitest).

Status: **Build 1 complete** — the observability shell over a scripted mock event log. A society
visibly reasoning toward a guess (narrowing → glut → the gate abstaining then signing), scrubbable
from the first run. Build 2 (memory panel, society navigator) and Build 3 (live LLM agents) are not
yet built; new views drop in as further pure readers of the same log.
