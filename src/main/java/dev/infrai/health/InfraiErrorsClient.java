package dev.infrai.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiErrorsClient implements AppointmentSafetyService.FailureCapture {
    private final ServiceConfig config;
    private final HttpClient http;

    public InfraiErrorsClient(ServiceConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiErrorsClient(ServiceConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void capture(AppointmentSafetyService.FailureRecord failure) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "appointment-agent/slot-reservation failed");
        payload.put("message", failure.summary());
        payload.put("exception", failure.exception());
        payload.put("level", "error");
        payload.put("fingerprint", List.of("appointment-agent", "slot-reservation", failure.failureClass()));
        payload.put("context", Map.of(
                "workflow_id", failure.workflowId(),
                "clinic_code", failure.clinicCode(),
                "attempt", failure.attempt()));
        payload.put("environment", failure.environment());
        payload.put("service", "appointment-agent");
        payload.put("idempotency_key", failure.workflowId() + ":slot-reservation:" + failure.attempt());

        // The copyable call is infrai.errors.capture via POST /v1/errors/capture.
        postCapture(payload);
    }

    private void postCapture(Map<String, Object> payload) throws IOException, InterruptedException {
        String body = JsonCodec.encode(payload);
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(config.infraiBaseUri().resolve("/v1/errors/capture"))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", String.valueOf(payload.get("idempotency_key")))
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> envelope;
            try {
                envelope = JsonCodec.decodeObject(response.body());
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Infrai returned an unreadable response", malformed);
            }

            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                Thread.sleep(retryDelay(response, attempt).toMillis());
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw InfraiApiException.from(response.statusCode(), envelope.get("error"));
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response " + response.statusCode());
            }
            return;
        }
        throw new IOException("Infrai retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L * (1L << (attempt - 1)));
        }
    }

    public static final class InfraiApiException extends IOException {
        private final int statusCode;
        private final String code;

        private InfraiApiException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }

        static InfraiApiException from(int statusCode, Object errorValue) {
            if (errorValue instanceof Map<?, ?> error) {
                Object codeValue = error.get("code");
                Object messageValue = error.get("message");
                String code = codeValue == null ? "UNKNOWN" : String.valueOf(codeValue);
                String message = messageValue == null ? "Request rejected" : String.valueOf(messageValue);
                return new InfraiApiException(statusCode, code, message);
            }
            return new InfraiApiException(statusCode, "UNKNOWN", "Request rejected");
        }

        public int statusCode() { return statusCode; }
        public String code() { return code; }
    }
}
