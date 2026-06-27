package gate

import munit.FunSuite

/** Check E — omit-list integrity. An omit-listed package is exempt from Check D's unit-coverage
  * block only when an integration test exercises it: an existence guard, a no-erosion delta versus
  * the baseline, and a one-time bootstrap floor for a newly-omitted package. Ported from the Go
  * gate's `omit_test.go`.
  */
class OmitSuite extends FunSuite:

  private def snap(integration: Map[String, Double]): Snapshot =
    Snapshot(
      findings = List.empty,
      tests = TestSnapshot.empty.copy(integrationCoverage = integration)
    )

  private def diff(
      baseline: Map[String, Double],
      branch: Map[String, Double],
      config: Config
  ): Report =
    Diff(snap(baseline), snap(branch), Map.empty, config)

  private def cfg(omit: String, floor: Double = 30.0): Config =
    Config(omitCoverage = List(omit), bootstrapIntegrationFloor = floor)

  test("an omit entry with no integration coverage blocks") {
    val r = diff(Map.empty, Map.empty, cfg("pipeline"))
    assertEquals(r.verdict, Verdict.Fail)
    assertEquals(r.blocks.map(_.kind), List(Kind.OmitWithoutIntegration))
  }

  test("an omit entry exercised by integration coverage above the floor passes") {
    val cov = Map("claimalgebra/pipeline" -> 70.0)
    assertEquals(diff(cov, cov, cfg("pipeline")).verdict, Verdict.Pass)
  }

  test("an integration-coverage drop beyond the epsilon blocks") {
    val r = diff(
      Map("claimalgebra/pipeline" -> 70.0),
      Map("claimalgebra/pipeline" -> 60.0),
      cfg("pipeline")
    )
    assertEquals(r.blocks.map(_.kind), List(Kind.IntegrationCoverageDrop))
  }

  test("a new omit entry below the bootstrap floor blocks") {
    // No baseline integration figure (new entry), branch coverage under the floor.
    val r = diff(Map.empty, Map("claimalgebra/pipeline" -> 10.0), cfg("pipeline", floor = 30.0))
    assertEquals(r.blocks.map(_.kind), List(Kind.OmitBelowBootstrapFloor))
  }

  test("a new omit entry above the bootstrap floor passes") {
    val r = diff(Map.empty, Map("claimalgebra/pipeline" -> 40.0), cfg("pipeline", floor = 30.0))
    assertEquals(r.verdict, Verdict.Pass)
  }
