package gate

import munit.FunSuite

/** Check D — test discipline. A deleted test file, a new skip marker, or a per-package coverage
  * drop beyond the epsilon is a hard block; a drop in a file's test-count is an advisory only (the
  * count is gameable). Coverage is differential and omit-aware. Ported from the Go gate's
  * `scan_test.go` test-discipline cases.
  */
class TestDisciplineSuite extends FunSuite:

  private def snap(tests: TestSnapshot): Snapshot = Snapshot(findings = List.empty, tests = tests)

  private def diff(
      baseline: TestSnapshot,
      branch: TestSnapshot,
      renames: Map[String, String] = Map.empty,
      config: Config = Config.empty
  ): Report =
    Diff(snap(baseline), snap(branch), renames, config)

  private def withFiles(fs: String*): TestSnapshot = TestSnapshot.empty.copy(files = fs.toList)
  private def withSkips(kv: (String, Int)*): TestSnapshot =
    TestSnapshot.empty.copy(skips = kv.toMap)
  private def withCoverage(kv: (String, Double)*): TestSnapshot =
    TestSnapshot.empty.copy(coverage = kv.toMap)

  test("deleting a test file blocks") {
    val r = diff(withFiles("FooSuite.scala", "BarSuite.scala"), withFiles("FooSuite.scala"))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(
      r.blocks,
      List(Block(Check.D, Kind.TestFileDeletion, "BarSuite.scala", "", 0, ""))
    )
  }

  test("a renamed test file is not counted as deleted") {
    val r = diff(
      withFiles("old/FooSuite.scala"),
      withFiles("new/FooSuite.scala"),
      renames = Map("old/FooSuite.scala" -> "new/FooSuite.scala")
    )
    assertEquals(r.verdict, Verdict.Pass)
  }

  test("a new skip marker blocks") {
    val r = diff(withSkips("FooSuite.scala" -> 0), withSkips("FooSuite.scala" -> 1))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.map(b => (b.kind, b.file)), List((Kind.NewSkipMarkers, "FooSuite.scala")))
  }

  test("a per-package coverage drop beyond the epsilon blocks") {
    val r = diff(withCoverage("claimalgebra" -> 90.0), withCoverage("claimalgebra" -> 80.0))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.map(b => (b.kind, b.file)), List((Kind.CoverageDrop, "claimalgebra")))
  }

  test("a coverage drop within the epsilon passes") {
    val r = diff(withCoverage("claimalgebra" -> 90.0), withCoverage("claimalgebra" -> 89.7))
    assertEquals(r.verdict, Verdict.Pass)
  }

  test("a renamed package reconciles its coverage key — not a false coverage drop") {
    // The coverage key is repo-relative (src/main/scala/...) so it shares the file renames'
    // path-space; translatePkg maps the baseline dir to the branch dir and the coverage matches.
    val baseline = withCoverage("src/main/scala/a/pkg" -> 90.0)
    val branch = withCoverage("src/main/scala/b/pkg" -> 90.0)
    val renames = Map("src/main/scala/a/pkg/X.scala" -> "src/main/scala/b/pkg/X.scala")
    assertEquals(diff(baseline, branch, renames).verdict, Verdict.Pass)
  }

  test(
    "a coverage drop in a package that SURVIVES a file move out is still caught (identity-first)"
  ) {
    // One file moves a/pkg -> b/pkg, but a/pkg still exists and its coverage cratered. The rename
    // map must not redirect a/pkg's comparison to b/pkg and hide the real drop — identity first.
    val baseline = withCoverage("src/main/scala/a/pkg" -> 90.0)
    val branch = withCoverage("src/main/scala/a/pkg" -> 40.0, "src/main/scala/b/pkg" -> 90.0)
    val renames = Map("src/main/scala/a/pkg/X.scala" -> "src/main/scala/b/pkg/X.scala")
    val r = diff(baseline, branch, renames)
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(
      r.blocks.map(b => (b.kind, b.file)),
      List((Kind.CoverageDrop, "src/main/scala/a/pkg"))
    )
  }

  test("a package whose coverage vanished blocks as a drop to none") {
    val r = diff(withCoverage("claimalgebra" -> 90.0), TestSnapshot.empty)
    assertEquals(r.blocks.map(b => (b.kind, b.file)), List((Kind.CoverageDrop, "claimalgebra")))
  }

  test("an omit-listed package is exempt from the Check D coverage-drop block") {
    val r = diff(
      withCoverage("claimalgebra/pipeline" -> 90.0),
      withCoverage("claimalgebra/pipeline" -> 10.0),
      config = Config(omitCoverage = List("pipeline"), bootstrapIntegrationFloor = 0.0)
    )
    // Check D does not block it (Check E governs omit entries instead); with no integration
    // coverage present, Check E would block — but here we only assert D's exemption via the kind.
    assert(!r.blocks.exists(_.kind == Kind.CoverageDrop), s"omit-listed pkg should be D-exempt: $r")
  }

  test("a test-count drop is an advisory, not a block") {
    val r = diff(
      TestSnapshot.empty.copy(testCounts = Map("FooSuite.scala" -> 5)),
      TestSnapshot.empty.copy(testCounts = Map("FooSuite.scala" -> 3))
    )
    assertEquals(r.verdict, Verdict.Advisory)
    assertEquals(r.blocks, List.empty[Block])
    assertEquals(
      r.advisories.map(a => (a.kind, a.file)),
      List((Kind.TestCountDrop, "FooSuite.scala"))
    )
  }
