# JMF Rules Reference

Full syntax reference, authoring guide, and diagnostic workflows for jacoco-method-filter rules files.

- [How to Use](#how-to-use)
- [Rule Anatomy](#rule-anatomy)
- [JVM Descriptor Type Mapping](#jvm-descriptor-type-mapping)
- [Descriptor Normalization](#descriptor-normalization)
- [Common Pitfalls](#common-pitfalls)
- [Scala Name Mangling](#scala-name-mangling)
- [Examples](#examples)
- [Exclude and Include Rules](#exclude-and-include-rules)
- [Global and Local Rules](#global-and-local-rules)
- [How Rules Are Merged](#how-rules-are-merged)
- [Verify: Preview What Gets Filtered](#verify-preview-what-gets-filtered)
- [Diagnostic Workflow](#diagnostic-workflow)
- [Verification Workflow](#verification-workflow)
- [Global Rule Safety Warning](#global-rule-safety-warning)
- [CLI Reference](#cli-reference)
- [Ready-to-Use Rules Template](#ready-to-use-rules-template)

---

## How to Use

1. Review the GLOBAL RULES in the template — they cover compiler-generated boilerplate
   (case class methods, lambdas, value class extensions, default parameters).
2. Add project-specific patterns in the PROJECT RULES section.
3. Keep rules narrow (by package), prefer flags (`synthetic`/`bridge`) for compiler artifacts,
   and add `id:` labels so logs are readable.
4. Use `--verify` mode to confirm rules match what you expect before committing.
5. **Prefer include rules over commenting out globals.** When a broad global accidentally matches
   a method with real logic, keep the global active and add a `+` include rule to rescue that
   specific method. Commenting out the global loses filtering for *all* other matching methods.
   See [Exclude and Include Rules](#exclude-and-include-rules).

---

## Rule Anatomy

```
<FQCN_glob>#<method_glob>(<descriptor_glob>)  [FLAGS]  [PREDICATES]  id:<label>
```

**FQCN_glob** — Class name in dot-form with globs.
Examples: `*MyClass`, `*.model.*`, `com.example.*`
Use `$` for inner classes (`Foo$Bar`) and companions (`Foo$`).

**method_glob** — Method name with globs.
Examples: `copy`, `get*`, `$anonfun$*`, `*_$eq`

**descriptor** — JVM descriptor in `(args)ret` format with globs.
Examples: `(I)V`, `(Ljava/lang/String;)*`, `(*)*`
Omitting the descriptor entirely is equivalent to `(*)*`.

**FLAGS** — Space-separated. Optional.
`public | protected | private | synthetic | bridge | static | abstract`
IMPORTANT: flags must be space-separated from the descriptor.
```
WRONG: *#*(*):synthetic    (colon makes it part of the descriptor)
RIGHT: *#*(*) synthetic    (space separates flag from descriptor)
```

**PREDICATES** — Space-separated key:value pairs. Optional.
- `ret:<glob>` — Match return type only (e.g., `ret:V`, `ret:Lcom/example/*;`)
- `id:<string>` — Identifier shown in logs/reports (required for traceability)
- `name-contains:<s>` — Method name must contain `<s>`
- `name-starts:<s>` — Method name must start with `<s>`
- `name-ends:<s>` — Method name must end with `<s>`

IMPORTANT: predicates must be space-separated from the descriptor.
```
WRONG: *#*(*):ret:V    (colon makes it part of the descriptor)
RIGHT: *#*(*) ret:V    (space separates predicate from descriptor)
```

> **Notes**
> - Regex selectors (`re:`) are not supported — globs only.
> - Always use dot-form (`com.example.Foo`) for class names.
> - Comments (`# …`) and blank lines are ignored.
> - `#` is both the comment marker (start of line) and FQCN/method separator.
>   Inline comments after rules are harmless but best avoided — use dedicated comment lines.

---

## JVM Descriptor Type Mapping

JMF matches against raw JVM bytecode descriptors, NOT source-level types.
Writing human-readable types (e.g., `"int"`, `"java.lang.String"`) produces rules that load
successfully but silently never match.

| Source type      | JVM descriptor        |
|------------------|-----------------------|
| `int`            | `I`                   |
| `boolean`        | `Z`                   |
| `long`           | `J`                   |
| `double`         | `D`                   |
| `float`          | `F`                   |
| `byte`           | `B`                   |
| `char`           | `C`                   |
| `short`          | `S`                   |
| `void` / `Unit`  | `V`                   |
| `String`         | `Ljava/lang/String;`  |
| `Option[A]`      | `Lscala/Option;`      |
| `Object`         | `Ljava/lang/Object;`  |
| `Array[Int]`     | `[I`                  |
| `Array[String]`  | `[Ljava/lang/String;` |

Use `javap -p -verbose <ClassFile.class>` to see actual descriptors.

---

## Descriptor Normalization

Short/empty descriptor forms are all equivalent wildcards:

| What you write | What JMF uses | Matches              |
|----------------|---------------|----------------------|
| (omitted)      | `(*)*`        | any args, any return |
| `()`           | `(*)*`        | any args, any return |
| `(*)`          | `(*)*`        | any args, any return |
| `(I)V`         | `(I)V`        | exactly int→void     |

**WARNING:** `*#productElement()` LOOKS like "no-arg method" but actually matches ALL overloads
(including `productElement(I)Ljava/lang/Object;`) because `()` normalizes to `(*)*`.
To target a specific overload, use an explicit descriptor: `*#myMethod()V` (actually no-arg, returns void).

---

## Common Pitfalls

**1. FQCN wildcard prefix required for qualified class names:**
```
WRONG: QueryResult#noMore()  id:qr-nomore
       (matches only unqualified "QueryResult", not "com.example...QueryResult")
RIGHT: *QueryResult#noMore() id:qr-nomore
       (* prefix matches any package prefix)
```

**2. Use JVM descriptors, not source types:**
```
WRONG: *#apply(int)*   (human-readable — silently never matches)
RIGHT: *#apply(I)*     (JVM format)
```

**3. Flags and predicates must be SPACE-separated from descriptor:**
```
WRONG: *#*(*):synthetic      (colon makes ":synthetic" part of descriptor)
RIGHT: *#*(*) synthetic      (space separates — flag parsed correctly)
WRONG: *#*(*):ret:V          (colon makes ":ret:V" part of descriptor)
RIGHT: *#*(*) ret:V          (space separates — predicate parsed correctly)
```

**4. Object return types in `ret:` globs need trailing semicolon:**
```
WRONG: *#make(*) ret:Lcom/example/model/Id   (missing semicolon)
RIGHT: *#make(*) ret:Lcom/example/model/Id;  (semicolon required)
```

**5.** Every rule should include `id:<label>` for traceability in logs.

**6.** `#` is both the comment marker (start of line) and FQCN/method separator. Inline comments
after rules are harmless but can be confusing. Best practice: use dedicated comment lines above the rule.

**7.** Audit your rules for human-readable descriptors:
```bash
grep -n '[a-z]\.[A-Z]' jmf-rules.txt | grep -v '^#'
```
Any matches likely contain human-readable class names in descriptors.

---

## Scala Name Mangling

JMF operates on bytecodes — globs must use the compiled method name, not the source name.
Common Scala name-mangling patterns:

| Source name              | Bytecode name       |
|--------------------------|---------------------|
| `name_=` (setter)        | `name_$eq`          |
| `a_+_b` (operator)       | `a_$plus_b`         |
| `?`                      | `$qmark`            |
| `!`                      | `$bang`             |
| `++`                     | `$plus$plus`        |
| `::`                     | `$colon$colon`      |
| Inner class `Foo.Bar`    | `Foo$Bar`           |
| Companion object `Foo`   | `Foo$`              |
| Lambda from `foo`        | `$anonfun$foo$1`    |

Use `javap -p classfile` to confirm the exact bytecode name before writing a glob.

---

## Examples

### Simple wildcards

```text
*#*(*)
```
Match EVERY method in EVERY class (any package).
**DO NOT commit this rule** — it suppresses all JaCoCo coverage and produces artificially inflated
(up to 100%) coverage numbers, silently masking regressions. Use only for one-off diagnostics and
remove immediately.

```text
*.dto.*#*(*)
```
Match every method on any class under any package segment named `dto`. Good when you treat DTOs as
generated/boilerplate.

### Scala case class helpers

```text
*.model.*#copy(*)           # case-class copy, any parameter list
*.model.*#productArity()    # NOTE: () normalizes to (*)* — matches ALL overloads, not just zero-arg
*.model.*#productElement(*) # JVM signature: (I)Ljava/lang/Object;
*.model.*#productPrefix()   # returns the case class name as a String
```

### Companion objects and defaults

```text
*.model.*$*#apply(*)        # companion apply factories — BE CAREFUL: can hide real factory logic
*.model.*$*#unapply(*)      # extractor unapply methods in companions
*#*$default$*(*)            # Scala default-argument helpers — compiler-synthesized, safe
```

### Anonymous / synthetic / bridge

```text
*#$anonfun$*                # any method whose name contains $anonfun$ (Scala lambdas)
*#*(*) synthetic            id:any-synthetic   # any ACC_SYNTHETIC method; scope by package!
*#*(*) bridge               id:any-bridge      # Java generic bridge methods (usually safe globally)
```

> NOTE: flags are space-separated, NOT colon-prefixed.

### Setters / fluent APIs

```text
*.dto.*#*_$eq(*)            # Scala var setters (source: name_= → bytecode: name_$eq)
*.builder.*#with*(*)        # builder-style fluent setters
*.client.*#with*(*) ret:Lcom/api/client/*;   # builder setters returning a specific type
```

> NOTE: Source-level `name_=` is compiled to `name_$eq` in bytecode.
> Using the source form `*_=(*)` would silently never match.

### Return-type constraints

```text
*.jobs.*#*(*) ret:V                           id:jobs-void   # void-returning, often orchestration
*.math.*#*(*) ret:I                           id:math-int    # int-returning math methods
*.model.*#*(*) ret:Lcom/example/model/*;      id:model-ret   # return type in model package
```

> NOTE: trailing semicolon is required for object type globs in `ret:`.

### Notes on class name form

- Always use dot-form (`com.example.Foo`) for PACKAGE separators (not slash-form `com/example/Foo`).
- The `$` character IS required for inner classes (`Foo$Bar`) and companion objects (`Foo$`).

### Lazy val compute methods

Scala compiles `lazy val foo = expr` into two methods:
- `foo()` — the accessor (checks bitmap flag, calls `$lzycompute` if unset)
- `foo$lzycompute()` — the actual initializer body (contains `expr`)

The `$lzycompute` method body IS the lazy initializer, so filtering it hides whatever `expr` does.
Only filter when the initializer is a trivial constant, a boundary call not unit-testable (e.g.,
`DriverManager.getConnection`), or an already-covered computation. Rescue with `+` include rules
for any lazy val that contains real logic.

```text
# CAUTION: only enable after verifying each lazy val's body is trivial or boundary-only
*#*$lzycompute(*)    id:scala-lzycompute

# Rescue a lazy val whose initializer contains real logic
+*MyService#cache$lzycompute(*)    id:keep-cache-lzycompute
```

The template ships this rule **disabled** (commented out). Enable it per class or enable globally
with include-rule rescues.

### Static companion forwarders

When Scala compiles a companion object, the compiler emits static forwarder methods on the main
class (`Foo`) that delegate to `Foo$.MODULE$.method()`. Each forwarder is a single-call delegate
with no logic of its own. There is no general glob to isolate them (they look like any public
static method), so rules must be class-specific:

```text
# Static forwarder on Foo delegates to Foo$.MODULE$.apply(...)
*Foo#apply(*)        id:foo-apply-fwd
*Foo#fromString(*)   id:foo-fromstring-fwd
```

Verify with `javap -p Foo.class` — forwarders appear as `public static` methods with a body of
`return Foo$.MODULE$.methodName(args)`.

### Iterator / trait mixin forwarders

When a class extends `Iterator` (or another large trait), the Scala compiler generates ~80+
forwarding methods on the implementing class, each delegating to the trait default implementation.
None contain project logic. Since the class name is project-specific, rules must be scoped to it:

```text
# QueryResult extends Iterator[Row] — filter all trait-generated forwarders
*QueryResult#to*(*)       id:qr-iter-to
*QueryResult#mk*(*)       id:qr-iter-mk
*QueryResult#map(*)       id:qr-iter-map
*QueryResult#flatMap(*)   id:qr-iter-flatmap
*QueryResult#filter*(*)   id:qr-iter-filter
*QueryResult#fold*(*)     id:qr-iter-fold
*QueryResult#reduce*(*)   id:qr-iter-reduce
# ... add remaining Iterator methods as needed
```

Use `javap -p ClassName.class | grep 'public'` to enumerate all forwarders, then write
scoped rules for the ones you want to filter.

---

## Exclude and Include Rules

By default, all rules are **exclusion rules** — they mark methods to be filtered from coverage.

**Include rules** (whitelist) can override exclusions for specific methods.
Prefix a rule with `+` to mark it as an inclusion:

```text
# Exclude all companion object apply methods
*$#apply(*)  id:comp-apply

# But keep this one — it has custom business logic
+com.example.Config$#apply(*)  id:keep-config-apply
```

**Resolution logic:**
- A method is **excluded** if any exclusion rule matches AND no inclusion rule matches.
- A method is **rescued** (kept in coverage) if both exclusion and inclusion rules match — **include always wins**.
- A method is **unaffected** if no exclusion rule matches.

### Preferred strategy: rescue, don't comment out

When a broad global rule accidentally matches a method with real logic, the preferred response is
to **keep the global active and add a `+` include rule** for the exception — not to comment out
the global.

Commenting out a global loses the filtering benefit for *every* other class it matched. A `+`
include rule is surgical: it rescues exactly the method that needs coverage while leaving all
other matches filtered.

```text
# BAD: commenting out the global loses filtering for ALL copy methods everywhere
#*#copy(*)   id:case-copy

# GOOD: keep the global, rescue the one method that has real logic
*#copy(*)    id:case-copy
+com.example.MutableRecord#copy(*)   id:keep-mutablerecord-copy
```

The `+` rule also serves as explicit documentation: a future reader sees that this method was
reviewed and confirmed to contain real logic.

**When commenting out is appropriate:**
- The rule causes widespread false positives across many unrelated classes (writing dozens of
  include rules would be noisier than disabling the global).
- The rule pattern is fundamentally wrong for this project (e.g., a naming collision on a domain
  term used everywhere).
- You are still evaluating a candidate global and have not yet committed to enabling it.

---

## Global and Local Rules

Most users can start with a **single local rules file**.

- **Simple (single file):** use `--local-rules jmf-rules.txt` (CLI) or the plugin default `jmf-rules.txt`
- **Advanced (two-layer):** use **global** rules (shared defaults) + **local** rules (project overrides)

### Rule sources

- **Global** rules can be a **local path or an HTTP/HTTPS URL**
- **Local** rules are a **local file path**

| Type       | Purpose                                              | Source       |
|------------|------------------------------------------------------|--------------|
| **Global** | Org-wide defaults (e.g., always ignore Scala boilerplate) | Path or URL |
| **Local**  | Project-specific overrides and additions             | Local file   |

**sbt:**
```scala
jmfGlobalRules := Some("https://myorg.com/scala-defaults.txt")
jmfLocalRules := Some(baseDirectory.value / "jmf-local-rules.txt")
```

**Maven:**
```xml
<configuration>
  <globalRules>https://myorg.com/scala-defaults.txt</globalRules>
  <localRules>${project.basedir}/jmf-local-rules.txt</localRules>
</configuration>
```

**CLI:**
```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --in target/classes \
  --out target/classes-filtered \
  --global-rules https://myorg.com/scala-defaults.txt \
  --local-rules jmf-local-rules.txt
```

---

## How Rules Are Merged

When using global and local rules:

1. **Global rules** are loaded first (from URL or path).
2. **Local rules** are appended.
3. During evaluation, **any include rule overrides any exclude rule** for the same method.

This lets you:
- Define broad exclusions globally (e.g., `*#copy(*)`)
- Override selectively in local rules (e.g., `+com.example.Config$#copy(*)`)

---

## Verify: Preview What Gets Filtered

`verify` runs against the **compiled class files** (bytecode) in the given `--in` directory
(e.g. `target/classes`), not against raw source code — so it only reports exclusions/rescues for
methods that **actually exist after compilation**.

> **Important:** Because `verify` scans bytecode, it sees all methods the JVM compiler generated
> (synthetic bridges, anonymous function bodies, default parameter accessors, etc.) alongside your
> hand-written code. Some broad exclusion rules may accidentally match methods you wrote yourself
> (e.g., `apply`). Use include rules (`+...`) to rescue those methods.

**sbt:**
```bash
sbt jmfVerify
```

**Maven:**
```bash
mvn jacoco-method-filter:verify
```

**CLI:**
```bash
java -cp ... io.moranaapps.jacocomethodfilter.CoverageRewriter \
  --verify \
  --in target/classes \
  --local-rules jmf-rules.txt
```

**Example output:**
```text
[verify] EXCLUDED (15 methods):
[verify]   com.example.User
[verify]     #copy(I)Lcom/example/User;    rule-id:case-copy
[verify]     #apply(...)...                rule-id:comp-apply

[verify] RESCUED by include rules (1 method):
[verify]   com.example.Config$
[verify]     #apply(Lcom/example/Config;)Lcom/...;  excl:comp-apply → incl:keep-config-apply

[verify] Summary: 42 classes scanned, 15 methods excluded, 1 method rescued
```

- **Excluded** — matched by an exclusion rule; will be filtered from coverage.
- **Rescued** — matched by an exclusion rule *and* an include rule (`+…`). Include always wins.
  The `excl:… → incl:…` trace shows which rules were involved.

---

## Diagnostic Workflow

When JaCoCo reports missed instructions after you believe you have a rule:

1. **Find the method in jacoco.xml:**
   ```bash
   grep -A2 'name="myMethod"' target/scala-2.12/jacoco/jacoco.xml
   ```

2. **Get the actual bytecode descriptor:**
   ```bash
   javap -p -verbose target/scala-2.12/classes/com/.../MyClass.class | grep -A4 "myMethod"
   ```

3. **Compare the descriptor** in your rule against the bytecode output.
   Common mistakes: `(int)` vs `(I)`, missing `;` after object types, `*` vs explicit return.

---

## Verification Workflow

Before committing any new JMF rule:

1. **Baseline:** note "Marked N methods" in build output.
   ```bash
   # sbt
   sbt '++2.12.18; jacoco'
   # Maven
   mvn clean verify -Pcode-coverage
   ```

2. Add the new rule to your rules file.

3. **Re-run:** "Marked N+k methods" confirms the rule matched.

4. **Use `--verify` mode** for a detailed matching report:
   ```bash
   java -jar jmf.jar --in classes/ --local-rules rules.txt --verify
   ```

---

## Global Rule Safety Warning

Global rules match **ALL classes in ALL packages**. Some rules in the template are intentionally
broad. Before committing, use `--verify` to check what they match, then add `+` include rules to
rescue any methods with real logic — rather than commenting the global out entirely.
See [Exclude and Include Rules](#exclude-and-include-rules) for the recommended strategy.

**`*#apply(*)  id:case-apply`**

Companion `apply` methods often contain validation or factory logic. Shipped commented-out as a
conservative default — but the recommended approach is to enable it and rescue false positives
with `+` include rules rather than leaving it off entirely.

Enable the rule, run `--verify`, then rescue false positives:
```text
*#apply(*)           id:case-apply
+*Config$#apply(*)   id:keep-config-apply    # has validation logic
+*Factory$#apply(*)  id:keep-factory-apply   # has branching
```

**`*#name()`, `*#groups()`, `*#optionalAttributes()`**

Added for compiler-generated Scala patterns (e.g., Regex group names, case class fields) but will
also match domain methods with those names. Run `--verify` to check collisions; rescue with `+`
include rules for any that contain real logic.

**`*#*$lzycompute(*)`** (disabled in template)

The `$lzycompute` method body IS the lazy val initializer — filtering it hides whatever the lazy
val computes. Shipped disabled. Recommended approach: enable selectively per class or enable
globally and rescue lazy vals with real logic:
```text
*#*$lzycompute(*)                id:scala-lzycompute
+*MyService#cache$lzycompute(*)  id:keep-cache-lzycompute   # complex initializer
```

---

## CLI Reference

| Flag | Required | Description |
|------|----------|-------------|
| `--in <dir>` | Yes | Input classes directory (must exist, must contain `.class` files) |
| `--out <dir>` | Unless `--verify` | Output classes directory |
| `--global-rules <path\|url>` | At least one of the two | Global rules file path or URL |
| `--local-rules <path>` | At least one of the two | Local rules file path |
| `--dry-run` | No | Only print matches; do not modify classes |
| `--verify` | No | Read-only scan: list all methods that would be excluded by rules |

In rewrite mode, `--out` is required (omit only when using `--verify`).

---

## Ready-to-Use Rules Template

- **Scala (sbt) project:** [`jmf-rules.template.txt`](../jmf-rules.template.txt)
- **Maven project:** [`maven-plugin/src/main/resources/jmf-rules.template.txt`](../maven-plugin/src/main/resources/jmf-rules.template.txt)

Copy the template to your project root and customize the PROJECT RULES section.
