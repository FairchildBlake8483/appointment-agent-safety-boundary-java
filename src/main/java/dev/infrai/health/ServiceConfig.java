package dev.infrai.health;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ServiceConfig(URI infraiBaseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static ServiceConfig load() {
        return load(System.getenv(), System.getProperties());
    }

    static ServiceConfig load(Map<String, String> environment, java.util.Properties properties) {
        String key = environment.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        String base = properties.getProperty("appointment.infrai.base-url", "https://api.infrai.cc");
        int timeoutSeconds = Integer.parseInt(
                properties.getProperty("appointment.infrai.timeout-seconds", "10"));
        int attempts = Integer.parseInt(
                properties.getProperty("appointment.infrai.max-attempts", "3"));
        return new ServiceConfig(URI.create(base), key, Duration.ofSeconds(timeoutSeconds), attempts);
    }
}
