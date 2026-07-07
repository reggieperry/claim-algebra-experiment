package claimalgebra.society

import claimalgebra.calculus.{Ledger, Status}
import claimalgebra.{Belnap, BlockReason, Testimony}
import org.scalacheck.Prop.forAll

/** Slice 1 of the hypothesis-lifecycle fix (channel-asymmetry retirement) — the SELF-WITHDRAWAL
  * detection predicate and masking, PURE core. This changes what the gate signs (the fail-closed
  * core), so the fail-closed invariants are the point: a retirement fires only on a genuinely
  * DEFEATED hypothesis (all pro-authors withdrew, ≥ 2 standing refuters) and NEVER on a live glut.
  *
  * The centerpiece is the "plant or fungus" jam from the real game log: a hypothesis the society
  * correctly abandoned jammed the gate as a false glut for fifty events. The predicate retires it
  * at e-20 (the driller's self-withdrawal), not e-19 (the skeptic alone), and masking removes the
  * jamming con so the gate abstains for the correct reason.
  */
class HypothesisLifecycleSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  // The plant-or-fungus cast (built through the real validating constructors).
  private val driller = mkAgent("driller")
  private val skeptic = mkAgent("skeptic")
  private val grower = mkAgent("grower")
  private val pof = mkAnswer("plant or fungus")
  private val apple = mkAnswer("apple tree")
  private val q1 = mkQuestion("q1")

  // The real game's seqs are sparse (e-14, e-17, e-19, e-20, e-61, e-66). On the wire `seq` == the
  // 1-based position (the frontend playhead), so `take(k)` is the prefix through e-k. Reconstruct a
  // contiguous 1..66 log with the real events at their seq-positions and belief-inert filler (a
  // GateAbstain "watching") elsewhere, so `take(19)` / `take(20)` land exactly at the skeptic's and
  // the driller's refutations.
  private val realEvents: Map[Int, Event] = Map(
    14 -> mkAssert(14, driller, pof, "plant or fungus?"),
    17 -> answerGiven(17, q1, OracleAnswer.Yes), // "plant: YES" — belief-inert
    19 -> refute(19, skeptic, pof),
    20 -> refute(
      20,
      driller,
      pof
    ), // the SELF-WITHDRAWAL — the driller refutes its own e-14 assertion
    61 -> mkAssert(61, driller, apple, "apple tree"),
    66 -> corroborate(66, grower, apple)
  )
  private val pofGame: Vector[Event] =
    (1 to 66).map(i => realEvents.getOrElse(i, Event.GateAbstain(i, i.toLong, "watching"))).toVector

  // --- Retires at e-20, not e-19 ---

  test("plant-or-fungus is NOT retired at e-19 (skeptic alone; the driller still stands behind)") {
    assertEquals(
      GameCore.retiredCandidates(pofGame.take(19)),
      Set.empty[Answer],
      clue("one refuter and the asserting driller still supporting → a live glut, held")
    )
  }

  test("plant-or-fungus IS retired at e-20 (the driller self-withdraws → 2 refuters, none behind)") {
    assertEquals(
      GameCore.retiredCandidates(pofGame.take(20)),
      Set(pof),
      clue("every pro-author self-withdrew and 2 distinct refuters stand → defeated")
    )
  }

  test("at e-20 the jamming glut is gone — masking reads Gap, not Conflict") {
    // The UNMASKED slot is a genuine-looking Glut (pro from e-14, con from e-19/e-20) — the false
    // glut that jammed the real game.
    val unmasked = Ledger.belief(GameCore.project(pofGame.take(20))).fold(identity, _.operative)
    assertEquals(Testimony.corner(unmasked), Belnap.Glut, clue("the pre-fix false glut"))
    // Masked, plant-or-fungus is off both channels → the slot is a clean gap.
    assertEquals(GameCore.belief(pofGame, 20).status, Status.Missing)
    assertEquals(
      GameCore.decide(pofGame, 20),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Gap)),
      clue("no live hypothesis — abstain for the correct reason, not a false-glut jam")
    )
  }

  // --- Invariant 1: never retire live surviving pro support (the real-glut HOLD mirrors) ---
  // The log lacks these three; add them explicitly. Each has live support on the pro channel, so it
  // is a REAL glut the gate must HOLD, not a defeat to retire.

  private val a1 = mkAgent("a1")
  private val a2 = mkAgent("a2")
  private val a3 = mkAgent("a3")
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")

  test("masking a DEFEATED rival lets the leading live candidate sign (the payoff)") {
    // dog is live with two distinct backers; cat is asserted once by a3 then abandoned — a3
    // self-withdraws and two others refute it, so cat is DEFEATED and retired.
    val log = Vector(
      mkAssert(1, a1, dog),
      corroborate(2, a2, dog),
      mkAssert(3, a3, cat),
      refute(4, a3, cat), // the sole author self-withdraws
      refute(5, a1, cat),
      refute(6, a2, cat) // → cat: every author withdrew, 3 standing refuters → retired
    )
    assertEquals(
      GameCore.retiredCandidates(log),
      Set(cat),
      clue("cat is defeated, not a live glut")
    )
    // UNMASKED, cat's pro (a3's assertion) + con jams the whole slot as a glut, so the gate cannot
    // sign dog — the exact false-glut jam the fix removes.
    val unmasked = Ledger.belief(GameCore.project(log)).fold(identity, _.operative)
    assertEquals(Testimony.corner(unmasked), Belnap.Glut, clue("the pre-fix jam"))
    // MASKED, cat is off the board and dog — clean True, two distinct backers — SIGNS.
    assertEquals(
      GameCore.decide(log, 6),
      GateDecision.Sign(dog),
      clue("the defeated rival's con no longer holds the live leader hostage")
    )
  }

  test("invariant 1 — RE-DEFENDED: asserter corroborates after two refutes → held, not retired") {
    // X asserts, Y+Z refute, X corroborates AFTER — X's latest stance is pro, so it stands behind.
    val log = Vector(
      mkAssert(1, a1, dog),
      refute(2, a2, dog),
      refute(3, a3, dog),
      corroborate(4, a1, dog)
    )
    assertEquals(
      GameCore.retiredCandidates(log),
      Set.empty[Answer],
      clue("live pro support → hold")
    )
    assertEquals(Testimony.corner(GameCore.slot(log, 4)), Belnap.Glut, clue("a real glut"))
    assertEquals(
      GameCore.decide(log, 4),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Conflict))
    )
  }

  test("invariant 1 — AUTHOR-SILENT, refuted by others → held (the anti-CWS pin)") {
    // X asserts, Y+Z refute, X stays SILENT. X never withdrew, so its assertion is live support.
    // This is the exact case self-withdrawal HOLDS and channel-recency would WRONGLY retire —
    // retiring here masks the con and lets a clean-but-wrong rival sign with no backstop.
    val log = Vector(mkAssert(1, a1, dog), refute(2, a2, dog), refute(3, a3, dog))
    assertEquals(
      GameCore.retiredCandidates(log),
      Set.empty[Answer],
      clue("the asserter is silent, not withdrawn — live support holds it")
    )
    assertEquals(
      GameCore.decide(log, 3),
      GateDecision.Abstain(AbstainReason.Blocked(BlockReason.Conflict)),
      clue("the gate HOLDS the contested claim — never signs around it")
    )
  }

  test("invariant 1 — SINGLE REFUTER → held (below the ≥2 floor)") {
    // X asserts and self-withdraws, ONE refuter total — one lone refutation never retires, mirroring
    // one lone assertion never signing.
    val log = Vector(mkAssert(1, a1, dog), refute(2, a1, dog))
    assertEquals(
      GameCore.retiredCandidates(log),
      Set.empty[Answer],
      clue("one refuter < MinRefuters")
    )
  }

  // --- Invariant 2: conservative ---

  property(
    "invariant 2 — a retired candidate has ≥ MinRefuters refuters and every author withdrew"
  ) {
    forAll(genLog) { (log: Vector[Event]) =>
      val sts = GameCore.stances(log)
      GameCore.retiredCandidates(log).forall { c =>
        val s = sts(c)
        val standingRefuters = s.lastCon.keySet.count(a => GameCore.standsAgainst(s, a))
        val authors = s.lastPro.keySet
        (standingRefuters >= GameCore.MinRefuters) &&
        authors.nonEmpty &&
        authors.forall(a => !GameCore.standsBehind(s, a))
      }
    }
  }

  property("invariant 2 — a candidate with exactly one standing refuter is NEVER retired") {
    forAll(genLog) { (log: Vector[Event]) =>
      val retired = GameCore.retiredCandidates(log)
      GameCore.stances(log).forall { case (c, s) =>
        val standingRefuters = s.lastCon.keySet.count(a => GameCore.standsAgainst(s, a))
        if standingRefuters == 1 then !retired.contains(c) else true
      }
    }
  }

  // --- Invariant 3: structural / non-generative (metamorphic) ---

  private def flipAnswer(o: OracleAnswer): OracleAnswer = o match
    case OracleAnswer.Yes => OracleAnswer.No
    case OracleAnswer.No => OracleAnswer.Unknown
    case OracleAnswer.Unknown => OracleAnswer.Yes

  /** Replace every note/content string and flip every oracle answer, keeping all STRUCTURE (`seq`,
    * `agent`, `candidateId`, `questionId`) fixed. A candidate-value string is deliberately NOT
    * touched (it is structure, not prose). Events with no prose the predicate could read pass
    * through unchanged.
    */
  private def scramble(e: Event): Event = e match
    case Event.Assert(s, t, a, c, content) => Event.Assert(s, t, a, c, content + "·scrambled")
    case Event.Corroborate(s, t, a, c, note) => Event.Corroborate(s, t, a, c, note + "·scrambled")
    case Event.Refute(s, t, a, c, note) => Event.Refute(s, t, a, c, note + "·scrambled")
    case Event.Strike(s, t, a, c, note) => Event.Strike(s, t, a, c, note + "·scrambled")
    case Event.QuestionAsked(s, t, a, q, content) =>
      Event.QuestionAsked(s, t, a, q, content + "·scrambled")
    case Event.AnswerGiven(s, t, q, ans, g) => Event.AnswerGiven(s, t, q, flipAnswer(ans), g)
    case Event.GateAbstain(s, t, reason) => Event.GateAbstain(s, t, reason + "·scrambled")
    case other => other

  property("invariant 3 — scrambling every note/content/answer leaves retiredCandidates unchanged") {
    forAll(genLog) { (log: Vector[Event]) =>
      assertEquals(
        GameCore.retiredCandidates(log.map(scramble)),
        GameCore.retiredCandidates(log)
      )
    }
  }

  test(
    "invariant 3 — retiredCandidates does not read the oracle answer (plant-or-fungus, flipped)"
  ) {
    // Flip the "plant: YES" oracle reply to NO; retirement is unaffected — it reads the agents'
    // refutations, never the oracle's meaning (which would be the forbidden generative judgment).
    assertEquals(
      GameCore.retiredCandidates(pofGame.take(20).map(scramble)),
      GameCore.retiredCandidates(pofGame.take(20))
    )
  }

  // --- Invariant 4: reversible (retire to trace, recoverable) ---

  test("invariant 4 — a fresh corroboration above the refutations resurrects; no event deleted") {
    val retiredLog = pofGame.take(20)
    assert(GameCore.retiredCandidates(retiredLog).contains(pof), "pof is retired at e-20")

    // The librarian's marker for the retirement (want − have).
    val markers1 = GameCore.reconcileRetirements(retiredLog, retiredLog.size + 1, 100L)
    assertEquals(
      markers1,
      List(Event.Retired(21, 100L, pof)),
      clue("emits Retired for the new defeat")
    )
    val marked = retiredLog :+ markers1.head

    // A FRESH agent corroborates plant-or-fungus above the refutations (seq 22 > the driller's e-20).
    val revived = marked :+ corroborate(marked.size + 1, grower, pof)
    assert(
      !GameCore.retiredCandidates(revived).contains(pof),
      "fresh live support above the latest refutation → no longer defeated"
    )
    val markers2 = GameCore.reconcileRetirements(revived, revived.size + 1, 200L)
    assertEquals(
      markers2,
      List(Event.Resurrected(23, 200L, pof)),
      clue("emits Resurrected on recovery")
    )

    // Nothing deleted — the log only grew; every original event is still present.
    assert(retiredLog.forall(revived.contains), "no event deleted (retire to trace, not delete)")
    assert(revived.sizeIs > retiredLog.size)
  }

  // --- Invariant 5(i): sign floor + leading path intact (byte-identical no-retirement path) ---

  property("invariant 5(i) — project ≡ maskedProject(_, ∅)") {
    forAll(genLog) { (log: Vector[Event]) =>
      assertEquals(GameCore.maskedProject(log, Set.empty), GameCore.project(log))
    }
  }

  property("invariant 5(i) — with no retirement at a prefix, belief is the unmasked read exactly") {
    forAll(genLog) { (log: Vector[Event]) =>
      (0 to log.size).forall { k =>
        val prefix = log.take(k)
        if GameCore.retiredCandidates(prefix).isEmpty then
          GameCore.belief(log, k) == Ledger.resolve(GameCore.project(prefix))
        else true
      }
    }
  }

  // --- reconcileRetirements: markers track the predicate, no-op when already in sync ---

  test("reconcileRetirements is a no-op once the markers match the predicate") {
    val retiredLog = pofGame.take(20)
    val markers = GameCore.reconcileRetirements(retiredLog, retiredLog.size + 1, 100L)
    val synced = retiredLog ++ markers
    assertEquals(
      GameCore.reconcileRetirements(synced, synced.size + 1, 200L),
      List.empty[Event],
      clue("want == have → nothing to emit; markers are trace, the predicate is authority")
    )
  }
