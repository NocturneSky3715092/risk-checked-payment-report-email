package dev.fintech.report;

import java.time.Instant;

public final class SendPaymentReport {
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("usage: <recipient> <payment-id> <amount-minor> <currency> <LOW|MEDIUM|HIGH>");
            System.exit(2);
        }
        PaymentEvent event = new PaymentEvent(args[1], "demo-account", args[0], Long.parseLong(args[2]),
                args[3], PaymentEvent.RiskLevel.valueOf(args[4]), Instant.now());
        InfraiEmailClient client = new InfraiEmailClient(ReportConfig.fromEnvironment());
        PaymentReportService service = new PaymentReportService(new PdfPaymentReport(), client::send);
        PaymentReportService.AuditResult result = service.process(event);
        System.out.printf("payment_id=%s outcome=%s message_id=%s report_sha256=%s%n",
                result.paymentId(), result.outcome(), result.messageId(), result.reportSha256());
    }
}
