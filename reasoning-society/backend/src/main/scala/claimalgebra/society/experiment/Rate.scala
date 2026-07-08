package claimalgebra.society
package experiment

/** A measured rate — `count` of `n` trials — with its 95% Wilson score confidence interval. The
  * pre-registered decision rules read the interval, not the point estimate, so a rare-event rate is
  * reported with its uncertainty rather than as a point (fallible-oracle-experiment-design
  * §Analysis, "power for rare events"). Mirrors `claimalgebra.experiment.Rate` in the root module —
  * a follow-up should lift a single Wilson `Rate` into the domain-neutral `claimAlgebra` library so
  * both experiments share it (fallible-oracle-build-plan §Slice 2).
  */
final case class Rate(count: Int, n: Int):

  def point: Double = if n == 0 then 0.0 else count.toDouble / n.toDouble

  /** The 95% Wilson score interval (z = 1.96), clamped to [0, 1]; sound at small `n` and the 0/1
    * boundaries where the normal approximation degenerates. An empty rate is (0, 0).
    */
  def ci95: (Double, Double) =
    if n == 0 then (0.0, 0.0)
    else
      val z = 1.96
      val z2 = z * z
      val nD = n.toDouble
      val p = count.toDouble / nD
      val denom = 1.0 + z2 / nD
      val center = (p + z2 / (2.0 * nD)) / denom
      val margin = z * math.sqrt(p * (1.0 - p) / nD + z2 / (4.0 * nD * nD)) / denom
      (math.max(0.0, center - margin), math.min(1.0, center + margin))
