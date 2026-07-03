package claimalgebra.extract

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

/** `Money.parse` is the mechanical decode the extractor grounds on — deterministic, total, and
  * bounded — so it gets both halves of the contract (the figures it must accept and the non-figures
  * it must refuse) plus a property that it never throws and is a function of its input alone.
  */
class MoneySuite extends ScalaCheckSuite:

  test("parse decodes a dollar figure with thousands separators to cents") {
    assertEquals(Money.parse("$1,234,567").map(_.toCents), Some(123456700L))
  }

  test("parse decodes a bare integer and a decimal amount") {
    assertEquals(Money.parse("100").map(_.toCents), Some(10000L))
    assertEquals(Money.parse("100.50").map(_.toCents), Some(10050L))
  }

  test("parse rounds a sub-cent amount to the nearest cent") {
    assertEquals(Money.parse("0.005").map(_.toCents), Some(1L))
  }

  test("parse refuses a label, the empty string, junk, and a negative") {
    assertEquals(Money.parse("Total Debt"), None)
    assertEquals(Money.parse(""), None)
    assertEquals(Money.parse("   "), None)
    assertEquals(Money.parse("1.2.3"), None)
    assertEquals(Money.parse("-5"), None)
  }

  test("parse refuses scientific notation, a sign, and a dangling decimal point") {
    assertEquals(Money.parse("1e9"), None)
    assertEquals(Money.parse("+5"), None)
    assertEquals(Money.parse("1234567."), None)
  }

  test("parse refuses an overflowing figure rather than wrapping it to a wrong value") {
    assertEquals(Money.parse("99999999999999999999999999"), None)
  }

  test("parse refuses an over-long span before doing any work") {
    assertEquals(Money.parse("1" * 100), None)
  }

  test("cents refuses a negative count and admits zero") {
    assertEquals(Money.cents(-1L), None)
    assertEquals(Money.cents(0L).map(_.toCents), Some(0L))
  }

  property("parse is total and deterministic — never throws, same input same output") {
    forAll((s: String) => Money.parse(s) == Money.parse(s))
  }
