package claimalgebra.society
package experiment

class TargetMatchSuite extends munit.FunSuite:

  private def ans(s: String): Answer = Answer.from(s).getOrElse(fail(s"bad answer: $s"))

  test("a leading qualifier is dropped — the society naming the refined concept still matches") {
    assert(TargetMatch.matches(ans("dog"), ans("domestic dog")), clue("domestic dog IS a dog"))
    assert(TargetMatch.matches(ans("dog"), ans("the dog")))
    assert(TargetMatch.matches(ans("chair"), ans("a chair")))
    assert(TargetMatch.matches(ans("cat"), ans("domesticated cat")))
  }

  test("matching is symmetric and case/punctuation-insensitive") {
    assert(TargetMatch.matches(ans("domestic dog"), ans("Dog")))
    assert(TargetMatch.matches(ans("dog"), ans("DOG.")))
  }

  test("pre-registered synonyms match; unrelated things do not") {
    assert(TargetMatch.matches(ans("dog"), ans("canine")))
    assert(TargetMatch.matches(ans("cat"), ans("feline")))
    assert(!TargetMatch.matches(ans("dog"), ans("cat")), clue("different animals"))
    assert(!TargetMatch.matches(ans("dog"), ans("apple")))
  }

  test("a broader category is NOT the target — no over-crediting") {
    assert(!TargetMatch.matches(ans("dog"), ans("animal")), clue("animal is not dog"))
    assert(!TargetMatch.matches(ans("dog"), ans("mammal")))
    assert(!TargetMatch.matches(ans("apple"), ans("fruit")))
  }
