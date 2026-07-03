package claimalgebra.calculus

import claimalgebra.*
import claimalgebra.Generators.given
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Gen}

/** Property tests for the calculus meta-theorems (`claim-calculus` §§4-8) against their operational
  * realization — the [[Ledger]] event-fold (`belief`/`resolve`). Determinacy (Thm 4.1) and
  * normalization (Thm 4.2) are the totality of the fold; non-fabrication (Thm 6.2) is that no
  * combinator manufactures a fresh signable value; confluence on the assertion fragment (Thm 5.3 /
  * Lemma 5.2) is the order-independence `corroborate`'s commutative monoid gives the fold. The
  * algebra-level laws these rest on live in the core law suites; here they are pinned generatively
  * at the fold, over a generated stream of evidence events.
  */
class LedgerLawsSuite extends ScalaCheckSuite:

  /** An evidence event over `Int`: `Asserted`/`Superseded` carry a generated `Testimony`;
    * `Withdrawn` is nullary. Weighted toward assertions so most streams reach a signing state, with
    * enough supersede/withdraw to exercise the non-commutative channel transforms.
    */
  given Arbitrary[Evidence[Int]] = Arbitrary(
    Gen.frequency(
      4 -> Arbitrary.arbitrary[Testimony[Int]].map(Evidence.Asserted(_)),
      2 -> Arbitrary.arbitrary[Testimony[Int]].map(Evidence.Superseded(_)),
      1 -> Gen.const(Evidence.Withdrawn[Int]())
    )
  )

  private val states = Set(Status.Resolved, Status.Missing, Status.Conflict, Status.Superseded)

  /** Thm 4.1 (determinacy) + 4.2 (strong normalization): the fold is a TOTAL function — any finite
    * event sequence resolves to exactly one of the four states, with no `MatchError` and no
    * divergence (a left fold over a finite `Seq` halts in `|E|` steps by construction).
    */
  property(
    "Thm 4.1/4.2 — the fold is total: any event stream resolves to exactly one of four states"
  ) {
    forAll { (events: List[Evidence[Int]]) =>
      states.contains(Ledger.resolve("slot", events).status)
    }
  }

  /** Thm 6.2 (non-fabrication): if a slot signs a value, that value ORIGINATES in the stream — some
    * `Asserted`/`Superseded` event supplied it with pro-support. No combinator invents a signable
    * value. `corroborate` only unions keys, `supersede`/`strike` only move a key's channels, and
    * the signed value is always the operative's — never a refuted trace.
    */
  property("Thm 6.2 — non-fabrication: a signed value originates in some event's pro-support") {
    forAll { (events: List[Evidence[Int]]) =>
      Ledger.resolve("slot", events).value.forall { v =>
        events.exists {
          case Evidence.Asserted(g) => g.supported.contains(v)
          case Evidence.Superseded(g) => g.supported.contains(v)
          case Evidence.Withdrawn() => false
        }
      }
    }
  }

  /** Thm 5.3 / Lemma 5.2 (confluence on assertions): over the ASSERTION fragment the fold is
    * order-independent — `corroborate` is a commutative monoid, so a permutation of `Asserted`
    * events signs the same value. (Supersede/withdraw are order-sensitive BY DESIGN — the
    * non-theorem — so they are excluded here.)
    */
  property("Thm 5.3 — assertions fold order-independently (the commutative fragment)") {
    forAll { (ts: List[Testimony[Int]]) =>
      val asserts = ts.map(Evidence.Asserted(_))
      Ledger.resolve("slot", asserts).value == Ledger.resolve("slot", asserts.reverse).value
    }
  }
