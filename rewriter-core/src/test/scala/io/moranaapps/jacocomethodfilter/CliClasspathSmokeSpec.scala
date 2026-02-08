package io.moranaapps.jacocomethodfilter

import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.net.URLClassLoader
import scala.sys.process._

/**
 * Smoke test: verify the CLI can run with only the fat JAR + scala-library + scopt
 * on the classpath (no standalone ASM jars).
 *
 * This ensures the shaded ASM inside the fat JAR is sufficient at runtime.
 */
class CliClasspathSmokeSpec extends AnyFunSuite {

  /** Collect classpath entries from both java.class.path and the classloader chain. */
  private def classpathEntries: Seq[String] = {
    val fromProperty = System.getProperty("java.class.path", "")
      .split(File.pathSeparator).filter(_.nonEmpty).toSeq

    val fromClassLoader: Seq[String] = {
      var cl: ClassLoader = getClass.getClassLoader
      val urls = scala.collection.mutable.ArrayBuffer.empty[String]
      while (cl != null) {
        cl match {
          case ucl: URLClassLoader => urls ++= ucl.getURLs.map { u =>
            new File(u.toURI).getAbsolutePath
          }
          case _ =>
        }
        cl = cl.getParent
      }
      urls.toSeq
    }

    (fromProperty ++ fromClassLoader).distinct
  }

  /**
   * Find the fat JAR (produced by `Compile / packageBin := assembly.value`).
   * It lives next to the classes directory: target/scala-X.Y/jacoco-method-filter-core_X.Y-VER.jar
   *
   * We derive the directory from the classes dir on the classpath, then find the
   * latest matching jar.
   */
  private def fatJar: Option[File] = {
    val all = classpathEntries
    // The classes dir is on the test classpath; its parent holds the fat JAR
    val classesDir = all.map(new File(_)).find { f =>
      f.getAbsolutePath.contains("rewriter-core") &&
        f.getName == "classes" &&
        !f.getAbsolutePath.contains("test-classes")
    }
    val scalaTargetDir = classesDir.map(_.getParentFile).getOrElse {
      // Fallback: walk up from test-classes
      val testClasses = all.map(new File(_)).find { f =>
        f.getAbsolutePath.contains("rewriter-core") && f.getName == "test-classes"
      }
      testClasses.map(_.getParentFile).getOrElse(
        sys.error(s"Cannot locate rewriter-core target dir.\nClasspath:\n${all.mkString("\n")}")
      )
    }

    val candidates = Option(scalaTargetDir.listFiles())
      .getOrElse(Array.empty)
      .filter { f =>
        f.getName.startsWith("jacoco-method-filter-core") &&
          f.getName.endsWith(".jar") &&
          !f.getName.contains("-sources") &&
          !f.getName.contains("-javadoc")
      }
      // Only accept jars that actually contain shaded ASM (i.e., real fat JARs)
      .filter { f =>
        val jar = new java.util.jar.JarFile(f)
        try jar.getEntry("jmf/shaded/asm/ClassVisitor.class") != null
        finally jar.close()
      }
      .sortBy(_.lastModified())
      .reverse

    candidates.headOption
  }

  /**
   * Build a minimal classpath: fat JAR + scala-library + scopt.
   * No standalone ASM jars allowed.
   */
  private def minimalClasspath: String = {
    val all = classpathEntries
    val runtimeDeps = all.filter { entry =>
      val name = new File(entry).getName
      name.startsWith("scala-library") || name.startsWith("scopt")
    }
    val jar = fatJar.getOrElse { return "" } // caller uses assume() to skip
    val keep = jar.getAbsolutePath +: runtimeDeps

    // Sanity: no standalone ASM
    assert(
      !keep.exists(e => new File(e).getName.matches("asm(-\\w+)?-.*\\.jar")),
      s"Minimal classpath should not contain a standalone ASM jar, but got:\n${keep.mkString("\n")}"
    )
    assert(keep.nonEmpty,
      s"Minimal classpath is empty.\nAll classpath entries:\n${all.mkString("\n")}")
    keep.mkString(File.pathSeparator)
  }

  test("CLI prints usage and exits 2 with no args (no standalone ASM on classpath)") {
    assume(fatJar.isDefined,
      "Fat JAR not found — run `sbt rewriterCore/assembly` first. Skipping smoke test.")
    val java = sys.props.getOrElse("java.home", "/usr") + File.separator + "bin" + File.separator + "java"
    val cp   = minimalClasspath

    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val exitCode = Process(
      Seq(java, "-cp", cp, "io.moranaapps.jacocomethodfilter.CoverageRewriter")
    ).!(ProcessLogger(stdout.append(_).append("\n"), stderr.append(_).append("\n")))

    // scopt prints usage to stderr and CoverageRewriter calls sys.exit(2) for bad args
    assert(exitCode === 2, s"Expected exit code 2 but got $exitCode.\nstdout: $stdout\nstderr: $stderr")
    val combined = stdout.toString + stderr.toString
    assert(combined.contains("--in") || combined.contains("Usage"), s"Expected usage output but got:\n$combined")
  }
}
