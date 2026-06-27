package gate

import cats.effect.{IO, Resource}
import cats.syntax.all.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The effectful source scanner: walk a tree, read its `.scala` files, and assemble the file-based
  * parts of a [[Snapshot]] (suppressions, skip markers, the test-file set, test counts) via the
  * pure [[SourceScan]]. Findings (scalafix / wartremover) and coverage (scoverage) are tool-output
  * and are filled by a later scanner; this one is hermetic — it reads files, runs no toolchain.
  *
  * Paths in the snapshot are repo-relative to `root` and forward-slashed, so the diff's path keys
  * and the rename map line up across platforms.
  */
object Scanner:

  /** Scan the tree rooted at `root` into the file-based parts of a [[Snapshot]]. */
  def scan(root: Path): IO[Snapshot] =
    scalaFiles(root).flatMap(_.traverse(readEntry(root, _))).map(SourceScan.assemble)

  /** The regular `.scala` files under `root`, the walk stream released as a `Resource`. */
  private def scalaFiles(root: Path): IO[List[Path]] =
    Resource.fromAutoCloseable(IO.blocking(Files.walk(root))).use { stream =>
      IO.blocking {
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
          .toList
      }
    }

  private def readEntry(root: Path, p: Path): IO[(String, String)] =
    IO.blocking(Files.readString(p)).map(content => (relativize(root, p), content))

  private def relativize(root: Path, p: Path): String =
    root.relativize(p).toString.replace('\\', '/')
