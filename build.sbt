// claim-algebra-lab — the falsification experiment for the verifiable claim algebra.
//
// Multi-module: `claim-algebra` is the pure, dependency-minimal library (no cats-effect);
// `extract` is the shared LLM/grounding infra; `workbench` is the credit product; the root
// module is the lab (the falsification experiment + its credit `pipeline`); `gate` is the
// independent differential gate. Packages stay `claimalgebra.*` across modules — the module
// boundary is the sbt directory, and the hard seam is pure-vs-effectful (the library carries
// no cats-effect and no SDK).
//
// Dependency versions are recent-stable at setup; confirm/bump on first `sbt update`.

ThisBuild / scalaVersion := "3.3.4" // Scala 3 LTS
ThisBuild / organization := "io.github.reggieperry"
ThisBuild / version := "0.1.0-SNAPSHOT"

// Standard Scala 3 scalafix setup: the compiler emits SemanticDB (built in to
// Scala 3, no plugin) so semantic rules can run; the syntactic Scalazzi rules in
// `.scalafix.conf` do not need it, but the gate's later semantic rules will.
ThisBuild / semanticdbEnabled := true

// Compiler discipline is build-wide (ThisBuild), so the `gate` subproject is held to
// the same bar — fatal warnings, unused-checking, value-discard — that it enforces.
//
// The gate's wart scan (its second Check A finding source) runs in a DEDICATED sbt invocation with
// -Dgate.wartScan=true: it turns wartremover's warts on as WARNINGS and drops -Werror, so findings
// ENUMERATE rather than failing the build — which would collapse the differential count and collide
// with the gate's own fail-closed compile precondition. The normal build (and `sbt check`) keeps
// -Werror and no warts, so wartremover is inert there.
val wartScanMode = sys.props.get("gate.wartScan").contains("true")

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-explain",
  "-Wunused:all",
  "-Wvalue-discard"
) ++ (if (wartScanMode) Nil else Seq("-Werror")) // -Werror off only under the wart scan

ThisBuild / wartremoverWarnings := (if (wartScanMode) Warts.unsafe else Nil)
ThisBuild / wartremoverErrors := Nil // never fatal — the gate diffs warts, it never gates absolutely

// Determinism is first-class (seeded corpora, a mechanical grader, law/ScalaCheck suites). The
// single-module build ran fully serial; across several modules sbt parallelizes test TASKS by
// project, so pin it build-wide — no in-module parallelism, and at most one test task across the
// whole build at once (the ProvLawsSuite maxSize/thrash rationale).
ThisBuild / Test / parallelExecution := false
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

lazy val catsCore = "org.typelevel" %% "cats-core" % "2.12.0"
lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"
// The ring hierarchy (Semiring / Rig / CommutativeRig) lives in `org.typelevel:algebra`,
// not cats-kernel — the provenance polynomial ℕ[X] is a CommutativeRig (claim-algebra.html §4).
// Pinned to the same 2.12.0 as `algebra-laws` so the instance and its law bundle agree.
lazy val algebra = "org.typelevel" %% "algebra" % "2.12.0"
// The official Anthropic Java SDK (the model boundary), reached over JVM interop and kept behind the
// `LlmCall` facade — a Java artifact, so `%` not `%%`. Version resolved and the API confirmed against
// the artifact at first wiring (scala-llm.md). API-key auth only.
lazy val anthropic = "com.anthropic" % "anthropic-java" % "2.47.0"
// The official OpenAI Java SDK — same boundary, behind the same `LlmCall` facade, so the workbench
// can run GPT as well as Claude. A Java artifact (`%`); version + API confirmed against the resolved
// jar (scala-llm.md). API-key auth only (OPENAI_API_KEY via OpenAIOkHttpClient.fromEnv()).
lazy val openai = "com.openai" % "openai-java" % "4.41.0"
// Jackson annotations for the boundary carrier (the structured-output DTO) — pinned to the version
// the SDK resolves so the schema reflection agrees. Direct because the carrier imports it directly.
lazy val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % "2.19.4"
lazy val munit = "org.scalameta" %% "munit" % "1.0.0" % Test
lazy val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % "1.0.0" % Test
lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.18.0" % Test
// Law-checking: discipline `checkAll` against the cats / algebra law bundles, run in munit.
lazy val catsLaws = "org.typelevel" %% "cats-laws" % "2.12.0" % Test
lazy val algebraLaws = "org.typelevel" %% "algebra-laws" % "2.12.0" % Test
lazy val disciplineMunit = "org.typelevel" %% "discipline-munit" % "2.0.0" % Test
lazy val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test

// The pure claim algebra — the library. Dependency-minimal (cats-kernel via cats-core, and the
// `algebra` ring hierarchy for ℕ[X]); NO cats-effect and NO SDK, so the pure-vs-effectful seam is
// structural rather than aspirational. Everything else depends on it. Its test scope carries the
// discipline law stack (cats-laws / algebra-laws / discipline-munit) but NOT munit-cats-effect —
// the core has no `IO` to test.
lazy val claimAlgebra = (project in file("claim-algebra"))
  .settings(
    name := "claim-algebra",
    libraryDependencies ++= Seq(
      catsCore,
      algebra,
      munit,
      munitScalacheck,
      scalacheck,
      catsLaws,
      algebraLaws,
      disciplineMunit
    )
  )

