package gate

import munit.FunSuite

/** Check B — new suppressions. A newly-introduced or scope-broadened suppression directive blocks;
  * a pre-existing one does not (differential). Broadening a targeted suppression to a blanket one
  * is a NEW directive key, so it registers. Ported from the Go gate's `suppression_test.go`.
  */
class SuppressionSuite extends FunSuite:

  private def snap(suppressions: List[Suppression]): Snapshot =
    Snapshot(findings = List.empty, suppressions = suppressions)

  private def diff(
      baseline: List[Suppression],
      branch: List[Suppression],
      renames: Map[String, String] = Map.empty
  ): Report =
    Diff(snap(baseline), snap(branch), renames)

  test("a newly-introduced suppression blocks") {
    val r = diff(List.empty, List(Suppression("a.scala", "scalafix:off DisableSyntax")))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(
      r.blocks,
      List(Block(Check.B, Kind.NewSuppression, "a.scala", "scalafix:off DisableSyntax", 0, ""))
    )
  }

  test("a pre-existing suppression does not block — differential") {
    val ss = List(Suppression("a.scala", "@nowarn"))
    assertEquals(diff(ss, ss).verdict, Verdict.Pass)
  }

  test("broadening a targeted suppression to a blanket one is a new suppression") {
    val r = diff(
      List(Suppression("a.scala", "scalafix:off DisableSyntax")), // targeted
      List(Suppression("a.scala", "scalafix:off")) // blanket — a distinct key
    )
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.map(_.code), List("scalafix:off"))
  }

  test("a renamed file matches the suppression against its baseline — no block") {
    val r = diff(
      List(Suppression("old.scala", "@nowarn")),
      List(Suppression("new.scala", "@nowarn")),
      renames = Map("old.scala" -> "new.scala")
    )
    assertEquals(r.verdict, Verdict.Pass)
  }
