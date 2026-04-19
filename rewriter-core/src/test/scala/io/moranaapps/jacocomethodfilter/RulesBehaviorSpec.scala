package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import TestSupport._

/** Tests verifying rule matching behavior, global rule patterns, and rule syntax pitfalls. */
class RulesBehaviorSpec extends AnyFunSuite {

  // Descriptor format: must be JVM internal, not human-readable

  test("human-readable descriptor does not match JVM descriptor") {
    val rule = Rules.parseLine("*QueryResultRow#apply(int)* id:s1-human").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "com.example.QueryResultRow", "apply", "(I)Ljava/lang/Object;", acc),
      "human-readable 'int' should NOT match JVM descriptor 'I'")
  }

  test("JVM descriptor matches correctly") {
    val rule = Rules.parseLine("*QueryResultRow#apply(I)* id:s1-jvm").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.QueryResultRow", "apply", "(I)Ljava/lang/Object;", acc),
      "JVM descriptor 'I' should match")
  }

  test("human-readable String descriptor does not match") {
    val rule = Rules.parseLine("*#getAs(java.lang.String,*)* id:s1-str-human").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "com.example.QueryResultRow", "getAs",
      "(Ljava/lang/String;Lscala/reflect/ClassTag;)Ljava/lang/Object;", acc),
      "human-readable 'java.lang.String' should NOT match JVM format")
  }

  test("JVM String descriptor matches correctly") {
    val rule = Rules.parseLine("*#getAs(Ljava/lang/String;*)* id:s1-str-jvm").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.QueryResultRow", "getAs",
      "(Ljava/lang/String;Lscala/reflect/ClassTag;)Ljava/lang/Object;", acc),
      "JVM format 'Ljava/lang/String;' should match")
  }

  // FQCN globs: * prefix required to match qualified class names

  test("bare class name without * prefix does not match FQCN") {
    val rule = Rules.parseLine("QueryResult#noMore() id:s2-bare").get
    val acc = access(public = true)

    assert(!Rules.matches(rule, "com.example.db.balta.classes.QueryResult", "noMore", "()V", acc),
      "bare 'QueryResult' should NOT match fully-qualified name")
  }

  test("* prefix matches FQCN correctly") {
    val rule = Rules.parseLine("*QueryResult#noMore() id:s2-wildcard").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.db.balta.classes.QueryResult", "noMore", "()V", acc),
      "'*QueryResult' should match fully-qualified name")
  }

  test("bare class name matches only exact class name") {
    val rule = Rules.parseLine("QueryResult#noMore() id:s2-exact").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "QueryResult", "noMore", "()V", acc),
      "bare name should match unqualified class name")
  }

  // Non-matching rules: silently ignored, no error

  test("rule that matches nothing loads without error") {
    val file = write(tmpFile(), Seq("com.nonexistent.pkg.DoesNotExist#neverCalled(*) id:s3-phantom"))
    val rules = Rules.load(file)

    assert(rules.size == 1, "non-matching rule should still load")
    assert(!Rules.matches(rules.head, "com.example.Real", "method", "(I)V", access(public = true)),
      "phantom rule should not match any real method")
  }

  test("non-matching rule produces no error during matching") {
    val file = write(tmpFile(), Seq("completely.wrong.Pattern$$$#$$method$$(I)V id:s3-wrong"))
    val rules = Rules.load(file)
    assert(rules.size == 1)

    assert(!Rules.matches(rules.head, "com.example.Real", "doWork", "(I)V", access(public = true)))
  }

  // Global rules: case class scalar helpers

  test("global rules cover canEqual, equals, hashCode, unapply, toString") {
    val acc = access(public = true)
    val cls = "com.example.User"

    Seq(
      ("canEqual", "*#canEqual(*) id:case-canequal",   "(Ljava/lang/Object;)Z"),
      ("equals",   "*#equals(*) id:case-equals",       "(Ljava/lang/Object;)Z"),
      ("hashCode", "*#hashCode(*) id:case-hashcode",   "()I"),
      ("unapply",  "*#unapply(*) id:case-unapply",     "(Ljava/lang/Object;)Lscala/Option;"),
      ("toString", "*#toString() id:case-tostring",    "()Ljava/lang/String;")
    ).foreach { case (method, line, descriptor) =>
      val rule = Rules.parseLine(line).get
      assert(Rules.matches(rule, cls, method, descriptor, acc),
        s"global rule should match $method on $cls")
    }
  }

  test("global rule covers apply on any class (case-apply)") {
    val rule = Rules.parseLine("*#apply(*) id:case-apply").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.User$", "apply", "(Ljava/lang/String;I)Lcom/example/User;", acc))
    assert(Rules.matches(rule, "com.example.Event$", "apply", "()Lcom/example/Event;", acc))
    assert(Rules.matches(rule, "com.example.User", "apply", "(I)Ljava/lang/Object;", acc),
      "case-apply matches apply on non-companion classes too — rescue with + include rule if needed")
  }

  test("global rules cover copy and copy$default$*") {
    val acc = access(public = true)
    val cls = "com.example.User"

    val rCopy = Rules.parseLine("*#copy(*) id:case-copy").get
    assert(Rules.matches(rCopy, cls, "copy", "(Ljava/lang/String;I)Lcom/example/User;", acc))

    val rDefault = Rules.parseLine("*#copy$default$*(*) id:case-copy-defaults").get
    assert(Rules.matches(rDefault, cls + "$", "copy$default$1", "()Ljava/lang/String;", acc))
    assert(Rules.matches(rDefault, cls + "$", "copy$default$2", "()I", acc))
  }

  // Global rules: Product trait helpers

  test("global rules cover productElement, productArity, productPrefix, productIterator") {
    val acc = access(public = true)
    val cls = "com.example.User"

    Seq(
      ("productElement",  "*#productElement() id:case-prod-element",     "(I)Ljava/lang/Object;"),
      ("productArity",    "*#productArity() id:case-prod-arity",         "()I"),
      ("productPrefix",   "*#productPrefix() id:case-prod-prefix",       "()Ljava/lang/String;"),
      ("productIterator", "*#productIterator() id:case-prod-iterator",   "()Lscala/collection/Iterator;")
    ).foreach { case (method, line, descriptor) =>
      val rule = Rules.parseLine(line).get
      assert(Rules.matches(rule, cls, method, descriptor, acc),
        s"global rule should match $method")
    }
  }

  test("global rules cover productElementName and productElementNames (Scala 2.13+)") {
    val acc = access(public = true)
    val cls = "com.example.User"

    val rName  = Rules.parseLine("*#productElementName(*) id:case-prod-element-name").get
    val rNames = Rules.parseLine("*#productElementNames() id:case-prod-element-names").get

    assert(Rules.matches(rName,  cls, "productElementName",  "(I)Ljava/lang/String;", acc))
    assert(Rules.matches(rNames, cls, "productElementNames", "()Lscala/collection/immutable/Iterator;", acc))
  }

  test("global rules cover tupled and curried") {
    val acc = access(public = true)
    val cls = "com.example.MyFunc$"

    val rTupled  = Rules.parseLine("*#tupled() id:case-tupled").get
    val rCurried = Rules.parseLine("*#curried() id:case-curried").get

    assert(Rules.matches(rTupled,  cls, "tupled",  "(Lscala/Function1;)Lscala/Function1;", acc))
    assert(Rules.matches(rCurried, cls, "curried", "(Lscala/Function1;)Lscala/Function1;", acc))
  }

  test("global rules for name, groups, optionalAttributes match any class (collision risk)") {
    val acc = access(public = true)

    Seq(
      ("name",               "*#name() id:case-name"),
      ("groups",             "*#groups() id:case-groups"),
      ("optionalAttributes", "*#optionalAttributes() id:case-optionalAttributes")
    ).foreach { case (method, line) =>
      val rule = Rules.parseLine(line).get
      assert(Rules.matches(rule, "com.example.Regex$", method, "()Ljava/lang/String;", acc),
        s"should match compiler-generated $method")
      assert(Rules.matches(rule, "com.example.Config", method, "()Ljava/lang/String;", acc),
        s"$method also matches domain classes — rescue with + include rule if needed")
    }
  }

  // Global rules: companion objects and constructors

  test("global rule covers companion <init>") {
    val rule = Rules.parseLine("*$#<init>(*) id:gen-ctor").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.User$", "<init>", "(Ljava/lang/String;)V", acc))
    assert(!Rules.matches(rule, "com.example.User", "<init>", "(Ljava/lang/String;)V", acc),
      "gen-ctor targets only companion $ classes")
  }

  test("global rule covers companion <clinit>") {
    val rule = Rules.parseLine("*$#<clinit>() id:gen-clinit").get
    val acc  = access(staticA = true)

    assert(Rules.matches(rule, "com.example.Config$", "<clinit>", "()V", acc))
    assert(!Rules.matches(rule, "com.example.Config", "<clinit>", "()V", acc),
      "gen-clinit targets only companion $ classes")
  }

  test("global rule covers writeReplace (Java serialization hook on case class)") {
    val rule = Rules.parseLine("*#writeReplace(*) id:case-writereplace").get
    val acc  = access(privateA = true)

    assert(Rules.matches(rule, "com.example.User",  "writeReplace", "()Ljava/lang/Object;", acc))
    assert(Rules.matches(rule, "com.example.Event", "writeReplace", "()Ljava/lang/Object;", acc))
    assert(!Rules.matches(rule, "com.example.User",  "writeObject", "()V", acc),
      "must not match writeObject — only writeReplace")
  }

  test("global rules cover companion apply, unapply, toString, readResolve") {
    val acc = access(public = true)

    Seq(
      ("apply",       "*$*#apply(*) id:comp-apply",             "(Ljava/lang/String;)Lcom/example/User;"),
      ("unapply",     "*$*#unapply(*) id:comp-unapply",         "(Lcom/example/User;)Lscala/Option;"),
      ("toString",    "*$*#toString(*) id:comp-tostring",        "()Ljava/lang/String;"),
      ("readResolve", "*$*#readResolve(*) id:comp-readresolve", "()Ljava/lang/Object;")
    ).foreach { case (method, line, descriptor) =>
      val rule = Rules.parseLine(line).get
      assert(Rules.matches(rule, "com.example.User$", method, descriptor, acc),
        s"global rule should match companion $method")
      assert(!Rules.matches(rule, "com.example.User", method, descriptor, acc),
        s"companion rule must not match non-companion class for $method")
    }
  }

  // Global rules: macro expansion helpers

  test("global rules cover macro expansion anonfun and inst methods") {
    val acc = access(public = true)

    val rAnonfun = Rules.parseLine("*$macro$*#$anonfun$inst$macro$* id:macro-inst").get
    val rInst    = Rules.parseLine("*$macro$*#inst$macro$* id:macro-inst").get

    assert(Rules.matches(rAnonfun, "com.example.Foo$macro$1", "$anonfun$inst$macro$1", "(I)V", acc))
    assert(Rules.matches(rInst,    "com.example.Foo$macro$2", "inst$macro$2",          "()V", acc))
    assert(!Rules.matches(rAnonfun, "com.example.Foo", "$anonfun$inst$macro$1", "(I)V", acc),
      "macro rule must not match classes without $macro$ in the name")
  }

  // Global rules: lambda, lazy val, deserializeLambda, extensions, andThen/compose, defaults

  test("global rules cover $anonfun$ with synthetic flag") {
    val rule = Rules.parseLine("*#* synthetic name-contains:$anonfun$ id:scala-anonfun").get
    val acc = access(synthetic = true)

    assert(Rules.matches(rule, "com.example.Service", "$anonfun$process$1", "(I)V", acc))
    assert(Rules.matches(rule, "com.example.Service", "$anonfun$init$2", "()V", acc))
    assert(!Rules.matches(rule, "com.example.Service", "$anonfun$process$1", "(I)V", access(public = true)))
  }

  test("global rule covers $lzycompute (lazy val synchronization wrapper)") {
    val rule = Rules.parseLine("*#*$lzycompute(*) id:scala-lzycompute").get
    val acc = access(privateA = true)

    assert(Rules.matches(rule, "com.example.Service", "cache$lzycompute", "()Ljava/lang/Object;", acc))
    assert(Rules.matches(rule, "com.example.Config",  "conn$lzycompute",  "()V", access()))
    assert(!Rules.matches(rule, "com.example.Service", "cache", "()Ljava/lang/Object;", acc),
      "must not match the accessor — only the $lzycompute companion method")
  }

  test("global rules cover $deserializeLambda$") {
    val rule = Rules.parseLine("*#$deserializeLambda$(*) id:scala-deser-lambda").get
    val acc = access(privateA = true, staticA = true)

    assert(Rules.matches(rule, "com.example.Handler", "$deserializeLambda$",
      "(Ljava/lang/invoke/SerializedLambda;)Ljava/lang/Object;", acc))
  }

  test("global rules cover all $extension methods") {
    val extensions = Seq(
      "hashCode$extension", "equals$extension", "toString$extension",
      "canEqual$extension", "productIterator$extension", "productElement$extension",
      "productArity$extension", "productPrefix$extension", "copy$extension",
      "copy$default$1$extension"
    )
    extensions.foreach { methodName =>
      val rule = Rules.parseLine(s"*#$methodName(*) id:valclass-ext-test").get
      assert(Rules.matches(rule, "com.example.MyVal$", methodName, "(I)I", access(public = true)),
        s"should match extension method: $methodName")
    }
  }

  test("global rules cover andThen and compose") {
    val rAndThen = Rules.parseLine("*#andThen(*) id:fn1-andthen").get
    val rCompose = Rules.parseLine("*#compose(*) id:fn1-compose").get
    val acc = access(public = true)

    assert(Rules.matches(rAndThen, "com.example.MyFunc", "andThen",
      "(Lscala/Function1;)Lscala/Function1;", acc))
    assert(Rules.matches(rCompose, "com.example.MyFunc", "compose",
      "(Lscala/Function1;)Lscala/Function1;", acc))
  }

  test("global rules cover default parameter accessors") {
    val rule = Rules.parseLine("*#*$default$*(*) id:gen-defaults").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.Config$", "apply$default$1", "()Ljava/lang/String;", acc))
    assert(Rules.matches(rule, "com.example.Config$", "apply$default$2", "()I", acc))
    assert(Rules.matches(rule, "com.example.Config$", "copy$default$1", "()Ljava/lang/String;", acc))
  }

  // Synthetic flag: restricts to ACC_SYNTHETIC methods only

  test("synthetic flag restricts to ACC_SYNTHETIC methods only") {
    val rWithFlag    = Rules.parseLine("*#$anonfun$*(*) synthetic id:flag-synth").get
    val rWithoutFlag = Rules.parseLine("*#$anonfun$*(*) id:noflag-synth").get

    val synthAcc = access(synthetic = true)
    val pubAcc   = access(public = true)

    assert(Rules.matches(rWithFlag, "pkg.A", "$anonfun$do$1", "(I)V", synthAcc))
    assert(!Rules.matches(rWithFlag, "pkg.A", "$anonfun$do$1", "(I)V", pubAcc))
    assert(Rules.matches(rWithoutFlag, "pkg.A", "$anonfun$do$1", "(I)V", synthAcc))
    assert(Rules.matches(rWithoutFlag, "pkg.A", "$anonfun$do$1", "(I)V", pubAcc))
  }

  // Version header handling

  test("file without version header loads rules normally") {
    val file = write(tmpFile(), Seq("# No version header", "*#copy(*) id:no-header"))
    val rules = Rules.load(file)
    assert(rules.size == 1)
  }

  test("comment-style version header is silently ignored") {
    val file = write(tmpFile(), Seq("# [jmf:1.0.0]", "*#copy(*) id:comment-header"))
    val rules = Rules.load(file)
    assert(rules.size == 1)
  }

  test("non-comment version header causes parse error") {
    val file = write(tmpFile(), Seq("[jmf:1.0.0]", "*#copy(*) id:raw-header"))
    intercept[IllegalArgumentException] { Rules.load(file) }
  }

  // Flag syntax: space-separated, not colon-prefixed

  test("colon-prefix :synthetic is treated as part of descriptor, not a flag") {
    val rule = Rules.parseLine("*#*(*):synthetic id:colon-synth").get

    assert(!rule.flags.contains("synthetic"))
    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V", access(synthetic = true)),
      "rule with :synthetic in descriptor should not match real descriptors")
  }

  test("space-separated synthetic flag works correctly") {
    val rule = Rules.parseLine("*#*(*) synthetic id:space-synth").get

    assert(rule.flags.contains("synthetic"))
    assert(Rules.matches(rule, "com.example.Foo", "bar", "(I)V", access(synthetic = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V", access(public = true)))
  }

  test("colon-prefix :bridge is treated as part of descriptor, not a flag") {
    val rule = Rules.parseLine("*#*(*):bridge id:colon-bridge").get

    assert(!rule.flags.contains("bridge"))
    assert(!Rules.matches(rule, "com.example.Foo", "bar", "(I)V", access(bridge = true)),
      "rule with :bridge in descriptor should not match real descriptors")
  }

  // Predicate syntax: ret: must be space-separated

  test("colon-prefixed :ret:V is part of descriptor, not a predicate") {
    val rule = Rules.parseLine("*.jobs.*#*(*):ret:V id:colon-ret").get

    assert(rule.retGlob.isEmpty)
    assert(!Rules.matches(rule, "com.jobs.Runner", "execute", "()V", access(public = true)))
  }

  test("space-separated ret:V works correctly") {
    val rule = Rules.parseLine("*.jobs.*#*(*) ret:V id:space-ret").get

    assert(rule.retGlob.isDefined)
    assert(Rules.matches(rule, "com.jobs.Runner", "execute", "()V", access(public = true)))
    assert(!Rules.matches(rule, "com.jobs.Runner", "execute", "()I", access(public = true)))
  }

  // Descriptor normalization: omitted / () / (*) are all wildcards

  test("omitted, (), and (*) all normalize to wildcard") {
    val rOmitted = Rules.parseLine("*#method id:norm-omit").get
    val rEmpty   = Rules.parseLine("*#method() id:norm-empty").get
    val rStar    = Rules.parseLine("*#method(*) id:norm-star").get
    val acc = access(public = true)

    Seq("()V", "(I)I", "(Ljava/lang/String;)Ljava/lang/Object;").foreach { d =>
      assert(Rules.matches(rOmitted, "pkg.A", "method", d, acc), s"omitted should match $d")
      assert(Rules.matches(rEmpty,   "pkg.A", "method", d, acc), s"() should match $d")
      assert(Rules.matches(rStar,    "pkg.A", "method", d, acc), s"(*) should match $d")
    }
  }

  test("() looks like no-arg but matches all overloads due to normalization") {
    val rule = Rules.parseLine("*#productElement() id:norm-parens").get
    val acc = access(public = true)

    assert(Rules.matches(rule, "com.example.User", "productElement", "(I)Ljava/lang/Object;", acc),
      "() normalizes to (*)* and should match (I)Ljava/lang/Object;")
    assert(Rules.matches(rule, "com.example.User", "productElement", "()I", acc))
  }

  // ret: semicolon: object type globs require trailing semicolon

  test("ret: without trailing semicolon does not match object return type") {
    val rNoSemi   = Rules.parseLine("*#make(*) ret:Lcom/example/model/Id id:ret-nosemi").get
    val rWithSemi = Rules.parseLine("*#make(*) ret:Lcom/example/model/Id; id:ret-semi").get
    val acc = access(public = true)

    assert(!Rules.matches(rNoSemi,   "x.M", "make", "(I)Lcom/example/model/Id;", acc))
    assert(Rules.matches(rWithSemi,  "x.M", "make", "(I)Lcom/example/model/Id;", acc))
  }

  // Inline comment handling: # after rule is ignored

  test("inline # comment after rule is ignored as unknown token") {
    val rule = Rules.parseLine("*$#<init>(*) id:gen-ctor # constructors").get

    assert(rule.id.contains("gen-ctor"))
    assert(rule.flags.isEmpty)
    assert(Rules.matches(rule, "com.example.User$", "<init>", "(Ljava/lang/String;)V", access(public = true)))
  }

  test("dedicated comment line is cleanly ignored") {
    val file = write(tmpFile(), Seq("# constructors", "*$#<init>(*) id:gen-ctor"))
    val rules = Rules.load(file)
    assert(rules.size == 1)
    assert(rules.head.id.contains("gen-ctor"))
  }

  // Colon-prefixed forms: load without crash but never match

  test("colon-prefixed flags load without error but produce non-matching rules") {
    val file = write(tmpFile(), Seq(
      "*#*(*):synthetic id:col-synth",
      "*#*(*):bridge id:col-bridge",
      "*#*(*):ret:V id:col-ret"
    ))
    val rules = Rules.load(file)
    assert(rules.size == 3)

    val acc = access(public = true, synthetic = true, bridge = true)
    rules.foreach { r =>
      assert(!Rules.matches(r, "com.Foo", "bar", "()V", acc),
        s"colon-prefixed rule ${r.id.getOrElse("?")} should not match real descriptors")
    }
  }

  // Include rules (+ prefix): rescue methods from broad exclusions

  test("+ prefix produces an Include-mode rule") {
    val rule = Rules.parseLine("+com.example.Config$#apply(*) id:keep-config-apply").get
    assert(rule.mode == Include)
    assert(rule.id.contains("keep-config-apply"))
  }

  test("+ with whitespace after + is still parsed as Include") {
    val rule = Rules.parseLine("+ com.example.Config$#apply(*) id:keep-config-apply").get
    assert(rule.mode == Include)
    assert(Rules.matches(rule, "com.example.Config$", "apply", "(Ljava/lang/String;)V", access(public = true)))
  }

  test("include rule matches the same method as a corresponding exclude rule") {
    val excl = Rules.parseLine("*#apply(*) id:case-apply").get
    val incl = Rules.parseLine("+com.example.Config$#apply(*) id:keep-config-apply").get
    val acc  = access(public = true)

    assert(Rules.matches(excl, "com.example.Config$", "apply", "(Ljava/lang/String;)Lcom/example/Config;", acc))
    assert(Rules.matches(incl, "com.example.Config$", "apply", "(Ljava/lang/String;)Lcom/example/Config;", acc))
  }

  test("include wins over exclude regardless of rule order in file") {
    val rulesInclFirst = Seq(
      Rules.parseLine("+com.example.Config$#apply(*) id:keep-config-apply").get,
      Rules.parseLine("*#apply(*) id:case-apply").get
    )
    val rulesExclFirst = Seq(
      Rules.parseLine("*#apply(*) id:case-apply").get,
      Rules.parseLine("+com.example.Config$#apply(*) id:keep-config-apply").get
    )
    val acc  = access(public = true)
    val fqcn = "com.example.Config$"
    val desc = "(Ljava/lang/String;)Lcom/example/Config;"

    assert(RuleResolver.resolve(rulesInclFirst, fqcn, "apply", desc, acc).isRescued, "include-first: rescued")
    assert(RuleResolver.resolve(rulesExclFirst, fqcn, "apply", desc, acc).isRescued, "exclude-first: rescued")
  }

  // name-starts: and name-ends: predicates

  test("name-starts: matches only methods whose name begins with prefix") {
    val rule = Rules.parseLine("*#*(*) name-starts:get id:starts-get").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Repo", "getById",   "(I)Ljava/lang/Object;", acc))
    assert(Rules.matches(rule, "com.example.Repo", "getName",   "()Ljava/lang/String;",  acc))
    assert(!Rules.matches(rule, "com.example.Repo", "setName",  "(Ljava/lang/String;)V", acc))
    assert(!Rules.matches(rule, "com.example.Repo", "doGet",    "()V",                   acc),
      "name-starts: must not match mid-name occurrence")
  }

  test("name-ends: matches only methods whose name ends with suffix") {
    val rule = Rules.parseLine("*#*(*) name-ends:$eq id:ends-eq").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Bean", "name_$eq",    "(Ljava/lang/String;)V", acc),
      "Scala setter name_$eq should match name-ends:$eq")
    assert(Rules.matches(rule, "com.example.Bean", "value_$eq",   "(I)V",                  acc))
    assert(!Rules.matches(rule, "com.example.Bean", "$eq_name",   "(Ljava/lang/String;)V", acc),
      "must not match when suffix appears at start, not end")
    assert(!Rules.matches(rule, "com.example.Bean", "getValue",   "()Ljava/lang/String;",  acc))
  }

  test("name-starts: and name-ends: can be combined in one rule") {
    val rule = Rules.parseLine("*#*(*) name-starts:get name-ends:Id id:starts-get-ends-id").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Repo", "getById",    "(I)Ljava/lang/Object;", acc))
    assert(Rules.matches(rule, "com.example.Repo", "getUserId",  "(I)Ljava/lang/Object;", acc))
    assert(!Rules.matches(rule, "com.example.Repo", "findById",  "(I)Ljava/lang/Object;", acc),
      "fails name-starts: check")
    assert(!Rules.matches(rule, "com.example.Repo", "getByName", "(Ljava/lang/String;)Ljava/lang/Object;", acc),
      "fails name-ends: check")
  }

  // name-contains: as isolated predicate

  test("name-contains: matches when substring is present in method name") {
    val rule = Rules.parseLine("*#*(*) name-contains:$anon id:contains-anon").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Foo", "outer$anon$1", "()V", acc))
    assert(Rules.matches(rule, "com.example.Foo", "$anonfun$1",   "()V", acc))
    assert(!Rules.matches(rule, "com.example.Foo", "doWork",      "()V", acc),
      "must not match when substring is absent")
    assert(!Rules.matches(rule, "com.example.Foo", "anon",        "()V", acc),
      "must not match when $ prefix is absent")
  }

  // Multiple flags combined: AND semantics

  test("multiple flags require all to be present (AND semantics)") {
    val rule = Rules.parseLine("*#*(*) synthetic bridge id:synth-bridge").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(synthetic = true, bridge = true)),
      "both synthetic and bridge set — should match")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(synthetic = true)),
      "only synthetic — bridge missing, should not match")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(bridge = true)),
      "only bridge — synthetic missing, should not match")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)),
      "neither flag set — should not match")
  }

  // Access-level flags as rule-side filters

  test("public flag restricts rule to public methods only") {
    val rule = Rules.parseLine("*#*(*) public id:pub-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(privateA = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(protectedA = true)))
  }

  test("private flag restricts rule to private methods only") {
    val rule = Rules.parseLine("*#*(*) private id:priv-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(privateA = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
  }

  test("protected flag restricts rule to protected methods only") {
    val rule = Rules.parseLine("*#*(*) protected id:prot-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(protectedA = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
  }

  test("static flag restricts rule to static methods only") {
    val rule = Rules.parseLine("*#*(*) static id:static-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(staticA = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
  }

  test("abstract flag restricts rule to abstract methods only") {
    val rule = Rules.parseLine("*#*(*) abstract id:abstract-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(abstractA = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
  }

  test("bridge flag restricts rule to bridge methods only") {
    val rule = Rules.parseLine("*#*(*) bridge id:bridge-only").get

    assert(Rules.matches(rule, "com.example.Foo", "m", "()V", access(bridge = true)))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(synthetic = true)),
      "synthetic alone does not satisfy bridge flag")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V", access(public = true)))
  }

  // Duplicate predicate tokens: last value wins

  test("duplicate id: tokens — last value wins") {
    val rule = Rules.parseLine("*#copy(*) id:first id:second").get
    assert(rule.id.contains("second"), "last id: token should win")
  }

  test("duplicate name-contains:/name-starts:/name-ends: tokens — last value wins") {
    val rContains = Rules.parseLine("*#*(*) name-contains:foo name-contains:bar id:dup-contains").get
    val rStarts   = Rules.parseLine("*#*(*) name-starts:get name-starts:set id:dup-starts").get
    val rEnds     = Rules.parseLine("*#*(*) name-ends:$eq name-ends:$buf id:dup-ends").get
    val acc       = access(public = true)

    assert(rContains.nameContains.contains("bar"),  "last name-contains: should win")
    assert(rStarts.nameStarts.contains("set"),      "last name-starts: should win")
    assert(rEnds.nameEnds.contains("$buf"),         "last name-ends: should win")

    assert(Rules.matches(rContains, "com.example.Foo", "foobar", "()V", acc), "bar is substring of foobar")
    assert(!Rules.matches(rContains, "com.example.Foo", "foo",   "()V", acc), "first value overwritten")
  }

  // ? wildcard in glob: matches exactly one character

  test("? in class selector matches exactly one character") {
    val rule = Rules.parseLine("com.example.Us?r#copy(*) id:q-cls").get
    val acc  = access(public = true)

    assert(Rules.matches(rule,  "com.example.User", "copy", "(Ljava/lang/String;)V", acc))
    assert(!Rules.matches(rule, "com.example.Usr",  "copy", "(Ljava/lang/String;)V", acc),
      "? requires exactly one char — zero chars should not match")
    assert(!Rules.matches(rule, "com.example.Userr", "copy", "(Ljava/lang/String;)V", acc),
      "? requires exactly one char — two chars should not match")
  }

  test("? in method selector matches exactly one character") {
    val rule = Rules.parseLine("*#ge?(*) id:q-method").get
    val acc  = access(public = true)

    assert(Rules.matches(rule,  "com.example.Foo", "get", "()V", acc))
    assert(!Rules.matches(rule, "com.example.Foo", "ge",  "()V", acc))
    assert(!Rules.matches(rule, "com.example.Foo", "getX","()V", acc))
  }

  // Package-scoped class glob

  test("package-scoped class glob matches only classes in that package") {
    val rule = Rules.parseLine("com.example.model.*#copy(*) id:pkg-scope").get
    val acc  = access(public = true)

    assert(Rules.matches(rule,  "com.example.model.User",  "copy", "(Ljava/lang/String;)V", acc))
    assert(Rules.matches(rule,  "com.example.model.Event", "copy", "(Ljava/lang/String;)V", acc))
    assert(!Rules.matches(rule, "com.example.service.UserService", "copy", "(Ljava/lang/String;)V", acc),
      "different package — must not match")
    assert(!Rules.matches(rule, "com.other.model.User", "copy", "(Ljava/lang/String;)V", acc),
      "different root package — must not match")
  }

  // Exact dot-form FQCN without wildcards

  test("exact FQCN class selector matches that class and nothing else") {
    val rule = Rules.parseLine("com.example.Foo#method(*) id:exact-fqcn").get
    val acc  = access(public = true)

    assert(Rules.matches(rule,  "com.example.Foo",    "method", "()V", acc))
    assert(!Rules.matches(rule, "com.example.FooBar", "method", "()V", acc),
      "must not match a class that merely starts with the name")
    assert(!Rules.matches(rule, "com.other.Foo",      "method", "()V", acc),
      "must not match same simple name in a different package")
  }

  // ret: with wildcard and array patterns

  test("ret: wildcard glob matches any return type starting with prefix") {
    val rule = Rules.parseLine("*#*(*) ret:Lscala/* id:ret-scala-any").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Foo", "m", "()Lscala/Option;",     acc))
    assert(Rules.matches(rule, "com.example.Foo", "m", "()Lscala/Some;",       acc))
    assert(Rules.matches(rule, "com.example.Foo", "m", "()Lscala/collection/Iterator;", acc))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()Ljava/lang/String;", acc))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V",                  acc))
  }

  test("ret: matches primitive array return type [I (int[])") {
    val rule = Rules.parseLine("*#*(*) ret:[I id:ret-int-array").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Foo", "m", "()[I",  acc))
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()[J", acc), "long[] should not match int[]")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()I",  acc), "int should not match int[]")
  }

  test("ret: wildcard [* matches any array return type") {
    val rule = Rules.parseLine("*#*(*) ret:[* id:ret-any-array").get
    val acc  = access(public = true)

    assert(Rules.matches(rule, "com.example.Foo", "m", "()[I",                    acc), "int[]")
    assert(Rules.matches(rule, "com.example.Foo", "m", "()[Ljava/lang/String;",   acc), "String[]")
    assert(Rules.matches(rule, "com.example.Foo", "m", "()[[I",                   acc), "int[][]")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()Ljava/lang/String;",   acc), "non-array")
    assert(!Rules.matches(rule, "com.example.Foo", "m", "()V",                    acc))
  }
}
