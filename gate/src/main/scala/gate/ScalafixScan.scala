package gate

import cats.effect.IO

import java.nio.file.{Path, Paths}
import scala.util.matching.Regex

/** The scalafix findings source for Check A. scalafix has no machine-readable reporter, so the live
  * run parses its `--check` console diagnostics, which take the form
  * `[error] <path>:<line>:<col>: error: [<Rule>] <message>` (captured against the real tool). The
  * finding's code is the rule name (e.g. `DisableSyntax.var`). Parsing is pure and tested; the live
  * `sbt scalafixAll --check` invocation is the thin adapter. wartremover is a future second source
  * — the [[Finding]] model is tool-agnostic.
  */
object ScalafixScan:

  private val diagnostic: Regex =
    """(?m)^\[\w+\]\s+(.+?):(\d+):(\d+):\s+\w+:\s+\[([^\]]+)\]\s+(.*)$""".r

  /** Parse scalafix `--check` output into findings; the file is whatever path the tool printed
    * (absolute, in practice) and is relativized by [[findings]].
    */
  def parse(output: String): List[Finding] =
    diagnostic
      .findAllMatchIn(output)
      .map(m => Finding(m.group(1), m.group(4), m.group(2).toInt, m.group(5).trim))
      .toList

  /** Run scalafix in check mode over the tree at `repo` and return its findings, repo-relative. A
    * clean tree yields none; a tree with violations yields one finding per diagnostic.
    */
  def findings(repo: Path): IO[List[Finding]] =
    Proc.run(Seq("sbt", "-batch", "scalafixAll --check"), repo).map { r =>
      parse(s"${r.stdout}\n${r.stderr}").map(f => f.copy(file = relativize(repo, f.file)))
    }

  private def relativize(repo: Path, file: String): String =
    val p = Paths.get(file)
    (if p.isAbsolute then repo.relativize(p).toString else file).replace('\\', '/')
