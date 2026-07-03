package claimalgebra

import munit.DisciplineSuite

import Generators.given

/** Pins the carrier annihilator `0 · x = 0` for every render carrier — the fail-closed law `ν̂`
  * relies on at the gap, which no stock bundle carries (see [[CarrierRigLaws]]). One `checkAll` per
  * carrier: the fuzzy `Lev`, the non-idempotent-product `Viterbi`, and the counting `Count`.
  */
class CarrierRigLawsSuite extends DisciplineSuite:

  checkAll("Lev.carrierRig (0 · x = 0)", CarrierRigLaws.annihilation[Lev])
  checkAll("Viterbi.carrierRig (0 · x = 0)", CarrierRigLaws.annihilation[Viterbi])
  checkAll("Count.carrierRig (0 · x = 0)", CarrierRigLaws.annihilation[Count])
