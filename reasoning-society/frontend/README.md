# reasoning-society / frontend

The observability UI for the [reasoning society](../README.md) — a **pure viewer** of the ordered
event log the [`backend/`](../backend) emits. An instrument, not a consumer app: dark, dense,
information-first; the data (grades, claims, Belnap-corner colors) is the only bright thing
(brief §8).

Stack: **React + Vite** (standard Vite React layout).

## Layout

    index.html             — Vite entry
    vite.config.js         — bound to 0.0.0.0 for headless/remote viewing (brief §6)
    public/                — static assets served as-is
    src/
      main.jsx             — React entry
      App.jsx              — root component (scaffold placeholder)
      index.css, App.css   — base instrument styling
      components/          — panels: belief-state, event-stream, transport, memory, navigator
      assets/              — bundled assets

Every panel will be a **view of the fold at the playhead** — no panel holds its own mutable state
(brief §1).

## Run (headless workstation, viewed from a laptop — brief §6)

    npm install
    npm run dev            # binds 0.0.0.0:5173 (see vite.config.js)

Reach it from the laptop via the LAN IP (`http://<workstation-ip>:5173`) or an SSH tunnel
(`ssh -L 5173:localhost:5173 <workstation>`). Run under a named tmux window so it survives
disconnects. `npm run build` produces a static `dist/`.

Status: scaffold — the shell is a placeholder; the panels land in Build 1.
