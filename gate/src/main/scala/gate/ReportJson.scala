package gate

/** The gate's output contract: a [[Report]] rendered as JSON for a consuming chain node.
  * Hand-rolled over the closed report shape (no JSON dependency); the verdict is lowercase, the
  * check is its letter (`build` for the precondition), and the kind is snake_case — wire-compatible
  * with a Go original's report. Slices are always present as arrays, never absent.
  */
object ReportJson:

  /** Encode a report as a single-line JSON object. */
  def encode(report: Report): String =
    val blocks = report.blocks.map(encodeBlock).mkString("[", ",", "]")
    val advisories = report.advisories.map(encodeAdvisory).mkString("[", ",", "]")
    s"""{"verdict":${str(verdict(report.verdict))},"summary":${str(report.summary)},""" +
      s""""blocks":$blocks,"advisories":$advisories}"""

  private def encodeBlock(b: Block): String =
    s"""{"check":${str(check(b.check))},"kind":${str(kind(b.kind))},"file":${str(b.file)},""" +
      s""""code":${str(b.code)},"line":${b.line},"message":${str(b.message)}}"""

  private def encodeAdvisory(a: Advisory): String =
    s"""{"check":${str(check(a.check))},"kind":${str(kind(a.kind))},"file":${str(a.file)},""" +
      s""""code":${str(a.code)},"message":${str(a.message)}}"""

  private def verdict(v: Verdict): String = v match
    case Verdict.Pass => "pass"
    case Verdict.Advisory => "advisory"
    case Verdict.Fail => "fail"

  private def check(c: Check): String = c match
    case Check.A => "A"
    case Check.B => "B"
    case Check.D => "D"
    case Check.E => "E"
    case Check.Build => "build"

  private def kind(k: Kind): String = k match
    case Kind.NewError => "new_errors"
    case Kind.RelocatedError => "relocated_error"
    case Kind.NewSuppression => "new_suppression"
    case Kind.TestFileDeletion => "test_file_deletion"
    case Kind.NewSkipMarkers => "new_skip_markers"
    case Kind.CoverageDrop => "coverage_drop"
    case Kind.TestCountDrop => "test_count_drop"
    case Kind.OmitWithoutIntegration => "omit_without_integration"
    case Kind.IntegrationCoverageDrop => "integration_coverage_drop"
    case Kind.OmitBelowBootstrapFloor => "omit_below_bootstrap_floor"
    case Kind.CompileError => "compile_error"

  /** A JSON string literal with the mandatory escapes (`"`, `\`, and the C0 control characters). */
  def str(s: String): String =
    val sb = new StringBuilder("\"")
    s.foreach { c =>
      val escaped = c match
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case other if other < ' ' => f"\\u${other.toInt}%04x"
        case other => other.toString
      sb.append(escaped)
      ()
    }
    sb.append('"')
    sb.toString
