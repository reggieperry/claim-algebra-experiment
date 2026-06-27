package gate

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import munit.CatsEffectSuite

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The git seam: `parseRenames` is pure; merge-base and the scratch worktree are exercised against
  * a real temporary repository, so the Proc + git wiring is proven end-to-end.
  */
class GitSuite extends CatsEffectSuite:

  test("parseRenames keeps only rename rows") {
    val out = "M\tsrc/A.scala\nR100\told/B.scala\tnew/B.scala\nA\tsrc/C.scala\n"
    assertEquals(Git.parseRenames(out), Map("old/B.scala" -> "new/B.scala"))
  }

  test("mergeBase finds the common ancestor and a worktree checks out the baseline tree") {
    tempRepo.use { repo =>
      for
        _ <- run(repo, "init", "-q", "-b", "main")
        _ <- run(repo, "config", "user.email", "t@example.com")
        _ <- run(repo, "config", "user.name", "Test")
        _ <- write(repo, "a.txt", "A")
        _ <- run(repo, "add", "-A")
        _ <- run(repo, "commit", "-qm", "c1")
        base <- run(repo, "rev-parse", "HEAD").map(_.stdout.trim)
        _ <- run(repo, "branch", "feature")
        _ <- write(repo, "b.txt", "B") // a second commit on main, after the branch point
        _ <- run(repo, "add", "-A")
        _ <- run(repo, "commit", "-qm", "c2")
        _ <- run(repo, "checkout", "-q", "feature")
        _ <- write(repo, "c.txt", "C")
        _ <- run(repo, "add", "-A")
        _ <- run(repo, "commit", "-qm", "c3")
        mb <- Git.mergeBase(repo, "main")
        baselineHasB <- Git
          .worktree(repo, base)
          .use(t => IO.blocking(Files.exists(t.resolve("b.txt"))))
      yield
        assertEquals(mb, Some(base))
        assert(!baselineHasB, "the baseline worktree at c1 must not contain b.txt (added in c2)")
    }
  }

  private def run(repo: Path, args: String*): IO[ProcResult] =
    Proc.run("git" +: args, repo).flatTap { r =>
      IO.raiseUnless(r.exitCode == 0)(
        new RuntimeException(s"git ${args.mkString(" ")} failed (${r.exitCode}): ${r.stderr}")
      )
    }

  private def write(repo: Path, name: String, content: String): IO[Unit] =
    IO.blocking(Files.writeString(repo.resolve(name), content)).void

  private val tempRepo: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("gate-git")))(deleteRecursively)

  private def deleteRecursively(dir: Path): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(Files.walk(dir)))
      .use(stream => IO.blocking(stream.iterator().asScala.toList))
      .flatMap(
        _.sortBy(_.toString).reverse.traverse_(p => IO.blocking(Files.deleteIfExists(p)).void)
      )
