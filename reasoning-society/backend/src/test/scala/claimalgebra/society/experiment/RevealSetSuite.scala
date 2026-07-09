package claimalgebra.society
package experiment

import cats.effect.{IO, Ref}
import claimalgebra.extract.{CallError, LlmCall}
import munit.CatsEffectSuite

/** The reveal is the whole experiment's independent variable, so pin that it injects the candidate
  * SET into the SYSTEM prompt (trusted content) and never touches the user message — and that it
  * carries the set, not a single target (no leak of which one is hidden).
  */
class RevealSetSuite extends CatsEffectSuite:

  test(
    "withPreamble prepends the set preamble to the SYSTEM prompt, leaving the user message intact"
  ) {
    for
      sysRef <- Ref[IO].of("")
      userRef <- Ref[IO].of("")
      stub = new LlmCall[AgentMoveDto]:
        def call(s: String, u: String): IO[Either[CallError, AgentMoveDto]] =
          sysRef.set(s) *> userRef.set(u) *> IO.pure(Left(CallError.Malformed("stub")))
      _ <- RunRevealSet
        .withPreamble(stub, RunRevealSet.setPreamble)
        .call("ROLE RUBRIC", "GAME STATE")
      sys <- sysRef.get
      user <- userRef.get
    yield
      assert(sys.startsWith(RunRevealSet.setPreamble), clue(sys))
      assert(sys.contains("ROLE RUBRIC"), clue(sys))
      assertEquals(user, "GAME STATE") // the reveal is NOT in the user message
  }

  test("the reveal names the eight-word SET, not a single target (no leak of which one)") {
    val p = RunRevealSet.setPreamble
    val words = List("dog", "apple", "chair", "spoon", "book", "tree", "cup", "shoe")
    assert(words.forall(p.contains), clue(p))
    // it states the answer is one OF the set, i.e. does not single one out
    assert(p.toLowerCase.contains("one of these eight"), clue(p))
  }
