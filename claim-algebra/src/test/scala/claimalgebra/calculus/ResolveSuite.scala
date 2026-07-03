package claimalgebra.calculus

import claimalgebra.*

/** Pure, in-module unit coverage of the four-state read — [[BelnapReader.resolve]] /
  * [[BelnapReader.resolveSuperseded]] / [[BelnapReader.resolveAmended]] — built entirely from CORE
  * constructors (`Testimony.leaf`/`gap`/`refute`/`supersede`/`corroborate`), with no grounding or
  * `Corpus` in sight. The library's calculus API is thereby exercised within its own module; the
  * grounding-coupled integration paths live in a consuming module's suites.
  *
  * Asserts the STRUCTURAL four-state contract — status, governing value, struck prior. The `note`
  * prose is presentation, deliberately NOT pinned here (a consumer renders it).
  */
class ResolveSuite extends munit.FunSuite:

  private def lin(id: String): Lineage = Lineage.from(id).getOrElse(fail(s"bad lineage: $id"))
  private def leaf(value: String, doc: String = "doc"): Testimony[String] =
    Testimony.leaf(value, Prov.single(lin(doc)))

  // (status, governing value, struck prior) — the structural row; the `note` is omitted on purpose.
  private def struct[A](r: Resolution[A]): (Status, Option[A], Option[A]) =
    (r.status, r.value, r.struck)

  test("resolve: a single grounded leaf signs — Resolved with that value") {
    assertEquals(
      struct(BelnapReader.resolve(leaf("$100"))),
      (Status.Resolved, Some("$100"), None)
    )
  }

  test("resolve: the gap is Missing — fail-closed, nothing signs") {
    assertEquals(
      struct(BelnapReader.resolve(Testimony.gap[String])),
      (Status.Missing, None, None)
    )
  }

  test("resolve: two rival for-values are Conflict — ambiguity, cardinality 2, no con-mass") {
    val ambiguous = Testimony.corroborate(leaf("$100", "a"), leaf("$200", "b"))
    assertEquals(Testimony.corner(ambiguous), Belnap.True) // rival positives, still corner True
    assertEquals(ambiguous.cardinality, 2)
    assertEquals(struct(BelnapReader.resolve(ambiguous)), (Status.Conflict, None, None))
  }

  test("resolve: a value asserted AND refuted is Conflict — a genuine glut") {
    val glut = Testimony.corroborate(leaf("$100", "a"), Testimony.refute(leaf("$100", "b")))
    assertEquals(Testimony.corner(glut), Belnap.Glut) // con-channel mass — the real glut
    assertEquals(struct(BelnapReader.resolve(glut)), (Status.Conflict, None, None))
  }

  test("resolve: a refuted leaf with no replacement is Superseded — the struck value retained") {
    assertEquals(
      struct(BelnapReader.resolve(Testimony.refute(leaf("$100")))),
      (Status.Superseded, None, Some("$100"))
    )
  }

  test("resolveSuperseded: a clean amendment governs, the prior struck and retained") {
    val s = Testimony.supersede(leaf("$100", "base"), leaf("$200", "amend"))
    assertEquals(
      struct(BelnapReader.resolveSuperseded(s)),
      (Status.Superseded, Some("$200"), Some("$100"))
    )
  }

  test("resolveSuperseded: an amendment that grounds nothing leaves no governing value") {
    val s = Supersession(Testimony.refute(leaf("$100")), Testimony.gap[String])
    assertEquals(
      struct(BelnapReader.resolveSuperseded(s)),
      (Status.Superseded, None, Some("$100"))
    )
  }

  test("resolveSuperseded: a contested (ambiguous) amendment is Conflict, the prior retained") {
    val contested = Testimony.corroborate(leaf("$200", "a"), leaf("$300", "b"))
    val s = Supersession(Testimony.refute(leaf("$100")), contested)
    assertEquals(
      struct(BelnapReader.resolveSuperseded(s)),
      (Status.Conflict, None, Some("$100"))
    )
  }

  test("resolveSuperseded: an amendment that is itself refuted is Conflict, the prior retained") {
    val s = Supersession(Testimony.refute(leaf("$100")), Testimony.refute(leaf("$200")))
    assertEquals(
      struct(BelnapReader.resolveSuperseded(s)),
      (Status.Conflict, None, Some("$100"))
    )
  }

  test("resolveAmended: an amendment silent on the item leaves the base standing — Resolved") {
    assertEquals(
      struct(BelnapReader.resolveAmended(leaf("$100"), Testimony.gap[String])),
      (Status.Resolved, Some("$100"), None)
    )
  }

  test("resolveAmended: a base silent on the item takes the amendment as a fresh value") {
    assertEquals(
      struct(BelnapReader.resolveAmended(Testimony.gap[String], leaf("$200"))),
      (Status.Resolved, Some("$200"), None)
    )
  }

  test("resolveAmended: both speak — the amendment supersedes and the prior is retained") {
    assertEquals(
      struct(BelnapReader.resolveAmended(leaf("$100"), leaf("$200"))),
      (Status.Superseded, Some("$200"), Some("$100"))
    )
  }
