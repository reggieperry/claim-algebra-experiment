package claimalgebra.society

/** A3 (recovery-and-endgame): the two generation-contract fixes are prompt-side, so they cannot be
  * proven by the fold — these are regression pins that the constraints stay in the trusted rubrics.
  * The either/or STRUCTURAL guard is enforced and tested in `AgentMoveSuite`; this suite guards the
  * prompt CONTRACT that steers the cheap model, and the poison-by-precision reframe of the define
  * prompt (the apple bug: a biology-textbook "alive" excluded a picked fruit).
  */
class AgentPromptSuite extends munit.FunSuite:

  private val splitterPrompt =
    AgentStrategy.cohort.find(_.label == "category-splitter").map(_.systemPrompt).getOrElse("")

  test("the proposer contract demands a single yes/no proposition and forbids an either/or") {
    val p = splitterPrompt.toLowerCase
    assert(p.contains("single yes/no"), clue(splitterPrompt))
    assert(p.contains("either/or"), clue(splitterPrompt))
  }

  test(
    "the define prompt frames a term to the game, not to maximal precision (poison-by-precision)"
  ) {
    val p = AgentStrategy.definePrompt
    assert(p.toLowerCase.contains("distinguish"), clue(p))
    assert(!p.contains("PRECISELY"), clue("the define prompt must not demand maximal precision"))
  }

  test(
    "the define prompt carries the apple guard — 'alive' spans a once-living thing / a picked fruit"
  ) {
    val p = AgentStrategy.definePrompt.toLowerCase
    assert(p.contains("once-living"), clue(p))
    assert(p.contains("fruit"), clue(p))
  }
