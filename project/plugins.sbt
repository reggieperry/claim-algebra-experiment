// Build plugins are added with verified versions during the gate build (the
// scala-*.md rule set + the Scala differential gate). Intended set:
//
//   sbt-scalafmt    formatting (the standalone `scalafmt` CLI is available meanwhile)
//   sbt-scalafix    refactoring and lint rules
//   sbt-wartremover lint — a finding source for the differential gate
//   sbt-scoverage   coverage — the gate's coverage check
//   sbt-assembly    fat-jar binaries, named by function
