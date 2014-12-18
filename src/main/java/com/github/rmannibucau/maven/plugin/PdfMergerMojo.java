package com.github.rmannibucau.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.repository.RepositorySystem;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.util.PDFMergerUtility;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Mojo(name = "merge", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PdfMergerMojo extends AbstractMojo {
    @Parameter
    private List<File> pdfs;

    @Parameter
    private List<String> mavenPdfs;

    @Parameter(property = "pdf-merger.outputName", defaultValue = "all-in-one.pdf")
    private String outputName;

    @Parameter(property = "pdf-merger.output", defaultValue = "${project.build.directory}/generated/pdf/")
    private File outputDir;

    @Parameter(property = "pdf-merger.attach", defaultValue = "true")
    private boolean attach;

    @Parameter(property = "pdf-merger.classifier")
    private String classifier;

    @Parameter(property = "pdf-merger.skip", defaultValue = "false")
    private boolean skip;

    @Component
    private MavenProject project;

    @Component
    private MavenProjectHelper helper;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping " + getClass().getSimpleName());
            return;
        }

        final PDFMergerUtility merger = new PDFMergerUtility();
        if (pdfs != null) {
            for (final File pdf : pdfs) {
                merger.addSource(pdf);
            }
        }
        if (mavenPdfs != null) {
            for (final String coordinates : mavenPdfs) {
                final String[] infos = coordinates.split(":");
                final String type;
                if (infos.length < 3) {
                    throw new MojoExecutionException("format for librairies should be <groupId>:<artifactId>:<version>[:<type>[:<classifier>]]");
                }
                if (infos.length >= 4) {
                    type = infos[3];
                } else {
                    type = "pdf";
                }

                final Artifact artifact;
                if (infos.length == 5) {
                    artifact = repositorySystem.createArtifactWithClassifier(infos[0], infos[1], infos[2], type, infos[4]);
                } else {
                    artifact = repositorySystem.createArtifact(infos[0], infos[1], infos[2], type);
                }

                final ArtifactResolutionRequest request = new ArtifactResolutionRequest();
                request.setArtifact(artifact);
                request.setResolveRoot(true);
                request.setResolveTransitively(false);
                request.setServers(session.getRequest().getServers());
                request.setMirrors(session.getRequest().getMirrors());
                request.setProxies(session.getRequest().getProxies());
                request.setLocalRepository(session.getLocalRepository());
                request.setRemoteRepositories(session.getRequest().getRemoteRepositories());
                repositorySystem.resolve(request);
                merger.addSource(artifact.getFile());
            }

        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalArgumentException(outputDir.getAbsolutePath() + " can't be created");
        }

        final File generated = new File(outputDir, outputName + ".pdf");
        merger.setDestinationFileName(generated.getAbsolutePath());

        try {
            merger.mergeDocuments();
        } catch (final IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (final COSVisitorException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (attach) {
            getLog().info("Attaching PDF " + getClass().getSimpleName());
            if (classifier != null && !classifier.isEmpty()) {
                helper.attachArtifact(project, "pdf", classifier, generated);
            } else {
                helper.attachArtifact(project, "pdf", generated);
            }
        }
    }
}
