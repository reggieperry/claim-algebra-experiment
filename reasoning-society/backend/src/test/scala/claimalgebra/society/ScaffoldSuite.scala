package claimalgebra.society

import claimalgebra.calculus.Status

/** Scaffold smoke test: proves the module compiles, the munit harness runs, and the `claim-algebra`
  * dependency resolves on the classpath (the four-state read the fold produces). Real behavior —
  * the event model, the fold, the actors, the agents — arrives in the Build 1 slice.
  */
class ScaffoldSuite extends munit.FunSuite {
  test("the claim-algebra calculus dependency resolves — four Belnap states") {
    assertEquals(Status.values.length, 4)
  }
}
