package dev.fintech.report;

import java.time.Instant;

public record PaymentEvent(
        String paymentId,
        String accountId,
        String recipientEmail,
        long amountMinor,
        String currency,
        RiskLevel risk,
        Instant occurredAt) {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public PaymentEvent {
        if (paymentId.isBlank() || accountId.isBlank() || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("payment, account, and recipient are required");
        }
        if (amountMinor <= 0 || currency.isBlank()) {
            throw new IllegalArgumentException("amount must be positive and currency is required");
        }
    }
}
