package dev.fintech.report;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public final class PaymentReportServiceTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger sends = new AtomicInteger();
        PaymentReportService service = new PaymentReportService(new PdfPaymentReport(), (to, subject, html, key) -> {
            sends.incrementAndGet();
            require(html.contains("data:application/pdf;base64,"), "email must carry the generated PDF");
            require(key.equals("payment-report:pay-42"), "retry identity must follow the payment");
            return "msg_test_42";
        });

        PaymentEvent highRisk = event("pay-41", PaymentEvent.RiskLevel.HIGH);
        PaymentReportService.AuditResult held = service.process(highRisk);
        require(held.outcome() == PaymentReportService.Outcome.REVIEW_REQUIRED, "high risk must be held");
        require(sends.get() == 0, "held payment must not send email");

        PaymentReportService.AuditResult sent = service.process(event("pay-42", PaymentEvent.RiskLevel.MEDIUM));
        require(sent.outcome() == PaymentReportService.Outcome.SENT, "medium risk should send");
        require(sent.messageId().equals("msg_test_42"), "message id must enter the audit result");
        require(sent.reportSha256().length() == 64, "report digest must be recorded");
        require(sends.get() == 1, "one eligible payment means one send");
        System.out.println("PASS: risk decision, PDF boundary, and audit result");
    }

    private static PaymentEvent event(String id, PaymentEvent.RiskLevel risk) {
        return new PaymentEvent(id, "acct-7", "user@example.com", 1299, "USD", risk,
                Instant.parse("2026-01-15T10:15:30Z"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
