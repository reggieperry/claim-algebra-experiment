package gate

import munit.FunSuite

/** The scoverage report parser, grounded in the real `scoverage.xml` structure captured from the
  * tool: `<class name=".." filename="pkg/dir/File.scala" statement-count="N" statements-invoked="M"
  * statement-rate=".." .../>`. The percent is RECOMPUTED from the integer counts (the rendered
  * `statement-rate` is locale-sensitive), aggregated per package directory, and keyed repo-relative
  * (the source root prefixed) so a package rename reconciles through the diff's `translatePkg`.
  */
class CoverageScanSuite extends FunSuite:

  // A fixture matching scoverage's writer: tags may break across lines; attributes after the name.
  private def report(classes: String): String =
    s"""<scoverage
       |statement-count="0" statements-invoked="0" statement-rate="0.00" version="1.0">
       |<packages><package name="p" statement-count="0" statements-invoked="0" statement-rate="0.00">
       |<classes>
       |$classes
       |</classes></package></packages></scoverage>""".stripMargin

  private def clazz(filename: String, count: Int, invoked: Int): String =
    s"""<class
       |name="c" filename="$filename" statement-count="$count" statements-invoked="$invoked"
       |statement-rate="0.00" branch-rate="0.00"></class>"""

  test("two classes in one package aggregate to one repo-relative key, percent from counts") {
    val xml = report(
      clazz("claimalgebra/pipeline/Node.scala", 6, 3) + "\n" +
        clazz("claimalgebra/pipeline/Ratio.scala", 4, 4)
    )
    val cov = CoverageScan.parse(xml)
    assertEquals(cov.keySet, Set("src/main/scala/claimalgebra/pipeline"))
    assertEqualsDouble(cov("src/main/scala/claimalgebra/pipeline"), 70.0, 1e-9) // (3+4)/(6+4) = 70%
  }

  test("a backslash filename is normalized to forward slashes") {
    val cov = CoverageScan.parse(report(clazz("claimalgebra\\extract\\Money.scala", 2, 1)))
    assertEquals(cov.keySet, Set("src/main/scala/claimalgebra/extract"))
  }

  test("a zero-statement package is dropped, never minted as 100%") {
    val cov = CoverageScan.parse(report(clazz("claimalgebra/empty/X.scala", 0, 0)))
    assertEquals(cov, Map.empty[String, Double])
  }

  test("a DOCTYPE is rejected — DTDs are disallowed (XXE closed)") {
    val malicious =
      """<!DOCTYPE scoverage [<!ENTITY x "boom">]>
        |<scoverage statement-count="0"><packages></packages></scoverage>""".stripMargin
    intercept[Exception](CoverageScan.parse(malicious))
  }
