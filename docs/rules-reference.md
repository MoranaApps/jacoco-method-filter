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
broad — verify they do not accidentally filter methods that contain real project logic.

**`*#apply(*)  id:case-apply`**

Adoption finding: companion `apply` methods often contain validation or factory logic. This rule
was disabled in production use because it silently suppressed coverage for a real factory method.
If you enable it, verify with `javap` that every matched `apply` is a trivial forwarder. Use a
`+` include rule to rescue any apply that contains real logic:
```text
+*MyModel$#apply(*)  id:keep-mymodel-apply
```

**`*#name()`, `*#groups()`, `*#optionalAttributes()`**

These match any zero-arg-style method with those names in any class. They were added for specific
compiler-generated Scala patterns (e.g., Regex group names, case class fields) but will also match
domain methods with those names. If your project defines such methods with real logic, rescue them
with `+` include rules or remove these globals.

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
