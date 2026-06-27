package gate

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite

import java.nio.file.{Path, Paths}

/** The runner orchestration, with in-memory stubs for compile / scan / git — so the control flow
  * (the fail-closed precondition, the baseline-vs-branch diff, the empty-baseline degradation) is
  * tested without a real toolchain or repo.
  */
class RunnerSuite extends CatsEffectSuite:

  private val repo = Paths.get("/repo")
  private val baselineTree = Paths.get("/baseline")

  private val compiles: Path => IO[Boolean] = _ => IO.pure(true)
  private val doesNotCompile: Path => IO[Boolean] = _ => IO.pure(false)

  // a git that fails loudly if any method is called — to prove the precondition short-circuits
  private val unusedGit: GitOps = GitOps(
    (_, _) => IO.raiseError(new RuntimeException("mergeBase must not be called")),
    (_, _, _) => IO.raiseError(new RuntimeException("renames must not be called")),
    (_, _) => Resource.eval(IO.raiseError(new RuntimeException("worktree must not be called")))
  )

  private def gitWith(base: Option[String]): GitOps = GitOps(
    (_, _) => IO.pure(base),
    (_, _, _) => IO.pure(Map.empty),
    (_, _) => Resource.pure[IO, Path](baselineTree)
  )

  test("a non-compiling tree blocks on the precondition and runs no checks") {
    Runner
      .run(
        repo,
        "origin/main",
        Config.empty,
        doesNotCompile,
        _ => IO.pure(Snapshot(Nil)),
        unusedGit
      )
      .map { r =>
        assertEquals(r.verdict, Verdict.Fail)
        assertEquals(r.blocks.map(b => (b.check, b.kind)), List((Check.Build, Kind.CompileError)))
      }
  }

  test("with a baseline, a new suppression in the branch blocks") {
    val branch = Snapshot(Nil, List(Suppression("a.scala", "@nowarn")))
    val baseline = Snapshot(Nil)
    val scan: Path => IO[Snapshot] = p => IO.pure(if p == baselineTree then baseline else branch)
    Runner.run(repo, "origin/main", Config.empty, compiles, scan, gitWith(Some("base-sha"))).map {
      r =>
        assertEquals(r.verdict, Verdict.Fail)
        assertEquals(r.blocks.map(_.kind), List(Kind.NewSuppression))
    }
  }

  test("an unchanged branch against its baseline passes") {
    val same = Snapshot(Nil, List(Suppression("a.scala", "@nowarn")))
    Runner
      .run(repo, "origin/main", Config.empty, compiles, _ => IO.pure(same), gitWith(Some("base")))
      .map { r =>
        assertEquals(r.verdict, Verdict.Pass)
      }
  }

  test("with no merge-base the gate degrades to an absolute gate over the branch") {
    val branch = Snapshot(List(Finding("a.scala", "Wart.Null", 1, "m")))
    Runner
      .run(repo, "origin/main", Config.empty, compiles, _ => IO.pure(branch), gitWith(None))
      .map { r =>
        assertEquals(r.verdict, Verdict.Fail)
        assertEquals(r.blocks.map(_.kind), List(Kind.NewError))
      }
  }
