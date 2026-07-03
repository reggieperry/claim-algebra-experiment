package fix

// A project-local scalafix lint rule — flags a non-fused monadic chain the built-in/typelevel rules
// cannot flag safely: an IO `.map(_ => x)` (or `.map(x => <x unused>)`) that should be `.as(x)`
// (or `.void` when the constant is `()`). SEMANTIC and SCOPED to `cats.effect.IO#map` by symbol, so
// — unlike TypelevelAs — it never fires on a plain `Iterator`/`List`/`Option` map-to-constant, where
// `.as` is not in scope and the anti-suppression gate would leave no escape. IO is the house effect
// (scala-modules.md: concrete IO is the default), so scoping to it covers the effectful chains.
//
// NOTE: scalafix compiles rules as Scala 2.13 — braces / `_` imports / `implicit`, not Scala 3.
import scalafix.v1._
import scala.meta._

final case class EffectMapConstDiag(pos: Position) extends Diagnostic {
  override def position: Position = pos
  override def message: String =
    "IO `.map(_ => x)` can be `.as(x)` (or `.void` for `_ => ()`) — functor fusion (right identity)"
}

class EffectMapConst extends SemanticRule("EffectMapConst") {

  private val ioMap = SymbolMatcher.exact("cats/effect/IO#map().")

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
      case t @ Term.Apply(Term.Select(_, m @ Term.Name("map")), List(fn: Term.Function))
          if ioMap.matches(m.symbol) && ignoresParam(fn) =>
        Patch.lint(EffectMapConstDiag(t.pos))
    }.asPatch
  }

  // The lambda discards its argument: an anonymous `_ =>` (cannot reference it), or a named `x =>`
  // whose body never mentions `x` (a conservative syntactic check — if the name appears, do NOT flag).
  private def ignoresParam(fn: Term.Function): Boolean = fn match {
    case Term.Function(List(param), body) =>
      param.name match {
        case Name.Anonymous() => true
        case named            => body.collect { case Term.Name(v) if v == named.value => () }.isEmpty
      }
    case _ => false
  }
}
