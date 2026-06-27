package gate

import munit.FunSuite

/** The pure source-scan extractors — suppression directives, skip markers, test-registration
  * counts, the test-file predicate, and the assembly of those into the file-based parts of a
  * [[Snapshot]]. These run on file contents as strings (no filesystem), so they are unit-tested
  * directly; the IO tree-walk that feeds them is [[Scanner]].
  */
class SourceScanSuite extends FunSuite:

  test("a blanket scalafix:off and a targeted one are distinct directives") {
    val content =
      """// scalafix:off
        |val x = 1
        |// scalafix:off DisableSyntax.var
        |""".stripMargin
    assertEquals(
      SourceScan.suppressions(content),
      List("scalafix:off", "scalafix:off DisableSyntax.var")
    )
  }

  test("scalafix:ok, @nowarn, and @SuppressWarnings are all detected") {
    val content =
      """val a = 1 // scalafix:ok DisableSyntax.null
        |@nowarn def f = 1
        |@nowarn("msg") def g = 2
        |@SuppressWarnings(Array("DisableSyntax.var")) val v = 3
        |""".stripMargin
    assertEquals(
      SourceScan.suppressions(content),
      List("scalafix:ok DisableSyntax.null", "@nowarn", "@nowarn(\"msg\")", "@SuppressWarnings")
    )
  }

  test("a clean file has no suppressions") {
    assertEquals(SourceScan.suppressions("val x = 1\ndef f = x + 1\n"), List.empty[String])
  }

  test("skip markers — .ignore, munitIgnore, assume(false) — are counted") {
    val content =
      """test("a".ignore) {}
        |override def munitIgnore = true
        |test("b") { assume(false, "wip") }
        |test("c") { assume( false ) }
        |""".stripMargin
    assertEquals(SourceScan.skipCount(content), 4)
  }

  test("test and property registrations are counted") {
    val content =
      """test("one") {}
        |property("two") { forAll(...) }
        |test("three") {}
        |""".stripMargin
    assertEquals(SourceScan.testCount(content), 3)
  }

  test("a test file is a .scala file under a src/test path") {
    assert(SourceScan.isTestFile("gate/src/test/scala/gate/DiffSuite.scala"))
    assert(!SourceScan.isTestFile("gate/src/main/scala/gate/Diff.scala"))
    assert(!SourceScan.isTestFile("src/test/resources/fixture.txt"))
  }

  test("assemble pairs suppressions with their file across the whole tree") {
    val entries = List(
      "src/main/scala/A.scala" -> "// scalafix:off\nval a = 1\n",
      "src/test/scala/ASuite.scala" -> "@nowarn\ntest(\"x\") {}\n"
    )
    val snap = SourceScan.assemble(entries)
    assertEquals(
      snap.suppressions.toSet,
      Set(
        Suppression("src/main/scala/A.scala", "scalafix:off"),
        Suppression("src/test/scala/ASuite.scala", "@nowarn")
      )
    )
  }

  test("assemble records skips, test-counts, and files only for test files") {
    val entries = List(
      "src/main/scala/A.scala" -> "test(\"not-a-test-here\") {}\n", // a main file: not counted
      "src/test/scala/ASuite.scala" -> "test(\"a\".ignore) {}\ntest(\"b\") {}\n"
    )
    val snap = SourceScan.assemble(entries)
    assertEquals(snap.tests.files, List("src/test/scala/ASuite.scala"))
    assertEquals(snap.tests.skips, Map("src/test/scala/ASuite.scala" -> 1))
    assertEquals(snap.tests.testCounts, Map("src/test/scala/ASuite.scala" -> 2))
    assertEquals(
      snap.findings,
      List.empty[Finding]
    ) // findings come from the toolchain scanner (later)
  }
