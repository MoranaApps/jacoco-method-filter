package io.moranaapps.mavenplugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Mojo(name = "init-rules", threadSafe = true)
public class InitRulesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jmf.rulesFile", defaultValue = "${project.basedir}/jmf-rules.txt")
    private File rulesFile;

    @Parameter(property = "jmf.overwrite", defaultValue = "false")
    private boolean overwrite;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (rulesFile.exists() && !overwrite) {
            getLog().info("Rules file exists: " + rulesFile.getAbsolutePath());
            getLog().info("To replace, use: -Djmf.overwrite=true");
            return;
        }

        createRulesFile();
    }

    private void createRulesFile() throws MojoExecutionException {
        File templateRef = new File(project.getBasedir(), "jmf-rules.template.txt");
        
        getLog().info("Generating rules file: " + rulesFile.getAbsolutePath());
        
        try {
            if (!templateRef.exists()) {
                getLog().warn("Template unavailable at: " + templateRef.getAbsolutePath());
                getLog().info("Creating basic rules file");
                writeBasicRules();
            } else {
                Files.copy(templateRef.toPath(), rulesFile.toPath(), 
                          StandardCopyOption.REPLACE_EXISTING);
                getLog().info("Rules file created from template");
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("Rules file creation failed", ex);
        }
    }

    private void writeBasicRules() throws IOException {
        String basicRules = "# jacoco-method-filter — Rules Configuration\n" +
                           "# [jmf:1.0.0]\n" +
                           "#\n" +
                           "# Define coverage filter rules below.\n" +
                           "# Syntax: <class_pattern>#<method_pattern>(<descriptor_pattern>) [FLAGS]\n" +
                           "#\n" +
                           "# Common examples:\n" +
                           "# *#canEqual(*)       id:case-canequal\n" +
                           "# *#equals(*)         id:case-equals\n" +
                           "# *#hashCode(*)       id:case-hashcode\n" +
                           "# *#toString(*)       id:case-tostring\n" +
                           "#\n" +
                           "# Project-specific rules:\n\n";
        
        try (FileOutputStream out = new FileOutputStream(rulesFile)) {
            out.write(basicRules.getBytes("UTF-8"));
        }
    }
}
