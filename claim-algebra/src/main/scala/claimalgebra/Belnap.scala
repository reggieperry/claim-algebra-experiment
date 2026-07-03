package claimalgebra

/** The four Belnap corners, read structurally off a (pro, con) pair.
  *
  * `Gap` is knowledge-bottom (told nothing); `Glut` is knowledge-top (told both); `True` is
  * supported-and-unrefuted; `False` is refuted-and-unsupported. The corner is a mechanical read of
  * whether each channel carries evidence — no model in the loop — which is what lets a mechanical
  * grader and the gap/glut discrimination stay model-free, and identical under every trust model.
  * It is read off the provenance polynomials ([[Prov.isZero]] per channel), not a rendered grade,
  * so the chosen model can never move it.
  */
enum Belnap:
  case Gap, True, False, Glut

object Belnap:
  /** The structural corner from whether each channel is empty: the single mapping shared by the
    * polynomial ([[Prov.isZero]]) and the rendered grade ([[Lev.isBottom]]). A channel "fires" when
    * it carries evidence (is not empty).
    */
  def from(proEmpty: Boolean, conEmpty: Boolean): Belnap = (proEmpty, conEmpty) match
    case (true, true) => Gap
    case (false, true) => True
    case (true, false) => False
    case (false, false) => Glut
