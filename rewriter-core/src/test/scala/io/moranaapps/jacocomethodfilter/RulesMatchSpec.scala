package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.TableDrivenPropertyChecks._
import TestSupport._

class RulesMatchSpec extends AnyFunSuite {

  // Build a single rule via load() so we also test the parser path
  private def loadOne(line: String) = {
    val p = write(tmpFile(), Seq(line))
    val rs = Rules.load(p)
    assert(rs.size == 1)
    rs.head
  }

  test("class glob matches when input is dot-form; rules may use dot or slash") {
    val rDot = loadOne("com.example.*#copy public")
    val acc  = access(public = true)

    val ok = Seq("com.example.User", "com.example.sub.User")
    ok.foreach { cls =>
      assert(Rules.matches(rDot, cls, "copy", desc("I", "V"), acc))
    }

    val ko = Seq("com.examples.User", "org.example.User")
    ko.foreach { cls =>
      assert(!Rules.matches(rDot, cls, "copy", desc("I", "V"), acc))
    }

    // Slash-style rule still matches dot-form input
    val rSlash = loadOne("com/example/*#copy public")
    assert(Rules.matches(rSlash, "com.example.User", "copy", desc("I", "V"), acc))
    assert(!Rules.matches(rSlash, "org.example.User", "copy", desc("I", "V"), acc))
  }

  test("name helpers: contains/starts/ends must all pass when present") {
    // Descriptor omitted -> wildcard
    val r = loadOne("pkg.A#$anonfun$* synthetic name-contains:fun name-starts:$anonfun$ name-ends:$1")
    val acc = access(synthetic = true)

    assert(Rules.matches(r, "pkg.A", "$anonfun$do$1", desc("", "V"), acc))
    assert(!Rules.matches(r, "pkg.A", "$anonfun$do$2", desc("", "V"), acc)) // wrong ends
    assert(!Rules.matches(r, "pkg.A", "anonfun$do$1",  desc("", "V"), acc)) // wrong starts
    assert(!Rules.matches(r, "pkg.A", "$anon$do$1",    desc("", "V"), acc)) // wrong contains
  }

  test("flags: public/protected/private/synthetic/bridge/static/abstract") {
    // All with descriptor omitted -> wildcard
    val rPubSynth  = loadOne("x.Y#foo public synthetic")
    val rPrivBridge= loadOne("x.Y#foo private bridge")
    val rStatic    = loadOne("x.Y#foo static")
    val rAbstract  = loadOne("x.Y#foo abstract")
    val d = desc("I", "I")

    assert(Rules.matches(rPubSynth, "x.Y", "foo", d, access(public = true,  synthetic = true)))
    assert(!Rules.matches(rPubSynth, "x.Y", "foo", d, access(public = true))) // missing synthetic

    assert(Rules.matches(rPrivBridge, "x.Y", "foo", d, access(privateA = true, bridge = true)))
    assert(!Rules.matches(rPrivBridge, "x.Y", "foo", d, access(privateA = true)))

    assert(Rules.matches(rStatic, "x.Y", "foo", d, access(staticA = true)))
    assert(!Rules.matches(rStatic, "x.Y", "foo", d, access(public = true)))

    assert(Rules.matches(rAbstract, "x.Y", "foo", d, access(abstractA = true)))
    assert(!Rules.matches(rAbstract, "x.Y", "foo", d, access(public = true)))
  }

  test("descriptor exact match AND return-only ret:<glob>") {
    // r1: explicit descriptor must match exactly
    val r1 = loadOne("x.Z#bar(I)I public")

    // r2/r3: descriptor omitted -> wildcard; ret:<glob> constrains only the return
    val r2 = loadOne("x.Z#bar public ret:I")
    val r3 = loadOne("x.Z#bar public ret:V")

    val acc = access(public = true)

    // r1 exact desc
    assert(Rules.matches(r1, "x.Z", "bar", desc("I", "I"), acc))
    assert(!Rules.matches(r1, "x.Z", "bar", desc("I", "V"), acc))

    // r2 ret must be I (args don't matter)
    assert(Rules.matches(r2, "x.Z", "bar", desc("Ljava/lang/String;", "I"), acc))
    assert(!Rules.matches(r2, "x.Z", "bar", desc("I", "V"), acc))

    // r3 ret must be V (args don't matter)
    assert(Rules.matches(r3, "x.Z", "bar", desc("", "V"), acc))
    assert(!Rules.matches(r3, "x.Z", "bar", desc("I", "I"), acc))
  }

  test("table-driven: grow your catalog fast") {
    // Descriptor omitted -> wildcard
    val rule    = loadOne("com.example.model.*#$anonfun$* synthetic")
    val accSynth= access(synthetic = true)
    val accPub  = access(public = true)

    val rows = Table(
      ("cls",                         "method",           "desc",           "access",   "expect"),
      ("com.example.model.User$",     "$anonfun$do$1",    desc("I", "V"),   accSynth,    true),
      ("com.example.model.User",      "do",               desc("I", "V"),   accSynth,    false),
      ("com.example.other.User$",     "$anonfun$do$1",    desc("I", "V"),   accSynth,    false),
      ("com.example.model.User$",     "$anonfun$do$1",    desc("I", "V"),   accPub,      false) // missing synthetic
    )

    forAll(rows) { (cls, m, d, a, e) =>
      assert(Rules.matches(rule, cls, m, d, a) == e, s"$cls#$m$d")
    }
  }

