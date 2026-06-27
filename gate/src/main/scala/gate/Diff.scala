package gate

/** The pure diff engine: compare a baseline snapshot against the branch tip and classify the
  * weakening the branch introduced. Differential, not an absolute ceiling — it blocks only the
  * deltas versus the merge-base, so against an empty baseline it degrades to an absolute gate and
  * works from the first commit. Slice 1 implements Check A (finding identity); B/D/E land in later
  * slices.
  */
object Diff:

  /** Compare `baseline` against `branch`, translating baseline paths through `renames` so a
    * relocated finding matches its baseline rather than counting as new. Returns the aggregate
    * [[Report]].
    */
  def apply(baseline: Snapshot, branch: Snapshot, renames: Map[String, String]): Report =
    val (blocks, advisories) = checkA(baseline.findings, branch.findings, renames)
    val v = verdict(blocks, advisories)
    Report(v, summarize(v, blocks, advisories), blocks, advisories)

  /** The differential identity of a finding — a `(file, code)` pair. */
  final private case class FileCode(file: String, code: String)

  private def translate(renames: Map[String, String], path: String): String =
    renames.getOrElse(path, path)

  /** Check A — finding-identity weakening. A new `(file, code)` instance is a hard [[Block]] when
    * the GLOBAL count for that code rose, and a soft [[Advisory]] when an increase in one file is
    * matched by a decrease elsewhere (a relocation a count cannot tell from a coincidental
    * fix-plus-new). Accounting is by INSTANCE, not by distinct key, so two new findings at one site
    * both register.
    *
    * Baseline paths are translated through `renames` (so a renamed file's findings line up with the
    * branch); branch paths are already in branch space and pass through unchanged.
    */
  private def checkA(
      baseline: List[Finding],
      branch: List[Finding],
      renames: Map[String, String]
  ): (List[Block], List[Advisory]) =
    val basePerFile: Map[FileCode, Int] =
      baseline
        .groupBy(f => FileCode(translate(renames, f.file), f.code))
        .view
        .mapValues(_.size)
        .toMap
    val basePerCode: Map[String, Int] =
      baseline.groupBy(_.code).view.mapValues(_.size).toMap
    val branchByKey: Map[FileCode, List[Finding]] =
      branch.groupBy(f => FileCode(f.file, f.code))
    val branchPerCode: Map[String, Int] =
      branch.groupBy(_.code).view.mapValues(_.size).toMap

    // Per code the surplus = max(0, net increase) instances are provably new and block; any further
    // new instances are matched 1:1 by decreases elsewhere and downgrade to a relocation advisory.
    val initialSurplus: Map[String, Int] =
      branchPerCode.flatMap { case (code, n) =>
        val net = n - basePerCode.getOrElse(code, 0)
        Option.when(net > 0)(code -> net)
      }

    // Walk the increased (file, code) keys in a stable order; within each, the instances beyond the
    // baseline's count, in line order — emitting one item per new instance, surplus first.
    val increased: List[FileCode] =
      branchByKey.iterator
        .collect { case (k, fs) if fs.sizeIs > basePerFile.getOrElse(k, 0) => k }
        .toList
        .sortBy(k => (k.file, k.code))

    val (blocks, advisories, _) =
      increased.foldLeft((List.empty[Block], List.empty[Advisory], initialSurplus)) {
        case (acc, k) =>
          val newInstances = branchByKey(k).sortBy(_.line).drop(basePerFile.getOrElse(k, 0))
          newInstances.foldLeft(acc) { case ((bs, as, surplus), f) =>
            if surplus.getOrElse(k.code, 0) > 0 then
              val block = Block(Check.A, Kind.NewError, k.file, k.code, f.line, f.message)
              (bs :+ block, as, surplus.updated(k.code, surplus(k.code) - 1))
            else
              val advisory = Advisory(Check.A, Kind.RelocatedError, k.file, k.code, f.message)
              (bs, as :+ advisory, surplus)
          }
      }
    (blocks, advisories)

  /** Reduce a diff's blocks and advisories to a single verdict — any block fails, else any advisory
    * is advisory, else pass.
    */
  private def verdict(blocks: List[Block], advisories: List[Advisory]): Verdict =
    if blocks.nonEmpty then Verdict.Fail
    else if advisories.nonEmpty then Verdict.Advisory
    else Verdict.Pass

  /** A one-line human-readable summary of the verdict. */
  private def summarize(v: Verdict, blocks: List[Block], advisories: List[Advisory]): String =
    if blocks.isEmpty && advisories.isEmpty then "pass"
    else s"${render(v)}: ${blocks.size} block(s), ${advisories.size} advisory(ies)"

  private def render(v: Verdict): String = v match
    case Verdict.Pass => "pass"
    case Verdict.Advisory => "advisory"
    case Verdict.Fail => "fail"
