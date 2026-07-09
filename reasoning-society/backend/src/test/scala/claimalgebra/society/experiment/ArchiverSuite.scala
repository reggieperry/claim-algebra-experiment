package claimalgebra.society
package experiment

import cats.effect.IO
import munit.CatsEffectSuite

import java.nio.file.{Files, Path}

/** The archiver persists the FULL event log of every game, not just the record — outcome 3 is
  * adjudicated from the log, so a single instance must survive the run. This pins that a game
  * writes a file under `dir/arm/` carrying its adjudicated header and the rendered log.
  */
class ArchiverSuite extends CatsEffectSuite with SocietyFixtures:

  private val cell = SweepCell(1.0, "perfect", "dev", k = 1)

  private def tmpDir: IO[Path] = IO.blocking(Files.createTempDirectory("archiver-test"))

  test("archive writes one log file per game, with the adjudicated header and the rendered log") {
    val apple = mkAnswer("apple")
    val log: Vector[Event] = Vector(
      Event.TargetRegistered(1, 0L, apple, 7L),
      Event.GateSign(2, 0L, apple)
    )
    val rec =
      GameRecord(
        cell,
        apple,
        Some(apple),
        PrimaryOutcome.SignCorrect,
        Some(SignPath.BackerQuorum),
        7L
      )
    for
      dir <- tmpDir
      armDir <- Archiver.archive(dir, "seam-open", List((rec, log)))
      files <- IO.blocking(Files.list(armDir).toArray.toList.map(_.toString))
      content <- IO.blocking(Files.readString(armDir.resolve("dev-apple-7.log")))
    yield
      assertEquals(files.size, 1, clue(files))
      assert(content.contains("arm=seam-open"), clue(content))
      assert(content.contains("outcome=SignCorrect"), clue(content))
      assert(content.contains("signPath=BackerQuorum"), clue(content))
      assert(content.contains("SIGN \"apple\""), clue(content)) // the renderLog of the GateSign
  }
