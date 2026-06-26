---
paths:
  - "**/*.scala"
  - "**/*.sc"
  - "build.sbt"
---

# Scala domain modeling with the type system

Encode the algebra's invariants in the type system so the compiler rejects an illegal value before it reaches a node — the four-corner Belnap value, `Claim[K, A]`, `Testimony` with its two provenance channels, and the corroboration combiner all carry their laws as types, not as runtime checks. Sources: the Scala 3 reference (enums, opaque type aliases, contextual abstractions / type classes), *Functional Programming in Scala* 2nd ed (ADTs, the `Either`-to-`Validated` derivation and error accumulation in chapter 4), *Scala with Cats* (the anatomy of a type class, type classes and variance), and *Programming in Scala* 5th ed (case classes and pattern matching). The substitutability discipline is from Liskov (`craft-abstraction.md`); the value-object and ubiquitous-language discipline is from Evans (`craft-domain-modeling.md`).

> See `craft-domain-modeling.md` for value objects, entities, and naming in the ubiquitous language; `craft-abstraction.md` for abstract data types and the substitution principle; `scala-style.md` for the brace and given-clarity defaults; and `scala-llm.md` for the structured-output type as the typed model boundary at the LLM call.

## The shape of the discipline: encode what's cheap, test the residue

"Make illegal states unrepresentable" is the aspiration, not the whole job. Under the Curry-Howard correspondence a type reads as a proposition and a value of that type as its proof, so the compiler discharges the invariants you manage to encode — though Scala's nontermination, casts, and exceptions mean a well-typed program *approximates* a proof rather than being one. Push each invariant into the type while it stays cheap and clear: an `enum`/ADT for a closed set of cases (below), an opaque type behind a validating smart constructor. Some invariants are too costly — or impossible — to state in Scala's type system, and that residue is where the work lives. Close it with tests (`scala-testing.md`): ScalaCheck properties and `discipline` `checkAll` prove the laws and rules a value must obey on valid input, and they pin how the smart constructor behaves when something tries to build an illegal value. The type carries what it cheaply can; property and law tests carry the rest, the negative space included.

- **An opaque type without a validating smart constructor is just obfuscation.** The payoff of wrapping a primitive is the *constructor* that rejects or normalizes bad input. If every `String` is a valid `Lineage`, the opacity buys type-safety but not an invariant — pair the opaque type with the check, or do not open the scope at all.
- **Fail closed by default: reject the illegal input.** A validating constructor returns `Option`/`Either` (or maps the bad case to a safe bottom); it never throws and never admits the bad value. Normalize instead of reject only when the mapping is *total and lossless* — clamping a known-bounded number, trimming insignificant whitespace; when in doubt, reject (`scala-errors.md`).
- **Test both sides of every uncodified invariant.** Write the law or property on valid values *and* a test that the constructor refuses or normalizes the illegal ones. The negative space is half the contract; an untested constructor is an unproven invariant.
- **A type with no illegal state needs no constructor.** `Ev(pro, con)` is a plain `case class`: every pair of `Lev`s is a legal Belnap value, so there is nothing to reject. Do not add ceremony where the type already makes all states legal — the goal is the invariant, not the wrapper.

**Worked example — `Lev`.** The per-channel confidence is a strength in `[0, 1]` with `0` the bottom (no evidence). Scala will not cheaply make "a `Double` in `[0, 1]`" a type, so `Lev` is an `opaque type Lev = Double` whose only constructors are `bottom`, `top`, and a clamping `deg` that is fail-closed: `if !(p > 0.0) then bottom` catches `NaN` and every non-positive input, and `p >= 1.0` clamps to the top. The tests then carry both halves — `checkAll` pins the `Order` and bounded-semilattice laws on generated valid degrees, and `LevSuite` pins that `deg(NaN)`, `deg(0.0)`, and `deg(-1.0)` all collapse to the bottom. The invariant the type cannot state is enforced by the constructor and proven by the test, which is exactly what stops a forged or non-finite confidence from clearing the acceptance gate.

## ADTs are enums; make illegal states unrepresentable

The central rule of this file: an invalid value should not type-check. A closed set of cases is an enum, and the compiler then checks every match for exhaustiveness — a missing corner is a compile error, not a `MatchError` in a node at trial time.

