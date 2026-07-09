package claimalgebra.society
package experiment

import cats.effect.syntax.all.*
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import claimalgebra.extract.{AnthropicLlmCall, LlmCall, OpenAiLlmCall}

/** E3 — measure the real error-correlation between redundant confirmers
  * (fallible-oracle-interpretation §E3). The redundancy curve dialed ρ; the whole transfer to the
  * real system rests on it being HIGH for same-model checks. This measures it. A pre-registered set
  * of yes/no questions with known ground truth (a mix of common-misconception traps and easy
  * anchors) is posed to three confirmers, and their JOINT error is compared to the product of
  * marginals:
  *
  *   - two instances of the SAME model (Haiku × Haiku) — the monoculture,
  *   - two DIFFERENT model families (Haiku × GPT) — cross-family diversity,
  *   - a model and a MECHANICAL check (the pre-registered answer) — a non-generative second check.
  *
  * The operationally relevant number is the JOINT error rate: under a two-confirmation requirement
  * it IS the fail-open rate (both confirmers must be wrong together). Independent errors give joint
  * ≈ q²; correlated errors give joint ≈ q; a mechanical second check gives joint ≈ 0. `rho` is the
  * error correlation that places each pairing on the redundancy curve.
  *
  * {{{sbt "reasoningSociety/runMain claimalgebra.society.experiment.RunConfirmerCorrelation"}}}
  */
object RunConfirmerCorrelation extends IOApp.Simple:

  /** Yes/no questions with pre-registered ground truth (`true` = yes). Traps are common
    * misconceptions where a model may err; anchors are clear, to keep the marginal error realistic.
    */
  private val questions: List[(String, Boolean)] = List(
    // Comparative / numerical near-threshold items, where capable models approximate and can err.
    "Is Russia larger in area than the surface area of Pluto?" -> true, // 17.1M vs 16.7M km^2
    "Is the Sahara Desert larger in area than the contiguous United States?" -> true, // 9.2M vs 8.1M
    "On Venus, is a single day longer than a single year?" -> true, // 243 vs 225 Earth days
    "On average, is the surface of Venus hotter than the surface of Mercury?" -> true,
    "Is Africa larger in area than the combined area of China, the United States, and India?" -> true,
    "Is the Eiffel Tower taller than 350 meters?" -> false, // ~330 m
    "Is Mount Kilimanjaro taller than 6000 meters?" -> false, // 5895 m
    "Is the deepest point of the ocean deeper than 10 kilometers?" -> true, // ~10.9 km
    "Is Alaska larger in area than Texas, California, and Montana combined?" -> true,
    "Is a blue whale's tongue heavier than an adult African elephant?" -> false, // ~2.7 t vs ~6 t
    "Is Greenland larger in area than Mexico?" -> true, // 2.16M vs 1.96M km^2
    "Did the fax machine exist before the telephone?" -> true, // 1843 vs 1876
    "Was Oxford University founded before the Aztec Empire?" -> true, // ~1096 vs 1428
    // Obscure taxonomy, less rehearsed than the common misconceptions.
    "Is a barnacle a crustacean?" -> true,
    "Is a Portuguese man o' war a single organism rather than a colony?" -> false,
    "Is a horseshoe crab more closely related to spiders than to crabs?" -> true,
    "Is a koala a bear?" -> false,
    "Is a red panda in the same taxonomic family as the giant panda?" -> false,
    "Is a hyena more closely related to cats than to dogs?" -> true,
    "Is a sweet potato closely related to a common potato?" -> false,
    "Is a king crab a true crab?" -> false, // hermit-crab lineage
    "Is a Venus flytrap native to a small region of the Carolinas in the United States?" -> true,
    "Is a wolverine a member of the weasel family?" -> true,
    "Is a manatee more closely related to an elephant than to a walrus?" -> true
  )

  private val systemPrompt =
    "You are answering a yes/no factual question. Put exactly one word in the \"answer\" field: yes or no."

  def run: IO[Unit] =
    (AnthropicLlmCall.clientResource, OpenAiLlmCall.clientResource).tupled.use {
      (aClient, oClient) =>
        val haiku = AnthropicLlmCall(aClient, classOf[TruthDto])
        val gpt = OpenAiLlmCall(oClient, classOf[TruthDto])
        def ask(llm: LlmCall[TruthDto], q: String): IO[Option[Boolean]] =
          llm.call(systemPrompt, q).map {
            case Right(dto) =>
              ModelTruthOracle.parse(dto.answer) match
                case OracleAnswer.Yes => Some(true)
                case OracleAnswer.No => Some(false)
                case OracleAnswer.Unknown => None
            case Left(_) => None
          }
        for
          _ <- IO.println(
            s"=== E3 confirmer error-correlation — LIVE (Haiku, Haiku, GPT), ${questions.size} pre-registered questions ==="
          )
          rows <- questions.parTraverseN(4) { (q, truth) =>
            (ask(haiku, q), ask(haiku, q), ask(gpt, q)).parTupled.map { (a, b, g) =>
              // err = did NOT return the correct yes/no (a None or a wrong answer both count as a miss).
              (a != Some(truth), b != Some(truth), g != Some(truth))
            }
          }
          _ <- IO.println(render(rows))
        yield ()
    }

  private def render(rows: List[(Boolean, Boolean, Boolean)]): String =
    val a = rows.map(_._1)
    val b = rows.map(_._2)
    val g = rows.map(_._3)
    // the mechanical check never errs (it IS the pre-registered answer), so its error vector is all-false.
    val mech = List.fill(rows.size)(false)
    val header =
      f"${"pairing"}%24s ${"err_A"}%6s ${"err_B"}%6s ${"joint (both wrong)"}%19s ${"indep q_A*q_B"}%14s ${"rho"}%7s"
    def rate(xs: List[Boolean]): Double = xs.count(identity).toDouble / xs.size
    def joint(x: List[Boolean], y: List[Boolean]): Double =
      x.zip(y).count((p, q) => p && q).toDouble / x.size
    def rho(x: List[Boolean], y: List[Boolean]): Double =
      val n = x.size.toDouble
      val xs = x.map(if _ then 1.0 else 0.0)
      val ys = y.map(if _ then 1.0 else 0.0)
      val mx = xs.sum / n
      val my = ys.sum / n
      val cov = xs.zip(ys).map((p, q) => (p - mx) * (q - my)).sum / n
      val sx = math.sqrt(xs.map(p => (p - mx) * (p - mx)).sum / n)
      val sy = math.sqrt(ys.map(q => (q - my) * (q - my)).sum / n)
      if sx == 0.0 || sy == 0.0 then Double.NaN else cov / (sx * sy)
    def row(name: String, x: List[Boolean], y: List[Boolean]): String =
      val r = rho(x, y)
      val rs = if r.isNaN then "  n/a" else f"$r%.2f"
      f"$name%24s ${rate(x)}%6.2f ${rate(y)}%6.2f ${joint(x, y)}%19.3f ${rate(x) * rate(y)}%14.3f ${rs}%7s"
    List(
      header,
      row("same model (Haiku×Haiku)", a, b),
      row("diff family (Haiku×GPT)", a, g),
      row("model × mechanical", a, mech)
    ).mkString("\n")
