# claim-algebra-lab

A standalone Scala service that runs the **falsification experiment** for the verifiable claim algebra: a controlled, three-arm comparison — naive prose, disciplined baseline, claim algebra — over a nine-node credit-analysis topology with a sealed fault key, measuring how often each arm produces a confidently-wrong-at-signature (CWS) result.

It is a synchronous, instrumented pipeline, not a product and not an actor system. The actor model is reserved for the general system this work may seed (see `docs/actors/`).

## Where to start

- `CLAUDE.md` — the charter, the settled decisions, the coding discipline, and build commands.
- `docs/claim-algebra/falsifying-the-claim-algebra.html` — the experiment design.
- `docs/claim-algebra/claim-algebra-belnap.html` — the algebra the code implements.
- `docs/claim-algebra/falsification-experiment.md` — the build shape.

## Build

Scala 3 + sbt (JDK 25). Dependency versions in `build.sbt` are recent-stable at setup — confirm on first resolve.

```bash
sbt compile
sbt test            # munit + ScalaCheck
sbt scalafmtCheckAll
```
