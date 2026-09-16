package com.parking.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dependency-free branded PDF writer for summary reports. */
public final class ReportExporter {
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    private ReportExporter() { }

    /** Creates a portable, non-overwriting report path inside the supplied directory. */
    public static Path createUniqueReportPath(Path directory, String baseName, String extension) throws IOException {
        if (directory == null) throw new IllegalArgumentException("Report directory is required");
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Files.createDirectories(normalizedDirectory);
        if (!Files.isDirectory(normalizedDirectory)) {
            throw new IOException("Report output is not a directory: " + normalizedDirectory);
        }
        String stem = portableFilePart(baseName, "parkingos-report");
        String suffix = portableFilePart(extension, "pdf");
        if (!suffix.startsWith(".")) suffix = "." + suffix;
        String prefix = stem + "-" + LocalDateTime.now().format(FILE_TIMESTAMP);
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String name = prefix + (attempt == 0 ? "" : "-" + attempt) + suffix;
            Path candidate = normalizedDirectory.resolve(name).normalize();
            if (!candidate.startsWith(normalizedDirectory)) {
                throw new IOException("Report path escaped its output directory");
            }
            if (!Files.exists(candidate)) return candidate;
        }
        throw new IOException("Could not allocate a unique report filename");
    }

    public static void writeBrandedPdf(Path path, String title, String dateRange,
                                       List<String> summary, List<String> rows) throws IOException {
        writeBrandedPdf(path, title, dateRange, summary, rows, List.of());
    }

    /**
     * Phase 5: branded report with header rule, summary block, column detail
     * lines, and an optional totals footer. Single Helvetica family, three
     * weights-by-size (18/14/10/9) — matches the in-app type scale closely
     * enough for paper without embedding fonts.
     */
    public static void writeBrandedPdf(Path path, String title, String dateRange,
                                       List<String> summary, List<String> rows,
                                       List<String> totals) throws IOException {
        List<Page> pages = paginate(title, dateRange, summary, rows, totals);
        writePdf(path, pages);
    }

    private static final int PAGE_WIDTH = 612;
    private static final int PAGE_HEIGHT = 792;
    private static final int MARGIN = 40;
    private static final int TOP_Y = 750;
    private static final int BOTTOM_Y = 60;
    private static final int ROWS_PER_PAGE = 44;

    private record Page(String stream, int number, int total) { }

    private static List<Page> paginate(String title, String dateRange, List<String> summary,
                                       List<String> rows, List<String> totals) {
        List<String> safeRows = rows == null ? List.of() : rows;
        List<String> safeTotals = totals == null ? List.of() : totals;
        int pageCount = Math.max(1, (int) Math.ceil(safeRows.size() / (double) ROWS_PER_PAGE));
        List<Page> pages = new ArrayList<>(pageCount);
        for (int page = 0; page < pageCount; page++) {
            int from = page * ROWS_PER_PAGE;
            int to = Math.min(safeRows.size(), from + ROWS_PER_PAGE);
            List<String> slice = safeRows.subList(from, to);
            boolean last = page == pageCount - 1;
            pages.add(new Page(pageStream(title, dateRange, summary, slice,
                    last ? safeTotals : List.of(), page + 1, pageCount), page + 1, pageCount));
        }
        return pages;
    }

    private static String pageStream(String title, String dateRange, List<String> summary,
                                     List<String> rows, List<String> totals,
                                     int number, int total) {
        StringBuilder stream = new StringBuilder();
        // Branded header: wordmark, rule, title block with date range.
        stream.append("BT /F1 18 Tf 40 ").append(TOP_Y).append(" Td (PARKINGOS) Tj ET ");
        stream.append(headerRule());
        stream.append("BT /F1 14 Tf 40 ").append(TOP_Y - 46).append(" Td (")
                .append(escape(title)).append(") Tj ET ");
        stream.append("BT /F1 9 Tf 40 ").append(TOP_Y - 64).append(" Td (")
                .append(escape(dateRange)).append(") Tj ET ");
        int cursor = TOP_Y - 88;
        // Summary block.
        for (String line : summary == null ? List.<String>of() : summary) {
            cursor -= 15;
            stream.append("BT /F1 10 Tf 40 ").append(cursor).append(" Td (")
                    .append(escape(line)).append(") Tj ET ");
        }
        cursor -= 12;
        stream.append("BT /F1 10 Tf 40 ").append(cursor).append(" Td (REPORT DETAILS - GARAGE SCOPED) Tj ET ");
        cursor -= 16;
        // Detail rows; cursor never passes the footer reserve.
        for (String line : rows) {
            if (cursor <= BOTTOM_Y + 40) break;
            cursor -= 13;
            stream.append("BT /F1 9 Tf 40 ").append(cursor).append(" Td (")
                    .append(escape(line)).append(") Tj ET ");
        }
        // Totals footer pinned above the page number.
        for (String line : totals) {
            cursor -= 14;
            stream.append("BT /F1 10 Tf 40 ").append(cursor).append(" Td (")
                    .append(escape(line)).append(") Tj ET ");
        }
        // Footer: page number, generation timestamp.
        stream.append("BT /F1 8 Tf 40 40 Td (Page ").append(number).append(" of ").append(total)
                .append("  |  Generated ").append(escape(LocalDateTime.now().format(FILE_TIMESTAMP)))
                .append(") Tj ET ");
        return stream.toString();
    }

    private static String headerRule() {
        // 1pt branded rule under the wordmark (teal-ish gray in WinAnsi-safe black).
        return "0 0 0 RG 1 w 40 " + (TOP_Y - 8) + " m " + (PAGE_WIDTH - MARGIN) + " " + (TOP_Y - 8) + " l S ";
    }

    private static void writePdf(Path path, List<Page> pages) throws IOException {
        if (path == null) throw new IllegalArgumentException("PDF output path is required");
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("PDF output path has no parent directory");
        Files.createDirectories(parent);
        if (Files.exists(target)) throw new FileAlreadyExistsException(target.toString());

        int pageCount = pages.size();
        // Objects: 1 catalog, 2 pages, 3..3+N-1 page dicts, then font, then contents.
        int firstPageObj = 3;
        int fontObj = firstPageObj + pageCount;
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writeLatin1(pdf, "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n");
        List<Integer> offsets = new ArrayList<>();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) kids.append(firstPageObj + i).append(" 0 R ");
        writeObject(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        writeObject(pdf, offsets, 2, "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>");
        for (int i = 0; i < pageCount; i++) {
            int contentObj = fontObj + 1 + i;
            writeObject(pdf, offsets, firstPageObj + i,
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT + "] "
                    + "/Resources << /Font << /F1 " + fontObj + " 0 R >> >> /Contents " + contentObj + " 0 R >>");
        }
        writeObject(pdf, offsets, fontObj, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        for (Page page : pages) {
            byte[] contentBytes = page.stream().getBytes(StandardCharsets.ISO_8859_1);
            offsets.add(pdf.size());
            writeLatin1(pdf, (fontObj + 1 + page.number() - 1) + " 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
            pdf.write(contentBytes);
            writeLatin1(pdf, "\nendstream\nendobj\n");
        }

        int xref = pdf.size();
        int size = fontObj + 1 + pageCount;
        writeLatin1(pdf, "xref\n0 " + size + "\n0000000000 65535 f \n");
        for (int offset : offsets) writeLatin1(pdf, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        writeLatin1(pdf, "trailer\n<< /Size " + size + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");

        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(pdf.toByteArray());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            temporary = null;
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static void writeObject(ByteArrayOutputStream pdf, List<Integer> offsets,
                                    int number, String body) throws IOException {
        offsets.add(pdf.size());
        writeLatin1(pdf, number + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void writeLatin1(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String portableFilePart(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        result = result.replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("[-. ]+$", "");
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) return fallback;
        if (result.matches("(?i)con|prn|aux|nul|com[1-9]|lpt[1-9]")) return "-" + result;
        return result;
    }

    private static String escape(String value) {
        String input = value == null ? "" : value;
        StringBuilder escaped = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            char encoded = winAnsiByte(character);
            if (encoded == '\\' || encoded == '(' || encoded == ')') escaped.append('\\');
            escaped.append(encoded);
        }
        return escaped.toString();
    }

    private static char winAnsiByte(char character) {
        return switch (character) {
            case '\u20AC' -> '\u0080'; // euro
            case '\u201A' -> '\u0082';
            case '\u0192' -> '\u0083';
            case '\u201E' -> '\u0084';
            case '\u2026' -> '\u0085';
            case '\u2020' -> '\u0086';
            case '\u2021' -> '\u0087';
            case '\u02C6' -> '\u0088';
            case '\u2030' -> '\u0089';
            case '\u0160' -> '\u008A';
            case '\u2039' -> '\u008B';
            case '\u0152' -> '\u008C';
            case '\u017D' -> '\u008E';
            case '\u2018' -> '\u0091';
            case '\u2019' -> '\u0092';
            case '\u201C' -> '\u0093';
            case '\u201D' -> '\u0094';
            case '\u2022' -> '\u0095';
            case '\u2013' -> '\u0096';
            case '\u2014' -> '\u0097';
            case '\u02DC' -> '\u0098';
            case '\u2122' -> '\u0099';
            case '\u0161' -> '\u009A';
            case '\u203A' -> '\u009B';
            case '\u0153' -> '\u009C';
            case '\u017E' -> '\u009E';
            case '\u0178' -> '\u009F';
            default -> character >= 0x20 && character <= 0xFF ? character : '?';
        };
    }
}
