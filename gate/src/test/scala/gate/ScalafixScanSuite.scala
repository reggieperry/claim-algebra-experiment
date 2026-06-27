package gate

import munit.FunSuite

/** The scalafix diagnostic parser, grounded in the real `scalafix --check` output format captured
  * from the tool: `[error] <path>:<line>:<col>: error: [<Rule>] <message>`.
  */
class ScalafixScanSuite extends FunSuite:

  test("a DisableSyntax diagnostic parses into a finding (path, rule, line, message)") {
    val output =
      """[info] compiling 1 Scala source ...
        |[error] /repo/gate/src/main/scala/gate/Bad.scala:5:5: error: [DisableSyntax.var] mutable state should be avoided
        |[error]     var x = 1
        |[error]     ^^^
        |[error] (gate / scalafixAll) scalafix.sbt.ScalafixFailed: LinterError
        |""".stripMargin
    assertEquals(
      ScalafixScan.parse(output),
      List(
        Finding(
          "/repo/gate/src/main/scala/gate/Bad.scala",
          "DisableSyntax.var",
          5,
          "mutable state should be avoided"
        )
      )
    )
  }

  test("a clean run produces no findings") {
    val output = "[info] compiling\n[success] Total time: 1 s\n"
    assertEquals(ScalafixScan.parse(output), List.empty[Finding])
  }

  test("two diagnostics in one output both parse") {
    val output =
      """[error] /repo/A.scala:1:1: error: [DisableSyntax.var] no var
        |[error] /repo/B.scala:2:3: error: [DisableSyntax.null] no null
        |""".stripMargin
    assertEquals(
      ScalafixScan.parse(output).map(_.code),
      List("DisableSyntax.var", "DisableSyntax.null")
    )
  }
