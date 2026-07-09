package claimalgebra.society
package experiment

/** The pre-registered adjudication of the stronger-closer trial, as a pure, testable function of
  * the three arms' correct-sign counts (W weak, S1 strong closer+adversary, D strong everything).
  * It is DIRECTION-AWARE — it tests the SIGN of each Newcombe difference interval, not merely that
  * it excludes 0 — and it consults D even when S1 fails to clear W, so the closer-vs-search
  * diagnostic (design outcomes 2/5) is reachable in every branch, not only when S1 already
  * unblocked.
  */
object StrongerCloserOutcome:

  enum Outcome:
    /** W's rate exceeded the drift threshold — the null did not reproduce; every contrast caveated.
      */
    case Drift

    /** S1 signs distinguishably BELOW W — a strong closer that hurts. Unexpected → logs. */
    case AnomalyS1BelowW

    /** Search binds: a strong splitter (D) unblocks beyond what the closer alone does. */
    case SearchBinds

    /** D signs distinguishably BELOW S1 — a strong splitter that hurts (over-decomposition). →
      * logs.
      */
    case AnomalyDBelowS1

    /** Unblocked and the closer+adversary allocation captures the gain (D does not exceed S1). */
    case CloserSuffices

    /** Nothing distinguishably exceeds W at this tier — not gated by the tier at these roles. */
    case NotUnblocked

  /** Classify from the raw counts. `driftThreshold` is checked first (a drifted W invalidates the
    * contrasts); then the anomalies and the unblock/diagnostic branches by interval SIGN.
    */
  def classify(
      cW: Int,
      nW: Int,
      cS1: Int,
      nS1: Int,
      cD: Int,
      nD: Int,
      driftThreshold: Double
  ): Outcome =
    val wRate = if nW == 0 then 0.0 else cW.toDouble / nW
    val (ubLo, ubHi) = ProportionDiff.newcombe95(cS1, nS1, cW, nW) // S1 − W
    val s1AboveW = ubLo > 0.0
    val s1BelowW = ubHi < 0.0
    val (dgLo, dgHi) = ProportionDiff.newcombe95(cD, nD, cS1, nS1) // D − S1
    val dAboveS1 = dgLo > 0.0
    val dBelowS1 = dgHi < 0.0
    val dAboveW = ProportionDiff.newcombe95(cD, nD, cW, nW)._1 > 0.0 // D − W
    if wRate > driftThreshold then Outcome.Drift
    else if s1BelowW then Outcome.AnomalyS1BelowW
    else if s1AboveW && dAboveS1 then Outcome.SearchBinds
    else if s1AboveW && dBelowS1 then Outcome.AnomalyDBelowS1
    else if s1AboveW then Outcome.CloserSuffices
    else if dAboveW then Outcome.SearchBinds // S1 didn't clear W, but the strong splitter did
    else Outcome.NotUnblocked

  def message(o: Outcome): String = o match
    case Outcome.Drift =>
      "CAVEAT — W drifted above the threshold: the null did not reproduce; read every contrast with this caveat."
    case Outcome.AnomalyS1BelowW =>
      "ANOMALY — S1 signs distinguishably BELOW W (a strong closer that hurts). Not adjudicable from rates → read the archived logs."
    case Outcome.SearchBinds =>
      "OUTCOME 2 — unblocked, and SEARCH binds: the strong splitter (D) unblocks beyond the closer alone. A strong closer is insufficient; the searcher is the lever."
    case Outcome.AnomalyDBelowS1 =>
      "OUTCOME 5 — ANOMALY: D signs distinguishably BELOW S1 (a strong splitter that hurts — over-decomposition). Not adjudicable from rates → read the archived logs."
    case Outcome.CloserSuffices =>
      "OUTCOME 1 — unblocked, closer+adversary suffices: S1 clears W and D does not exceed it. The allocation lever reaches the degraded regime."
    case Outcome.NotUnblocked =>
      "OUTCOME 3 — NOT unblocked: neither S1 nor D distinguishably exceeds W. Capability under degradation is not gated by this tier at these roles (top up to N=16/target before a firm null)."