  test("omitted descriptor == () == (*)") {
    val rNo   = loadOne("pkg.C#go public")
    val rPar  = loadOne("pkg.C#go() public")
    val rStar = loadOne("pkg.C#go(*) public")
    val acc = access(public = true)

    val ds = Seq(desc("", "V"), desc("I", "I"), desc("Ljava/lang/String;", "I"))
    ds.foreach { d =>
      assert(Rules.matches(rNo,  "pkg.C", "go", d, acc))
      assert(Rules.matches(rPar, "pkg.C", "go", d, acc))
      assert(Rules.matches(rStar,"pkg.C", "go", d, acc))
    }
  }

  test("class glob accepts $ for Scala inner/object names") {
    val r = loadOne("com.example.*$#copy")
    val acc = access()
    assert(Rules.matches(r, "com.example.User$", "copy", desc("", "V"), acc))
    assert(!Rules.matches(r, "com.example.User",  "copy", desc("", "V"), acc))
  }

  test("no flags -> any access matches") {
    val r = loadOne("x.A#f") // no flags
    val ds = desc("I", "I")
    val accs = Seq(
      access(public = true),
      access(privateA = true),
      access(protectedA = true),
      access(synthetic = true),
      access(bridge = true),
      access(staticA = true),
      access(abstractA = true)
    )
    accs.foreach(a => assert(Rules.matches(r, "x.A", "f", ds, a)))
  }

  test("unknown tokens are ignored") {
    val r = loadOne("x.A#f weird-token another:thing public")
    assert(r.flags == Set("public"))
    val acc = access(public = true)
    assert(Rules.matches(r, "x.A", "f", desc("", "V"), acc))
  }

  test("ret:<glob> with package globs; desc still enforced when present") {
    val r1 = loadOne("x.M#make(*) ret:Lcom/example/model/*;") // desc omitted -> wildcard
    val r2 = loadOne("x.M#create(Ljava/lang/String;)Lcom/example/model/Id; ret:Lcom/example/model/*;")

    val acc = access()
    // r1: any args, any ret under com/example/model
    assert(Rules.matches(r1, "x.M", "make", "(I)Lcom/example/model/Id;", acc))
    assert(!Rules.matches(r1, "x.M", "make", "(I)I", acc))

    // r2: exact descriptor required AND return matches ret:<glob>
    assert(Rules.matches(r2, "x.M", "create", "(Ljava/lang/String;)Lcom/example/model/Id;", acc))
    assert(!Rules.matches(r2, "x.M", "create", "(I)Lcom/example/model/Id;", acc))     // args don't match desc
    assert(!Rules.matches(r2, "x.M", "create", "(Ljava/lang/String;)I", acc))         // ret doesn't match ret:<glob>
  }

  test("method glob + name helpers use AND semantics") {
    val r = loadOne("p.Q#re* name-starts:re name-contains:ad name-ends:$1 synthetic")
    val acc = access(synthetic = true)
    assert(Rules.matches(r, "p.Q", "read$1",      desc("", "V"), acc))
    assert(!Rules.matches(r, "p.Q", "read$2",     desc("", "V"), acc)) // wrong end
    assert(!Rules.matches(r, "p.Q", "repose$1",   desc("", "V"), acc)) // missing 'ad'
    assert(!Rules.matches(r, "p.Q", "load$1",     desc("", "V"), acc)) // wrong start
  }

  test("descriptor glob patterns inside (args)ret") {
    val r1 = loadOne("p.R#f(Ljava/lang/*;)I")
    val r2 = loadOne("p.R#g(*)V")

    val acc = access()
    assert(Rules.matches(r1, "p.R", "f", "(Ljava/lang/String;)I", acc))
    assert(Rules.matches(r1, "p.R", "f", "(Ljava/lang/Object;)I", acc))
    assert(!Rules.matches(r1, "p.R", "f", "(I)I", acc))
    assert(!Rules.matches(r1, "p.R", "f", "(Ljava/lang/String;)V", acc))

    assert(Rules.matches(r2, "p.R", "g", "(I)V", acc))
    assert(Rules.matches(r2, "p.R", "g", "()V", acc))
    assert(!Rules.matches(r2, "p.R", "g", "(I)I", acc))
  }

  test("regex selectors are rejected with a clear message") {
    val p = tmpFile()
    write(p, Seq("re:^x\\.A#f(*)")) // class uses re:
    intercept[IllegalArgumentException] {
      Rules.load(p)
    }
  }

  test("Rules.matches rejects slash-form inputs (dot-only contract)") {
    val r = loadOne("x.A#f")
    val acc = access()
    val ex = intercept[IllegalArgumentException] {
      Rules.matches(r, "x/A", "f", desc("", "V"), acc)
    }
    assert(ex.getMessage.contains("dot form"))
  }

}
