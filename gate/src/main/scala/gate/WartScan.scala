package gate

import cats.effect.IO

import java.nio.file.{Path, Paths}
import scala.util.matching.Regex

/** The wartremover findings source — a SECOND Check A source alongside scalafix (the finding's code
  * is the bare wart name, e.g. `OptionPartial`, never colliding with scalafix's dotted
  * `DisableSyntax.*`). wartremover emits a Scala 3 diagnostic whose location is in a `-- Warning:`
  * header and whose wart is in a later `[wartremover:<Name>] <message>` line, so the pure [[parse]]
  * is a stateful line-walk correlating the two; the live adapter runs a dedicated wart-scan sbt
  * invocation (warts as warnings, -Werror off — see build.sbt) so findings enumerate.
  */
object WartScan:

  private val header: Regex = """--\s+(?:Warning|Error):\s+(.+?):(\d+):(\d+)""".r
  private val wart: Regex = """\[wartremover:(\w+)\]\s*(.*)""".r

  /** Parse wart-scan output into findings, correlating each `-- Warning:` header's `(file, line)`
    * with the next `[wartremover:Name]` line; a header followed by a non-wart message yields
    * nothing.
    */
  def parse(output: String): List[Finding] =
    val (findings, _) =
      output.linesIterator.foldLeft((List.empty[Finding], Option.empty[(String, Int)])) {
        case ((acc, lastLoc), line) =>
          header.findFirstMatchIn(line) match
            case Some(h) => (acc, Some((h.group(1), h.group(2).toInt)))
            case None =>
              wart.findFirstMatchIn(line) match
                case Some(w) =>
                  lastLoc match
                    case Some((file, ln)) =>
                      (acc :+ Finding(file, w.group(1), ln, w.group(2).trim), None)
                    case None => (acc, lastLoc)
                case None => (acc, lastLoc)
      }
    findings

  /** Run the wart scan over the tree at `repo` and return its findings, repo-relative. The
    * dedicated `-Dgate.wartScan=true` invocation turns warts on as warnings and drops -Werror
    * (build.sbt), and `clean` is required so a stale Zinc no-op compile does not suppress the
    * diagnostics. FAIL-CLOSED: warts are warnings here, so a non-zero exit is a real COMPILE error,
    * not findings — raise (operational, exit 2) rather than read truncated warts that would
    * undercount Check A and fail open. (Unlike scalafix `--check`, whose non-zero exit just means
    * findings exist.)
    */
  def findings(repo: Path): IO[List[Finding]] =
    Proc.run(Seq("sbt", "-batch", "-Dgate.wartScan=true", "clean", "Test/compile"), repo).flatMap {
      r =>
        if r.exitCode != 0 then
          IO.raiseError(
            new RuntimeException(s"wart scan compile failed (sbt exit ${r.exitCode}) — fail-closed")
          )
        else
          IO.pure(
            parse(s"${r.stdout}\n${r.stderr}").map(f => f.copy(file = relativize(repo, f.file)))
          )
    }

  private def relativize(repo: Path, file: String): String =
    val p = Paths.get(file)
    (if p.isAbsolute then repo.relativize(p).toString else file).replace('\\', '/')
