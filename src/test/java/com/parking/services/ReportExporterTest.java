package com.parking.services;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDirectoryAndPortableUniqueFilename() throws Exception {
        Path output = ReportExporter.createUniqueReportPath(tempDir.resolve("nested"),
                "report: revenue/operations?", ".pdf");

        assertTrue(Files.isDirectory(output.getParent()));
        assertTrue(output.getFileName().toString().matches("[A-Za-z0-9._-]+-[0-9]{8}-[0-9]{6}-[0-9]{3}\\.pdf"));
        assertTrue(output.getFileName().toString().indexOf(':') < 0);
        assertTrue(output.getFileName().toString().indexOf('/') < 0);

        ReportExporter.writeBrandedPdf(output, "Title", "today", List.of("Summary"), List.of("Row"));
        Path second = ReportExporter.createUniqueReportPath(output.getParent(),
                "report: revenue/operations?", ".pdf");
        assertNotEquals(output, second);
    }

    @Test
    void doesNotOverwriteExistingPdf() throws Exception {
        Path output = tempDir.resolve("existing.pdf");
        ReportExporter.writeBrandedPdf(output, "Title", "today", List.of(), List.of());

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> ReportExporter.writeBrandedPdf(output, "Replacement", "today", List.of(), List.of()));
    }

    @Test
    void generatedPdfHasByteAccurateStructureAndPortableSpecialText() throws Exception {
        Path output = tempDir.resolve("complete-report.pdf");
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            rows.add("2026-09-12 | €1,234.50 | Café (paid) \\ row " + i);
        }

        ReportExporter.writeBrandedPdf(output, "Revenue & Operations", "2026-09-01 to 2026-09-12",
                List.of("Total: €308,625.00", "Completed: 250"), rows);

        byte[] bytes = Files.readAllBytes(output);
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4\n"));
        assertTrue(pdf.endsWith("%%EOF\n"));
        assertTrue(pdf.contains("Caf\u00E9"));
        assertTrue(pdf.contains("\u0080"), "Euro should use its WinAnsi byte");
        assertTrue(pdf.contains("\\(paid\\) \\\\"), "PDF delimiters must be escaped");

        Matcher length = Pattern.compile("/Length (\\d+) >>\\nstream\\n").matcher(pdf);
        assertTrue(length.find());
        int streamStart = length.end();
        int streamEnd = pdf.indexOf("\nendstream", streamStart);
        assertEquals(Integer.parseInt(length.group(1)), streamEnd - streamStart);

        int xref = pdf.indexOf("xref\n");
        Matcher startxref = Pattern.compile("startxref\\n(\\d+)\\n%%EOF").matcher(pdf);
        assertTrue(startxref.find());
        assertEquals(xref, Integer.parseInt(startxref.group(1)));
        String[] xrefLines = pdf.substring(xref).split("\\n");
        assertEquals("0000000000 65535 f ", xrefLines[2]);
        for (int object = 1; object <= 5; object++) {
            int offset = Integer.parseInt(xrefLines[2 + object].substring(0, 10));
            assertEquals(object + " 0 obj", pdf.substring(offset, pdf.indexOf('\n', offset)));
        }
    }

    @Test
    void failedMoveCleansTemporaryPdfOutput() throws Exception {
        Path target = tempDir.resolve("already-a-directory.pdf");
        Files.createDirectory(target);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> ReportExporter.writeBrandedPdf(target, "Title", "today", List.of(), List.of()));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().startsWith("already-a-directory.pdf")
                    && file.getFileName().toString().endsWith(".tmp")));
        }
    }
}
