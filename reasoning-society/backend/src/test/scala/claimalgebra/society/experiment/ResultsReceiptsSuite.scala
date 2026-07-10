package claimalgebra.society
package experiment

import java.nio.file.{Files, Path, Paths}

/** The verify-results check: recompute each published crown-jewel result table from its committed
  * receipt (`docs/reasoning-society/receipts/<name>.digest`, distilled by [[RunDistillReceipts]])
  * and assert it equals the exact enumerated tuple the dev-ledger claim asserts. Reuses the repo's
  * own [[ProportionDiff]] (Newcombe) and [[Rate]] (Wilson) so a recomputed interval cannot drift
  * from the one the report printed. Runs inside `sbt check`, so the commit-path gate signs the
  * narrow table-claims when — and only when — every headline number here reproduces.
  *
  * Fail-closed by construction: a missing receipt fails the test (never a silent skip), and
  * [[Receipts.parseDigest]] rejects a tampered or truncated receipt before any table is read.
  *
  *   - clm-0014 — composed-cell: the seam-gated vs seam-open tables and the +1 win accounting.
  *   - clm-0023 — stronger-closer: the Sonnet 0/0/0 and Opus 0/0/1 win tables.
  *   - clm-0032 — reveal-set: the A/B tables, the Newcombe B−A interval, and its sensitivity.
  *   - clm-0026 — capacity: the channel-plus-decoder arithmetic (no archive; formula only).
  *
  * The interpretive halves of those findings (the readings — "tier does not gate", "a joint
  * channel-plus-decoder ceiling") are NOT asserted here: a recompute verifies the numbers reproduce
  * from the receipts, never that an interpretation is correct.
  */
