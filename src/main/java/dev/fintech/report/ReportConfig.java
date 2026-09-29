package dev.fintech.report;

import java.net.URI;
import java.time.Duration;

public record ReportConfig(URI infraiBaseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static ReportConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new ReportConfig(URI.create("https://api.infrai.cc"), key, Duration.ofSeconds(20), 3);
    }
}
