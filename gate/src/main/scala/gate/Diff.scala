package gate

/** The pure diff engine: compare a baseline snapshot against the branch tip and classify the
  * weakening the branch introduced. Differential, not an absolute ceiling — it blocks only the
  * deltas versus the merge-base, so against an empty baseline it degrades to an absolute gate and
  * works from the first commit. Runs Check A (finding identity), B (new suppressions), D (test
  * discipline), and E (omit-list integrity); the IO layer that fills the snapshots — the scanner,
  * the git merge-base baseline, and the runner/CLI with the compile precondition — wraps this core.
  */
object Diff:

  /** Compare `baseline` against `branch`, translating baseline paths through `renames` so a
    * relocated finding matches its baseline rather than counting as new. `config` supplies the
    * coverage omit-list and the integration floor each omitted package must clear. Returns the
    * aggregate [[Report]] across Check A (finding identity), B (new suppressions), D (test
    * discipline), and E (omit-list integrity).
    */
  def apply(
      baseline: Snapshot,
      branch: Snapshot,
      renames: Map[String, String],
      config: Config = Config.empty
  ): Report =
    val (aBlocks, aAdvisories) = checkA(baseline.findings, branch.findings, renames)
    val bBlocks = checkB(baseline.suppressions, branch.suppressions, renames)
    val (dBlocks, dAdvisories) = checkD(baseline.tests, branch.tests, renames, config)
    val eBlocks = checkE(baseline.tests, branch.tests, config)
    val blocks = aBlocks ++ bBlocks ++ dBlocks ++ eBlocks
    val advisories = aAdvisories ++ dAdvisories
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

  /** Check B — new suppressions. Blocks any `(file, directive)` whose count rose versus the
    * translated baseline — a newly-introduced or scope-broadened suppression (the blanket and
    * targeted forms are distinct keys, so broadening registers). Differential: a pre-existing
    * suppression does not block.
    */
  private def checkB(
      baseline: List[Suppression],
      branch: List[Suppression],
      renames: Map[String, String]
  ): List[Block] =
    val base =
      baseline.groupBy(s => s.copy(file = translate(renames, s.file))).view.mapValues(_.size).toMap
    val branchCounts = branch.groupBy(identity).view.mapValues(_.size).toMap
    branchCounts.keysIterator
      .filter(k => branchCounts(k) > base.getOrElse(k, 0))
      .toList
      .sortBy(k => (k.file, k.directive))
      .map(k => Block(Check.B, Kind.NewSuppression, k.file, k.directive, 0, ""))

  /** Check D — test discipline. A deleted test file, a new skip marker, or a per-package unit
    * coverage drop beyond [[coverageEpsilon]] is a hard block; a drop in a file's test-count is an
    * advisory only (the count is gameable). Omit-listed packages are exempt from the coverage block
    * here and verified by Check E instead. Coverage is differential and reconciled through package
    * renames, so a moved package is not mistaken for a loss.
    */
  private def checkD(
      baseline: TestSnapshot,
      branch: TestSnapshot,
      renames: Map[String, String],
      cfg: Config
  ): (List[Block], List[Advisory]) =
    val branchFiles = branch.files.toSet
    val deletionBlocks =
      baseline.files.sorted.collect {
        case bf if !branchFiles.contains(translate(renames, bf)) =>
          Block(Check.D, Kind.TestFileDeletion, bf, "", 0, "")
      }

    val baseSkips = translateCounts(baseline.skips, p => translate(renames, p))
    val skipBlocks =
      branch.skips.keys.toList.sorted.collect {
        case f if branch.skips(f) > baseSkips.getOrElse(f, 0) =>
          Block(Check.D, Kind.NewSkipMarkers, f, "", 0, "")
      }

    val coverageBlocks =
      baseline.coverage.keys.toList.sorted.flatMap { pkg =>
        if cfg.omits(pkg) then None
        else
          val baseCov = baseline.coverage(pkg)
          branch.coverage.get(translatePkg(pkg, renames)) match
            case None =>
              Some(
                Block(
                  Check.D,
                  Kind.CoverageDrop,
                  pkg,
                  "",
                  0,
                  f"statement coverage $baseCov%.1f%% -> none (package absent)"
                )
              )
            case Some(bc) if bc < baseCov - coverageEpsilon =>
              Some(
                Block(
                  Check.D,
                  Kind.CoverageDrop,
                  pkg,
                  "",
                  0,
                  f"statement coverage $baseCov%.1f%% -> $bc%.1f%%"
                )
              )
            case _ => None
      }

    val baseCounts = translateCounts(baseline.testCounts, p => translate(renames, p))
    val countAdvisories =
      branch.testCounts.keys.toList.sorted.collect {
        case f if branch.testCounts(f) < baseCounts.getOrElse(f, 0) =>
          Advisory(Check.D, Kind.TestCountDrop, f, "", "")
      }

    (deletionBlocks ++ skipBlocks ++ coverageBlocks, countAdvisories)

  /** Check E — omit-list integrity. An omit-listed package is exempt from Check D only when an
    * integration test exercises it: (1) existence — integration coverage present and above zero;
    * (2) no-erosion — an entry that existed at the baseline may not drop beyond
    * [[coverageEpsilon]]; (3) bootstrap — a NEW entry (no baseline integration figure) must clear
    * `cfg.bootstrapIntegrationFloor`, once only. Every package an entry matches must clear the
    * contract on its own.
    */
  private def checkE(baseline: TestSnapshot, branch: TestSnapshot, cfg: Config): List[Block] =
    cfg.omitCoverage.flatMap { omit =>
      val matched =
        matchingPkgs(omit, List(baseline.integrationCoverage, branch.integrationCoverage))
      if matched.isEmpty then
        List(
          Block(
            Check.E,
            Kind.OmitWithoutIntegration,
            omit,
            "",
            0,
            "omitted from unit coverage but no integration test exercises it"
          )
        )
      else
        matched.flatMap { pkg =>
          branch.integrationCoverage.get(pkg).filter(_ > 0.0) match
            case None =>
              Some(
                Block(
                  Check.E,
                  Kind.OmitWithoutIntegration,
                  omit,
                  "",
                  0,
                  s"$pkg: omitted from unit coverage but no integration test exercises it"
                )
              )
            case Some(branchCov) =>
              baseline.integrationCoverage.get(pkg) match
                case Some(baseCov) if branchCov < baseCov - coverageEpsilon =>
                  Some(
                    Block(
                      Check.E,
                      Kind.IntegrationCoverageDrop,
                      omit,
                      "",
                      0,
                      f"$pkg integration coverage $baseCov%.1f%% -> $branchCov%.1f%%"
                    )
                  )
                case None if branchCov < cfg.bootstrapIntegrationFloor =>
                  Some(
                    Block(
                      Check.E,
                      Kind.OmitBelowBootstrapFloor,
                      omit,
                      "",
                      0,
                      f"$pkg: new omit entry integration coverage $branchCov%.1f%% < bootstrap floor ${cfg.bootstrapIntegrationFloor}%.1f%%"
                    )
                  )
                case _ => None
        }
    }

  /** Re-key a per-file count map through a path-translation function, summing collisions. */
  private def translateCounts(m: Map[String, Int], tr: String => String): Map[String, Int] =
    m.foldLeft(Map.empty[String, Int]) { case (acc, (k, v)) =>
      val key = tr(k)
      acc.updated(key, acc.getOrElse(key, 0) + v)
    }

  /** Map a baseline package directory path to its branch path when the package's directory was
    * renamed (derived from file renames whose directory changed); unchanged when none applies.
    */
  private def translatePkg(pkg: String, renames: Map[String, String]): String =
    val dirRenames = renames.keys.toList.sorted.map(from => (dirOf(from), dirOf(renames(from))))
    dirRenames
      .collectFirst {
        case (oldDir, newDir)
            if oldDir != newDir && oldDir != "." && (pkg == oldDir || pkg.endsWith("/" + oldDir)) =>
          pkg.stripSuffix(oldDir) + newDir
      }
      .getOrElse(pkg)

  /** The directory part of a `/`-separated path — `.` when there is none (mirrors Go `path.Dir`).
    */
  private def dirOf(p: String): String =
    val i = p.lastIndexOf('/')
    if i < 0 then "." else if i == 0 then "/" else p.substring(0, i)

  /** The package paths across the given coverage maps that match the omit entry, sorted and unique.
    */
  private def matchingPkgs(omit: String, covs: List[Map[String, Double]]): List[String] =
    covs.flatMap(_.keys).filter(pkg => Config.matchPkg(pkg, omit)).distinct.sorted

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
