package claimalgebra

import algebra.ring.CommutativeRig
import cats.kernel.Order

/** The outcome of the acceptance gate. `Accepted` carries the signed value; `Blocked` carries why —
  * the verdict the trial record stores (claim-algebra.html §4).
  */
enum Decision[+A]:
  case Accepted(value: A)
  case Blocked(reason: BlockReason)

/** Why the gate refused to sign. A non-`True` corner, a sub-threshold grade, or a failed
  * verification each block — and none of them is a confident-wrong, which is exactly the asymmetry
  * the experiment's CWS metric rests on (abstaining, flagging a conflict, and reporting a
  * refutation are all passes, not misses).
  */
enum BlockReason:
  case Gap // told nothing (N)
  case Conflict // told both (B, the glut — the same value asserted AND refuted)
  case Refuted // told the opposite (F)
  case Ambiguous // told several — ≥ 2 rival positives (corner True, cardinality ≥ 2), no one value
  case BelowThreshold // True, but the support is weaker than the threshold θ
  case Unverified // True and strong enough, but verification failed

/** The acceptance gate — the only place a value becomes a signature (claim-algebra.html §4):
  *
  * {{{accept(c) ⟺ corner = True ∧ cardinality = 1 ∧ grade ≥ θ ∧ verify(c)}}}
  *
  * CWS is measured here, so a gap, a glut, a false, or an AMBIGUITY (≥ 2 rival positives) can never
  * be signed; only a verified, UNAMBIGUOUS `True` whose support clears the threshold yields a
  * signature. The corner and the cardinality are read STRUCTURALLY from the provenance
  * (model-free), and the grade is RENDERED on the pro-channel under the arm's trust model `M` — so
  * the threshold `θ` lives in `M`, and the same network gates under any model by swapping
  * `(nu, theta)`, never rerunning it. The cardinality conjunct only NARROWS acceptance, so the CWS
  * asymmetry is preserved and tightened (foundations: fold 2). `True ∧ cardinality = 1 ⟹ a value`,
  * so a "no value" block is unreachable; the `value` fold's empty branch is defensive, folded into
  * `Ambiguous`.
  */
object Gate:

  def accept[A, M](t: Testimony[A], theta: M, nu: Lineage => M, verifier: Verifier[A])(using
      rig: CommutativeRig[M],
      ord: Order[M]
  ): Decision[A] =
    Testimony.corner(t) match
      case Belnap.Gap => Decision.Blocked(BlockReason.Gap)
      case Belnap.Glut => Decision.Blocked(BlockReason.Conflict)
      case Belnap.False => Decision.Blocked(BlockReason.Refuted)
      case Belnap.True =>
        if t.cardinality != 1 then Decision.Blocked(BlockReason.Ambiguous)
        else
          val grade = t.provPro.evaluate(nu)
          if !ord.gteqv(grade, theta) then Decision.Blocked(BlockReason.BelowThreshold)
          else if !verifier(t) then Decision.Blocked(BlockReason.Unverified)
          else
            t.value.fold[Decision[A]](Decision.Blocked(BlockReason.Ambiguous))(Decision.Accepted(_))
