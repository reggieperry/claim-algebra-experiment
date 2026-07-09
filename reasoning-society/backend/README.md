# reasoning-society / backend

The Scala 3 (cats-effect) backend of the
[Auditable Society of Minds](../../docs/reasoning-society/auditable-society-of-minds-v1.md). It is
an sbt **subproject of the root build** — declared in the repo `build.sbt` as `reasoningSociety`,
`(project in file("reasoning-society/backend"))` — so it inherits the repo toolchain and the
build gate; there is no separate build here.

## Role

Runs the society of cheap, diverse LLM agents and emits a single ordered **event log**; the belief
state is a **pure fold** over that log (the claim-calculus `Ledger`). The `../frontend` React app is
a pure viewer of the emitted log.

- **Uses `claim-algebra`** — the `Ledger` fold, `Testimony` / `Gate`, the four-state `Resolution`.
- **Uses `extract`** — the `LlmCall` facade for the agents (cheap Haiku tier per the brief).
- **Actors are lightly implemented** on `cats.effect.std.Queue` — a lightweight mailbox design:
  address, mailbox, one message at a time, send/create/designate. No Akka/Pekko.

## Layout (standard sbt)

    src/main/scala/claimalgebra/society/   — backend sources (package claimalgebra.society)
    src/main/resources/                    — runtime resources
    src/test/scala/claimalgebra/society/   — munit + ScalaCheck suites
    src/test/resources/                    — test fixtures

## Build & test (from the repo root)

    sbt reasoningSociety/compile
    sbt reasoningSociety/test
    sbt check            # the full gate: scalafmt, scalafix, library-neutrality, all suites

Status: scaffold — structure and wiring in place; behavior lands in Build 1.
