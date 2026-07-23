package claimalgebra

import munit.FunSuite

/** Regression tests for involutive refutation versus idempotent strike. */
class SupersessionStrikeSuite extends FunSuite:
  test("superseding an already-refuted prior keeps the prior struck False"):
    val priorCon = Generators.prov("prior-con")
    val prior = Testimony.single(1, Prov.zero, priorCon)
    val amendment = Testimony.leaf(2, Generators.prov("amendment"))

    val result = Testimony.supersede(prior, amendment)

    assertEquals(Testimony.corner(result.struck), Belnap.False)
    assertEquals(result.struck.provPro, Prov.zero)
    assertEquals(result.struck.provCon, priorCon)
    assertEquals(result.operative, amendment)

  test("superseding a glut folds both prior channels into con"):
    val priorPro = Generators.prov("prior-pro")
    val priorCon = Generators.prov("prior-con")
    val prior = Testimony.single(1, priorPro, priorCon)
    val amendment = Testimony.leaf(2, Generators.prov("amendment"))

    val result = Testimony.supersede(prior, amendment)

    assertEquals(Testimony.corner(result.struck), Belnap.False)
    assertEquals(result.struck.provPro, Prov.zero)
    assertEquals(result.struck.provCon, Prov.plus(priorPro, priorCon))
