# Maven Integration by Profile

> **⚠️ DEPRECATED - Legacy Integration Method**
>
> This manual profile-based integration approach is **deprecated** as of version 1.2.0.
> 
> **Please use the published Maven plugin instead**: [`jacoco-method-filter-maven-plugin`](../../maven-plugin/)
>
> **Why migrate:**
> - ✅ Simpler configuration (no copy-paste of large XML profiles)
> - ✅ Automatic version management
> - ✅ Easier to maintain across multiple projects
> - ✅ Better aligned with Maven best practices
>
> **See the [Maven example](../../examples/maven-basic) for the recommended approach.**
>
> ---
>
> **This document is retained for:**
> - Advanced users requiring fine-grained control
> - Legacy projects that cannot migrate immediately
> - Reference implementation for custom build setups
>
> **Last updated:** v1.0.0 (profile-based approach)

---

This project provides a Maven profile for integrating **JaCoCo coverage with method-level filtering**.  
It rewrites compiled classes according to custom rules, attaches the JaCoCo agent for test runs, and
generates **filtered coverage reports** (HTML + XML).

- [Add Dependency](#add-dependency)
- [Profile to place in `root pom.xml`](#profile-to-place-in-root-pomxml)
- [Deactivation Properties for Child Modules](#deactivation-properties-for-child-modules)

---

## Add Dependency

Add the dependency to your target project:

```xml
<dependencies>
    <dependency>
        <groupId>MoranaApps</groupId>
        <artifactId>jacoco-method-filter-core_2.12</artifactId>
        <version>1.0.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Profile to place in `root pom.xml`

Add the following profile to the `<profiles>` section of your **root POM**

```xml
<!--
  Profile: code-coverage
  Purpose: Integrates JaCoCo code coverage with custom filtering (jacoco-method-filter).
  Workflow:
    1. Resolve JaCoCo CLI jar (nodeps variant).
    2. Compute skip flags for aggregator projects.
    3. Rewrite classes to apply filtering rules.
    4. Swap in filtered classes before tests.
    5. Run tests with JaCoCo agent attached.
    6. Generate filtered coverage reports (HTML + XML).
   Last  modified: jmf:v1.0.0
-->
<profile>
    <id>code-coverage</id>
    <build>
        <plugins>
            <!--
              Step 1: Resolve JaCoCo CLI jar into Maven properties.
              We use the `nodeps` classifier so it is executable as a fat JAR.
            -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.6.1</version>
                <executions>
                    <execution>
                        <id>resolve-jacoco-cli</id>
                        <phase>validate</phase>
                        <goals><goal>properties</goal></goals>
                        <configuration>
                            <propertiesPrefix>dep</propertiesPrefix>
                            <artifacts>
                                <artifact>
                                    <groupId>org.jacoco</groupId>
                                    <artifactId>org.jacoco.cli</artifactId>
                                    <version>${jacoco.version}</version>
                                    <classifier>nodeps</classifier>
                                </artifact>
                            </artifacts>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!--
              Step 2: Ant tasks for:
                (a) computing skip.coverage for aggregator modules,
                (b) determining test.classes.dir,
                (c) computing jacoco.report.skip based on exec presence,
                (d) swapping filtered classes back into target/classes before tests.
              We use a single antrun with multiple executions to share properties.
            -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.1.0</version>
                <executions>

                    <!-- Compute skip.coverage (aggregators should skip coverage) -->
                    <execution>
                        <id>compute-skip-flag</id>
                        <phase>validate</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <exportAntProperties>true</exportAntProperties>
                            <target>
                                <condition property="computed.skip.coverage" value="true" else="false">
                                    <equals arg1="${project.packaging}" arg2="pom"/>
                                </condition>
                                <!-- Only set if not defined already (Ant won't override Maven-set props) -->
                                <property name="skip.coverage" value="${computed.skip.coverage}"/>
                            </target>
                        </configuration>
                    </execution>

                    <!-- Decide test.classes.dir (filtered vs unfiltered) -->
                    <execution>
                        <id>pick-test-classes-dir</id>
                        <phase>validate</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <exportAntProperties>true</exportAntProperties>
                            <target>
                                <condition property="coverage.on" value="true">
                                    <equals arg1="${skip.coverage}" arg2="false"/>
                                </condition>
                                <condition property="test.classes.dir"
                                           value="${project.build.directory}/classes-filtered"
                                           else="${project.build.outputDirectory}">
                                    <equals arg1="${skip.coverage}" arg2="false"/>
                                </condition>
                            </target>
                        </configuration>
                    </execution>

                    <!-- Overlay filtered classes back into target/classes -->
                    <execution>
                        <id>use-filtered-classes</id>
                        <phase>test-classes</phase> <!-- runs right before tests -->
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <target>
                                <echo message="[code-coverage] Overlaying filtered classes into ${project.build.outputDirectory} (if present)"/>
                                <copy todir="${project.build.outputDirectory}" overwrite="true">
                                    <!-- If classes-filtered doesn't exist, this fileset is empty and copy is a no-op -->
                                    <fileset dir="${project.build.directory}/classes-filtered" erroronmissingdir="false"/>
                                </copy>
                            </target>
                        </configuration>
                    </execution>

                    <!-- Compute jacoco.report.skip based on jacoco.exec presence and print a message if missing -->
                    <execution>
                        <id>compute-skip-jacoco-report</id>
                        <phase>verify</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <exportAntProperties>true</exportAntProperties>
                            <target>
                                <!-- is aggregator? -->
                                <condition property="is.aggregator" value="true" else="false">
                                    <equals arg1="${project.packaging}" arg2="pom"/>
                                </condition>

                                <!-- does exec file exist? -->
                                <available file="${project.build.directory}/jacoco.exec" property="jacoco.exec.present"/>

                                <!-- final skip: skip.coverage OR aggregator OR missing exec -->
                                <condition property="skip.jacoco.report" value="true" else="false">
                                    <or>
                                        <equals arg1="${skip.coverage}" arg2="true"/>
                                        <equals arg1="${is.aggregator}" arg2="true"/>
                                        <not><isset property="jacoco.exec.present"/></not>
                                    </or>
                                </condition>

                                <!-- Friendly message when skipping due to missing exec (only in non-aggregator modules) -->
                                <condition property="jacoco.exec.missing.msg"
                                           value="[code-coverage] jacoco.exec not found in ${project.build.directory} — skipping JaCoCo report generation."
                                           else="">
                                    <and>
                                        <equals arg1="${skip.jacoco.report}" arg2="true"/>
                                        <not><equals arg1="${is.aggregator}" arg2="true"/></not>
                                        <not><isset property="jacoco.exec.present"/></not>
                                    </and>
                                </condition>
                                <echo message="${jacoco.exec.missing.msg}"/>
                            </target>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!--
              Step 3: Attach JaCoCo runtime agent to surefire/failsafe test runs.
              This produces jacoco.exec, controlled by skip.coverage flag.
            -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                        <configuration>
                            <skip>${skip.coverage}</skip>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!--
              Step 4: Rewrite compiled classes to apply filter rules, and
              Step 5: Run JaCoCo CLI to generate reports.
              CoverageRewriter injects @CoverageGenerated annotations per rules file.
            -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>

                <!-- Include JaCoCo CLI, Scala, ASM libs -->
                <dependencies>
                    <dependency>
                        <groupId>org.jacoco</groupId>
                        <artifactId>org.jacoco.cli</artifactId>
                        <version>${jacoco.version}</version>
                        <classifier>nodeps</classifier>
                    </dependency>
                    <dependency>
                        <groupId>org.scala-lang</groupId>
                        <artifactId>scala-library</artifactId>
                        <version>${scala.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.ow2.asm</groupId>
                        <artifactId>asm-tree</artifactId>
                        <version>9.7.1</version>
                    </dependency>
                    <dependency>
                        <groupId>org.ow2.asm</groupId>
                        <artifactId>asm-commons</artifactId>
                        <version>9.7.1</version>
                    </dependency>
                </dependencies>

                <configuration>
                    <includePluginDependencies>true</includePluginDependencies>
                    <includeProjectDependencies>true</includeProjectDependencies>
                    <classpathScope>runtime</classpathScope>
                </configuration>

                <executions>
                    <!-- Rewrite compiled classes -->
                    <execution>
                        <id>rewrite-classes</id>
                        <phase>process-classes</phase>
                        <goals><goal>java</goal></goals>
                        <configuration>
                            <skip>${skip.coverage}</skip>
                            <mainClass>io.moranaapps.jacocomethodfilter.CoverageRewriter</mainClass>
                            <arguments>
                                <argument>--in</argument><argument>${project.build.outputDirectory}</argument>
                                <argument>--out</argument><argument>${project.build.directory}/classes-filtered</argument>
                                <argument>--rules</argument><argument>${session.executionRootDirectory}/jmf-rules.txt</argument>
                            </arguments>
                        </configuration>
                    </execution>

                    <!-- Generate filtered JaCoCo report -->
                    <execution>
                        <id>jacoco-report-filtered</id>
                        <phase>verify</phase>
                        <goals><goal>exec</goal></goals>
                        <configuration>
                            <skip>${skip.jacoco.report}</skip>
                            <executable>java</executable>
                            <arguments>
                                <argument>-jar</argument>
                                <argument>${settings.localRepository}/org/jacoco/org.jacoco.cli/${jacoco.version}/org.jacoco.cli-${jacoco.version}-nodeps.jar</argument>

                                <argument>report</argument>
                                <argument>${project.build.directory}/jacoco.exec</argument>

                                <argument>--classfiles</argument>
                                <argument>${project.build.outputDirectory}</argument>

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

            <!--
              Step 6: Ensure test plugins (surefire, failsafe) use the computed test.classes.dir.
              This allows both filtered and unfiltered runs to work consistently.
            -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <classesDirectory>${test.classes.dir}</classesDirectory>
                </configuration>
            </plugin>

            <!-- Preparation for IT test integration  -->
            <!-- plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <classesDirectory>${test.classes.dir}</classesDirectory>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin -->

        </plugins>
    </build>
</profile>
```

### Deactivation Properties for Child Modules

```xml
<properties>
    <skip.coverage>true</skip.coverage>
</properties>
```
