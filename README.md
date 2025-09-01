
# jacoco-method-filter

**Scala-based bytecode rewriter for Java/Scala projects** that injects an annotation whose simple name contains
`Generated` into selected methods *before* JaCoCo reporting. Since **JaCoCo ≥ 0.8.2** ignores classes and methods
annotated with an annotation whose simple name contains `Generated` (with retention `CLASS` or `RUNTIME`), this lets
you **filter coverage at the method level** without touching your source code — and keep **HTML and XML numbers
consistent**.

- [Why this exists](#why-this-exists)
- [Goals](#goals)
- [Non-goals](#non-goals)
- [Rules file format](#rules-file-format)
- [Usage — sbt plugin](#usage--sbt-plugin)
- [Usage — Maven (local & public)](#usage--maven-local--public)
- [Safety & troubleshooting](#safety--troubleshooting)
- [License](#license)

---

## Why this exists

JaCoCo does not natively support arbitrary **method-level** filtering based on patterns.  
Typical needs include removing **compiler noise** from Scala/Java coverage (e.g., Scala `copy`, `$default$N`,
`$anonfun$*`, or `synthetic/bridge` methods) while keeping **real business logic** visible in coverage metrics.

---

## Goals

- Method-level filtering using a simple **rules file** (globs + flags).
- **No source changes**: the tool annotates bytecode (`.class`) after compile.
- Works locally and in CI with **sbt**, **Maven**, and **GitHub Actions**.
- Supports **Scala and Java** (JVM bytecode).
- Simple flow: `test → rewriter → jacococli report`.

## Non-goals

- Do not modify `jacoco.exec`.
- Do not implement a custom JaCoCo HTML renderer.
- Do not optimize bytecode beyond adding the marker annotation.
- Do not enforce coverage thresholds (leave that to your CI policy).

---

## Rules file format

A rules file defines **method-filtering rules**, one per line.
Each rule tells the rewriter _which methods should be annotated as `*Generated` and therefore ignored by JaCoCo._

### General Syntax

```text
<FQCN_glob>#<method_glob>(<descriptor_glob>) [FLAGS and PREDICATES...]
```

- `FQCN_glob` – fully qualified class name, **glob in dot form (`.`)**. `$` allowed for inner classes.
  - Examples:
    - `*.model.*`, `com.example.*`, `*`
- `method_glob` – method name glob
  - Examples:
    - `copy`
    - `$anonfun$*`
    - `get*`
    - `*_$eq`
- `descriptor_glob` – JVM method descriptor in `(args)ret`.
  - you may omit it entirely.
    - `x.A#m2` ⇒ treated as `x.A#m2(*)*` (wildcard args & return).
  - If provided, short/empty forms normalize as:
    - `""`, `"()"`, `"(*)"` ⇒ all become `"(*)*"` (match any args & return).
    - Examples:
      - `(I)I` → takes int, returns int
      - `(Ljava/lang/String;)V` → takes String, returns void
      - `()` or `(*)` or omitted → any args, any return
- `FLAGS` _(optional)_ – space or comma separated access modifiers.
  - Supported: `public | protected | private | synthetic | bridge | static | abstract`.
- **Predicates** (optional) – fine-grained constraints:
  - `ret:<glob>` → match return type only (e.g. `ret:V`, `ret:I`, `ret:Lcom/example/*;`).
  - `id:<string>` → identifier for logs/reports.
  - `name-contains:<s>` → method name must contain `<s>`.
  - `name-starts:<s>` → method name must start with `<s>`.
  - `name-ends:<s>` → method name must end with `<s>`.

### Examples

```text
# Simple wildcards
*#*(*)
*.dto.*#*(*)

# Scala case class helpers
*.model.*#copy(*)
*.model.*#productArity()
*.model.*#productElement(*)
*.model.*#productPrefix()

# Companion objects and defaults
*.model.*$*#apply(*)
*.model.*$*#unapply(*)
*#*$default$*(*)

# Anonymous/synthetic methods
*#$anonfun$*
*#*(*):synthetic            # any synthetic
*#*(*):bridge               # any bridge

# Setters / fluent APIs
*.dto.*#*_$eq(*)
*.builder.*#with*(*)
*.client.*#with*(*) ret:Lcom/api/client/*

# Return-type constraints
*.jobs.*#*(*):ret:V
*.math.*#*(*):ret:I
*.model.*#*(*):ret:Lcom/example/model/*
```

#### Notes

- Regex selectors (`re:`) are not supported — **globs only**.
- **Always use dot-form (**`com.example.Foo`**) for class names**.
  - Rules written with either dot or slash still match, but all inputs to the matcher must be dot-form.
- Comments (`# …`) and blank lines are ignored.

---

## Usage — sbt plugin

- Add the plugin to your build:

```scala
// project/plugins.sbt
addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.5.0")
addSbtPlugin("MoranaApps" % "jacoco-method-filter-sbt" % "0.1.0-SNAPSHOT")
```

- In your project build:

```scala
enablePlugins(morana.coverage.JacocoFilterPlugin)

// make the tool available at runtime for the plugin to run
libraryDependencies += "MoranaApps" %% "jacoco-method-filter-core" % "0.1.0-SNAPSHOT"

// (optional) overrides
coverageRewriteRules     := baseDirectory.value / "rules" / "coverage-rules.txt"
coverageRewriteOutputDir := target.value / s"scala-${scalaBinaryVersion.value}" / "classes-filtered"
jacocoExec               := target.value / "jacoco.exec"
jacocoCliJar             := baseDirectory.value / "tools" / "jacococli.jar"
```

### Workflow

- **1.** Run tests → `sbt-jacoco` produces `target/jacoco.exec` and unfiltered classes.
- **2.** Rewrite classes according to your rules (adds `@Generated`):

```scala
sbt coverageRewrite
```

- **3.** Generate filtered JaCoCo report:

```scala
sbt coverageReportFiltered
```

- **4.** Or run the full pipeline in one step:

```scala
sbt coverageFiltered
```

#### Output

- Filtered classes: `target/scala-*/classes-filtered`
- HTML report: `target/jacoco-html/`
- XML report: `target/jacoco.xml`

> **Notes**
>
>- Rules file defaults to rules/coverage-rules.txt (relative to project root).
>- You can run in dry mode with:
>
>```scala
>coverageRewriteDryRun := true
>```
>
>- FQCN inputs should be dot-form (com.example.Foo). Rules may use dot or slash globs.

---

## Usage — Maven (local & public)

### Local snapshot usage

Follow the [Installation — Local Development](./DEVELOPER.md#local-development) instructions to add local resolvers.
Then activate the profile to produce **filtered coverage**:

```bash
mvn -Pcoverage-filtered clean verify
```

### Public release usage

Once published to Maven Central, no extra resolvers are needed:

```xml
<dependencies>
    <dependency>
        <groupId>MoranaApps</groupId>
        <artifactId>jacoco-method-filter-core_2.13</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

And in your `pom.xml` add the `coverage-filtered` profile:

```xml
<profiles>
  <profile>
    <id>coverage-filtered</id>
    <build>
      <plugins>
        <!-- JaCoCo agent -->
        <plugin>
          <groupId>org.jacoco</groupId>
          <artifactId>jacoco-maven-plugin</artifactId>
          <version>0.8.12</version>
          <executions>
            <execution>
              <goals><goal>prepare-agent</goal></goals>
            </execution>
          </executions>
        </plugin>

        <!-- Run rewriter -->
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>exec-maven-plugin</artifactId>
          <version>3.5.0</version>
          <executions>
            <execution>
              <id>rewrite-classes</id>
              <phase>test</phase>
              <goals><goal>java</goal></goals>
              <configuration>
                <mainClass>io.moranaapps.jacocomethodfilter.CoverageRewriter</mainClass>
                <arguments>
                  <argument>--in</argument><argument>${project.build.outputDirectory}</argument>
                  <argument>--out</argument><argument>${project.build.directory}/classes-filtered</argument>
                  <argument>--rules</argument><argument>${project.basedir}/rules/coverage-rules.txt</argument>
                </arguments>
              </configuration>
            </execution>
          </executions>
        </plugin>

        <!-- Generate report via jacococli -->
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>exec-maven-plugin</artifactId>
          <version>3.5.0</version>
          <executions>
            <execution>
              <id>jacoco-report-filtered</id>
              <phase>verify</phase>
              <goals><goal>exec</goal></goals>
              <configuration>
                <executable>java</executable>
                <arguments>
                  <argument>-jar</argument>
                  <argument>${project.build.directory}/tools/jacococli.jar</argument>
                  <argument>report</argument>
                  <argument>${project.build.directory}/jacoco.exec</argument>
                  <argument>--classfiles</argument>
                  <argument>${project.build.directory}/classes-filtered</argument>
                  <argument>--sourcefiles</argument>
                  <argument>${project.basedir}/src/main/java</argument>
                  <argument>--sourcefiles</argument>
                  <argument>${project.basedir}/src/main/scala</argument>
                  <argument>--html</argument>
                  <argument>${project.build.directory}/jacoco-html</argument>
                  <argument>--xml</argument>
                  <argument>${project.build.directory}/jacoco.xml</argument>
                </arguments>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

Run:

```bash
mvn -Pcoverage-filtered clean verify
```

Outputs:

- Filtered classes → `target/classes-filtered`
- HTML report → `target/jacoco-html/index.html`
- XML report → `target/jacoco.xml`

---

## Safety & troubleshooting

- **HTML didn’t change?** Ensure `jacococli report` uses `--classfiles` pointing at your `classes-filtered` directory.
- **Don’t filter too broadly.** Start with `$default$N`, `synthetic|bridge`, and `$anonfun$*` in specific packages.
- **Dry-run first.** Verify matched methods before annotating.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
