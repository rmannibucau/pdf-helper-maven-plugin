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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.PDFMergerUtility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Mojo(name = "merge", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class PdfMergerMojo extends AbstractMojo {
    @Parameter
    private List<Pdf> pdfs;

    @Parameter
    private List<Pdf> mavenPdfs;

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
            for (final Pdf pdf : pdfs) {
                addPdf(merger, pdf, pdf.getLocation());
            }
        }
        if (mavenPdfs != null) {
            for (final Pdf mavenPdf : mavenPdfs) {
                final String[] infos = mavenPdf.getLocation().split(":");
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
                addPdf(merger, mavenPdf, artifact.getFile().getAbsolutePath());
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
            getLog().info("Attaching PDF " + generated.getAbsolutePath());
            if (classifier != null && !classifier.isEmpty()) {
                helper.attachArtifact(project, "pdf", classifier, generated);
            } else {
                helper.attachArtifact(project, "pdf", generated);
            }
        } else {
            getLog().info("Generated PDF " + generated.getAbsolutePath());
        }
    }

    private void addPdf(final PDFMergerUtility merger, final Pdf pdf, final String fileLocation) throws MojoExecutionException {
        final boolean hasTitle = pdf.getTitle() != null && !pdf.getTitle().isEmpty();
        final boolean hasDescription = pdf.getDescription() != null && !pdf.getDescription().isEmpty();
        if (hasDescription || hasTitle) {
            try {
                final PDDocument document = new PDDocument();

                final PDPage page = new PDPage();
                document.addPage(page);

                final PDPageContentStream contentStream = new PDPageContentStream(document, page);
                final float titleHeight;
                if (hasTitle) { // should be 1 line
                    contentStream.beginText();
                    final int fontSize = pdf.getTitleSize();
                    final PDType1Font font = PDType1Font.getStandardFont(pdf.getTitleFont());
                    final float titleWidth = width(font, fontSize, pdf.getTitle());
                    titleHeight = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;

                    contentStream.setFont(font, fontSize);
                    contentStream.moveTextPositionByAmount((page.getMediaBox().getWidth() - titleWidth) / 2, page.getMediaBox().getHeight() - 50 - titleHeight);
                    contentStream.drawString(pdf.getTitle());
                    contentStream.endText();
                } else {
                    titleHeight = 0;
                }
                if (hasDescription) {
                    final PDType1Font font = PDType1Font.getStandardFont(pdf.getDescriptionFont());

                    final int margins = 80;
                    final float maxWidth = page.getMediaBox().getWidth() - margins * 2;

                    final List<String> lines = new LinkedList<String>();
                    final StringBuilder current = new StringBuilder();
                    for (char c : pdf.getDescription().toCharArray()) {
                        if (c == ' ' || c == '\n') {
                            current.append(c); // we don't want "\n " or "\n\n"
                            if (width(font, pdf.getDescriptionSize(), current.toString()) > maxWidth) {
                                current.setLength(current.length() - 1); // strip this not readable character
                                lines.add(current.toString());
                                current.setLength(0);
                            }
                        } else if (c == '.' || c == '!' || c == '?' || c == ':') {
                            current.append(c);
                            if (width(font, pdf.getDescriptionSize(), current.toString()) > maxWidth) {
                                lines.add(current.toString());
                                current.setLength(0);
                            }
                        } else {
                            current.append(c);
                        }
                    }
                    if (current.length() > 0) {
                        lines.add(current.toString());
                    }

                    float positionY = 700 - (pdf.getDescriptionSize() + titleHeight);

                    for (final String string : lines) {
                        contentStream.beginText();
                        contentStream.setFont(font, pdf.getDescriptionSize());
                        contentStream.moveTextPositionByAmount(margins, positionY);
                        contentStream.drawString(string);
                        contentStream.endText();
                        positionY = positionY - 20;
                    }
                }
                contentStream.close();

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                document.save(baos);
                document.close();
                merger.addSource(new ByteArrayInputStream(baos.toByteArray()));
            } catch (final Exception ioe) {
                throw new MojoExecutionException(ioe.getMessage(), ioe);
            }
        }
        merger.addSource(pdf.getLocation());
    }

    private float width(final PDType1Font font, final int fontSize, final String current) throws IOException {
        return font.getStringWidth(current) / 1000 * fontSize;
    }
}
