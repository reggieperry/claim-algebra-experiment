package claimalgebra.extract

import munit.FunSuite

/** `Corpus.containsFigure` is the grounding membership test — a whole figure, not a fragment, and
  * not a hallucination. The subtle part is the boundary: a `,`/`.` that is a thousands separator or
  * decimal point continues the number (a fragment), but one that is ordinary punctuation does not
  * (a legitimately punctuated citation must still ground).
  */
class CorpusSuite extends FunSuite:

  test("a whole figure grounds, including one followed by sentence punctuation") {
    val c = Corpus("Total debt was $1,234,567, EBITDA $400,000. Interest $100,000.")
    assert(c.containsFigure("$1,234,567"), "followed by a comma + space")
    assert(c.containsFigure("$400,000"), "followed by a period + space")
    assert(c.containsFigure("$100,000"), "followed by a period at end of text")
  }

  test("a fragment of a larger figure does not ground") {
    val c = Corpus("Total debt was $1,234,567.")
    assert(!c.containsFigure("234,567"), "leading comma sits between digits")
    assert(!c.containsFigure("1,234"), "trailing comma sits between digits")
  }

  test("a span absent from the corpus, or empty, does not ground") {
    val c = Corpus("EBITDA $400,000 for the period")
    assert(!c.containsFigure("$999,999"))
    assert(!c.containsFigure(""))
  }

  test("a fragment of a compound (colon) ratio does not ground — the colon continues the number") {
    val c = Corpus("maintain a ratio of not less than 3.50:1.00 at all times")
    assert(!c.containsFigure("3.50"), "numerator sits against the colon")
    assert(!c.containsFigure("1.00"), "denominator sits against the colon")
    assert(c.containsFigure("3.50:1.00"), "the whole ratio grounds")
  }

  test("containsFigureWithin grounds a whole-figure value inside its locator") {
    val c = Corpus("aggregate principal amount of $500,000,000 on the Closing Date")
    assert(c.containsFigureWithin("$500,000,000", "principal amount of $500,000,000 on"))
    assert(
      !c.containsFigureWithin("$500,000", "principal amount of $500,000,000 on"),
      "fragment in locator"
    )
    assert(
      !c.containsFigureWithin("$500,000,000", "a locator not present in the corpus"),
      "fake locator"
    )
    assert(!c.containsFigureWithin("", "principal amount of $500,000,000 on"), "blank value")
  }

  test("containsFigureWithin closes the truncated-locator hole — boundary judged against the doc") {
    val c = Corpus("the facility is $1,250,000,000 in aggregate")
    // a locator truncated mid-number must not make the fragment look whole at the locator's edge
    assert(!c.containsFigureWithin("$1,250,000", "$1,250,000"))
  }

  test("containsFigureWithin rejects a value that is whole only OUTSIDE the locator") {
    // "$400,000" is whole at its own occurrence, but inside the cited locator it is a fragment of
    // "$400,000,000" — the value must be whole AT the locator, not merely somewhere in the document.
    val c = Corpus("a standalone $400,000 fee; separately a $400,000,000 facility in aggregate")
    assert(!c.containsFigureWithin("$400,000", "a $400,000,000 facility"))
  }
