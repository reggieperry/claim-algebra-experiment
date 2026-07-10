package claimalgebra.society
package experiment

import cats.effect.{IO, IOApp}
import cats.syntax.all.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

/** Distill the gitignored per-game archives under `results/<archive>/<arm>/<game>.log` into the
  * committed receipts under `docs/reasoning-society/receipts/<label>.digest`. Run once, by hand, on
  * the machine that holds the archives:
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunDistillReceipts"}}}
  *
  * This is the *provenance* half of the verify-results check: a skeptic who holds the archives (or
  * reproduces a run) re-runs this and diffs the emitted receipt against the committed one. The
  * config stamp is read from the archive directory name (`…-<stamp>`), so the receipt records which
  * run it came from. Fail-closed: any header that does not parse aborts the whole distillation
  * rather than emitting a partial receipt. It reads only game-header lines, never the transcript
  * bodies, so the receipts carry no more than arm/cell/target/seed/outcome/signPath.
  */
object RunDistillReceipts extends IOApp.Simple:

  private val ResultsDir = "results"
  private val ReceiptsDir = "docs/reasoning-society/receipts"

  def run: IO[Unit] =
    for
      root <- repoRoot
      results = root.resolve(ResultsDir)
      present <- IO.blocking(Files.isDirectory(results))
      _ <-
        if !present then
          IO.println(
            s"no $ResultsDir/ directory under $root — nothing to distill (archives are gitignored)"
          )
        else distillAll(root, results)
    yield ()

  private def distillAll(root: Path, results: Path): IO[Unit] =
    for
      archives <- childDirs(results)
      receiptsDir <- IO.blocking(Files.createDirectories(root.resolve(ReceiptsDir)))
      _ <- IO.println(s"distilling ${archives.size} archive(s) -> $receiptsDir")
      _ <- archives.sortBy(_.getFileName.toString).traverse_(distillOne(receiptsDir, _))
    yield ()

  private def distillOne(receiptsDir: Path, archive: Path): IO[Unit] =
    val base = archive.getFileName.toString
    val stamp = base.split("-").lastOption.getOrElse(base)
    val label = base.stripSuffix(s"-$stamp")
    for
      logs <- logFiles(archive)
      headers <- logs.traverse(firstLine)
      rows <- headers.zip(logs).traverse { case (line, path) =>
        IO.fromEither(
          line
            .toRight(s"empty archive log (no header line): $path")
            .flatMap(Receipts.parseArchiveHeader)
            .leftMap(msg => new IllegalStateException(msg))
        )
      }
      content = Receipts.renderDigest(label, stamp, rows)
      target = receiptsDir.resolve(s"$label.digest")
      _ <- IO.blocking(Files.write(target, content.getBytes(StandardCharsets.UTF_8)))
      _ <- IO.println(
        s"  $label  stamp=$stamp  games=${rows.size}  sha256=${Receipts.sha256Of(rows)}"
      )
    yield ()

  private def firstLine(p: Path): IO[Option[String]] =
    IO.blocking(Files.readAllLines(p, StandardCharsets.UTF_8).asScala.headOption)

  private def logFiles(dir: Path): IO[List[Path]] =
    IO.blocking {
      val stream = Files.walk(dir)
      try
        stream
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".log"))
          .toList
      finally stream.close()
    }

  private def childDirs(dir: Path): IO[List[Path]] =
    IO.blocking {
      val stream = Files.list(dir)
      try stream.iterator().asScala.filter(Files.isDirectory(_)).toList
      finally stream.close()
    }

  private def repoRoot: IO[Path] =
    IO.blocking {
      val start = Paths.get("").toAbsolutePath
      ancestors(start)
        .find(p => Files.exists(p.resolve("build.sbt")))
        .getOrElse(start)
    }

  private def ancestors(p: Path): LazyList[Path] =
    p #:: Option(p.getParent).map(ancestors).getOrElse(LazyList.empty)