- **Model every closed alternative as a Scala 3 `enum`, not a hand-rolled sealed trait plus case classes.** An enum is the house ADT form: it desugars to a sealed hierarchy, gives you `ordinal`, `values`, `valueOf`, and `fromOrdinal` for free, and reads as the domain vocabulary. Reach for a bare `sealed trait` only when a case needs its own members the enum syntax can't express; default to `enum`.

```scala
// The four-corner Belnap value as a closed ADT. No fifth corner can exist.
enum Belnap:
  case True       // asserted, not denied
  case False      // denied, not asserted
  case Both       // asserted and denied — the glut
  case Neither    // neither — the gap

// Exhaustive by construction: drop a case and the compiler flags every match.
def isContradiction(b: Belnap): Boolean = b match
  case Belnap.Both                              => true
  case Belnap.True | Belnap.False | Belnap.Neither => false
```

- **Parameterize an enum case only when the case genuinely carries data**, with an explicit `extends` clause for the shared field. Keep the data minimal and the invariant inside the type.

```scala
// A node verdict is a closed set; one corner carries the reason it fired.
enum Verdict(val passing: Boolean):
  case Clean              extends Verdict(true)
  case Flagged(reason: String) extends Verdict(false)
  case Abstain            extends Verdict(false)
```

- **Generic, variant enums replace the old `sealed trait T[+A]` ADT.** `Claim[K, A]` and the corroboration result are parameterized enums. Declare variance on the type parameter (below) and let the compiler check it across every case.

## Variance: read the parameter's role, then annotate

A parameter that the type only *produces* is covariant (`+A`); one it only *consumes* is contravariant (`-A`); a type that does both — or that is mutable — is invariant. *Scala with Cats* is explicit that Cats keeps its type classes invariant for exactly this reason; a producer-only result type is the case where covariance pays off.

- **A result-shaped enum is covariant in its value and error parameters.** The `Validated` derivation in *Functional Programming in Scala* (ch. 4) lands on `enum Validated[+E, +A]` precisely because a `Validated` only ever yields its `E` or its `A` — it never accepts one as input. Our corroboration result follows that shape.

```scala
// Covariant in both: a Corroborated[Nothing, A] substitutes anywhere a
// Corroborated[E, A] is wanted, and an Invalid[E] flows up unchanged.
enum Corroborated[+E, +A]:
  case Valid(value: A)
  case Invalid(errors: cats.data.NonEmptyChain[E])
```

- **A consumer abstraction is contravariant.** A `Grader[-A]` that only reads an `A` to score it should be `-A`, so a `Grader[Claim[?, Any]]` substitutes where a `Grader[Claim[?, Specific]]` is wanted.
- **Make a type invariant when in doubt, and always when it both reads and writes its parameter, holds it in a mutable cell, or is a type class.** Invariance is the safe default; widen to `+`/`-` only when you have shown the parameter appears in one position only. Do not annotate a type-class trait's parameter — keep `Semigroup[A]`, `Eq[A]`, and the bilattice instances invariant, as Cats does.

## Wrap primitives in opaque types, behind a smart constructor

A bare `String` or `Double` carries no domain meaning and lets a node pass the wrong one. An opaque type is a distinct type at compile time that erases to its underlying representation at runtime — zero allocation, full checking. This is the Scala form of "make illegal states unrepresentable" at the scalar level.

- **Give every scalar with a domain meaning an opaque type, and make the only way to build it a validating constructor.** Keep the `opaque type` and its companion together so the alias is transparent only inside that scope; outside it, the underlying type is invisible and no caller can forge a value past the check.

```scala
object Grades:
  // Distinct from a raw Double everywhere outside this scope; erases to Double.
  opaque type Grade = Double

  object Grade:
    /** The only constructor: a grade is a corroboration weight in [0, 1]. */
    def from(d: Double): Either[String, Grade] =
      if d >= 0.0 && d <= 1.0 then Right(d)
      else Left(s"grade out of range: $d")

  extension (g: Grade)
    def value: Double = g
    def combineMin(other: Grade): Grade = math.min(g, other)
```

- **Add behavior through `extension` methods on the opaque type**, not by exposing the representation. The representation stays sealed inside the defining scope; callers see only the operations the domain allows.
- **Use opaque types for the identifiers and channel tags the experiment routes** — `FaultId`, `NodeId`, a provenance `Channel` label — so a fault key can't be passed where a node id is wanted. This is the value-object building block from `craft-domain-modeling.md` expressed in Scala.

## `Testimony`, the two provenance channels, and the evidential grade

