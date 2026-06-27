package gate

/** The pure data model of the Scala differential anti-weakening gate: the per-`(file, code)`
  * finding the scanner produces, the snapshot of one tree, and the verdict items a diff reports.
  * The diff and verdict logic live in [[Diff]]; the scanner, git baseline capture, and runner wrap
  * this pure core in later slices. Ported from the vendored Go gate
  * (`docs/reference/a-go-original/`): the `(File, Code)` finding model and the closed
  * Check/Kind/Verdict vocabulary transfer unchanged; only the scanner and runner are
  * Scala-specific.
  */

/** One scanner diagnostic — a scalafix or wartremover finding, or a compiler warning. Its
  * DIFFERENTIAL identity is the `(file, code)` pair; `line` and `message` are carried for
  * gate-as-feedback (causal-first block items) and are NOT part of that identity.
  */
final case class Finding(file: String, code: String, line: Int, message: String)

/** Everything the gate captures from one tree — the baseline at the merge-base, or the branch tip.
  * Slice 1 carries only the findings (Check A); suppressions and test-discipline data are added
  * with their checks in later slices.
  */
final case class Snapshot(findings: List[Finding])

/** The aggregate verdict of a diff. `Pass` — no blocks, no advisories; `Advisory` — no blocks but
  * at least one advisory (a relocation, surfaced for review); `Fail` — at least one block.
  */
enum Verdict:
  case Pass, Advisory, Fail

/** Which gate check produced a verdict item. `Build` is the compile precondition (a later slice);
  * the letters match the vendored taxonomy — A lint/finding identity, B new suppressions, D test
  * discipline, E omit-list integrity.
  */
enum Check:
  case A, B, D, E, Build

/** The specific weakening (or signal) a verdict item reports — the closed set, grown one slice at a
  * time as each check lands. Slice 1: the two Check-A kinds.
  */
enum Kind:
  case NewError, RelocatedError

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
  * (empty, never absent), mirroring the Go report's stable wire shape for a consuming chain node.
  */
final case class Report(
    verdict: Verdict,
    summary: String,
    blocks: List[Block],
    advisories: List[Advisory]
)
