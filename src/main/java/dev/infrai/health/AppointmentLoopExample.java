package dev.infrai.health;

public final class AppointmentLoopExample {
    private AppointmentLoopExample() {}

    public static void main(String[] args) throws Exception {
        ServiceConfig config = ServiceConfig.load();
        AppointmentSafetyService service = new AppointmentSafetyService(
                new InfraiErrorsClient(config),
                notice -> System.out.printf("ops_notice=%s workflow=%s clinic=%s%n",
                        notice.reason(), notice.workflowId(), notice.clinicCode()));

        AppointmentSafetyService.Request request =
                new AppointmentSafetyService.Request("wf-demo-1042", "CARDIO-02", 1, "development");
        AppointmentSafetyService.Outcome outcome = service.run(request,
                ignored -> { throw new IllegalStateException("reservation step rejected"); });

        System.out.printf("status=%s%npatient_message=%s%n", outcome.status(), outcome.patientMessage());
    }
}
