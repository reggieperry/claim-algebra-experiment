package gate

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*

import java.nio.file.{Path, Paths}

/** The gate CLI. Runs the differential gate over the current repository against a target ref
  * (default `origin/main`), prints the [[Report]] as JSON, and exits 0 (pass/advisory), 1 (fail),
  * or 2 (operational — a toolchain or IO failure).
  *
  * Deployment is a STANDALONE binary (an sbt-assembly fat jar named by function — a later task),
  * because the live precondition and findings scan shell out to `sbt`; launching it via `sbt
  * gate/run` would nest sbt inside sbt and contend on the build lock. Built as a binary it has no
  * such conflict.
  *
  * The live scan layers the scalafix findings (Check A) onto the source scan (Checks B and D's file
  * signals). Coverage (scoverage) and wartremover are not yet wired — their snapshot fields stay
  * empty, so Check D's coverage block and Check E are inert until those scanners land; the config
  * omit-list defaults to empty.
  */
object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val repo = Paths.get("").toAbsolutePath.normalize
    val target = args.headOption.getOrElse("origin/main")
    Runner
      .run(repo, target, Config.empty, liveCompile, liveScan, GitOps.live)
      .flatMap(report => IO.println(ReportJson.encode(report)).as(exitCode(report.verdict)))
      .handleErrorWith(operational)

  /** The fail-closed build precondition: a green `sbt compile` over the tree. */
  private def liveCompile(tree: Path): IO[Boolean] =
    Proc.run(Seq("sbt", "-batch", "compile"), tree).map(_.exitCode == 0)

  /** The source scan (suppressions, skips, the test-file set, counts) with scalafix findings
    * layered on.
    */
  private def liveScan(tree: Path): IO[Snapshot] =
    (Scanner.scan(tree), ScalafixScan.findings(tree)).mapN((snap, findings) =>
      snap.copy(findings = findings)
    )

  private def exitCode(v: Verdict): ExitCode = v match
    case Verdict.Fail => ExitCode.Error
    case Verdict.Pass | Verdict.Advisory => ExitCode.Success

  /** An operational failure (toolchain/IO) is exit 2, distinct from a substantive fail — and is
    * itself reported as JSON so a consuming node parses one shape.
    */
  private def operational(t: Throwable): IO[ExitCode] =
    val message = Option(t.getMessage).getOrElse(t.toString)
    val json =
      s"""{"verdict":"operational","summary":${ReportJson.str(
          message
        )},"blocks":[],"advisories":[]}"""
    IO.println(json).as(ExitCode(2))
