// claim-algebra-lab — the falsification experiment for the verifiable claim algebra.
//
// Single root module for now. Split into modules (algebra / experiment / gate), each
// producing a function-named binary, when the second binary is written — not before.
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
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-explain",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Werror" // warnings fail the build — e.g. the "Infinite loop in function body" that a self-referential given triggers
)

lazy val catsCore = "org.typelevel" %% "cats-core" % "2.12.0"
lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"
// The ring hierarchy (Semiring / Rig / CommutativeRig) lives in `org.typelevel:algebra`,
// not cats-kernel — the provenance polynomial ℕ[X] is a CommutativeRig (claim-algebra.html §4).
// Pinned to the same 2.12.0 as `algebra-laws` so the instance and its law bundle agree.
lazy val algebra = "org.typelevel" %% "algebra" % "2.12.0"
// The official Anthropic Java SDK (the model boundary), reached over JVM interop and kept behind the
// `LlmCall` facade — a Java artifact, so `%` not `%%`. Version resolved and the API confirmed against
// the artifact at first wiring (scala-llm.md). API-key auth only.
lazy val anthropic = "com.anthropic" % "anthropic-java" % "2.44.0"
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

lazy val root = (project in file("."))
  .aggregate(gate)
  .settings(
    name := "claim-algebra-lab",
    // Run test suites sequentially. Determinism is first-class here, and ordered,
    // single-threaded execution also avoids CPU thrash from many ScalaCheck/law
    // suites contending at once.
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      algebra,
      anthropic,
      jacksonAnnotations,
      munit,
      munitScalacheck,
      scalacheck,
      catsLaws,
      algebraLaws,
      disciplineMunit,
      munitCatsEffect
    )
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
// Scala 3 scalafix rules, then the law-first test suite. `sbt check` is green only
// when all three are.
addCommandAlias(
  "check",
  "; scalafmtSbtCheck ; scalafmtCheckAll ; scalafixAll --check ; test"
)
