package claimalgebra

import claimalgebra.Generators.given
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

/** Coverage pins for laws the revised `claim-algebra.html` / `claim-calculus.html` state (some
  * newly scoped by the formal-edition revision) that a law-coverage audit found the suite did not
  * yet carry. Each pins already-correct behavior of the shipped code — a RED here is a live
  * regression, not a test to relax. The audit confirmed the normalization pin discriminates by a
  * mutation check (deleting `Testimony.of`'s `(0,0)` filter left the rest of the suite green).
  */
class RevisionLawsSuite extends ScalaCheckSuite:

  // §6 row 8 / §7 (Fig 3) — the STRUCTURAL corner equals the RENDERED corner ONLY under a
  // zero-reflecting ν̂. Coincidence under a zero-reflecting valuation; divergence under one that is
  // not: a nonzero pro-polynomial can render to ⊥, collapsing a structural True to a rendered Gap.
  // The gate reads only the structural corner precisely so this render-side collapse can neither
  // manufacture nor hide a sign.
  test("corner_s = corner_ν only under a zero-reflecting valuation — coincidence and divergence") {
    val t =
      Testimony.leaf(1, Generators.prov("s1")) // structural corner True (pro nonzero, con zero)
    assertEquals(Testimony.corner(t), Belnap.True)
    // A zero-reflecting ν (the nonzero token renders to ⊤): the rendered corner coincides.
    val nuTop: Lineage => Lev = _ => Lev.top
    assertEquals(Ev.corner(Testimony.renderEv(t)(nuTop)), Testimony.corner(t))
    // A NON-zero-reflecting ν (the nonzero token renders to ⊥): the rendered corner diverges to Gap,
    // while the structural corner is unchanged — so the equality genuinely requires zero reflection.
    val nuBot: Lineage => Lev = _ => Lev.bottom
    assertEquals(Ev.corner(Testimony.renderEv(t)(nuBot)), Belnap.Gap)
    assertNotEquals(Ev.corner(Testimony.renderEv(t)(nuBot)), Testimony.corner(t))
  }

  // §3 — a normalized testimony carries no `(0,0)` candidate entries: an operation that generates one
  // (⊗ₖ over disjoint support) must DROP it. `derive` of a True-on-1 and a refuted-on-2 convolves to
  // candidate 3 with channels `(s1·0, 0·s2) = (0,0)`; normalization drops it, so the result is the
  // gap, not a map with a phantom `(0,0)` key. (A retained `(0,0)` cannot manufacture a sign, but it
  // breaks the invariant the structural reads rely on — this is the mutation-surviving discriminator
  // for the `of` filter.)
  test("normalization drops a generated (0,0) candidate — no phantom entries (§3)") {
    val t = Testimony.leaf(1, Generators.prov("s1"))
    val refuted = Testimony.single(2, Prov.zero, Generators.prov("s2"))
    assertEquals(Testimony.derive(t, refuted)(_ + _), Testimony.gap[Int])
  }

  // §5.1 / §6 row 3 — corroboration is NOT idempotent: `t ⊞ t` doubles the provenance (`2·s1 ≠ s1`)
  // because ℕ[X] addition is not idempotent. The Testimony-level headline the per-channel `Prov.plus`
  // properties imply but no single test asserts directly.
  test("corroborate is not idempotent — t ⊞ t ≠ t (§6 row 3)") {
    val t = Testimony.leaf(1, Generators.prov("s1"))
    assertNotEquals(Testimony.corroborate(t, t), t)
  }

  // §2 — ℕ[X] is zero-sum-free: `p + q = 0 ⇒ p = 0 ∧ q = 0`. Not entailed by the commutative-rig laws
  // (ℤ is a rig counterexample); it is what forces a con residue to persist on the fold (Thm 6.4
  // exclusivity / the rehabilitation asymmetry — con never cancels).
  property("ℕ[X] is zero-sum-free: p + q = 0 ⇒ p = 0 ∧ q = 0 (§2)") {
    forAll { (p: Prov, q: Prov) =>
      !Prov.plus(p, q).isZero || (p.isZero && q.isZero)
    }
  }

  // §7 / Fig 3 — ν̂ PRESERVES zero but need not REFLECT it: a NONZERO polynomial can evaluate to the
  // carrier bottom under some valuation. The witness underneath the corner-condition divergence above.
  test("ν̂ preserves but does not reflect zero — a nonzero polynomial can render to ⊥ (§7)") {
    val p = Generators.prov("s1")
    assert(!p.isZero, "fixture must be a nonzero polynomial")
    assertEquals(p.evaluate((_: Lineage) => Lev.bottom), Lev.bottom)
  }