`Testimony` and `Claim[K, A]` are the experiment's central types; model them so a malformed one cannot be constructed. The grade is the evidential pair `Ev(pro, con)`, and the two provenance channels are distinct fields, not one bag.

- **Model the evidential pair as a small immutable value object whose constructor enforces its invariant**, and the two provenance channels as two named, separately typed fields — never a single untyped map. A `case class` with a `private` constructor plus a validating `apply` keeps the for/against and the two channels coherent.

```scala
final case class Ev private (pro: Grade, con: Grade)
object Ev:
  def of(pro: Grade, con: Grade): Ev = new Ev(pro, con)  // both already validated

// Two provenance channels as distinct fields — making the pair a conceptual
// whole and forbidding "channel A's value under channel B's label".
final case class Provenance(forChannel: Channel, againstChannel: Channel)
```

- **Carry the claim's key as a phantom-style type parameter `K`** so two claims about different subjects don't unify, and the value type `A` as the payload. Let the enum or case class hold the `Ev` grade and the `Provenance` together, and never expose a setter — "change" means construct a new `Claim` (the immutable value-object rule).

## Corroboration accumulates errors — use `Validated`, never `Either`

This is a correctness rule, not a style preference. `Either` is a monad and *short-circuits* at the first `Left`; the corroboration combiner must surface *every* failing channel at once, which is the applicative `Validated`. *Functional Programming in Scala* derives `Validated` from `Either[List[E], A]` for exactly this reason: the difference is entirely in how two failures combine (concatenated via a `Semigroup`, not dropped).

- **Use `cats.data.Validated` for the corroboration combiner**, and combine with the applicative `mapN`, which accumulates every failing channel's error rather than dropping all but the first. Reserve `Either` for genuinely sequential, fail-fast steps where a later check depends on an earlier one's success. The house error container is `ValidatedNec[E, A]` — `Validated[NonEmptyChain[E], A]` — for the reason `scala-errors.md` sets out (constant-time accumulation, at-least-one-error in `Invalid`); use it here too rather than `ValidatedNel`.

```scala
import cats.data.ValidatedNec
import cats.syntax.all.*

def corroborate(
    a: ValidatedNec[Defect, Ev],
    b: ValidatedNec[Defect, Ev],
    c: ValidatedNec[Defect, Ev]
): ValidatedNec[Defect, Belnap] =
  // Applicative: every Invalid channel contributes its Defect; none is dropped.
  (a, b, c).mapN((x, y, z) => combineCorners(x, y, z))
```

- **Type the accumulated error as a domain `Defect` enum, not `String`** — a closed set of corroboration failures the grader can match on mechanically, in keeping with the determinism-first decision (no LLM judge on the headline number).
- **Keep the error collection non-empty.** An empty error list is an illegal `Invalid` state; the `NonEmptyChain` inside `ValidatedNec` makes that unrepresentable and removes the empty-case branch from every consumer.

## Behavior is a type class via `given`/`using`

The bilattice operations — the two lattice meets and joins, the negation twist, the corroboration `Semigroup` — are type-class instances, defined as *Scala with Cats* describes: a trait parameterized over the carrier, with `given` instances summoned through `using`.

- **Define a bilattice operation as a trait with the type parameter, and provide a single `given` instance for the carrier.** Summon it with `using` (or a context bound `[A: Bilattice]`), and reach for `summon[Bilattice[A]]` only when you need the instance as a value.

```scala
trait Bilattice[A]:
  extension (x: A)
    def meetT(y: A): A   // truth-order meet
    def joinT(y: A): A   // truth-order join
  def negate(x: A): A    // the for/against twist

given Bilattice[Belnap] with
  extension (x: Belnap)
    def meetT(y: Belnap): Belnap = ???
    def joinT(y: Belnap): Belnap = ???
  def negate(x: Belnap): Belnap = ???

def consensus[A: Bilattice](xs: List[A]): A = ???  // context bound, no named param
```

- **Derive a `given` from other `given`s for compound carriers** — a `Bilattice` over `Claim[K, A]` built from the underlying instances in scope — rather than hand-writing each. Conditional givens are the house mechanism for lifting an operation over a wrapper.
- **Keep the algebra's type classes pure and lawful, and keep them invariant.** A `given` instance is a fact about a type; the laws (associativity, the bilattice absorption laws) are the contract a substitute must honor, per the substitution principle in `craft-abstraction.md`. State the laws as ScalaCheck properties (see `scala-testing.md`) — the type cannot express them, so the property suite does.
