---
paths:
  - "**/*.scala"
  - "**/*.sc"
  - "build.sbt"
---

# Scala testing

How to write Scala tests: munit suite structure, fixtures without shared mutable state, deterministic property-based testing with ScalaCheck, type-class laws checked with `discipline`, and the algebra's laws as the default test shape. Sources: the munit docs (FunSuite, fixtures, the ScalaCheck integration), the ScalaCheck user guide (`forAll`, `Gen`, labeling, shrinking, seeds), the Typelevel `discipline` / `cats-laws` / `algebra-laws` docs (`checkAll` and the `*Tests` law bundles), and *The Science of Functional Programming* (Sergei Winitzki) for the semigroup associativity law, the monoid identity laws, and the structural-analysis stance that a typeclass instance is correct only once its laws are verified. The TDD cadence and design discipline are in `craft-tdd.md`; this rule is the Scala mechanics. The engines are pinned in `build.sbt`: `munit`, `munit-scalacheck`, `scalacheck`, `cats-laws`, `algebra-laws`, `discipline-munit`, and `munit-cats-effect`, all `% Test`.

> See `craft-tdd.md` for red-green-refactor and "listen to the tests", `scala-types.md` for the `enum` ADTs and `opaque` types whose laws these tests pin, `scala-style.md` for the brace and `given`/`using` defaults the examples follow, and `craft-domain-modeling.md` for why `Claim[K, A]` / `Axia` / `Validation` carry behavior worth a law.

## Structure and naming

- **A test file is one `class` extending `munit.FunSuite`** (or `munit.ScalaCheckSuite` when it carries properties — `ScalaCheckSuite` extends `FunSuite`, so example and property tests live in the same class). Each case is a `test("...") { ... }` call whose name is a sentence about the behavior, not the method: `test("corroborate is commutative on agreeing evidence")`, not `test("corroborate")`.
- **One behavior per test, arrange-act-assert in order**, the act step a single call to the unit under test. Keep computation out of the test body — no branching or loops that derive the expected value; a `want` you have to compute is a `want` that can be wrong the same way the code is.
- **Group by the type under test, not by the layer.** The algebra core (`Claim`, `Axia`, `Validation`, the bilattice) gets pure suites with no effect runtime; the pipeline, fault injector, and grader get their own suites. Name suites `XxxSuite` for the type `Xxx`.

## Assertions and failure messages

- **Use munit's `assertEquals(obtained, expected)`** — obtained first, expected second; munit prints a red/green diff on mismatch. Do not reach for raw `assert(a == b)` where an equality is meant: `assert` prints only "false", `assertEquals` prints both sides. Reserve `assert(cond, clue)` for genuine predicates, and attach a `clue(...)` so the failure names the value.
- **Assert on returned values and final state, not on interactions**, wherever collaborators are pure and fast — this is the classicist default `craft-tdd.md` sets. Pin interaction order only where the protocol itself is the unit under test (for example, that a node emits its control-script calls in sequence).
- **`assertEquals` compares by `==`, so the types under test need a lawful `equals`.** Prefer `case class`/`enum` (structural equality for free) and `opaque type`s whose underlying value compares correctly. Never compare `Double` grades with `==`; assert within a tolerance with `assertEqualsDouble(obtained, expected, delta)`.

## Fixtures, lifecycle, and no shared mutable state

- **Construct fresh state inside each test, or with a per-test `FunFixture`.** The default house position: a `val` in the suite body is shared across every test in the class, so any mutation leaks between cases and makes failures order-dependent. Immutable fixtures (a sample `Claim`, a seeded corpus) may be suite-level `val`s; anything mutable or owning a resource must not be.
- **For per-test resources use `FunFixture[T]`** with explicit setup and teardown, then `fixture.test("name") { resource => ... }`. Each test gets its own `T`; teardown runs even on failure. Compose two with `FunFixture.map2`.

