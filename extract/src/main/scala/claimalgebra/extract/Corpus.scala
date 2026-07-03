package claimalgebra.extract

/** The document text an extraction is grounded against. A cited figure is grounded only if it
  * occurs VERBATIM here AND as a self-contained figure — never a fragment of a larger number. A
  * citation that points nowhere is a hallucination, and one that points only into the middle of
  * another figure (`"234,567"` inside `"$1,234,567"`) is a guess; both yield a gap, never a signed
  * value.
  */
final case class Corpus(text: String):

  /** Whether `span` occurs verbatim and as a WHOLE figure — at least one occurrence whose
    * boundaries do not continue a number. A boundary continues a number only when the neighbouring
    * character is a digit, or a `,`/`.` that is itself flanked by a digit (a thousands separator or
    * decimal point). A `,`/`.` that is ordinary punctuation (`"$100,000."`, `"$1,000 and"`) does
    * NOT break the figure, so a legitimately punctuated citation still grounds, while a fragment
    * like `"234,567"` inside `"$1,234,567"` (its leading `,` sits between digits) does not.
    */
  def containsFigure(span: String): Boolean =
    span.nonEmpty && occurrences(span).exists(whole(span))

  /** Whether `span` occurs verbatim as a substring — phrase grounding for a non-figure citation (a
    * supersession or withdrawal sentence), where the whole-figure boundary of [[containsFigure]]
    * does not apply. A hallucinated span that appears nowhere is rejected here too.
    */
  def containsSpan(span: String): Boolean = span.nonEmpty && text.contains(span)

  /** Anchor-and-extract membership: whether `value` occurs as a WHOLE figure in this document at a
    * position contained within an occurrence of `locator`. The model points at a verbatim `locator`
    * (a short snippet) and `value` must be a whole figure inside it — but the whole-figure boundary
    * is judged against the FULL document, so a `locator` truncated mid-number cannot make a
    * fragment look whole, and a `value` that is only whole somewhere ELSE in the document (outside
    * the locator) does not count. Both empty and a value absent from every locator occurrence are
    * rejected. This is the recall path: the model may wrap or reformat freely around the value,
    * since only the locator and the value are checked, not the surrounding text.
    */
  def containsFigureWithin(value: String, locator: String): Boolean =
    value.nonEmpty && locator.nonEmpty && {
      val locOccs = occurrences(locator).toList
      locOccs.nonEmpty && occurrences(value).exists { at =>
        // whole-figure boundary judged against the FULL document (`whole`), then required to fall
        // inside an occurrence of the locator — so a truncated locator can't make a fragment look
        // whole, and a value that is whole only elsewhere does not count.
        whole(value)(at) && locOccs.exists(ls =>
          at >= ls && at + value.length <= ls + locator.length
        )
      }
    }

  private def occurrences(span: String): Iterator[Int] =
    Iterator.iterate(text.indexOf(span))(from => text.indexOf(span, from + 1)).takeWhile(_ >= 0)

  private def whole(span: String)(at: Int): Boolean =
    !continuesBefore(at) && !continuesAfter(at + span.length)

  private def continuesBefore(at: Int): Boolean =
    if at <= 0 then false
    else
      val c = text.charAt(at - 1)
      c.isDigit || (isSeparator(c) && at - 2 >= 0 && text.charAt(at - 2).isDigit)

  private def continuesAfter(end: Int): Boolean =
    if end >= text.length then false
    else
      val c = text.charAt(end)
      c.isDigit || (isSeparator(c) && end + 1 < text.length && text.charAt(end + 1).isDigit)

  // Characters that continue a number when flanked by digits: the thousands separator `,`, the
  // decimal point `.`, and the ratio colon `:` (so a bare numerator `3.50` of `3.50:1.00` is a
  // fragment, not a whole figure).
  private def isSeparator(c: Char): Boolean = c == ',' || c == '.' || c == ':'
