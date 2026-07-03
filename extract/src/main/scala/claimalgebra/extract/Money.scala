package claimalgebra.extract

import cats.kernel.Order

/** A monetary figure in non-negative integer cents — the leaf payload an extractor grounds. A
  * DISTINCT opaque type over `Long`: the only constructors are [[Money.cents]] (a validated
  * non-negative count) and [[Money.parse]] (a deterministic decode of a figure such as
  * `"$1,234,567"` or `"1234567.89"`), so a malformed or negative figure cannot exist. The decode is
  * mechanical and total — the same span always yields the same figure — which is what lets a trial
  * be reproduced from its record and keeps the model out of the grading path.
  */
opaque type Money = Long

object Money:

  /** Zero — no money. The naive prose arm's default for a missing figure. */
  val zero: Money = 0L

  /** The safe constructor: a non-negative count of cents, else `None`. */
  def cents(n: Long): Option[Money] = Option.when(n >= 0)(n)

  /** Decode a monetary figure from a cited span — `$`, thousands separators, and surrounding space
    * stripped, then read as plain decimal dollars and rounded to cents. Fail-closed and BOUNDED:
    * the raw span is length-capped first (so a giant input does no work), and the cleaned remainder
    * must match a strict `digits(.digits)?` shape — no sign, and crucially no scientific notation,
    * which `BigDecimal` would expand into a multi-gigabyte integer and OOM (an OOM is fatal, so
    * `Try` would NOT catch it). The 1..16 integer-digit cap keeps the value well under `Long`
    * cents, and `toLongExact` is a final guard against any residual overflow. A label, a sentence,
    * a sign, an exponent, an over-long input, or the empty string yields `None`, never a guess.
    * Assumes a non-null Scala `String`; a nullable Java field is lifted at the boundary
    * (`Extractor.ground`).
    */
  def parse(raw: String): Option[Money] =
    if raw.length > MaxRawLength then None
    else
      val cleaned = raw.trim.replace("$", "").replace(",", "").replace(" ", "")
      if !cleaned.matches("\\d{1,16}(\\.\\d+)?") then None
      else
        scala.util
          .Try((BigDecimal(cleaned) * 100).setScale(0, BigDecimal.RoundingMode.HALF_UP).toLongExact)
          .toOption
          .flatMap(cents)

  /** No real cited figure is longer than this; a span beyond it is refused before any work
    * (CWE-400).
    */
  private val MaxRawLength = 64

  extension (m: Money)
    def toCents: Long = m

    /** Render as a corpus-ready dollar figure — comma-grouped, with cents only when non-zero
      * (`$1,234,567`, `$1,234,567.89`). Round-trips through [[parse]], so a rendered figure
      * grounds.
      */
    def display: String =
      val dollars = m / 100L
      val rem = m % 100L
      val grouped = dollars.toString.reverse.grouped(3).mkString(",").reverse
      if rem == 0L then s"$$$grouped" else f"$$$grouped.$rem%02d"

  // Order by amount; supplies Eq too. Safe as a less-than on the underlying Long (no self-loop,
  // since `fromLessThan` never summons an instance for the opaque type).
  given Order[Money] = Order.fromLessThan((x, y) => x < y)