```scala
val tempStore: FunFixture[TrialStore] = FunFixture(
  setup = _ => TrialStore.inMemory(),     // one fresh store per test
  teardown = store => store.close()        // runs even if the test fails
)

tempStore.test("records a CWS flag for a faulted leaf") { store =>
  store.record(sampleRecord)
  assertEquals(store.count, 1)
}
```

- **Do not initialize resources in the class constructor or a suite-level `var`.** A suite may be instantiated for test discovery without running anything; a constructor-side resource then leaks. Reusable resources go through a `Fixture[T]` (override `beforeEach`/`afterEach`, register in `munitFixtures`); shared-once-per-suite setup goes through `beforeAll`/`afterAll`, never a mutated field.
- **Tests must be independently runnable and order-independent.** A test that only passes after another ran is a shared-state bug — fix the state, not the order. Do not rely on suite-level `var` accumulation.

## Effects under test

- **The algebra core is pure — test it directly, with no IO runtime.** `Claim`, `Axia`, the corroboration combiner, and the bilattice operations return values; assert on the values. This is most of the suite and must stay the fast, hermetic majority.
- **Where a unit returns `cats.effect.IO`, run it through munit-cats-effect, never `unsafeRunSync()` scattered in test bodies.** Return the `IO[Unit]` from the test and let the integration evaluate it. Do not bridge an `IO` to a `Future` or block on it to "make the assertion fit" — that mixing is the fragmentation `scala-concurrency.md` bans. Keep effectful suites few; push logic into the pure core so it can be example- and property-tested without a runtime.

## Property-based testing — the default for the algebra (ScalaCheck)

Property tests assert an invariant over a large generated input space and **shrink** any failure to a minimal counterexample, so they catch the cases you would never enumerate by hand. ScalaCheck is the house engine — the Scala equivalent of Go's `rapid` and Python's Hypothesis. For the claim algebra, **the laws are the specification**, so a property is the primary test of a combiner and an example test is the regression pin beside it.

- **Write a property with `property("...") = forAll { ... }`** in a `ScalaCheckSuite`. Favor strong properties — algebraic laws, a round-trip (`decode(encode(x)) == x`), or a comparison against a slow-but-obvious oracle — over trivially-true ones. Inside the body, prefer munit's `assertEquals` to a bare `Boolean`: it reports which side diverged.

```scala
import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll

class CorroborationSuite extends ScalaCheckSuite:

  property("corroborate is associative") {
    forAll { (a: Ev, b: Ev, c: Ev) =>
      assertEquals(
        corroborate(corroborate(a, b), c),
        corroborate(a, corroborate(b, c))
      )
    }
  }

  property("corroborate is commutative") {
    forAll((a: Ev, b: Ev) => corroborate(a, b) == corroborate(b, a))
  }
```

### Type-class laws via `discipline` — prefer this for any instance

When an algebra operation *is* a standard type class — the corroboration combiner as a `CommutativeMonoid` (or `BoundedSemilattice`), the value's `Eq`, the `Validated` `Applicative`, the bilattice's two orders as `Lattice` / `BoundedLattice`, the free provenance `Semiring` — do not hand-write its laws. Give it the instance and check the instance against the prebuilt law set with `discipline`'s `checkAll`. The stock bundles are complete and correct; a hand-rolled `forAll("associative")` can be subtly wrong or silently miss a law the bundle covers (left versus right identity, `combineN` consistency, absorption). The kernel and cats laws come from `cats-laws` (`CommutativeMonoidTests`, `BoundedSemilatticeTests`, `EqTests`, the `Applicative` laws); the lattice and semiring laws come from `algebra-laws` (`LatticeTests`, `BoundedLatticeTests`, `RingTests.semiring`); `discipline-munit`'s `DisciplineSuite` runs them.

```scala
import munit.DisciplineSuite
import cats.kernel.laws.discipline.{CommutativeMonoidTests, EqTests}

class AxiaLawsSuite extends DisciplineSuite:
  // requires given Arbitrary[Axia], given Eq[Axia], given CommutativeMonoid[Axia] in scope
  checkAll("Axia.commutativeMonoid", CommutativeMonoidTests[Axia].commutativeMonoid)
  checkAll("Axia.eq",                EqTests[Axia].eqv)
```

