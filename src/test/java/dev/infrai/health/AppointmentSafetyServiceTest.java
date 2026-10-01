package dev.infrai.health;

import java.util.ArrayList;
import java.util.List;

public final class AppointmentSafetyServiceTest {
    public static void main(String[] args) throws Exception {
        List<AppointmentSafetyService.FailureRecord> captures = new ArrayList<>();
        List<AppointmentSafetyService.OperationalNotice> notices = new ArrayList<>();
        AppointmentSafetyService service = new AppointmentSafetyService(captures::add, notices::add);
        AppointmentSafetyService.Request request =
                new AppointmentSafetyService.Request("wf-77", "NEURO-01", 2, "test");

        AppointmentSafetyService.Outcome outcome = service.run(request,
                ignored -> { throw new IllegalArgumentException("model selected an expired slot"); });

        check(outcome.status() == AppointmentSafetyService.Status.REVIEW_REQUIRED, "review status");
        check(outcome.confirmationId() == null, "no false confirmation");
        check(outcome.patientMessage().contains("existing care plan"), "safe patient instruction");
        check(captures.size() == 1, "one captured failure");
        check(captures.get(0).workflowId().equals("wf-77"), "workflow correlation");
        check(notices.size() == 1, "one operations notice");
        check(notices.get(0).reason().equals("APPOINTMENT_REVIEW_REQUIRED"), "notice reason");
        System.out.println("PASS: failed reservation enters review and emits one operational notice");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
