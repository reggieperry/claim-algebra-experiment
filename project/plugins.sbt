// Build plugins. Versions verified against Maven Central at add time; bump deliberately.
//
// Active:
//   sbt-scalafmt  formatting — `scalafmtCheckAll` / `scalafmtSbtCheck`
//   sbt-scalafix  lint + refactoring — the Scalazzi safe subset (DisableSyntax) and
//                 Scala 3 import hygiene (OrganizeImports); config in `.scalafix.conf`
//
// Still to add with the differential gate:
//   sbt-wartremover lint — a second finding source for the gate
//   sbt-scoverage   coverage — the gate's coverage check
//   sbt-assembly    fat-jar binaries, named by function
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
