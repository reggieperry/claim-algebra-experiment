package gate

import munit.FunSuite

/** The wartremover diagnostic parser, grounded in the real `-Dgate.wartScan=true` output captured
  * from the tool: a `-- Warning: <path>:<line>:<col>` header, then source context, then a separate
  * `[wartremover:<Name>] <message>` line. The parser correlates the header's location with the
  * following wart line, so the finding's code is the bare wart name (distinct from scalafix's
  * dotted `DisableSyntax.*`).
  */
class WartScanSuite extends FunSuite:

  test("a wart box parses into a finding correlating the header location with the wart line") {
    val output =
      """[warn] -- Warning: /repo/gate/src/main/scala/gate/CoverageScan.scala:32:6
        |[warn] 32 |  def parse(xml: String, sourceRoot: String = "src/main/scala") =
        |[warn]    |  ^
        |[warn]    |  [wartremover:DefaultArguments] Function has default arguments
        |[warn] 33 |    val doc = ...
        |""".stripMargin
    assertEquals(
      WartScan.parse(output),
      List(
        Finding(
          "/repo/gate/src/main/scala/gate/CoverageScan.scala",
          "DefaultArguments",
          32,
          "Function has default arguments"
        )
      )
    )
  }

  test("clean output produces no findings") {
    assertEquals(
      WartScan.parse("[info] compiling\n[success] Total time: 2 s\n"),
      List.empty[Finding]
    )
  }

  test("two wart boxes parse into two findings") {
    val output =
      """[warn] -- Warning: /repo/A.scala:1:1
        |[warn]    |  [wartremover:OptionPartial] Option#get is disabled
        |[warn] -- Warning: /repo/B.scala:9:3
        |[warn]    |  [wartremover:Any] Inferred type containing Any
        |""".stripMargin
    assertEquals(
      WartScan.parse(output).map(f => (f.file, f.code, f.line)),
      List(("/repo/A.scala", "OptionPartial", 1), ("/repo/B.scala", "Any", 9))
    )
  }

  test("a non-wartremover warning is not a finding") {
    val output =
      """[warn] -- Warning: /repo/C.scala:4:2
        |[warn]    |  method foo is deprecated
        |""".stripMargin
    assertEquals(WartScan.parse(output), List.empty[Finding])
  }
