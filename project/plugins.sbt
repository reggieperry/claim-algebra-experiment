// Build plugins. Versions verified against Maven Central at add time; bump deliberately.
//
// Active:
//   sbt-scalafmt  formatting — `scalafmtCheckAll` / `scalafmtSbtCheck`
//   sbt-scalafix  lint + refactoring — the Scalazzi safe subset (DisableSyntax) and
//                 Scala 3 import hygiene (OrganizeImports); config in `.scalafix.conf`
//   sbt-assembly  fat-jar binary for the differential gate (named by function), so it
//                 runs standalone as CI rather than nesting sbt inside sbt
//
// Still to add with the differential gate:
//   sbt-wartremover lint — a second finding source for the gate
//   sbt-scoverage   coverage — the gate's coverage check
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")