// The credit practice's taxonomy — the concrete evidence kinds (`CreditKind`), the desks, and the
// routing that INTERPRET the library's abstract `Kind` mark. A SIBLING of `extract` (dependsOn the
// library only — NO `extract -> credit` edge, so the shared grounding infra stays domain-neutral):
// the exemplar of extending the neutral library from one bounded context. No production consumer
// today (`SupersessionExtractor` takes the retraction kind by injection, defaulting neutral); a
// routing path adds `.dependsOn(credit)` when it wires the taxonomy in. `test->test` reuses the
// library's `Generators`.
lazy val credit = (project in file("credit"))
  .dependsOn(claimAlgebra % "compile->compile;test->test")
  .settings(
    name := "credit",
    libraryDependencies ++= Seq(
      catsCore,
      algebra,
      munit,
      munitScalacheck,
      scalacheck
    )
  )

// The shared model/grounding infrastructure: the `LlmCall` facade + the Anthropic/OpenAI adapters,
// the pure `Corpus` grounding primitive, the domain value types, and the extractors. Effectful
// (cats-effect + both SDKs + Jackson for the DTO carriers). Depended on by both the workbench and
// the experiment, which is why it is its own module rather than folded into either. Domain-neutral:
// `SupersessionExtractor` takes its retraction kind by injection (default `None`), naming no credit
// value, so a second practice can reuse this layer.
lazy val extract = (project in file("extract"))
  .dependsOn(claimAlgebra)
  .settings(
    name := "extract",
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      anthropic,
      openai,
      jacksonAnnotations,
      munit,
      munitScalacheck,
      scalacheck,
      munitCatsEffect
    )
  )

// The credit-deal workbench — the live product over real deals. Depends on the library and the
// shared extract infra; independent of the experiment. The `test->test` edge reuses the library's
// generators/law helpers (LedgerLawsSuite is checkAll-shaped, so the discipline stack is carried).
lazy val workbench = (project in file("workbench"))
  .dependsOn(claimAlgebra % "compile->compile;test->test", extract)
  .settings(
    name := "workbench",
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      anthropic,
      openai,
      jacksonAnnotations,
      munit,
      munitScalacheck,
      scalacheck,
      munitCatsEffect,
      catsLaws,
      algebraLaws,
      disciplineMunit
    )
  )

// The lab: the falsification experiment and its credit `pipeline` (experiment-private). Aggregates
// the whole build so `sbt check` / `test` fan out; depends on the library + extract and reuses the
// library's test helpers via `test->test`. Does NOT depend on the workbench — the experiment never
// imports it; the lab and the product are separate bounded contexts.
lazy val libraryNeutrality =
  taskKey[Unit]("Fail if claim-algebra/src carries domain vocabulary (the portability gate)")

lazy val root = (project in file("."))
  .aggregate(claimAlgebra, extract, credit, workbench, gate)
  .dependsOn(claimAlgebra % "compile->compile;test->test", extract)
  .settings(
    name := "claim-algebra-lab",
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      algebra,
      anthropic,
      openai,
      jacksonAnnotations,
      munit,
      munitScalacheck,
      scalacheck,
      catsLaws,
      algebraLaws,
      disciplineMunit,
      munitCatsEffect
    ),
    // The library-neutrality gate (docs/claim-algebra/library-portability-plan.md): claim-algebra
    // must carry no domain (credit / product / experiment) vocabulary, so it stays portable for
    // reuse across practices. Runs the grep-only pass (`--no-build` avoids nesting sbt inside sbt);
    // wired into `sbt check` so neutrality cannot regress.
    libraryNeutrality := {
      import scala.sys.process.*
      val log = streams.value.log
      val exit = Process(Seq("bash", "scripts/library-neutrality.sh", "--no-build")).!
      if (exit != 0)
        sys.error("library-neutrality gate failed — claim-algebra/src carries domain vocabulary")
      log.info("library-neutrality: claim-algebra is domain-neutral")
    }
  )

// The Scala differential gate (anti-weakening) — a self-contained binary that blocks
// regressions versus the merge-base. It is independent of the experiment (it scans
// files and runs the toolchain), so it depends on nothing in `root`. The pure diff/
// verdict engine is dependency-free; the runner/CLI's effect and process libraries are
// added when those slices land. Ported from the vendored Go gate under
// `docs/reference/a-go-original/` (reuse the design, replace scanner + runner).
lazy val gate = (project in file("gate"))
  .settings(
    name := "differential-gate",
    Test / parallelExecution := false,
    // cats-effect is the one effect system (scala-modules.md): the scanner and runner do filesystem
    // and subprocess IO, wrapped in `IO.blocking`. The pure diff engine needs none of it.
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      munit,
      munitScalacheck,
      scalacheck,
      munitCatsEffect
    ),
    // The gate ships as a standalone fat jar (named by function) so it runs as CI without nesting
    // sbt inside sbt (Main shells out to sbt for the compile precondition and the findings scan).
    // Build with `sbt gate/assembly`; run `java -jar gate/target/scala-*/differential-gate.jar`.
    assembly / mainClass := Some("gate.Main"),
    assembly / assemblyJarName := "differential-gate.jar"
  )

// One gate the build must pass: formatting (sources + sbt files), the Scalazzi /
// Scala 3 scalafix rules, the library-neutrality gate (claim-algebra stays domain-neutral),
// then the law-first test suite. `sbt check` is green only when all are.
addCommandAlias(
  "check",
  "; scalafmtSbtCheck ; scalafmtCheckAll ; scalafixAll --check ; libraryNeutrality ; test"
)