`checkAll` registers one named munit test per law, so a failure points at the exact law — `commutativeMonoid.associative`, `monoid.leftIdentity` — without your writing any of them. The instance is the design; the law check proves it lawful. This is the law-first red-green for an algebra: write the `checkAll` against the type class the operation must satisfy, watch it fail, implement the instance until every law passes.

**For the algebra-specific laws no stock bundle carries** — the two annihilators across the *two* meets (`gap ⊗ₖ x = gap` on the knowledge order, `false ⊗ x = false` on the truth order), the for/against channel swap that defines negation, the per-channel provenance homomorphism — author a custom `discipline.Laws` with its own `RuleSet` and `checkAll` it the same way:

```scala
import org.typelevel.discipline.Laws
import org.scalacheck.Prop.forAll
import cats.syntax.eq.*

trait ClaimAlgebraLaws[A] extends Laws:
  def A: ClaimAlgebra[A]
  def annihilators(using Arbitrary[A], Eq[A]): RuleSet = new DefaultRuleSet(
    name   = "claimAlgebra",
    parent = None,
    "knowledge-meet annihilator" -> forAll((x: A) => A.kmeet(A.gap,    x) === A.gap),
    "truth-meet annihilator"     -> forAll((x: A) => A.tmeet(A.bottom, x) === A.bottom)
  )
```

Verify the absorbing elements and the homomorphism direction against `claim-algebra-belnap.html` before pinning them — state the algebra's actual laws, never assume them from memory.

### The laws the algebra must carry

The corroboration combiner is a commutative monoid over evidence (a `BoundedSemilattice` if it is idempotent), and the bilattice gives it two annihilators. These are the laws *The Science of Functional Programming* names — semigroup associativity (§8.3.3) and the monoid identity law (§8.3.4) — applied to the algebra's own operations. When the combiner is given as that type class, the associativity, commutativity, identity, and idempotence laws below come from the `checkAll` above for free; keep the explicit `forAll` form here for the annihilators (until they are folded into the custom `RuleSet`) and for invariants that are not type-class laws. Each is a property; together they are the contract:

