package gate

import cats.effect.{IO, Resource}

import java.nio.file.{Files, Path}

/** The git seam the differential gate needs: the merge-base against a target ref (the baseline
  * commit), the file renames between two commits (so a relocated finding matches its baseline
  * rather than counting as new), and a detached scratch worktree at a commit (to scan the baseline
  * tree). All shell out through [[Proc]] with a fixed program name and validated refs.
  */
object Git:

  /** The merge-base of `HEAD` and `target` — the baseline commit. `None` when there is none (an
    * unrelated history, or `target` does not resolve), which the runner treats as an empty baseline
    * (the gate degrades to an absolute gate).
    */
  def mergeBase(repo: Path, target: String): IO[Option[String]] =
    Proc.run(Seq("git", "merge-base", "HEAD", target), repo).map { r =>
      Option.when(r.exitCode == 0)(r.stdout.trim).filter(_.nonEmpty)
    }

  /** The file renames from `base` to `head`, as a baseline-path -> branch-path map, parsed from
    * `git diff --name-status -M`. A non-zero exit yields no renames (fail-safe — unmatched
    * relocations then read as a fix plus a new finding, surfaced for review, never silently
    * dropped).
    */
  def renames(repo: Path, base: String, head: String): IO[Map[String, String]] =
    Proc.run(Seq("git", "diff", "--name-status", "-M", base, head), repo).map { r =>
      if r.exitCode == 0 then parseRenames(r.stdout) else Map.empty
    }

  /** Parse `git diff --name-status -M` output, keeping only rename rows (`R<score>\told\tnew`). */
  def parseRenames(output: String): Map[String, String] =
    output.linesIterator.flatMap { line =>
      val cols = line.split('\t')
      Option.when(cols.length == 3 && cols(0).startsWith("R"))(cols(1) -> cols(2))
    }.toMap

  /** A detached worktree at `sha`, as a `Resource` that adds it on acquire and removes it on
    * release (even on error or cancellation). The tree is scanned within `use`.
    */
  def worktree(repo: Path, sha: String): Resource[IO, Path] =
    Resource.make(
      for
        parent <- IO.blocking(Files.createTempDirectory("gate-baseline"))
        tree = parent.resolve("tree")
        added <- Proc.run(Seq("git", "worktree", "add", "--detach", tree.toString, sha), repo)
        _ <- IO.raiseUnless(added.exitCode == 0)(
          new RuntimeException(s"git worktree add failed (exit ${added.exitCode}): ${added.stderr}")
        )
      yield tree
    )(tree =>
      Proc.run(Seq("git", "worktree", "remove", "--force", tree.toString), repo).void *>
        IO.blocking(Files.deleteIfExists(tree.getParent)).void
    )
