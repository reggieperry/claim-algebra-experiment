package claimalgebra.society
package experiment

import cats.effect.IO

import java.nio.file.{Files, Path}

/** Persists a run arm to disk: one text file per game holding the adjudicated header plus the FULL
  * event log (fallible-oracle-composed-cell-experiment §Protocol item 3). The full log — not just
  * the `GameRecord` — is the substrate outcome 3 is adjudicated from, and at a perfect oracle a
  * single fail-open instance is an existence proof, so the log must survive the run. The directory
  * is harness-controlled (never model- or user-derived), so it is a plain blocking write on the
  * blocking pool (scala-concurrency); the game filename is sanitized to an inert charset
  * defensively.
  */
object Archiver:

  /** Write every `(record, log)` under `dir/arm/`, one `<cell>-<target>-<seed>.log` per game.
    * Returns the arm directory it wrote to.
    */
  def archive(dir: Path, arm: String, entries: List[(GameRecord, Vector[Event])]): IO[Path] =
    IO.blocking {
      val safeArm = sanitize(arm)
      val armDir = dir.resolve(safeArm)
      Files.createDirectories(armDir)
      entries.foreach { (rec, log) =>
        val name = sanitize(s"${rec.cell.difficulty}-${rec.trueTarget.value}-${rec.seed}") + ".log"
        val header =
          s"# arm=$arm cell=${rec.cell.difficulty} target=${rec.trueTarget.value} " +
            s"seed=${rec.seed} outcome=${rec.outcome} " +
            s"signed=${rec.signed.map(_.value).getOrElse("-")} " +
            s"signPath=${rec.signPath.map(_.toString).getOrElse("-")}\n"
        Files.write(armDir.resolve(name), (header + OracleSweep.renderLog(log)).getBytes("UTF-8"))
      }
      armDir
    }

  private def sanitize(raw: String): String = raw.replaceAll("[^A-Za-z0-9._-]", "_")
