# Git hooks

Version-controlled hooks for this repo. The `pre-commit` hook is a **secret-scan backstop**: it runs
[gitleaks](https://github.com/gitleaks/gitleaks) over the staged changes and blocks the commit if a
secret is detected. It fails closed — if gitleaks is not installed, the commit is blocked rather than
committed unscanned — and runs with `--redact`, so a detected secret value is never printed.

API keys live outside the repo (`~/.config/llm/keys.env`, loaded by direnv via the gitignored
`.envrc`); this hook is the backstop against a key ever being committed by accident.

## One-time setup (per clone / machine)

1. Install gitleaks: `sudo apt install gitleaks` (or `brew install gitleaks`).
2. Point git at these hooks: `git config core.hooksPath .githooks`.

`core.hooksPath` is local git config (not committed), so each clone sets it once; the hook script
itself is tracked here.
