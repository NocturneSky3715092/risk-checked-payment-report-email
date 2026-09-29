package dev.fintech.report;

import java.io.IOException;
import java.util.Base64;

public final class PaymentReportService {
    public enum Outcome { SENT, REVIEW_REQUIRED }
    public record AuditResult(String paymentId, Outcome outcome, String messageId, String reportSha256) {}

    private final PdfPaymentReport reports;
    private final MailGateway mail;

    public PaymentReportService(PdfPaymentReport reports, MailGateway mail) {
        this.reports = reports;
        this.mail = mail;
    }

    public AuditResult process(PaymentEvent event) throws IOException, InterruptedException {
        if (event.risk() == PaymentEvent.RiskLevel.HIGH) {
            return new AuditResult(event.paymentId(), Outcome.REVIEW_REQUIRED, null, null);
        }
        PdfPaymentReport.Document report = reports.render(event);
        String encoded = Base64.getEncoder().encodeToString(report.bytes());
        String html = "<p>Payment report " + escape(event.paymentId()) + " is ready.</p>"
                + "<p><a download=\"payment-report.pdf\" href=\"data:application/pdf;base64," + encoded
                + "\">Download PDF report</a></p><p>SHA-256: " + report.sha256() + "</p>";
        String messageId = mail.send(event.recipientEmail(), "Payment report " + event.paymentId(), html,
                "payment-report:" + event.paymentId());
        return new AuditResult(event.paymentId(), Outcome.SENT, messageId, report.sha256());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    @FunctionalInterface
    public interface MailGateway {
        String send(String to, String subject, String html, String idempotencyKey)
                throws IOException, InterruptedException;
    }
}
