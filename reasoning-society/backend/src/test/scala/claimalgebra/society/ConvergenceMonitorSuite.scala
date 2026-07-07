package claimalgebra.society

import org.scalacheck.Prop.forAll

import scala.concurrent.duration.*

/** The librarian's convergence monitor (librarian-convergence-monitor) — the PURE, STRUCTURAL,
  * NON-GENERATIVE detector of non-convergence.
  *
  * The load-bearing property is NON-GENERATIVITY, pinned by the metamorphic check (done FIRST): the
  * monitor reads the SHAPE of support over time, never what any claim MEANS. Permuting the claims'
  * content — consistently renaming every candidate, scrambling every note/content, flipping every
  * oracle answer — while holding the belief-state STRUCTURE fixed leaves the fire/no-fire decision
  * AND the emitted counts identical. If it did not, the monitor would be making a semantic judgment
  * → reject it.
  *
  * Then the behavioral acceptance: fires on the apple/not-alive thrash (churn + a persistent glut,
  * nothing consolidating) carrying structural counts and NO diagnosis; silent on a healthy
  * converging game and on early normal narrowing; belief-inert (the flag never moves belief or the
  * gate); and idempotent (at most once per stuck episode).
  */
class ConvergenceMonitorSuite extends munit.ScalaCheckSuite with SocietyFixtures:

  private val budget = SocietyConfig(maxRounds = 8, roundTimeout = 1.hour, hardDeadline = 1.minute)

  // === (1) THE METAMORPHIC CHECK — the non-generativity gate, done first ===

  // A consistent bijection on the generator's candidate pool {dog, cat, bird} — a 3-cycle, so it is
  // a genuine permutation of candidate IDENTITY. The monitor reads candidates only as opaque keys,
  // so a consistent rename must leave every structural read (backer counts, glut runs, churn)
  // isomorphic — and the flag decision and counts invariant.
  private val dog = mkAnswer("dog")
  private val cat = mkAnswer("cat")
  private val bird = mkAnswer("bird")
  private val renamePool: Map[Answer, Answer] = Map(dog -> cat, cat -> bird, bird -> dog)
  private def ren(c: Answer): Answer = renamePool.getOrElse(c, c)

  private def flipAnswer(o: OracleAnswer): OracleAnswer = o match
    case OracleAnswer.Yes => OracleAnswer.No
    case OracleAnswer.No => OracleAnswer.Unknown
    case OracleAnswer.Unknown => OracleAnswer.Yes

  /** Permute the CONTENT of a claim while holding its belief-state STRUCTURE (`seq`, `agent`,
    * position, and — crucially — WHICH agent takes WHICH stance on the same-position candidate)
    * fixed: rename every candidate through the bijection, scramble every note/content string, and
    * flip every oracle answer. The candidate rename is applied CONSISTENTLY, so the shape of
    * support is preserved and only the meaning changes.
    */
  private def scramble(e: Event): Event = e match
    case Event.Assert(s, t, a, c, content) => Event.Assert(s, t, a, ren(c), content + "·x")
    case Event.Corroborate(s, t, a, c, note) => Event.Corroborate(s, t, a, ren(c), note + "·x")
    case Event.Refute(s, t, a, c, note) => Event.Refute(s, t, a, ren(c), note + "·x")
    case Event.Strike(s, t, a, c, note) => Event.Strike(s, t, a, ren(c), note + "·x")
    case Event.QuestionAsked(s, t, a, q, content) => Event.QuestionAsked(s, t, a, q, content + "·x")
    case Event.AnswerGiven(s, t, q, ans, g) => Event.AnswerGiven(s, t, q, flipAnswer(ans), g)
    case Event.GateAbstain(s, t, reason) => Event.GateAbstain(s, t, reason + "·x")
    case Event.GateSign(s, t, c) => Event.GateSign(s, t, ren(c))
    case Event.Retired(s, t, c) => Event.Retired(s, t, ren(c))
    case Event.Resurrected(s, t, c) => Event.Resurrected(s, t, ren(c))
    // QuestionProposed / clarification / definition / ConvergenceWarning carry nothing the monitor
    // reads — pass them through unchanged.
    case other => other

  property("METAMORPHIC — permuting content leaves the flag decision and counts IDENTICAL") {
    forAll(genLog) { (log: Vector[Event]) =>
      assertEquals(
        Convergence.flag(log.map(scramble), budget),
        Convergence.flag(log, budget),
        clue("the monitor reads structure, not meaning — content permutation is invisible to it")
      )
    }
  }

  property("METAMORPHIC — the emit decision (incl. idempotency) is likewise content-invariant") {
    forAll(genLog) { (log: Vector[Event]) =>
      assertEquals(
        Convergence.warningToEmit(log.map(scramble), budget),
        Convergence.warningToEmit(log, budget)
      )
    }
  }

  // === (2) FIRES on the apple / not-a-living-organism thrash ===

  // The apple bug, reduced to its STRUCTURE (the monitor never reads the "NO to living organism"
  // poisoning that caused it): the seeker keeps proposing candidates in a region that cannot contain
  // the answer; "fossil" sits in an unresolved glut round after round (the seeker stands behind it,
  // a doubter refutes it — a live glut, never retired, never resolved), while "amber" is proposed
  // and then defeated (self-withdrawal + two refuters → retired: churn), and "petrified" is proposed
  // next. Nothing ever consolidates. Contiguous seqs, so a positional prefix is the playhead; the
  // AnswerGiven events are the round boundaries (their VALUES are irrelevant to the monitor).
  private val seeker = mkAgent("seeker")
  private val doubtA = mkAgent("doubtA")
  private val doubtB = mkAgent("doubtB")
  private val fossil = mkAnswer("fossil")
  private val amber = mkAnswer("amber")
  private val petrified = mkAnswer("petrified")
  private val q1 = mkQuestion("q1")
  private val q2 = mkQuestion("q2")

  private def build(makers: List[Int => Event]): Vector[Event] =
    makers.zipWithIndex.map { case (mk, i) => mk(i + 1) }.toVector

  private val appleLog: Vector[Event] = build(
    List(
      s => mkAssert(s, seeker, fossil), // 1  round 1: fossil proposed
      s => refute(s, doubtA, fossil), //   2          fossil refuted → live glut (seeker stands)
      s => questionAsked(s, seeker, q1), // 3
      s =>
        answerGiven(s, q1, OracleAnswer.No), // 4   the poisoning NO — value IGNORED by the monitor
      s => refute(s, doubtB, fossil), //   5  round 2: fossil re-refuted, still unresolved
      s => questionAsked(s, seeker, q2), // 6
      s => answerGiven(s, q2, OracleAnswer.Yes), // 7
      s => mkAssert(s, seeker, amber), //  8  round 3: amber proposed
      s => refute(s, doubtA, amber), //    9          amber refuted → live glut
      s => questionAsked(s, seeker, q1), // 10
      s => answerGiven(s, q1, OracleAnswer.No), // 11
      s => refute(s, seeker, amber), //    12 round 4: the seeker WITHDRAWS amber (self-refute)
      s => refute(s, doubtB, amber), //    13         a second refuter → amber DEFEATED (retired)
      s => questionAsked(s, seeker, q2), // 14
      s => answerGiven(s, q2, OracleAnswer.No), // 15
      s => mkAssert(s, seeker, petrified), // 16 round 5 (in progress): petrified proposed
      s => refute(s, doubtA, petrified) //  17          petrified refuted → live glut
    )
  )

  test("FIRES on the apple/not-alive thrash — churn + a persistent glut, nothing consolidating") {
    val fired = Convergence.flag(appleLog, budget)
    assert(fired.isDefined, clue("the search is clearly not converging — the monitor flags it"))
    // The emitted evidence is STRUCTURAL only — two counts, and NO candidate name / diagnosis (the
    // Warning type cannot carry one). fossil sat in glut across all five round-snapshots (persistence
    // 5); five rounds passed with nothing consolidating (rwc 5).
    assertEquals(
      fired,
      Some(Convergence.Warning(roundsWithoutConsolidation = 5, glutPersistence = 5)),
      clue("structural counts only — the librarian counts, it does not diagnose")
    )
  }

  test("does NOT fire on an EARLY prefix of the same thrash (two rounds is not clearly stuck)") {
    assertEquals(
      Convergence.flag(appleLog.take(7), budget),
      None,
      clue("flag LATE — a two-round prefix has not yet shown clear non-convergence")
    )
  }

  test("the apple flag is itself content-invariant (fires the same, same counts, when scrambled)") {
    // A bijection over the apple candidates — a genuine permutation of identity.
    val appleRename: Map[Answer, Answer] =
      Map(fossil -> amber, amber -> petrified, petrified -> fossil)
    def renA(c: Answer): Answer = appleRename.getOrElse(c, c)
    def scrambleApple(e: Event): Event = e match
      case Event.Assert(s, t, a, c, content) => Event.Assert(s, t, a, renA(c), content + "·z")
      case Event.Refute(s, t, a, c, note) => Event.Refute(s, t, a, renA(c), note + "·z")
      case Event.AnswerGiven(s, t, q, ans, g) => Event.AnswerGiven(s, t, q, flipAnswer(ans), g)
      case Event.QuestionAsked(s, t, a, q, content) =>
        Event.QuestionAsked(s, t, a, q, content + "·z")
      case other => other
    assertEquals(
      Convergence.flag(appleLog.map(scrambleApple), budget),
      Convergence.flag(appleLog, budget),
      clue("renaming the candidates and flipping the answers does not move the flag")
    )
  }

  // === (3) SILENT on a healthy converging game and on early narrowing ===

  private val helper = mkAgent("helper")
  private val apple = mkAnswer("apple")

  test("SILENT on a healthy converging game — a candidate reaches 2 backers and signs") {
    val converging = build(
      List(
        s => mkAssert(s, seeker, apple), //         1
        s => questionAsked(s, seeker, q1), //       2
        s => answerGiven(s, q1, OracleAnswer.Yes), // 3
        s => corroborate(s, helper, apple) //       4  a second distinct backer → signable
      )
    )
    // The slot is signable (clean True, 2 distinct backers, cardinality 1), so the search is not
    // stuck — the monitor never flags a game that is about to sign.
    assertEquals(
      GameCore.decide(converging, converging.size),
      GateDecision.Sign(apple),
      clue("precondition: the converging game signs")
    )
    assertEquals(Convergence.flag(converging, budget), None, clue("no false alarm on convergence"))
  }

  test(
    "SILENT on early normal narrowing — questions asked, no candidate tried yet, even at budget"
  ) {
    // Pure Q&A, no candidate ever proposed. `candidatesTried` is false, so even with the budget
    // fraction spent the backstop does NOT fire — asking questions is progress, not a stall.
    val narrowing = build(
      List(
        s => questionAsked(s, seeker, q1), //        1
        s => answerGiven(s, q1, OracleAnswer.Yes), //  2
        s => questionAsked(s, seeker, q2), //        3
        s => answerGiven(s, q2, OracleAnswer.No), //   4
        s => questionAsked(s, seeker, q1), //        5
        s => answerGiven(s, q1, OracleAnswer.Yes), //  6
        s => questionAsked(s, seeker, q2), //        7
        s => answerGiven(s, q2, OracleAnswer.No) //    8
      )
    )
    val tight = SocietyConfig(maxRounds = 5, roundTimeout = 1.hour, hardDeadline = 1.minute)
    assertEquals(
      Convergence.flag(narrowing, tight),
      None,
      clue("no candidate has even been tried — narrowing via questions is not non-convergence")
    )
  }

  // === (4) each structural signal fires in isolation ===

  test("Signal 2 in isolation — a persistent glut with NO churn still fires") {
    // Only "fossil", asserted once and refuted once: a live glut that persists across five round
    // boundaries with no other candidate (no churn) and no consolidation.
    val persistentGlut = build(
      List(
        s => mkAssert(s, seeker, fossil), //          1
        s => refute(s, doubtA, fossil), //            2   glut from here on
        s => questionAsked(s, seeker, q1), //         3
        s => answerGiven(s, q1, OracleAnswer.Yes), //   4  round boundary
        s => questionAsked(s, seeker, q2), //         5
        s => answerGiven(s, q2, OracleAnswer.Yes), //   6  round boundary
        s => questionAsked(s, seeker, q1), //         7
        s => answerGiven(s, q1, OracleAnswer.Yes), //   8  round boundary
        s => questionAsked(s, seeker, q2), //         9
        s => answerGiven(s, q2, OracleAnswer.Yes) //   10 round boundary
      )
    )
    val fired = Convergence.flag(persistentGlut, budget)
    assert(
      fired.exists(_.glutPersistence >= Convergence.WindowRounds),
      clue(s"persistent glut: $fired")
    )
  }

  test("Signal 3 in isolation — budget consumed with a candidate stuck at one backer (no glut)") {
    // "apple" sits at a single backer forever — clean but never corroborated, never refuted. No
    // glut, no churn; only the budget backstop catches it once the round budget is mostly spent.
    val stalledAtOne = build(
      List(
        s => mkAssert(s, seeker, apple), //           1  one backer, clean, never grows
        s => questionAsked(s, seeker, q1), //         2
        s => answerGiven(s, q1, OracleAnswer.Yes), //   3
        s => questionAsked(s, seeker, q2), //         4
        s => answerGiven(s, q2, OracleAnswer.Yes), //   5
        s => questionAsked(s, seeker, q1), //         6
        s => answerGiven(s, q1, OracleAnswer.Yes), //   7
        s => questionAsked(s, seeker, q2), //         8
        s => answerGiven(s, q2, OracleAnswer.Yes) //   9
      )
    )
    val tight = SocietyConfig(maxRounds = 5, roundTimeout = 1.hour, hardDeadline = 1.minute)
    val fired = Convergence.flag(stalledAtOne, tight)
    assert(fired.isDefined, clue(s"budget spent, nothing consolidating: $fired"))
    assertEquals(fired.map(_.glutPersistence), Some(0), clue("no glut — the budget backstop fired"))
  }

  // === (5) BELIEF-INERT — the flag never moves belief, the gate, or the sign decision ===

  private def stripWarnings(log: Vector[Event]): Vector[Event] =
    log.filterNot {
      case _: Event.ConvergenceWarning => true
      case _ => false
    }

  property(
    "a spliced ConvergenceWarning is belief-inert — project/belief/decide/nextMove unchanged"
  ) {
    forAll(genLog) { (log: Vector[Event]) =>
      val warning = Event.ConvergenceWarning(log.size + 1, 0L, 3, 4)
      val appended = log :+ warning
      val spliced = // insert in the middle too — position must not shift belief
        (log.take(log.size / 2) :+ warning) ++ log.drop(log.size / 2)
      List(appended, spliced).forall { warned =>
        GameCore.project(warned) == GameCore.project(stripWarnings(warned)) &&
        GameCore.belief(warned, warned.size) == GameCore.belief(log, log.size) &&
        GameCore.decide(warned, warned.size) == GameCore.decide(log, log.size) &&
        GameCore.nextMove(warned, warned.size, roundComplete = true) ==
          GameCore.nextMove(log, log.size, roundComplete = true)
      }
    }
  }

  test(
    "a game that WOULD abstain still abstains with a warning present (the flag manufactures no sign)"
  ) {
    // The apple thrash abstains (a glut jams the slot); appending the flag the monitor would emit
    // changes nothing — the gate still abstains, never a signature.
    val abstains = GameCore.nextMove(appleLog, appleLog.size, roundComplete = true)
    assertEquals(abstains, Move.Abstain, clue("precondition: the stuck game abstains"))
    val warned = appleLog :+ Event.ConvergenceWarning(appleLog.size + 1, 0L, 5, 5)
    assertEquals(
      GameCore.nextMove(warned, warned.size, roundComplete = true),
      Move.Abstain,
      clue("the flag is a request for help, not permission to guess")
    )
    assertEquals(
      GameCore.decide(warned, warned.size),
      GameCore.decide(appleLog, appleLog.size),
      clue("the gate decision is byte-identical with the warning present")
    )
  }

  // === (6) IDEMPOTENT — fire at most once per stuck episode ===

  test("idempotent — once a warning is standing, warningToEmit suppresses re-firing") {
    // First close: the detector fires and there is no standing warning → emit.
    val first = Convergence.warningToEmit(appleLog, budget)
    assert(first.isDefined, clue("the first close emits the warning"))
    // The single writer appends it; the episode is still unresolved (nothing consolidated) → active.
    val afterEmit = appleLog :+ Event.ConvergenceWarning(appleLog.size + 1, 0L, 5, 5)
    assert(Convergence.active(afterEmit), clue("the warning is standing — the stall is unresolved"))
    assertEquals(
      Convergence.warningToEmit(afterEmit, budget),
      None,
      clue("no re-fire while the same stuck episode is unresolved — not naggy")
    )
  }

  test("active is false on a log with no warning at all") {
    assertEquals(Convergence.active(appleLog), false)
  }
