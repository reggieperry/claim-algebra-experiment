// claim-algebra-lab — the falsification experiment for the verifiable claim algebra.
//
// Single root module for now. Split into modules (algebra / experiment / gate), each
// producing a function-named binary, when the second binary is written — not before.
//
// Dependency versions are recent-stable at setup; confirm/bump on first `sbt update`.

ThisBuild / scalaVersion := "3.3.4" // Scala 3 LTS
ThisBuild / organization := "io.github.reggieperry"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val catsCore        = "org.typelevel"  %% "cats-core"        % "2.12.0"
lazy val catsEffect      = "org.typelevel"  %% "cats-effect"      % "3.5.4"
lazy val munit           = "org.scalameta"  %% "munit"            % "1.0.0"  % Test
lazy val munitScalacheck = "org.scalameta"  %% "munit-scalacheck" % "1.0.0"  % Test
lazy val scalacheck      = "org.scalacheck" %% "scalacheck"       % "1.18.0" % Test

lazy val root = (project in file("."))
  .settings(
    name := "claim-algebra-lab",
    libraryDependencies ++= Seq(catsCore, catsEffect, munit, munitScalacheck, scalacheck),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-explain",
      "-Wunused:all",
      "-Wvalue-discard"
    )
  )
