package dev.fintech.report;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient {
    // Canonical call: infrai.email.send
    private static final String SEND_PATH = "/v1/email/send";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final HttpClient http;
    private final ReportConfig config;

    public InfraiEmailClient(ReportConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build();
    }

    public String send(String to, String subject, String html, String idempotencyKey)
            throws IOException, InterruptedException {
        String body = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\",\"idempotency_key\":\""
                + json(idempotencyKey) + "\"}";
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.infraiBaseUri().resolve(SEND_PATH))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                sleep(backoff(response, attempt));
                continue;
            }
            if (!envelope.ok()) throw new InfraiException(envelope.errorCode(), response.statusCode());
            if (response.statusCode() >= 500) throw new IOException("email transport returned " + response.statusCode());
            if (envelope.messageId() == null) throw new IOException("email response omitted message_id");
            return envelope.messageId();
        }
        throw new IOException("email retry budget exhausted");
    }

    private static Envelope decodeEnvelope(String body, int status) throws IOException {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) throw new IOException("email response was not an envelope (HTTP " + status + ")");
        Matcher id = MESSAGE_ID.matcher(body);
        Matcher code = ERROR_CODE.matcher(body);
        return new Envelope(Boolean.parseBoolean(ok.group(1)), id.find() ? id.group(1) : null,
                code.find() ? code.group(1) : "EMAIL_REJECTED");
    }

    private static Duration backoff(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .flatMap(InfraiEmailClient::seconds)
                .map(Duration::ofSeconds)
                .orElse(Duration.ofMillis(250L << (attempt - 1)));
    }

    private static java.util.Optional<Long> seconds(String value) {
        try { return java.util.Optional.of(Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
    }

    private static void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Envelope(boolean ok, String messageId, String errorCode) {}

    public static final class InfraiException extends IOException {
        private final String code;
        private final int status;
        public InfraiException(String code, int status) {
            super("email rejected: " + code);
            this.code = code;
            this.status = status;
        }
        public String code() { return code; }
        public int status() { return status; }
    }
}
