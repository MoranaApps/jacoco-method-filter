package io.moranaapps.mavenplugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Mojo(name = "rewrite", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, 
      requiresDependencyResolution = ResolutionScope.RUNTIME, threadSafe = true)
public class RewriteMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private org.apache.maven.plugin.descriptor.PluginDescriptor pluginDescriptor;

    @Parameter(property = "jmf.rulesFile", defaultValue = "${project.basedir}/jmf-rules.txt")
    private File rulesFile;

    @Parameter(property = "jmf.inputDirectory", defaultValue = "${project.build.outputDirectory}")
    private File inputDirectory;

    @Parameter(property = "jmf.outputDirectory", defaultValue = "${project.build.directory}/classes-filtered")
    private File outputDirectory;

    @Parameter(property = "jmf.dryRun", defaultValue = "false")
    private boolean dryRun;

    @Parameter(property = "jmf.skip", defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Execution bypassed via skip parameter");
            return;
        }

        checkInputs();
        runTransformation();
    }

    private void checkInputs() throws MojoExecutionException {
        StringBuilder errors = new StringBuilder();
        
        if (rulesFile == null || !rulesFile.exists()) {
            errors.append("\n  - Rules configuration missing");
            if (rulesFile != null) errors.append(" at: ").append(rulesFile.getAbsolutePath());
            errors.append("\n    Solution: execute 'mvn ")
                  .append(pluginDescriptor.getGroupId())
                  .append(":")
                  .append(pluginDescriptor.getArtifactId())
                  .append(":")
                  .append(pluginDescriptor.getVersion())
                  .append(":init-rules'");
        }
        
        if (inputDirectory == null || !inputDirectory.isDirectory()) {
            errors.append("\n  - Invalid input location");
            if (inputDirectory != null) errors.append(": ").append(inputDirectory.getAbsolutePath());
        }
        
        if (errors.length() > 0) {
            throw new MojoExecutionException("Configuration problems detected:" + errors);
        }
    }

    private void runTransformation() throws MojoExecutionException {
        String javaCmd = locateJavaExec();
        List<String> command = assembleCmdLine(javaCmd);
        
        getLog().info("╔═══ JaCoCo Method Filter: Bytecode Rewrite ═══");
        getLog().info("║ Source:      " + inputDirectory.getAbsolutePath());
        getLog().info("║ Destination: " + outputDirectory.getAbsolutePath());
        getLog().info("║ Rules:       " + rulesFile.getAbsolutePath());
        getLog().info("║ Dry run:     " + (dryRun ? "YES (no writes)" : "NO"));
        getLog().info("╚═══════════════════════════════════════════════");

        launchSubprocess(command);
    }

    private List<String> assembleCmdLine(String javaPath) throws MojoExecutionException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaPath);
        cmd.add("-cp");
        cmd.add(buildCp());
        cmd.add("io.moranaapps.jacocomethodfilter.CoverageRewriter");
        cmd.add("--in");
        cmd.add(inputDirectory.getAbsolutePath());
        cmd.add("--out");
        cmd.add(outputDirectory.getAbsolutePath());
        cmd.add("--rules");
        cmd.add(rulesFile.getAbsolutePath());
        if (dryRun) cmd.add("--dry-run");
        return cmd;
    }

    private String buildCp() throws MojoExecutionException {
        List<String> paths = new ArrayList<>();
        
        // Get dependencies from plugin descriptor
        List<Artifact> deps = pluginDescriptor.getArtifacts();
        for (Artifact dep : deps) {
            File jarFile = dep.getFile();
            if (jarFile != null && jarFile.exists()) {
                paths.add(jarFile.getAbsolutePath());
            }
        }
        
        if (paths.isEmpty()) {
            throw new MojoExecutionException("No plugin dependencies resolved");
        }
        
        return String.join(File.pathSeparator, paths);
    }

    private String locateJavaExec() {
        String home = System.getProperty("java.home");
        if (home == null) return "java";
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        String binName = osName.startsWith("win") ? "java.exe" : "java";
        File execFile = new File(new File(home, "bin"), binName);
        return execFile.exists() ? execFile.getAbsolutePath() : "java";
    }

    private void launchSubprocess(List<String> cmdLine) throws MojoExecutionException {
        try {
            Process p = new ProcessBuilder(cmdLine).redirectErrorStream(true).start();
            
            Thread outputCollector = new Thread(() -> {
                try (BufferedReader rdr = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = rdr.readLine()) != null) {
                        routeLogLine(line);
                    }
                } catch (IOException ex) {
                    getLog().error("Output capture issue: " + ex.getMessage());
                }
            });
            outputCollector.start();
            
            int result = p.waitFor();
            outputCollector.join();
            
            if (result != 0) {
                throw new MojoExecutionException("Tool terminated abnormally: code " + result);
            }
            
            getLog().info("Transformation completed");
            
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed starting subprocess", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Process interrupted", ex);
        }
    }

    private void routeLogLine(String content) {
        if (content.startsWith("[info]")) {
            getLog().info(content.substring(6).trim());
        } else if (content.startsWith("[match]")) {
            getLog().debug(content);
        } else if (content.startsWith("[warn]")) {
            getLog().warn(content.substring(6).trim());
        } else if (content.startsWith("[error]")) {
            getLog().error(content.substring(7).trim());
        } else {
            getLog().info(content);
        }
    }
}
