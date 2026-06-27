package gate

import cats.effect.{IO, Resource}

import java.nio.file.Path

/** The git operations the runner needs, as injectable functions so the orchestration is testable
  * with in-memory stubs (no real repo). [[GitOps.live]] wires the [[Git]] implementations.
  */
final case class GitOps(
    mergeBase: (Path, String) => IO[Option[String]],
    renames: (Path, String, String) => IO[Map[String, String]],
    worktree: (Path, String) => Resource[IO, Path]
)

object GitOps:
  val live: GitOps = GitOps(Git.mergeBase, Git.renames, Git.worktree)

/** The end-to-end orchestration: the fail-closed build precondition, then a scan of the branch and
  * of the merge-base baseline (in a scratch worktree), diffed into a [[Report]]. The compile, scan,
  * and git steps are injected so this logic is unit-testable; [[Main]] wires the live versions.
  */
object Runner:

  /** Run the gate over `repo` against `target`. If the tree does not compile, block immediately on
    * the precondition (a red build would silence the linters, so "no new findings" must not be
    * satisfiable by breaking it). With no merge-base, diff against an empty baseline — the gate
    * degrades to an absolute gate, so it works from the first commit or without a remote.
    */
  def run(
      repo: Path,
      target: String,
      config: Config,
      compile: Path => IO[Boolean],
      scan: Path => IO[Snapshot],
      git: GitOps
  ): IO[Report] =
    compile(repo).flatMap {
      case false => IO.pure(compileFailed)
      case true =>
        git.mergeBase(repo, target).flatMap {
          case None =>
            scan(repo).map(branch => Diff(Snapshot(List.empty), branch, Map.empty, config))
          case Some(base) =>
            for
              branch <- scan(repo)
              renames <- git.renames(repo, base, "HEAD")
              baseline <- git.worktree(repo, base).use(scan)
            yield Diff(baseline, branch, renames, config)
        }
    }

  private val compileFailed: Report =
    val block = Block(Check.Build, Kind.CompileError, "", "", 0, "the tree does not compile")
    Report(Verdict.Fail, "fail: build precondition (does not compile)", List(block), List.empty)
