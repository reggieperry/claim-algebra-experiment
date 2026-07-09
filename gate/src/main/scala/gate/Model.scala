package gate

/** The pure data model of the Scala differential anti-weakening gate: the per-`(file, code)`
  * finding the scanner produces, the snapshot of one tree, and the verdict items a diff reports.
  * The diff and verdict logic live in [[Diff]]; the scanner, git baseline capture, and runner wrap
  * this pure core in later slices. Ported from a Go original: the `(File, Code)` finding model and
  * the closed Check/Kind/Verdict vocabulary transfer unchanged; only the scanner and runner are
  * Scala-specific.
  */

/** One scanner diagnostic — a scalafix or wartremover finding, or a compiler warning. Its
  * DIFFERENTIAL identity is the `(file, code)` pair; `line` and `message` are carried for
  * gate-as-feedback (causal-first block items) and are NOT part of that identity.
  */
final case class Finding(file: String, code: String, line: Int, message: String)

/** One linter-suppression directive at a `(file, directive)` site. `directive` keeps the TARGETED
  * form (`scalafix:off DisableSyntax`, `@SuppressWarnings(Wart.Null)`) and the BLANKET form
  * (`scalafix:off`, `@nowarn`) as distinct keys, so broadening a targeted suppression to a blanket
  * one registers as a new suppression. The scanner produces these; [[Diff]]'s Check B only diffs
  * counts, so this model is language-agnostic.
  */
final case class Suppression(file: String, directive: String)

/** The test-discipline data captured from a tree, for Check D and Check E. The skip/test-count maps
  * are keyed by a repo-relative test-file path; coverage is keyed by a package directory path (so a
  * package rename derives from file renames). Language-agnostic — the scanner fills it from munit
  * skip markers, the test-file set, and scoverage reports.
  *
  * @param skips
  *   per-test-file count of skip markers (`.ignore`, `assume(false, …)`, `munitIgnore`)
  * @param testCounts
  *   per-test-file count of `test(...)`/`property(...)` registrations — an ADVISORY signal only
  *   (gameable, since a table of cases hides behind one registration)
  * @param files
  *   the test files present, for deletion detection
  * @param coverage
  *   package directory path -> unit-test statement-coverage percent
  * @param integrationCoverage
  *   package directory path -> integration-test statement-coverage percent
  */
final case class TestSnapshot(
    skips: Map[String, Int],
    testCounts: Map[String, Int],
    files: List[String],
    coverage: Map[String, Double],
    integrationCoverage: Map[String, Double]
)

object TestSnapshot:
  /** The empty snapshot — every map empty, no files — so a tree with no test data reads cleanly. */
  val empty: TestSnapshot = TestSnapshot(Map.empty, Map.empty, List.empty, Map.empty, Map.empty)

/** Tunes the diff for a repo: package directory paths whose UNIT coverage is intentionally omitted
  * from the Check D coverage-delta block (because integration tests exercise them instead, verified
  * by Check E), and the bootstrap-only floor a newly-omitted package must clear.
  *
  * @param bootstrapIntegrationFloor
  *   a one-time anti-token-test anchor, NOT a steady-state target: it is checked only when a
  *   package has no baseline integration figure (a new omit entry), to stop a token test from
  *   minting a permanent near-zero baseline. Once a baseline exists, no-erosion binds and this
  *   never applies again.
  */
final case class Config(omitCoverage: List[String], bootstrapIntegrationFloor: Double):
  /** Whether `pkg` is covered by an omit entry — an exact path or a trailing path segment. */
  def omits(pkg: String): Boolean = omitCoverage.exists(Config.matchPkg(pkg, _))

object Config:
  /** No omit-list, no floor — the default, under which Check D checks every package and Check E is
    * a no-op.
    */
  val empty: Config = Config(List.empty, 0.0)

  /** Match an omit entry against a package path as an exact path or a trailing path segment.
    * Coverage keys are repo-relative (`src/main/scala/claimalgebra/pipeline`), so a short omit
    * entry like `pipeline` or `claimalgebra/pipeline` still matches via the trailing-segment test.
    */
  def matchPkg(pkg: String, omit: String): Boolean = pkg == omit || pkg.endsWith("/" + omit)

/** The per-package statement-coverage drop (percentage points) tolerated before Check D or E
  * blocks.
  */
val coverageEpsilon: Double = 0.5

/** Everything the gate captures from one tree — the baseline at the merge-base, or the branch tip:
  * the lint/finding diagnostics (Check A), the suppression directives (Check B), and the
  * test-discipline data (Checks D and E). The latter two default to empty so a findings-only call
  * (and the Check A tests) need not supply them.
  */
final case class Snapshot(
    findings: List[Finding],
    suppressions: List[Suppression] = List.empty,
    tests: TestSnapshot = TestSnapshot.empty
)

/** The aggregate verdict of a diff. `Pass` — no blocks, no advisories; `Advisory` — no blocks but
  * at least one advisory (a relocation, surfaced for review); `Fail` — at least one block.
  */
enum Verdict:
  case Pass, Advisory, Fail

/** Which gate check produced a verdict item. `Build` is the compile precondition (a later slice);
  * the letters match the ported taxonomy — A lint/finding identity, B new suppressions, D test
  * discipline, E omit-list integrity.
  */
enum Check:
  case A, B, D, E, Build

/** The specific weakening (or signal) a verdict item reports — the closed set, grown one slice at a
  * time as each check lands. Check A: NewError / RelocatedError. Check B: NewSuppression. Check D:
  * TestFileDeletion / NewSkipMarkers / CoverageDrop / TestCountDrop. Check E:
  * OmitWithoutIntegration / IntegrationCoverageDrop / OmitBelowBootstrapFloor. (CompileError, the
  * build precondition, lands with the runner.)
  */
enum Kind:
  case NewError, RelocatedError
  case NewSuppression
  case TestFileDeletion, NewSkipMarkers, CoverageDrop, TestCountDrop
  case OmitWithoutIntegration, IntegrationCoverageDrop, OmitBelowBootstrapFloor
  case CompileError // the build precondition: the tree does not compile (fail-closed)

/** A hard verdict item: a weakening the branch introduced that FAILS the gate. Carries `line` and
  * `message` for causal-first feedback (the requirement the Go gate's `(file, code)` summary
  * missed).
  */
final case class Block(
    check: Check,
    kind: Kind,
    file: String,
    code: String,
    line: Int,
    message: String
)

/** A soft verdict item — surfaced for review, not blocking (e.g. a finding that moved files, which
  * a count cannot tell from a coincidental fix-plus-new).
  */
final case class Advisory(check: Check, kind: Kind, file: String, code: String, message: String)

/** A diff's verdict and the items behind it — the gate's output contract. Slices are always present
  * (empty, never absent), mirroring the Go report's stable wire shape for a consuming node.
  */
final case class Report(
    verdict: Verdict,
    summary: String,
    blocks: List[Block],
    advisories: List[Advisory]
)