- **Associativity** — `corroborate(corroborate(a, b), c) == corroborate(a, corroborate(b, c))`. Order of folding evidence must not change the verdict.
- **Commutativity** — `corroborate(a, b) == corroborate(b, a)`. Which node spoke first must not change the verdict.
- **Identity** — `corroborate(a, neutral) == a` and `corroborate(neutral, a) == a`, where `neutral` is the no-evidence unit (Belnap's *unknown* / bottom on the knowledge axis).
- **The two annihilators** — the top and contradiction corners absorb: `corroborate(a, top) == top` and `corroborate(a, contradiction) == contradiction` (state the absorbing element your bilattice actually pins; verify it against `claim-algebra-belnap.html`, do not assume one from memory).
- **Idempotence** — `corroborate(a, a) == a` where the same evidence is presented twice and double-counting would be a fault. Confirm the algebra actually claims idempotence before pinning it — it is a real, falsifiable property of this combiner, not a given.

Pin each law as its own named property so a failure names the law that broke. Where several conditions form one law, label them and conjoin with `&&` so the report points at the failing conjunct:

```scala
property("neutral is a two-sided identity for corroborate") {
  forAll { (a: Ev) =>
    (corroborate(a, Ev.neutral) == a) :| "right identity" &&
    (corroborate(Ev.neutral, a) == a) :| "left identity"
  }
}
```

### Generators

- **Define a `Gen[T]` for each algebra type and expose it as an `Arbitrary[T]`** so `forAll` draws it implicitly. Build compound generators with a `for`-comprehension over `Gen.choose`, `Gen.oneOf`, and `Gen.const`; constrain at generation time, not by discarding inside the body.

```scala
import org.scalacheck.{Arbitrary, Gen}

val genEv: Gen[Ev] =
  for
    pro <- Gen.choose(0.0, 1.0)
    con <- Gen.choose(0.0, 1.0)
  yield Ev(pro, con)

given Arbitrary[Ev] = Arbitrary(genEv)
```

- **Constrain generators at generation time** with `Gen.choose` / `Gen.oneOf` / `suchThat` rather than drawing wide and discarding inside the property — discards bleed into `maxDiscardRatio` and can starve the run. Keep generators total and pure: no wall-clock, no real IO, no ambient `scala.util.Random`; the only randomness is ScalaCheck's seeded source.
- **Use `classify`/`collect` to confirm the distribution covers the corners** — that the `Ev` generator actually produces the four Belnap corners (true, false, unknown, contradiction) and not just the middle. A property that never reaches a corner is green for the wrong reason.

### Determinism — no flakiness

The experiment's headline number must be reproducible, and so must its tests. A flaky property is a broken property.

- **A property must be a pure function of its drawn inputs.** No clock, no filesystem, no network, no shared `Random`. If the unit needs a clock or a store, inject a deterministic double (`craft-tdd.md`, "only mock what you own").
- **ScalaCheck runs from a seed; pin it so a green run stays green and a red one reproduces.** When a property fails, munit prints the seed; fix it for the suite by overriding `scalaCheckInitialSeed` (a base-64 `Seed` string) so the run is identical machine to machine, and tune the budget with `scalaCheckTestParameters`:

```scala
override def scalaCheckInitialSeed = "x9aQ2...base64-seed..."   // reproduce a found counterexample

override def scalaCheckTestParameters =
  super.scalaCheckTestParameters
    .withMinSuccessfulTests(500)   // laws deserve a wide sample
    .withMaxDiscardRatio(5)
```

- **When a property finds a counterexample, copy the minimized case into a named example test before you fix the code** — the law property covers the space, the example pins this exact regression forever. Then fix the code and watch both go green.
- **Reach for `forAllNoShrink` only to diagnose a misleading shrink**, never as the committed form — committed properties keep shrinking on so failures report the minimal counterexample. If shrinking lands on an invalid input, supply a `suchThat`-guarded `Shrink` rather than disabling it.

## Coverage

- **Measure with scoverage (`sbt coverage test coverageReport`) and treat it as a signal, not a target.** Never write assertion-free tests, or call a function with no `assert`, to raise the number — a covered line with no assertion is untested. The differential gate scores coverage against the merge-base; the way to keep it up is more behavior pinned, not more lines touched.
- **Gate slow or external suites** (anything hitting the live model, a real store, or the network) behind a tag or a separate sbt configuration, keeping the default `sbt test` fast and hermetic — the same split `craft-tdd.md` draws between unit, integration, and end-to-end.

## Anti-weakening (what the differential gate forbids)

Treat any of these versus the merge-base as test-suite weakening — do not introduce them:

- A test or property deleted with no equivalent replacement, or a previously-running case newly disabled with `.ignore`, `assume(false, ...)`, `munitIgnore`, a removed registration, or a commented-out body.
- A net drop in assertion sites for a suite (removed `assertEquals` / `assert` / `assertEqualsDouble` / `intercept` / labeled `:|` conjuncts), or a `forAll` law narrowed to a fixed example that no longer covers the space.
- A `want` loosened to a wildcard or an always-true comparison; an `assertEquals` turned into a bare `assert(true)`; an exception assertion (`intercept[E]`) deleted; an error swallowed with `.toOption` or a discarded `Either` where it was previously asserted.
- `minSuccessfulTests` lowered, `maxDiscardRatio` raised to mask a starving generator, or a property's generator widened so the hard corners (the Belnap contradiction and top) are no longer reached.
- A failing assertion downgraded to a `println`/`clue`/log so the failure becomes invisible, or a property switched to `forAllNoShrink` and committed (hides the minimal counterexample).
- A committed regression example (the minimized counterexample copied from a past property failure) deleted — that re-admits a known-bad input.
