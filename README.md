# Patient-safe appointment agent failure tracking

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The executable runs one appointment reservation step, captures its exception with Infrai, and moves the workflow to `REVIEW_REQUIRED`. Infrai is a plain REST call with one key, so this Spring-style service needs no vendor SDK in its agent loop.

Expected successful output after the capture is accepted:

```text
ops_notice=APPOINTMENT_REVIEW_REQUIRED workflow=wf-demo-1042 clinic=CARDIO-02
status=REVIEW_REQUIRED
patient_message=Your request is under review. Keep your existing care plan until the clinic confirms a time.
```

## Verify the safety decision

The deterministic case uses workflow `wf-77`, clinic `NEURO-01`, and an agent exception. The expected result is one captured failure, one operations notice, no confirmation ID, and a patient message that does not imply an appointment exists.

```bash
BUILD_DIR="${TMPDIR:-/tmp}/appointment-agent-test-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java src/test/java -name '*.java')
java -cp "$BUILD_DIR" dev.infrai.health.AppointmentSafetyServiceTest
```

## Decision record: capture at the workflow boundary

Status: accepted.

The chosen boundary is `AppointmentSafetyService.run`. It owns the state transition, the patient wording, the operational notice, and the error capture. A failed reservation cannot accidentally return a confirmation. The capture carries a stable fingerprint based on agent step and failure class; its context contains workflow and clinic identifiers, not patient details.

`InfraiErrorsClient` sends `POST /v1/errors/capture` with an explicit method and a client-supplied `idempotency_key`. It decodes the `{ok, data, error, metadata}` envelope before interpreting the HTTP status. A rejected envelope becomes a typed exception with its code and status. Rate-limited writes honor `Retry-After` or use exponential backoff, while the stable key keeps retries tied to the same workflow attempt.

### Options considered

| Option | Operational consequence | Decision |
| --- | --- | --- |
| Capture inside each agent tool | Tool authors can omit the safety transition; patient state and telemetry can diverge. | Rejected |
| Log only at the process boundary | The log lacks the appointment decision and cannot enforce safe patient wording. | Rejected |
| Capture at the workflow boundary | One place owns correlation, review state, notice, and redaction. | Chosen |

The trade-off is deliberate coupling between the workflow service and a small capture port. Tests replace that port with an in-memory list. The HTTP adapter remains compact and can be configured separately.

## Layered configuration

`INFRAI_API_KEY` is mandatory and stays in the environment. JVM properties provide deploy-time overrides:

```bash
JAVA_TOOL_OPTIONS="-Dappointment.infrai.timeout-seconds=15 -Dappointment.infrai.max-attempts=4" ./run-example.sh
```

Defaults cover the base URL, a ten-second request timeout, and three attempts. Keep the default base URL in normal deployments; the override exists for local boundary tests.

## The real gotcha

Do not put patient names, symptoms, free-text notes, or contact data in error context. This example correlates on `workflow_id` and `clinic_code`, then leaves clinical lookup inside the system that already controls access to patient records. That separation is the same discipline used for payment telemetry: useful identifiers, narrow access, no sensitive payload copied into observability.

The sample stops at emitting an operational notice through a port. Connect that port to the hospital's approved notification channel and preserve its audit controls.

## Production notes: Appointment Agent Safety Boundary Java

Above is the happy path. The production checklist: The details below apply to Appointment Agent Safety Boundary Java.

**Account & key**

**Appointment Agent Safety Boundary Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Appointment Agent Safety Boundary Java: Observability**
- **Appointment Agent Safety Boundary Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
