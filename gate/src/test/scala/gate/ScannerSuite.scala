package gate

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import munit.CatsEffectSuite

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The effectful tree-walk wired to the pure assembly: scan a temporary tree and confirm the
  * file-based snapshot parts come back. The extraction logic is covered hermetically in
  * [[SourceScanSuite]]; this pins the IO — listing `.scala` files, reading them, and relativizing
  * paths.
  */
class ScannerSuite extends CatsEffectSuite:

  private def writeTree(files: Map[String, String]): Resource[IO, Path] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("gate-scan")))(deleteRecursively)
      .evalTap { dir =>
        files.toList.traverse_ { case (rel, content) =>
          val target = dir.resolve(rel)
          IO.blocking(Files.createDirectories(target.getParent)).void *>
            IO.blocking(Files.writeString(target, content)).void
        }
      }

  private def deleteRecursively(dir: Path): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(Files.walk(dir)))
      .use(stream => IO.blocking(stream.iterator().asScala.toList))
      .flatMap(
        _.sortBy(_.toString).reverse.traverse_(p => IO.blocking(Files.deleteIfExists(p)).void)
      )

  test("scan reads a tree and assembles suppressions, skips, test files, and counts") {
    val tree = Map(
      "src/main/scala/A.scala" -> "// scalafix:off\nval a = 1\n",
      "src/test/scala/ASuite.scala" -> "test(\"a\".ignore) {}\ntest(\"b\") {}\n"
    )
    writeTree(tree).use(Scanner.scan).map { snap =>
      assertEquals(snap.suppressions, List(Suppression("src/main/scala/A.scala", "scalafix:off")))
      assertEquals(snap.tests.files, List("src/test/scala/ASuite.scala"))
      assertEquals(snap.tests.skips, Map("src/test/scala/ASuite.scala" -> 1))
      assertEquals(snap.tests.testCounts, Map("src/test/scala/ASuite.scala" -> 2))
      assertEquals(snap.findings, List.empty[Finding])
    }
  }