class ResultsReceiptsSuite extends munit.FunSuite:

  test("clm-0014 composed-cell: the seam-gated and seam-open tables reproduce from the archive") {
    val r = receipt("composed-cell")
    assertEquals(r.stamp, "e4666df175ff43e3")
    val gated = r.games.filter(_.arm == "seam-gated")
    val open = r.games.filter(_.arm == "seam-open")
    assertEquals(gated.size, 52, clue("seam-gated games"))
    assertEquals(open.size, 52, clue("seam-open games"))

    // seam-gated: zero fail-open, 6 wins, every win oracle-confirmed.
    assertEquals(outcomes(gated), Map("Abstain" -> 46, "SignCorrect" -> 6))
    assertEquals(failOpen(gated), 0, clue("seam-gated fail-open"))
    assertEquals(signPaths(gated.filter(_.outcome == "SignCorrect")), Map("OracleConfirmed" -> 6))

    // seam-open: 15 fail-open and 5 wins, all 20 signed games via the 2-backer quorum (none oracle).
    assertEquals(outcomes(open), Map("Abstain" -> 32, "SignWrong" -> 15, "SignCorrect" -> 5))
    assertEquals(signPaths(open.filter(_.signPath != "-")), Map("BackerQuorum" -> 20))

    // the compressed "0.25/0.35" is the per-cell seam-open fail-open split: dev 8/32, held 7/20.
    assertEquals(open.count(_.cell == "dev"), 32)
    assertEquals(open.count(_.cell == "held"), 20)
    assertEquals(failOpen(open.filter(_.cell == "dev")), 8, clue("seam-open dev fail-open (0.25)"))
    assertEquals(
      failOpen(open.filter(_.cell == "held")),
      7,
      clue("seam-open held fail-open (0.35)")
    )
  }

  test(
    "clm-0014 composed-cell: the +1 win accounting (5 resurrected, 4 attrition) reproduces by pairing"
  ) {
    val r = receipt("composed-cell")
    val gated = byKey(r.games.filter(_.arm == "seam-gated"))
    val open = byKey(r.games.filter(_.arm == "seam-open"))
    val paired = gated.keySet.intersect(open.keySet)
    assertEquals(paired.size, 52, clue("paired (cell,target,seed) games"))

    val gatedWins = gated.values.count(_.outcome == "SignCorrect")
    val openWins = open.values.count(_.outcome == "SignCorrect")
    val resurrection =
      paired.count(k => gated(k).outcome == "SignCorrect" && open(k).outcome != "SignCorrect")
    val attrition =
      paired.count(k => open(k).outcome == "SignCorrect" && gated(k).outcome != "SignCorrect")

    assertEquals(gatedWins, 6, clue("gated wins"))
    assertEquals(openWins, 5, clue("open wins"))
    assertEquals(gatedWins - openWins, 1, clue("net win delta (a rise, not a drop)"))
    assertEquals(resurrection, 5, clue("resurrected: gated won, open did not"))
    assertEquals(attrition, 4, clue("attrition: open won, gated did not"))
  }

  test(
    "clm-0015 composed-cell: of the 5 resurrected wins, exactly 2 were open-arm lies converted to wins"
  ) {
    val r = receipt("composed-cell")
    val gated = byKey(r.games.filter(_.arm == "seam-gated"))
    val open = byKey(r.games.filter(_.arm == "seam-open"))
    val paired = gated.keySet.intersect(open.keySet)
    val resurrected =
      paired.filter(k => gated(k).outcome == "SignCorrect" && open(k).outcome != "SignCorrect")
    assertEquals(resurrected.size, 5, clue("resurrected wins"))

    // The essay's Finding-2 sentence: two of the gated wins were confident-wrongs in the open design.
    val liesToWins = resurrected.filter(k => open(k).outcome == "SignWrong")
    val abstainsToWins = resurrected.filter(k => open(k).outcome == "Abstain")
    assertEquals(liesToWins.size, 2, clue("open-arm confident-wrongs (lies) converted to wins"))
    assertEquals(abstainsToWins.size, 3, clue("open-arm abstains converted to wins"))
    assertEquals(liesToWins.map(_._2).toSet, Set("spoon", "pencil"), clue(liesToWins.toString))
  }

  test("clm-0023 stronger-closer: the Sonnet 0/0/0 and Opus 0/0/1 win tables reproduce") {
    val sonnet = receipt("stronger-closer-sonnet")
    val opus = receipt("stronger-closer-opus")

    assertEquals(wins(sonnet, "W-weak"), 0, clue("Sonnet W"))
    assertEquals(wins(sonnet, "S1-sonnet"), 0, clue("Sonnet S1"))
    assertEquals(wins(sonnet, "D-sonnet"), 0, clue("Sonnet D"))

    assertEquals(wins(opus, "W-weak"), 0, clue("Opus W"))
    assertEquals(wins(opus, "S1-opus"), 0, clue("Opus S1"))
    assertEquals(wins(opus, "D-opus"), 1, clue("Opus D — the lone win"))

    List("W-weak", "S1-sonnet", "D-sonnet").foreach(a =>
      assertEquals(sonnet.games.count(_.arm == a), 64, clue(a))
    )
    List("W-weak", "S1-opus", "D-opus").foreach(a =>
      assertEquals(opus.games.count(_.arm == a), 64, clue(a))
    )
  }

  test("clm-0032 reveal-set: the A/B tables, w, and the Newcombe B−A interval reproduce") {
    val r = receipt("reveal-the-set")
    val a = r.games.filter(_.arm == "A-withheld")
    val b = r.games.filter(_.arm == "B-revealed")
    assertEquals(a.size, 64, clue("A withheld games"))
    assertEquals(b.size, 62, clue("B revealed games"))

    val aWins = a.count(_.outcome == "SignCorrect")
    val bWins = b.count(_.outcome == "SignCorrect")
    assertEquals(aWins, 0, clue("A correct (withheld)"))
    assertEquals(bWins, 5, clue("B correct (revealed)"))

    // the published Newcombe B − A interval [0.008, 0.175], excluding zero.
    val (lo, hi) = ProportionDiff.newcombe95(bWins, b.size, aWins, a.size)
    assertEqualsDouble(lo, 0.008, 0.001, clue(lo))
    assertEqualsDouble(hi, 0.175, 0.001, clue(hi))
    assert(lo > 0.0, clue((lo, hi)))

    // w (wrong-guess-reached-confirmation) = 0.016 in both arms — the sentence did not raise guessing.
    assertEqualsDouble(Rate(a.count(_.outcome == "SignWrong"), a.size).point, 0.016, 0.001)
    assertEqualsDouble(Rate(b.count(_.outcome == "SignWrong"), b.size).point, 0.016, 0.001)

    // all five B wins are oracle-confirmed.
    assertEquals(b.filter(_.outcome == "SignCorrect").map(_.signPath).toSet, Set("OracleConfirmed"))
  }

  test("clm-0032 reveal-set: both dispositions of the 2 dropped B games still exclude zero") {
    // both as non-wins -> 5/64 -> [0.006, 0.170]; both as wins -> 7/64 -> [0.030, 0.209].
    val (lo1, hi1) = ProportionDiff.newcombe95(5, 64, 0, 64)
    assertEqualsDouble(lo1, 0.006, 0.001, clue(lo1))
    assertEqualsDouble(hi1, 0.170, 0.001, clue(hi1))
    assert(lo1 > 0.0, clue((lo1, hi1)))

    val (lo2, hi2) = ProportionDiff.newcombe95(7, 64, 0, 64)
    assertEqualsDouble(lo2, 0.030, 0.001, clue(lo2))
    assertEqualsDouble(hi2, 0.209, 0.001, clue(hi2))
    assert(lo2 > 0.0, clue((lo2, hi2)))
  }

  test("clm-0026 capacity: the channel-plus-decoder arithmetic reproduces the published figures") {
    val c07 = capacity(0.7)
    assertEqualsDouble(c07, 0.119, 0.001, clue("C(0.7) bits/answer"))

    val totalBits = 16.0 * c07
    assertEqualsDouble(totalBits, 1.90, 0.01, clue("16 rounds -> bits"))

    assertEqualsDouble(log2(8.0), 3.0, 1e-9, clue("H(X) = log2(8) target-set entropy"))

    val singleGuess = math.pow(2.0, totalBits) / 8.0
    assertEqualsDouble(singleGuess, 0.46, 0.01, clue("told the set, single-guess success"))

    // open decoder ~10 bits: per-trial win ~ 2^bits / 2^10, and P(0 wins over 64).
    val perTrial = math.pow(2.0, totalBits) / math.pow(2.0, 10.0)
    val p0of64 = math.pow(1.0 - perTrial, 64.0)
    assertEqualsDouble(p0of64, 0.79, 0.01, clue("P(0-for-64)"))
  }

  test("a tampered or truncated receipt fails the integrity check (fail-closed)") {
    val r = receipt("composed-cell")
    val good = Receipts.renderDigest(r.name, r.stamp, r.games)
    assert(Receipts.parseDigest(good).isRight, clue("the freshly rendered receipt must parse"))

    val flipped = good.replaceFirst("Abstain", "SignCorrect")
    assert(flipped != good, clue("the fixture must actually change a row"))
    assert(Receipts.parseDigest(flipped).isLeft, clue("a flipped row must fail the sha check"))

    val dropped = good.linesIterator.filterNot(_.startsWith("seam-open")).mkString("\n")
    assert(
      Receipts.parseDigest(dropped).isLeft,
      clue("a dropped row must fail the count/sha check")
    )
  }

  // --- helpers -------------------------------------------------------------

  private def receipt(name: String): Receipt =
    val rel = s"docs/reasoning-society/receipts/$name.digest"
    val path = ancestors(Paths.get("").toAbsolutePath)
      .map(_.resolve(rel))
      .find(Files.exists(_))
      .getOrElse(fail(s"receipt not found from ${Paths.get("").toAbsolutePath}: $rel"))
    Receipts
      .parseDigest(Files.readString(path))
      .fold(e => fail(s"receipt $name did not verify: $e"), identity)

  private def outcomes(rows: List[GameRow]): Map[String, Int] =
    rows.groupBy(_.outcome).view.mapValues(_.size).toMap

  private def signPaths(rows: List[GameRow]): Map[String, Int] =
    rows.groupBy(_.signPath).view.mapValues(_.size).toMap

  private def failOpen(rows: List[GameRow]): Int = rows.count(_.outcome == "SignWrong")

  private def wins(r: Receipt, arm: String): Int =
    r.games.filter(_.arm == arm).count(_.outcome == "SignCorrect")

  private def byKey(rows: List[GameRow]): Map[(String, String, String), GameRow] =
    rows.map(g => (g.cell, g.target, g.seed) -> g).toMap

  private def log2(x: Double): Double = math.log(x) / math.log(2.0)

  private def capacity(p: Double): Double =
    val q = 1.0 - p // binary-symmetric-channel crossover
    1.0 - (-q * log2(q) - (1.0 - q) * log2(1.0 - q))

  private def ancestors(p: Path): LazyList[Path] =
    p #:: Option(p.getParent).map(ancestors).getOrElse(LazyList.empty)
