package dev.fintech.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class PdfPaymentReport {
    public record Document(byte[] bytes, String sha256) {}

    public Document render(PaymentEvent event) {
        String line = "Payment " + clean(event.paymentId()) + " | " + event.amountMinor() + " "
                + clean(event.currency()) + " | " + event.occurredAt();
        String stream = "BT /F1 11 Tf 50 760 Td (" + escapePdf(line) + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.US_ASCII).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int offset : offsets) pdf.append(String.format("%010d 00000 n \n", offset));
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        byte[] bytes = pdf.toString().getBytes(StandardCharsets.US_ASCII);
        return new Document(bytes, sha256(bytes));
    }

    private static String clean(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
