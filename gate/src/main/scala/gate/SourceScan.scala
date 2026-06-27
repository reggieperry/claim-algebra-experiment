package gate

import scala.util.matching.Regex

/** The pure source-scan extractors: from a `.scala` file's contents, the suppression directives it
  * carries (Check B), and — for a test file — its skip-marker and test-registration counts (Check
  * D); plus the test-file predicate and the assembly of those into the file-based parts of a
  * [[Snapshot]].
  *
  * These are heuristic, line-oriented matchers (the Go scanner is likewise token-based): they can
  * over-report inside a string or comment, which only over-blocks a NEW occurrence — a fail-closed
  * direction the author resolves by not introducing it. Findings (scalafix / wartremover) and
  * coverage (scoverage) are tool-output, not source text, and are scanned separately. The effectful
  * tree-walk that reads files and calls [[assemble]] is [[Scanner]].
  */
object SourceScan:

  /** A test file: a `.scala` file under a `src/test/` path. */
  def isTestFile(relPath: String): Boolean =
    relPath.endsWith(".scala") && relPath.contains("src/test/")

  // A scalafix region/line directive: `// scalafix:off`/`:ok`, optionally followed by rule names.
  // The blanket form (no rules) and the targeted form (with rules) become distinct directive keys,
  // so broadening one to the other registers as a new suppression in Check B.
  private val scalafixDirective: Regex = """//\s*scalafix:(off|ok)\b[ \t]*([^\r\n]*)""".r
  // `@nowarn` or `@nowarn("...")` — the parenthesized (targeted) form is a distinct key.
  private val nowarn: Regex = """@nowarn\b(\([^)]*\))?""".r
  private val suppressWarnings: Regex = """@SuppressWarnings\b""".r

  /** The suppression directives in a file's contents, one per occurrence, as normalized strings. */
  def suppressions(content: String): List[String] =
    val scalafix = scalafixDirective.findAllMatchIn(content).map { m =>
      val kind = m.group(1)
      val rules = Option(m.group(2)).map(_.trim).getOrElse("")
      if rules.isEmpty then s"scalafix:$kind" else s"scalafix:$kind $rules"
    }
    val noWarns = nowarn.findAllMatchIn(content).map(_.matched.trim)
    val suppressed = suppressWarnings.findAllMatchIn(content).map(_ => "@SuppressWarnings")
    (scalafix ++ noWarns ++ suppressed).toList

  // munit skip markers: a `.ignore` test tag, a `munitIgnore` suite override, or an `assume(false…)`.
  private val skipMarkers: List[Regex] =
    List("""\.ignore\b""".r, """\bmunitIgnore\b""".r, """\bassume\(\s*false\b""".r)
  // munit registrations: `test(...)` and `property(...)`. Advisory only — gameable.
  private val registrations: List[Regex] = List("""\btest\(""".r, """\bproperty\(""".r)

  private def totalMatches(regexes: List[Regex], content: String): Int =
    regexes.map(_.findAllMatchIn(content).size).sum

  /** The number of skip markers in a test file's contents. */
  def skipCount(content: String): Int = totalMatches(skipMarkers, content)

  /** The number of test/property registrations in a test file's contents (an advisory signal). */
  def testCount(content: String): Int = totalMatches(registrations, content)

  /** Assemble the file-based parts of a [[Snapshot]] from `(repo-relative path, contents)` entries.
    * Suppressions are gathered from every file; skips, test-counts, and the file set from test
    * files only. Findings and coverage are left empty for the toolchain scanner to fill.
    */
  def assemble(entries: List[(String, String)]): Snapshot =
    val suppressionsFound =
      entries.flatMap { case (path, content) =>
        suppressions(content).map(directive => Suppression(path, directive))
      }
    val testEntries = entries.filter { case (path, _) => isTestFile(path) }
    val skips = testEntries.map { case (path, c) => path -> skipCount(c) }.filter(_._2 > 0).toMap
    val counts = testEntries.map { case (path, c) => path -> testCount(c) }.filter(_._2 > 0).toMap
    val files = testEntries.map { case (path, _) => path }.sorted
    Snapshot(
      findings = List.empty,
      suppressions = suppressionsFound,
      tests = TestSnapshot(
        skips = skips,
        testCounts = counts,
        files = files,
        coverage = Map.empty,
        integrationCoverage = Map.empty
      )
    )
