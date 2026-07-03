package claimalgebra

import org.scalacheck.{Arbitrary, Cogen, Gen}

/** ScalaCheck generators for the algebra types, exposed as `Arbitrary` so the law bundles and
  * properties draw them implicitly. Generators are constrained at generation time (no
  * draw-and-discard) and weighted so the corners actually appear — a law that never reaches the
  * gap, the glut, or a non-trivial coefficient is green for the wrong reason (scala-testing.md).
  * `Cogen` instances let the bundles build the `A => B` arbitraries some laws quantify over.
  */
object Generators:

  val genLev: Gen[Lev] =
    Gen.frequency(
      1 -> Gen.const(Lev.bottom),
      1 -> Gen.const(Lev.top), // pin the top so it is exercised
      3 -> Gen.choose(0.01, 1.0).map(Lev.deg)
    )
  given Arbitrary[Lev] = Arbitrary(genLev)

  given Cogen[Lev] = Cogen[Double].contramap(_.strength)

  val genEv: Gen[Ev] =
    Gen.frequency(
      1 -> Gen.const(Ev.N), // each corner pinned in...
      1 -> Gen.const(Ev.T),
      1 -> Gen.const(Ev.F),
      1 -> Gen.const(Ev.B),
      4 -> (for { p <- genLev; c <- genLev } yield Ev(p, c)) // ...plus interior pairs
    )
  given Arbitrary[Ev] = Arbitrary(genEv)

  given Cogen[Ev] = Cogen[(Lev, Lev)].contramap(e => (e.pro, e.con))

  // Two NEUTRAL test-kinds (plus the un-annotated `None`) — enough to exercise κ̂'s union
  // non-trivially with no domain taxonomy. `Kind` is an open marker, so the library supplies its
  // own marks for the kind laws; a domain re-checks over its own closed enum. Case objects satisfy
  // the value-based equals/hashCode the `Kind` map-key contract requires.
  case object AlphaKind extends Kind
  case object BetaKind extends Kind
  val testKinds: List[Option[Kind]] = List(None, Some(AlphaKind), Some(BetaKind))

  // A small token pool so monomials and polynomials REPEAT tokens — that is what makes exponents
  // (joint use) and coefficients (alternative derivation) exceed one, exercising the non-idempotent
  // carriers. Spanning ids × kinds so the κ̂ kind-reads reach each mark; Lineage.from is the
  // validating constructor, so the flatMap drops anything blank (nothing here).
  val validLineages: List[Lineage] =
    for
      id <- List("s1", "s2", "s3", "s4", "s5")
      kind <- testKinds
      lineage <- Lineage.from(id, kind)
    yield lineage
  val genLineage: Gen[Lineage] = Gen.oneOf(validLineages)
  given Arbitrary[Lineage] = Arbitrary(genLineage)
  // Cogen keys on the id only — kind is inert to the kind-blind ν̂ valuations the law suites build.
  given Cogen[Lineage] = Cogen[String].contramap(_.id)

  /** A monomial: the unit pinned, plus products of one-to-three drawn tokens (repeats give
    * exponents > 1).
    */
  val genMonomial: Gen[Monomial] =
    Gen.frequency(
      1 -> Gen.const(Monomial.unit),
      4 -> Gen
        .choose(1, 3)
        .flatMap(n => Gen.listOfN(n, genLineage))
        .map(_.foldLeft(Monomial.unit)((m, l) => Monomial.times(m, Monomial.single(l))))
    )
  given Arbitrary[Monomial] = Arbitrary(genMonomial)

  /** A polynomial: zero and one pinned, plus sums of one-to-three drawn monomials (repeats give
    * coefficients > 1).
    */
  val genProv: Gen[Prov] =
    Gen.frequency(
      1 -> Gen.const(Prov.zero), // the gap — additive 0
      1 -> Gen.const(Prov.one), // the unit — multiplicative 1
      5 -> Gen
        .choose(1, 3)
        .flatMap(n => Gen.listOfN(n, genMonomial))
        .map(ms =>
          Prov.of(
            ms.foldLeft(Map.empty[Monomial, BigInt])((acc, m) =>
              acc.updated(m, acc.getOrElse(m, BigInt(0)) + 1)
            )
          )
        )
    )
  given Arbitrary[Prov] = Arbitrary(genProv)

  /** A non-empty provenance channel from known-valid ids — the sum of the singletons, i.e. each id
    * as an independent alternative source. For example tests.
    */
  def prov(ids: String*): Prov =
    ids.iterator
      .flatMap(id => Lineage.from(id))
      .foldLeft(Prov.zero)((p, l) => Prov.plus(p, Prov.single(l)))

  // A canonical string encoding of the polynomial, for the function-arbitraries some laws need.
  given Cogen[Prov] = Cogen[String].contramap { p =>
    p.terms.toList
      .map { case (m, c) =>
        m.exps.toList.map { case (l, e) => s"${l.id}^$e" }.sorted.mkString("*") + s"#$c"
      }
      .sorted
      .mkString("+")
  }

  // Render carriers, pinning the two identities (0 and 1) so the homomorphism laws exercise them.
  // The fuzzy carrier is Lev itself (its Arbitrary is above); Viterbi and Count are the
  // non-idempotent alternatives.
  //
  // Viterbi's `·` is real multiplication, which is not associative in floating point, so
  // `nu^(e+e')` would differ from `nu^e · nu^e'` in the last bit and the homomorphism law would
  // fail on rounding, not on substance. Drawing from exact DYADIC values (multiples of 1/4) keeps
  // every product exactly representable — grouping-invariant — while still exercising exponents
  // (0.5² = 0.25 ≠ 0.5), so the multiplicity-preservation the carrier exists to test is real.
  given Arbitrary[Viterbi] =
    Arbitrary(Gen.oneOf(0.0, 0.25, 0.5, 0.75, 1.0).map(Viterbi.apply))
  given Cogen[Viterbi] = Cogen[Double].contramap(_.value)
  given Arbitrary[Count] =
    Arbitrary(
      Gen.frequency(
        1 -> Gen.const(Count(0L)),
        1 -> Gen.const(Count(1L)),
        4 -> Gen.choose(0L, 6L).map(Count.apply)
      )
    )
  given Cogen[Count] = Cogen[Long].contramap(_.value)

  // A testimony over the candidate carrier: the gap pinned, plus one-to-three single candidates
  // corroborated together. Distinct values become rival keys (so the glut and the ambiguous case —
  // ≥ 2 for-candidates — both appear); a single candidate with support on both channels is a glut.
  // Building by `corroborate` exercises the union-merge the monoid rests on.
  val genTestimony: Gen[Testimony[Int]] =
    Gen.frequency(
      1 -> Gen.const(Testimony.gap[Int]),
      6 -> Gen
        .choose(1, 3)
        .flatMap(n =>
          Gen.listOfN(
            n,
            for
              v <- Gen.choose(0, 100)
              pp <- genProv
              pc <- genProv
            yield Testimony.single(v, pp, pc)
          )
        )
        .map(_.foldLeft(Testimony.gap[Int])(Testimony.corroborate))
    )
  given Arbitrary[Testimony[Int]] = Arbitrary(genTestimony)
