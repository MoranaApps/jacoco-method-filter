package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import TestSupport._

/**
 * Tests verifying behaviors documented in adoption feedback observations (JMF-NOTES.md).
 * Each test group references the corresponding observation section (section 1-13, section 10a-10n).
 */
class AdoptionObservationsSpec extends AnyFunSuite {

  // ---------------------------------------------------------------------------
  // section 1: Descriptor format must be JVM internal, not human-readable
  // ---------------------------------------------------------------------------

  test("section 1: human-readable descriptor does not match JVM descriptor") {
    val rule = Rules.parseLine("*QueryResultRow#apply(int)* id:s1-human").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "za.co.absa.QueryResultRow", "apply", "(I)Ljava/lang/Object;", acc),
      "human-readable 'int' should NOT match JVM descriptor 'I'")
  }

  test("section 1: JVM descriptor matches correctly") {
    val rule = Rules.parseLine("*QueryResultRow#apply(I)* id:s1-jvm").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "za.co.absa.QueryResultRow", "apply", "(I)Ljava/lang/Object;", acc),
      "JVM descriptor 'I' should match")
  }

  test("section 1: human-readable String descriptor does not match") {
    val rule = Rules.parseLine("*#getAs(java.lang.String,*)* id:s1-str-human").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "za.co.absa.QueryResultRow", "getAs",
      "(Ljava/lang/String;Lscala/reflect/ClassTag;)Ljava/lang/Object;", acc),
      "human-readable 'java.lang.String' should NOT match JVM format")
  }

  test("section 1: JVM String descriptor matches correctly") {
    val rule = Rules.parseLine("*#getAs(Ljava/lang/String;*)* id:s1-str-jvm").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "za.co.absa.QueryResultRow", "getAs",
      "(Ljava/lang/String;Lscala/reflect/ClassTag;)Ljava/lang/Object;", acc),
      "JVM format 'Ljava/lang/String;' should match")
  }

  // ---------------------------------------------------------------------------
  // section 2: FQCN globs must start with * to match qualified class names
  // ---------------------------------------------------------------------------

  test("section 2: bare class name without * prefix does not match FQCN") {
    val rule = Rules.parseLine("QueryResult#noMore() id:s2-bare").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "za.co.absa.db.balta.classes.QueryResult", "noMore", "()V", acc),
      "bare 'QueryResult' should NOT match fully-qualified name")
  }

  test("section 2: * prefix matches FQCN correctly") {
    val rule = Rules.parseLine("*QueryResult#noMore() id:s2-wildcard").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "za.co.absa.db.balta.classes.QueryResult", "noMore", "()V", acc),
      "'*QueryResult' should match fully-qualified name")
  }

  test("section 2: bare class name matches only exact class name") {
    val rule = Rules.parseLine("QueryResult#noMore() id:s2-exact").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "QueryResult", "noMore", "()V", acc),
      "bare name should match unqualified class name")
  }

  // ---------------------------------------------------------------------------
  // section 3: Non-matching rules are silently ignored
  // ---------------------------------------------------------------------------

  test("section 3: rule that matches nothing loads without error") {
    val file = write(tmpFile(), Seq(
      "com.nonexistent.pkg.DoesNotExist#neverCalled(*) id:s3-phantom"
    ))
    val rules = Rules.load(file)

    assert(rules.size == 1, "non-matching rule should still load")
    assert(!Rules.matches(rules.head, "com.example.Real", "method", "(I)V", access(public = true)),
      "phantom rule should not match any real method")
  }

  test("section 3: non-matching rule produces no error during matching") {
    val file = write(tmpFile(), Seq(
      "completely.wrong.Pattern$$$#$$method$$(I)V id:s3-wrong"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 1)

    val result = Rules.matches(rules.head, "com.example.Real", "doWork", "(I)V", access(public = true))
    assert(!result)
  }

  // ---------------------------------------------------------------------------
  // section 5: Scala 2.12 compiler-generated methods covered by globals
  // ---------------------------------------------------------------------------

  test("section 5: global rules cover $anonfun$ with synthetic flag") {
    val rule = Rules.parseLine("*#* synthetic name-contains:$anonfun$ id:scala-anonfun").get
    val acc = access(synthetic = true)

    assert(Rules.matches(rule, "com.example.Service", "$anonfun$process$1", "(I)V", acc))
    assert(Rules.matches(rule, "com.example.Service", "$anonfun$init$2", "()V", acc))
    assert(!Rules.matches(rule, "com.example.Service", "$anonfun$process$1", "(I)V", access(public = true)))
  }

  test("section 5: global rules cover $deserializeLambda$") {
    val rule = Rules.parseLine("*#$deserializeLambda$(*) id:scala-deser-lambda").get
    val acc = access(privateA = true, staticA = true)

    assert(Rules.matches(rule, "com.example.Handler", "$deserializeLambda$",
      "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;", acc))
  }

  test("section 5: global rules cover $extension methods") {
    val extensions = Seq(
      "hashCode$extension", "equals$extension", "toString$extension",
      "canEqual$extension", "productIterator$extension", "productElement$extension",
      "productArity$extension", "productPrefix$extension", "copy$extension",
      "copy$default$1$extension"
    )
    extensions.foreach { methodName =>
      val rule = Rules.parseLine(s"*#$methodName(*) id:valclass-ext-test").get
      val acc = access(public = true)
      assert(Rules.matches(rule, "com.example.MyVal$", methodName, "(I)I", acc),
        s"should match extension method: $methodName")
    }
  }

  test("section 5: global rules cover andThen/compose") {
    val rAndThen = Rules.parseLine("*#andThen(*) id:fn1-andthen").get
    val rCompose = Rules.parseLine("*#compose(*) id:fn1-compose").get
    val acc = access(public = true)

    assert(Rules.matches(rAndThen, "com.example.MyFunc", "andThen",
      "(Lscala/Function1;)Lscala/Function1;", acc))
    assert(Rules.matches(rCompose, "com.example.MyFunc", "compose",
      "(Lscala/Function1;)Lscala/Function1;", acc))
  }

  test("section 5: global rules cover default parameter accessors") {
    val rule = Rules.parseLine("*#*$default$*(*) id:gen-defaults").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.Config$", "apply$default$1", "()Ljava/lang/String;", acc))
    assert(Rules.matches(rule, "com.example.Config$", "apply$default$2", "()I", acc))
    assert(Rules.matches(rule, "com.example.Config$", "copy$default$1", "()Ljava/lang/String;", acc))
  }

  // ---------------------------------------------------------------------------
  // section 7: Avoid broad wildcards — prefer synthetic/bridge flags
  // ---------------------------------------------------------------------------

  test("section 7: synthetic flag restricts to ACC_SYNTHETIC methods only") {
    val rWithFlag = Rules.parseLine("*#$anonfun$*(*) synthetic id:s7-flag").get
    val rWithoutFlag = Rules.parseLine("*#$anonfun$*(*) id:s7-noflag").get

    val synthAcc = access(synthetic = true)
    val pubAcc = access(public = true)

    assert(Rules.matches(rWithFlag, "pkg.A", "$anonfun$do$1", "(I)V", synthAcc))
    assert(!Rules.matches(rWithFlag, "pkg.A", "$anonfun$do$1", "(I)V", pubAcc))

    assert(Rules.matches(rWithoutFlag, "pkg.A", "$anonfun$do$1", "(I)V", synthAcc))
    assert(Rules.matches(rWithoutFlag, "pkg.A", "$anonfun$do$1", "(I)V", pubAcc))
  }

  // ---------------------------------------------------------------------------
  // section 9: Rule file version header
  // ---------------------------------------------------------------------------

  test("section 9: file without version header loads rules normally") {
    val file = write(tmpFile(), Seq(
      "# No version header in this file",
      "*#copy(*) id:s9-no-header"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 1, "file without version header should load normally")
  }

  test("section 9: comment-style version header is silently ignored") {
    val file = write(tmpFile(), Seq(
      "# [jmf:1.0.0]",
      "*#copy(*) id:s9-comment-header"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 1, "comment version header should be ignored as a comment")
  }

  test("section 9: non-comment version header causes parse error") {
    val file = write(tmpFile(), Seq(
      "[jmf:1.0.0]",
      "*#copy(*) id:s9-raw-header"
    ))
    intercept[IllegalArgumentException] {
      Rules.load(file)
    }
  }

  // ---------------------------------------------------------------------------
  // section 10a: Colon-prefix flag syntax is wrong
  // ---------------------------------------------------------------------------

  test("section 10a: colon-prefix :synthetic is treated as part of descriptor, not a flag") {
    val rule = Rules.parseLine("*#*(*):synthetic id:s10a-colon").get

    assert(!rule.flags.contains("synthetic"),
      "colon-prefix :synthetic should NOT be parsed as a flag")

    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V",
      access(synthetic = true)),
      "rule with :synthetic in descriptor should not match real descriptors")
  }

  test("section 10a: space-separated synthetic flag works correctly") {
    val rule = Rules.parseLine("*#*(*) synthetic id:s10a-space").get

    assert(rule.flags.contains("synthetic"))
    assert(Rules.matches(rule, "com.example.Foo", "bar", "(I)V",
      access(synthetic = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V",
      access(public = true)))
  }

  test("section 10a: colon-prefix :bridge is also treated as descriptor") {
    val rule = Rules.parseLine("*#*(*):bridge id:s10a-bridge").get

    assert(!rule.flags.contains("bridge"),
      "colon-prefix :bridge should NOT be parsed as a flag")
    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V",
      access(bridge = true)),
      "rule with :bridge in descriptor should not match real descriptors")
  }

  // ---------------------------------------------------------------------------
  // section 10b: ret: predicate syntax is inconsistent
  // ---------------------------------------------------------------------------

  test("section 10b: colon-prefixed :ret:V is part of descriptor, not a predicate") {
    val rule = Rules.parseLine("*.jobs.*#*(*):ret:V id:s10b-colon").get

    assert(rule.retGlob.isEmpty,
      "colon-prefixed :ret:V should NOT be parsed as a ret predicate")
    assert(!Rules.matches(rule, "com.jobs.Runner", "execute", "()V", access(public = true)),
      "descriptor glob '(*):ret:V' should not match '()V'")
  }

  test("section 10b: space-separated ret:V works correctly") {
    val rule = Rules.parseLine("*.jobs.*#*(*) ret:V id:s10b-space").get

    assert(rule.retGlob.isDefined, "space-separated ret:V should be parsed as predicate")
    assert(Rules.matches(rule, "com.jobs.Runner", "execute", "()V", access(public = true)))
    assert(!Rules.matches(rule, "com.jobs.Runner", "execute", "()I", access(public = true)))
  }

  // ---------------------------------------------------------------------------
  // section 10e: Empty/short descriptor form equivalence
  // ---------------------------------------------------------------------------

  test("section 10e: omitted, (), and (*) all normalize to wildcard") {
    val rOmitted = Rules.parseLine("*#method id:s10e-omit").get
    val rEmpty = Rules.parseLine("*#method() id:s10e-empty").get
    val rStar = Rules.parseLine("*#method(*) id:s10e-star").get
    val acc = access(public = true)

    val descriptors = Seq("()V", "(I)I", "(Ljava/lang/String;)Ljava/lang/Object;")
    descriptors.foreach { d =>
      assert(Rules.matches(rOmitted, "pkg.A", "method", d, acc), s"omitted should match $d")
      assert(Rules.matches(rEmpty, "pkg.A", "method", d, acc), s"() should match $d")
      assert(Rules.matches(rStar, "pkg.A", "method", d, acc), s"(*) should match $d")
    }
  }

  // ---------------------------------------------------------------------------
  // section 10f: () reads as "no args" but means "any args" due to normalisation
  // ---------------------------------------------------------------------------

  test("section 10f: () matches methods WITH arguments due to normalization") {
    val rule = Rules.parseLine("*#productElement() id:s10f-parens").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.User", "productElement",
      "(I)Ljava/lang/Object;", acc),
      "() normalizes to (*)* and should match (I)Ljava/lang/Object;")

    assert(Rules.matches(rule, "com.example.User", "productElement", "()I", acc),
      "() should also match ()I")
  }

  // ---------------------------------------------------------------------------
  // section 10h: ret: object type globs — semicolon inconsistency
  // ---------------------------------------------------------------------------

  test("section 10h: ret: without trailing semicolon fails for exact object types") {
    val rExactNoSemi = Rules.parseLine("*#make(*) ret:Lcom/example/model/Id id:s10h-exact-nosemi").get
    val rExactWithSemi = Rules.parseLine("*#make(*) ret:Lcom/example/model/Id; id:s10h-exact-semi").get
    val acc = access(public = true)

    assert(!Rules.matches(rExactNoSemi, "x.M", "make", "(I)Lcom/example/model/Id;", acc),
      "ret:Lcom/example/model/Id (no semicolon) should NOT match 'Lcom/example/model/Id;'")
    assert(Rules.matches(rExactWithSemi, "x.M", "make", "(I)Lcom/example/model/Id;", acc),
      "ret:Lcom/example/model/Id; (with semicolon) should match 'Lcom/example/model/Id;'")
  }

  // ---------------------------------------------------------------------------
  // section 10j: # dual role — inline comments
  // ---------------------------------------------------------------------------

  test("section 10j: inline # comment after rule is ignored as unknown token") {
    val rule = Rules.parseLine("*$#<init>(*) id:gen-ctor # constructors").get

    assert(rule.id.contains("gen-ctor"), "id should be parsed correctly")
    assert(rule.flags.isEmpty, "no flags should be parsed")

    assert(Rules.matches(rule, "com.example.User$", "<init>", "(Ljava/lang/String;)V",
      access(public = true)))
  }

  test("section 10j: dedicated comment line is cleanly ignored") {
    val file = write(tmpFile(), Seq(
      "# constructors",
      "*$#<init>(*) id:gen-ctor"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 1)
    assert(rules.head.id.contains("gen-ctor"))
  }

  // ---------------------------------------------------------------------------
  // Additional: colon-prefixed forms fail silently (no crash)
  // ---------------------------------------------------------------------------

  test("colon-prefixed flags load without error but produce non-matching rules") {
    val file = write(tmpFile(), Seq(
      "*#*(*):synthetic id:col-synth",
      "*#*(*):bridge id:col-bridge",
      "*#*(*):ret:V id:col-ret"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 3, "all rules should load (no parse error)")

    val acc = access(public = true, synthetic = true, bridge = true)
    rules.foreach { r =>
      assert(!Rules.matches(r, "com.Foo", "bar", "()V", acc),
        s"colon-prefixed rule ${r.id.getOrElse("?")} should not match real descriptors")
    }
  }
}
