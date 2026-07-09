package gate

import munit.FunSuite

/** Check A — finding-identity weakening — over the pure diff engine. The gate is DIFFERENTIAL: a
  * finding that already existed at the merge-base does not block; only the net-new deltas a branch
  * introduces do. A finding that merely moved files (the global count unchanged) downgrades to an
  * advisory, because a count cannot tell a real relocation from a coincidental fix-plus-new. Ported
  * from a Go original's diff test.
  */
class DiffSuite extends FunSuite:

  private def finding(file: String, code: String, line: Int = 1, msg: String = "m"): Finding =
    Finding(file, code, line, msg)

  private def diff(
      baseline: List[Finding],
      branch: List[Finding],
      renames: Map[String, String] = Map.empty
  ): Report =
    Diff(Snapshot(baseline), Snapshot(branch), renames)

  test("identical snapshots pass — no blocks, no advisories") {
    val fs = List(finding("a.scala", "DisableSyntax.var"))
    val r = diff(fs, fs)
    assertEquals(r.verdict, Verdict.Pass)
    assertEquals(r.blocks, List.empty[Block])
    assertEquals(r.advisories, List.empty[Advisory])
  }

  test(
    "a pre-existing finding that persists does not block — differential, not an absolute ceiling"
  ) {
    val fs = List(finding("a.scala", "Wart.Null"))
    assertEquals(diff(fs, fs).verdict, Verdict.Pass)
  }

  test("a net-new finding is a hard block carrying its file, code, line, and message") {
    val r = diff(List.empty, List(finding("a.scala", "Wart.Null", line = 10, msg = "null used")))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(
      r.blocks,
      List(Block(Check.A, Kind.NewError, "a.scala", "Wart.Null", 10, "null used"))
    )
    assertEquals(r.advisories, List.empty[Advisory])
  }

  test("against an empty baseline the gate degrades to an absolute gate — any finding blocks") {
    val r = diff(List.empty, List(finding("x.scala", "DisableSyntax.throw")))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.size, 1)
  }

  test(
    "two new findings at one (file, code) site both block — accounting is by instance, not key"
  ) {
    val r = diff(
      List.empty,
      List(finding("a.scala", "Wart.Var", line = 2), finding("a.scala", "Wart.Var", line = 1))
    )
    assertEquals(r.blocks.size, 2)
    // sorted by line within the key, so the feedback is stable and causal-first
    assertEquals(r.blocks.map(_.line), List(1, 2))
  }

  test("a finding that moved files (global count unchanged) downgrades to a relocation advisory") {
    val r = diff(
      List(finding("a.scala", "Wart.Null")),
      List(finding("b.scala", "Wart.Null"))
    )
    assertEquals(r.verdict, Verdict.Advisory)
    assertEquals(r.blocks, List.empty[Block])
    assertEquals(
      r.advisories,
      List(Advisory(Check.A, Kind.RelocatedError, "b.scala", "Wart.Null", "m"))
    )
  }

  test("a renamed file matches the finding against its baseline — no block") {
    val r = diff(
      List(finding("old.scala", "Wart.Null", line = 5)),
      List(finding("new.scala", "Wart.Null", line = 5)),
      renames = Map("old.scala" -> "new.scala")
    )
    assertEquals(r.verdict, Verdict.Pass)
    assertEquals(r.blocks, List.empty[Block])
    assertEquals(r.advisories, List.empty[Advisory])
  }

  test("a net increase blocks the surplus and downgrades the matched remainder to advisory") {
    // baseline: one Wart.Null at a.scala. branch: one at a.scala (persists) + two at b.scala.
    // global count rose by 2, so 2 instances are provably new (block); the surplus is exactly the net.
    val r = diff(
      List(finding("a.scala", "Wart.Null")),
      List(
        finding("a.scala", "Wart.Null"),
        finding("b.scala", "Wart.Null", line = 1),
        finding("b.scala", "Wart.Null", line = 2)
      )
    )
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.size, 2)
    assertEquals(r.advisories, List.empty[Advisory])
  }

  test("blocks take precedence over advisories in the verdict") {
    // a new finding (block) at one code, and a relocation (advisory) at another.
    val r = diff(
      List(finding("a.scala", "Wart.Null")),
      List(finding("b.scala", "Wart.Null"), finding("c.scala", "Wart.Var"))
    )
    assert(r.blocks.nonEmpty, "the relocated-and-new mix should produce at least one block")
    assertEquals(r.verdict, Verdict.Fail)
  }

  test("the summary names the verdict and the block and advisory counts") {
    val r = diff(List.empty, List(finding("a.scala", "Wart.Null")))
    assertEquals(r.summary, "fail: 1 block(s), 0 advisory(ies)")
    assertEquals(diff(List.empty, List.empty).summary, "pass")
  }
