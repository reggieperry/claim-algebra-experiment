package claimalgebra.society
package experiment

/** The experiment's GRADING match: does `candidate` name the same thing as the sealed `target`?
  * Loosens exact equality to accept a semantically-equivalent phrasing — a leading qualifier
  * ("domestic dog" for "dog"), an article, or a pre-registered synonym ("canine" for "dog") —
  * because the society naming the RIGHT concept in different words is a correct answer, not a wrong
  * sign. The endgame diagnostic showed the society converging on "domestic dog" for the target
  * "dog" and the exact-string grader scoring it wrong; that was a grading artifact, not a search
  * failure.
  *
  * Deterministic and PRE-REGISTERED — no model in the loop, so the headline grade stays mechanical
  * (determinism-first). Conservative on purpose: a leading determiner or domestication mark is
  * dropped, but a broader category never becomes the target this way ("animal" does not match
  * "dog"), so it does not over-credit. HYPOTHESIS IDENTITY inside the game stays exact `Answer`
  * equality — "domestic dog" and "dog" are DISTINCT candidates on the board; this loosening applies
  * ONLY where the experiment compares a candidate to the sealed target (the classifier and the
  * structural guess-truth).
  */
object TargetMatch:

  /** Leading qualifiers dropped before comparison — determiners and domestication/genericness
    * marks. Deliberately short: enough to equate "the domestic dog" with "dog", not so much that a
    * DIFFERENT thing matches.
    */
  private val Qualifiers: Set[String] =
    Set(
      "a",
      "an",
      "the",
      "domestic",
      "domesticated",
      "wild",
      "house",
      "pet",
      "common",
      "typical",
      "ordinary",
      "regular",
      "standard"
    )

  /** Pre-registered synonym groups for the experiment's target vocabulary — declared up front, not
    * tuned per run. Each set is a group of interchangeable names for one thing.
    */
  private val SynonymGroups: List[Set[String]] = List(
    Set("dog", "canine", "hound", "pup", "puppy", "doggie"),
    Set("cat", "feline", "kitty", "kitten"),
    Set("car", "automobile", "auto", "motorcar"),
    Set("couch", "sofa"),
    Set("television", "tv"),
    Set("cup", "mug"),
    Set("bicycle", "bike")
  )

  /** A stable, order-independent fingerprint of the grading vocabulary (the qualifier set and the
    * synonym groups) for the config-surface stamp — since the fail-open metric certifies soundness
    * only relative to this table, a change to it must break run comparability visibly.
    */
  val stampTable: String =
    Qualifiers.toList.sorted.mkString(",") + "|" +
      SynonymGroups.map(_.toList.sorted.mkString(",")).sorted.mkString(";")

  def matches(target: Answer, candidate: Answer): Boolean =
    matchesRaw(target.value, candidate.value)

  /** The raw-string form, for the structural guess-truth where the candidate is a bare qid label.
    */
  def matchesRaw(target: String, candidate: String): Boolean =
    val t = normalize(target)
    val c = normalize(candidate)
    t.nonEmpty && (t == c || synonymous(t, c))

  private def synonymous(a: String, b: String): Boolean =
    SynonymGroups.exists(g => g.contains(a) && g.contains(b))

  private def normalize(raw: String): String =
    val tokens =
      raw.toLowerCase.replaceAll("[^a-z0-9 ]", " ").split("\\s+").toList.filter(_.nonEmpty)
    val stripped = tokens.dropWhile(Qualifiers.contains)
    // if the label was ALL qualifiers ("the"), keep the raw tokens rather than an empty string.
    (if stripped.isEmpty then tokens else stripped).mkString(" ")
