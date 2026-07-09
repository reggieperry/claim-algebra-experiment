package claimalgebra.society
package experiment

/** The interval for a DIFFERENCE of two independent proportions — the stronger-closer trial's
  * unblock test (S1 correct-rate − W correct-rate) and its closer-vs-search diagnostic (D − S1).
  * Newcombe (1998) "method 10": build each proportion's Wilson score interval, then square-and-add
  * the tail gaps. Pre-committed as the interval method so it is not chosen after the counts are
  * known (pre-registration item 1); Wilson-based, so it does not collapse when one arm signs 0.
  */
object ProportionDiff:

  /** The 95% Newcombe interval for `p1 − p2`, from the two Wilson intervals (reusing
    * [[Rate.ci95]]). `(0, 0)` denominators degrade to a rate of 0 (fail-closed, consistent with the
    * rest of the harness). The difference is deemed significant when the returned interval excludes
    * 0.
    */
  def newcombe95(c1: Int, n1: Int, c2: Int, n2: Int): (Double, Double) =
    val p1 = if n1 == 0 then 0.0 else c1.toDouble / n1
    val p2 = if n2 == 0 then 0.0 else c2.toDouble / n2
    val (l1, u1) = Rate(c1, n1).ci95
    val (l2, u2) = Rate(c2, n2).ci95
    val lo = (p1 - p2) - math.sqrt((p1 - l1) * (p1 - l1) + (u2 - p2) * (u2 - p2))
    val hi = (p1 - p2) + math.sqrt((u1 - p1) * (u1 - p1) + (p2 - l2) * (p2 - l2))
    (lo, hi)

  /** Does the difference interval exclude 0 (a distinguishable difference at 95%)? */
  def excludesZero(c1: Int, n1: Int, c2: Int, n2: Int): Boolean =
    val (lo, hi) = newcombe95(c1, n1, c2, n2)
    lo > 0.0 || hi < 0.0
