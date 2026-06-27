package gate

import munit.FunSuite

/** The JSON output contract: lowercase verdict, lettered check, snake_case kind, always-present
  * arrays, and correct string escaping.
  */
class ReportJsonSuite extends FunSuite:

  test("a pass report has empty arrays and a lowercase verdict") {
    val r = Report(Verdict.Pass, "pass", List.empty, List.empty)
    assertEquals(
      ReportJson.encode(r),
      """{"verdict":"pass","summary":"pass","blocks":[],"advisories":[]}"""
    )
  }

  test("a block renders its check letter, snake_case kind, and fields") {
    val r = Report(
      Verdict.Fail,
      "fail: 1 block(s), 0 advisory(ies)",
      List(Block(Check.A, Kind.NewError, "a.scala", "Wart.Null", 10, "null used")),
      List.empty
    )
    val json = ReportJson.encode(r)
    assert(json.contains(""""verdict":"fail""""), json)
    assert(json.contains(""""check":"A""""), json)
    assert(json.contains(""""kind":"new_errors""""), json)
    assert(json.contains(""""file":"a.scala""""), json)
    assert(json.contains(""""line":10"""), json)
    assert(json.contains(""""message":"null used""""), json)
  }

  test("the compile-precondition block renders as check build / compile_error") {
    val r = Report(
      Verdict.Fail,
      "fail",
      List(Block(Check.Build, Kind.CompileError, "", "", 0, "build does not compile")),
      List.empty
    )
    val json = ReportJson.encode(r)
    assert(json.contains(""""check":"build""""), json)
    assert(json.contains(""""kind":"compile_error""""), json)
  }

  test("string escaping handles quotes, backslashes, newlines, and control characters") {
    assertEquals(ReportJson.str("a\"b\\c\nd\te"), "\"a\\\"b\\\\c\\nd\\te\"")
    val withControl = "x" + 1.toChar + "y" // a literal U+0001 built at runtime — source stays ASCII
    assertEquals(ReportJson.str(withControl), "\"x\\u0001y\"")
  }
