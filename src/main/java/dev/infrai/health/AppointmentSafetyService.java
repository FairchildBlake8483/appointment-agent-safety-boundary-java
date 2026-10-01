package dev.infrai.health;

import java.time.Instant;
import java.util.Objects;

public final class AppointmentSafetyService {
    private final FailureCapture failureCapture;
    private final OperationalNotifier notifier;

    public AppointmentSafetyService(FailureCapture failureCapture, OperationalNotifier notifier) {
        this.failureCapture = Objects.requireNonNull(failureCapture);
        this.notifier = Objects.requireNonNull(notifier);
    }

    public Outcome run(Request request, AppointmentAgent agent) throws Exception {
        try {
            String confirmation = agent.reserve(request);
            return new Outcome(Status.CONFIRMED, confirmation,
                    "Appointment confirmed. Follow the clinic instructions in your confirmation.");
        } catch (Exception failure) {
            FailureRecord record = new FailureRecord(
                    request.workflowId(), request.clinicCode(), request.attempt(), request.environment(),
                    failure.getClass().getSimpleName(), "Slot reservation did not complete",
                    failure.getClass().getName());
            failureCapture.capture(record);
            notifier.notify(new OperationalNotice(
                    request.workflowId(), request.clinicCode(), "APPOINTMENT_REVIEW_REQUIRED", Instant.now()));
            return new Outcome(Status.REVIEW_REQUIRED, null,
                    "Your request is under review. Keep your existing care plan until the clinic confirms a time.");
        }
    }

    public enum Status { CONFIRMED, REVIEW_REQUIRED }

    public record Request(String workflowId, String clinicCode, int attempt, String environment) {}
    public record Outcome(Status status, String confirmationId, String patientMessage) {}
    public record OperationalNotice(String workflowId, String clinicCode, String reason, Instant createdAt) {}
    public record FailureRecord(String workflowId, String clinicCode, int attempt, String environment,
                                String failureClass, String summary, String exception) {}

    @FunctionalInterface
    public interface AppointmentAgent { String reserve(Request request) throws Exception; }

    @FunctionalInterface
    public interface FailureCapture { void capture(FailureRecord failure) throws Exception; }

    @FunctionalInterface
    public interface OperationalNotifier { void notify(OperationalNotice notice); }
}
