package io.moranaapps.mavenplugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private org.apache.maven.plugin.descriptor.PluginDescriptor pluginDescriptor;

    @Parameter(property = "jmf.jacocoExecFile", defaultValue = "${project.build.directory}/jacoco.exec")
    private File jacocoExecFile;

    @Parameter(property = "jmf.classesDirectory", defaultValue = "${project.build.directory}/classes-filtered")
    private File classesDirectory;

    @Parameter(property = "jmf.sourceDirectories")
    private File[] sourceDirectories;

    @Parameter(property = "jmf.reportDirectory", defaultValue = "${project.build.directory}/jacoco-report")
    private File reportDirectory;

    @Parameter(property = "jmf.xmlOutputFile", defaultValue = "${project.build.directory}/jacoco.xml")
    private File xmlOutputFile;

    @Parameter(property = "jmf.csvOutputFile", defaultValue = "${project.build.directory}/jacoco.csv")
    private File csvOutputFile;

    @Parameter(property = "jmf.reportName", defaultValue = "${project.name}")
    private String reportName;

    @Parameter(property = "jmf.jacocoIncludes", defaultValue = "**")
    private String jacocoIncludes;

    @Parameter(property = "jmf.jacocoExcludes", defaultValue = "")
    private String jacocoExcludes;

    @Parameter(property = "jmf.sourceEncoding", defaultValue = "UTF-8")
    private String sourceEncoding;

    @Parameter(property = "jmf.reportFormats", defaultValue = "html,xml,csv")
    private String reportFormats;

    @Parameter(property = "jmf.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "jmf.skipIfExecMissing", defaultValue = "true")
    private boolean skipIfExecMissing;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Report generation bypassed");
            return;
        }

        // Skip aggregator (pom-packaged) projects where there is no real output directory
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Report generation bypassed for aggregator project with packaging 'pom'");
            return;
        }

        if (!jacocoExecFile.exists()) {
            String msg = "Execution data not found: " + jacocoExecFile.getAbsolutePath();
            if (skipIfExecMissing) {
                getLog().info(msg + " (skipping)");
                return;
            } else {
                throw new MojoExecutionException(msg);
            }
        }

        if (sourceDirectories == null || sourceDirectories.length == 0) {
            List<String> roots = project.getCompileSourceRoots();
            List<File> dirs = new ArrayList<File>();
            if (roots != null) {
                for (String root : roots) {
                    if (root != null) {
                        File dir = new File(root);
                        if (dir.isDirectory()) {
                            dirs.add(dir);
                        }
                    }
                }
            }
            if (dirs.isEmpty()) {
                dirs.add(new File(project.getBasedir(), "src/main/java"));
            }
            sourceDirectories = dirs.toArray(new File[0]);
        }

        produceReports();
    }

    private void produceReports() throws MojoExecutionException {
        File cliJar = findJacocoCliJar();
        String javaCmd = locateJavaExec();
        List<String> command = buildReportCmd(javaCmd, cliJar);
        
        getLog().info("╔═══ JaCoCo Method Filter: Report Generation ═══");
        getLog().info("║ Exec data:  " + jacocoExecFile.getAbsolutePath());
        getLog().info("║ Classes:    " + classesDirectory.getAbsolutePath());
        getLog().info("║ Report:     " + reportDirectory.getAbsolutePath());
        getLog().info("║ Formats:    " + reportFormats);
        if (reportName != null && !reportName.isEmpty()) {
            getLog().info("║ Title:      " + reportName);
        }
        if (sourceEncoding != null && !sourceEncoding.isEmpty()) {
            getLog().info("║ Encoding:   " + sourceEncoding);
        }
        if (jacocoIncludes != null && !jacocoIncludes.isEmpty()) {
            getLog().info("║ Includes:   " + jacocoIncludes);
        }
        if (jacocoExcludes != null && !jacocoExcludes.isEmpty()) {
            getLog().info("║ Excludes:   " + jacocoExcludes);
        }
        getLog().info("╚════════════════════════════════════════════════");

        executeReportTool(command);
    }

    private List<String> buildReportCmd(String javaBin, File jarFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-jar");
        cmd.add(jarFile.getAbsolutePath());
        cmd.add("report");
        cmd.add(jacocoExecFile.getAbsolutePath());
        cmd.add("--classfiles");
        cmd.add(classesDirectory.getAbsolutePath());
        
        for (File src : sourceDirectories) {
            if (src.isDirectory()) {
                cmd.add("--sourcefiles");
                cmd.add(src.getAbsolutePath());
            }
        }
        
        // Parse report formats and conditionally add outputs
        Set<String> formats = new HashSet<>();
        if (reportFormats != null && !reportFormats.isEmpty()) {
            for (String format : reportFormats.split(",")) {
                formats.add(format.trim().toLowerCase());
            }
        }
        
        if (formats.contains("html")) {
            cmd.add("--html");
            cmd.add(reportDirectory.getAbsolutePath());
        }
        
        if (formats.contains("xml")) {
            cmd.add("--xml");
            cmd.add(xmlOutputFile.getAbsolutePath());
        }
        
        if (formats.contains("csv")) {
            cmd.add("--csv");
            cmd.add(csvOutputFile.getAbsolutePath());
        }
        
        // Add report name/title if provided
        if (reportName != null && !reportName.isEmpty()) {
            cmd.add("--name");
            cmd.add(reportName);
        }
        
        // Add source encoding if provided
        if (sourceEncoding != null && !sourceEncoding.isEmpty()) {
            cmd.add("--encoding");
            cmd.add(sourceEncoding);
        }
        
        // Add includes if provided and not default
        if (jacocoIncludes != null && !jacocoIncludes.isEmpty() && !"**".equals(jacocoIncludes)) {
            cmd.add("--includes");
            cmd.add(jacocoIncludes);
        }
        
        // Add excludes if provided
        if (jacocoExcludes != null && !jacocoExcludes.isEmpty()) {
            cmd.add("--excludes");
            cmd.add(jacocoExcludes);
        }
        
        return cmd;
    }

    private void executeReportTool(List<String> cmdArgs) throws MojoExecutionException {
        try {
            Process p = new ProcessBuilder(cmdArgs).redirectErrorStream(true).start();
            
            Thread logCapture = new Thread(() -> {
                try (BufferedReader rdr = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = rdr.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            getLog().info(line.trim());
                        }
                    }
                } catch (IOException ex) {
                    getLog().error("Log capture error: " + ex.getMessage());
                }
            });
            logCapture.start();
            
            int exitVal = p.waitFor();
            logCapture.join();
            
            if (exitVal != 0) {
                throw new MojoExecutionException("JaCoCo CLI exited with code: " + exitVal);
            }
            
            getLog().info("Reports generated successfully");
            
        } catch (IOException ex) {
            throw new MojoExecutionException("CLI launch failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Report generation interrupted", ex);
        }
    }

    private File findJacocoCliJar() throws MojoExecutionException {
        // Search plugin dependencies for JaCoCo CLI
        List<Artifact> deps = pluginDescriptor.getArtifacts();
        for (Artifact dep : deps) {
            if ("org.jacoco".equals(dep.getGroupId()) && 
                "org.jacoco.cli".equals(dep.getArtifactId())) {
                File jarLocation = dep.getFile();
                if (jarLocation != null && jarLocation.exists()) {
                    return jarLocation;
                }
            }
        }
        throw new MojoExecutionException("JaCoCo CLI JAR not found in plugin dependencies");
    }

    private String locateJavaExec() {
        String home = System.getProperty("java.home");
        if (home == null) return "java";
        
        String osName = System.getProperty("os.name", "").toLowerCase();
        String binName = osName.startsWith("win") ? "java.exe" : "java";
        File execFile = new File(new File(home, "bin"), binName);
        return execFile.exists() ? execFile.getAbsolutePath() : "java";
    }
}
