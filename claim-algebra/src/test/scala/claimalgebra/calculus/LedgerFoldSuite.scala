package claimalgebra.calculus

import claimalgebra.*

/** Pure, in-module unit coverage of the [[Ledger]] event-fold — `belief` (the ordered fold of
  * [[Evidence]] events into a slot's belief) and `resolve` (the four-state read off that fold) —
  * built from CORE constructors, no grounding. Complements [[LedgerLawsSuite]] (the generative
  * meta-theorems) with concrete per-shape examples: corroboration accumulates, rivals go ambiguous,
  * supersession pairs, an empty supersession is inert, and `Withdrawn` is absorbing (no
  * resurrection). Structural assertions (status/value/struck); the `note` prose is not pinned.
  */
class LedgerFoldSuite extends munit.FunSuite:

  private def lin(id: String): Lineage = Lineage.from(id).getOrElse(fail(s"bad lineage: $id"))
  private def leaf(value: String, doc: String = "doc"): Testimony[String] =
    Testimony.leaf(value, Prov.single(lin(doc)))
  private def struct[A](r: Resolution[A]): (Status, Option[A], Option[A]) =
    (r.status, r.value, r.struck)

  test("an empty event stream folds to the gap — Missing") {
    assertEquals(struct(Ledger.resolve(Seq.empty)), (Status.Missing, None, None))
  }

  test("a single Asserted event signs — Resolved") {
    assertEquals(
      struct(Ledger.resolve(Seq(Evidence.Asserted(leaf("$100"))))),
      (Status.Resolved, Some("$100"), None)
    )
  }

  test("the same value asserted twice corroborates to one candidate — Resolved, not ambiguous") {
    val evs = Seq(Evidence.Asserted(leaf("$100", "a")), Evidence.Asserted(leaf("$100", "b")))
    assertEquals(struct(Ledger.resolve(evs)), (Status.Resolved, Some("$100"), None))
  }

  test("two RIVAL values asserted fold to ambiguity — Conflict") {
    val evs = Seq(Evidence.Asserted(leaf("$100", "a")), Evidence.Asserted(leaf("$200", "b")))
    assertEquals(struct(Ledger.resolve(evs)), (Status.Conflict, None, None))
  }

  test("an assertion then a superseding value — Superseded, the prior struck and retained") {
    val evs = Seq(Evidence.Asserted(leaf("$100", "a")), Evidence.Superseded(leaf("$200", "b")))
    assertEquals(
      struct(Ledger.resolve(evs)),
      (Status.Superseded, Some("$200"), Some("$100"))
    )
  }

  test("a Superseded event with nothing operative yet is a fresh value — Resolved, nothing struck") {
    assertEquals(
      struct(Ledger.resolve(Seq(Evidence.Superseded(leaf("$200"))))),
      (Status.Resolved, Some("$200"), None)
    )
  }

  test("a Superseded event that grounds nothing is inert — the base stands") {
    val evs = Seq(Evidence.Asserted(leaf("$100")), Evidence.Superseded(Testimony.gap[String]))
    assertEquals(struct(Ledger.resolve(evs)), (Status.Resolved, Some("$100"), None))
  }

  test("an assertion then Withdrawn is Superseded — the struck value retained, none governing") {
    val evs = Seq(Evidence.Asserted(leaf("$100")), Evidence.Withdrawn[String]())
    assertEquals(struct(Ledger.resolve(evs)), (Status.Superseded, None, Some("$100")))
  }

  test("Withdrawn is absorbing — a second Withdrawn folds identically (no resurrection)") {
    val once = Seq(Evidence.Asserted(leaf("$100")), Evidence.Withdrawn[String]())
    val twice =
      Seq(
        Evidence.Asserted(leaf("$100")),
        Evidence.Withdrawn[String](),
        Evidence.Withdrawn[String]()
      )
    assertEquals(Ledger.belief(twice), Ledger.belief(once))
  }

  test("a single-assertion fold resolves identically to reading the leaf directly") {
    assertEquals(
      Ledger.resolve(Seq(Evidence.Asserted(leaf("$100")))),
      BelnapReader.resolve(leaf("$100"))
    )
  }
