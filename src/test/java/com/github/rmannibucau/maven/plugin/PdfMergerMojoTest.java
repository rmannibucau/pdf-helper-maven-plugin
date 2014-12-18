package com.github.rmannibucau.maven.plugin;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.PDFTextStripper;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PdfMergerMojoTest {
    @Rule
    public final MojoRule rule = new MojoRule();

    @Test
    public void mergeFiles() throws Exception {
        final String workDir = "target/PdfMergerMojoTest-mergeFiles-out";
        final String inputDir = "target/PdfMergerMojoTest-mergeFiles-in";
        FileUtils.mkdir(inputDir);

        final int pageNumber = 2;
        for (int i = 1; i <= pageNumber; i++) {
            final PDDocument document = new PDDocument();

            final PDPage page = new PDPage();
            document.addPage(page);

            final PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 12);
            contentStream.moveTextPositionByAmount(100, 700);
            contentStream.drawString("Page #" + i);
            contentStream.endText();
            contentStream.close();

            document.save(inputDir + "/pdf" + i + ".pdf");
            document.close();
        }

        final StringReader reader = new StringReader(
                "<configuration>" +
                "  <pdfs>" +
                "    <pdf>" + inputDir + "/pdf1.pdf</pdf>" +
                "    <pdf>" + inputDir + "/pdf2.pdf</pdf>" +
                "  </pdfs>" +
                "  <outputDir>" + workDir + "</outputDir>" +
                "  <outputName>result</outputName>" +
                "</configuration>");
        final DefaultPlexusConfiguration pluginConfiguration = new XmlPlexusConfiguration(Xpp3DomBuilder.build(reader));
        rule.configureMojo(new PdfMergerMojo(), pluginConfiguration).execute();

        final File output = new File(workDir + "/result.pdf");
        assertTrue(output.exists());

        final PDDocument doc = PDDocument.load(output);
        assertEquals(pageNumber, doc.getNumberOfPages());

        for (int i = 1; i <= pageNumber; i++) {
            final PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            assertEquals("Page #" + i, stripper.getText(doc).trim());
        }
    }
}
