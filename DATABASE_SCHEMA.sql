-- PostgreSQL 16+; bản thiết kế, chưa phải migration production.
-- Các schema bên dưới đại diện database của từng service để dễ xem trong một file.
-- Khi triển khai: tách từng khối sang database/user riêng; không có FK xuyên service.
BEGIN;
CREATE SCHEMA identity;
CREATE SCHEMA appointment;
CREATE SCHEMA clinical;
CREATE SCHEMA billing;
CREATE SCHEMA realtime;
CREATE SCHEMA notification;

CREATE TABLE identity.users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 email text NOT NULL CHECK (email = lower(trim(email))),
 password_hash text NOT NULL,
 role text NOT NULL CHECK(role IN ('PATIENT','DOCTOR','ADMIN')),
 display_name text NOT NULL,
 status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DISABLED')),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(email)
);
CREATE TABLE identity.refresh_sessions (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL REFERENCES identity.users(id),
 token_hash text NOT NULL UNIQUE, expires_at timestamptz NOT NULL, revoked_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), CHECK(expires_at > created_at)
);
CREATE INDEX refresh_user ON identity.refresh_sessions(user_id);

CREATE TABLE appointment.specialties (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text NOT NULL UNIQUE, active boolean NOT NULL DEFAULT true
);
CREATE TABLE appointment.doctors (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL UNIQUE,
 specialty_id uuid NOT NULL REFERENCES appointment.specialties(id),
 display_name text NOT NULL, bio text, active boolean NOT NULL DEFAULT true
);
CREATE TABLE appointment.slots (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), doctor_id uuid NOT NULL REFERENCES appointment.doctors(id),
 starts_at timestamptz NOT NULL, enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(doctor_id,starts_at), UNIQUE(id,doctor_id)
);
CREATE TABLE appointment.appointments (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), patient_id uuid NOT NULL,
 doctor_id uuid NOT NULL REFERENCES appointment.doctors(id), slot_id uuid NOT NULL,
 intake_id uuid NOT NULL UNIQUE, status text NOT NULL DEFAULT 'PENDING_PAYMENT'
 CHECK(status IN ('PENDING_PAYMENT','EXPIRED','CONFIRMED','IN_PROGRESS','COMPLETED')),
 amount_vnd bigint NOT NULL CHECK(amount_vnd >= 0), currency text NOT NULL DEFAULT 'VND' CHECK(currency='VND'),
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
 confirmed_at timestamptz, started_at timestamptz, completed_at timestamptz,
 version integer NOT NULL DEFAULT 1 CHECK(version > 0),
 FOREIGN KEY(slot_id,doctor_id) REFERENCES appointment.slots(id,doctor_id),
 UNIQUE(id,slot_id), CHECK(expires_at = created_at + interval '7 minutes'),
 CHECK(status NOT IN ('CONFIRMED','IN_PROGRESS','COMPLETED') OR confirmed_at IS NOT NULL),
 CHECK(status NOT IN ('IN_PROGRESS','COMPLETED') OR started_at IS NOT NULL),
 CHECK(status <> 'COMPLETED' OR completed_at IS NOT NULL)
);
-- Giữ một ca cho một lịch còn hiệu lực; claim vẫn tồn tại sau hoàn tất để ca không bị tái bán.
CREATE UNIQUE INDEX active_slot ON appointment.appointments(slot_id) WHERE status <> 'EXPIRED';
CREATE UNIQUE INDEX doctor_one_consultation ON appointment.appointments(doctor_id) WHERE status='IN_PROGRESS';
CREATE INDEX appointments_patient ON appointment.appointments(patient_id,created_at DESC);
CREATE INDEX pending_expiry ON appointment.appointments(expires_at) WHERE status='PENDING_PAYMENT';
CREATE TABLE appointment.slot_claims (
 slot_id uuid PRIMARY KEY REFERENCES appointment.slots(id), appointment_id uuid NOT NULL UNIQUE,
 kind text NOT NULL CHECK(kind IN ('HOLD','BOOKED')), expires_at timestamptz,
 FOREIGN KEY(appointment_id,slot_id) REFERENCES appointment.appointments(id,slot_id)
 DEFERRABLE INITIALLY DEFERRED,
 CHECK((kind='HOLD' AND expires_at IS NOT NULL) OR (kind='BOOKED' AND expires_at IS NULL))
);
CREATE TABLE appointment.reschedule_history (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), appointment_id uuid NOT NULL REFERENCES appointment.appointments(id),
 old_slot_id uuid NOT NULL REFERENCES appointment.slots(id), new_slot_id uuid NOT NULL REFERENCES appointment.slots(id),
 old_starts_at timestamptz NOT NULL, new_starts_at timestamptz NOT NULL,
 actor_id uuid NOT NULL, changed_at timestamptz NOT NULL DEFAULT now(),
 appointment_version integer NOT NULL, UNIQUE(appointment_id,appointment_version), CHECK(old_slot_id<>new_slot_id)
);
CREATE TABLE appointment.idempotency_requests (
 patient_id uuid NOT NULL, request_key text NOT NULL, request_hash text NOT NULL,
 appointment_id uuid REFERENCES appointment.appointments(id), created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(patient_id,request_key)
);

CREATE TABLE clinical.intakes (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), patient_id uuid NOT NULL,
 state text NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','SUBMITTED')),
 payload_ciphertext bytea NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12),
 key_id text NOT NULL, payload_version integer NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(), submitted_at timestamptz,
 CHECK(state <> 'SUBMITTED' OR submitted_at IS NOT NULL)
);
CREATE INDEX intake_patient ON clinical.intakes(patient_id,created_at DESC);
CREATE TABLE clinical.encounters (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), appointment_id uuid NOT NULL UNIQUE,
 intake_id uuid NOT NULL REFERENCES clinical.intakes(id), patient_id uuid NOT NULL, doctor_id uuid NOT NULL,
 state text NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','PUBLISHED')),
 created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz,
 CHECK(state <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE TABLE clinical.report_files (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), encounter_id uuid NOT NULL REFERENCES clinical.encounters(id),
 storage_key text NOT NULL UNIQUE, original_name text NOT NULL, mime_type text NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes>0), sha256 text NOT NULL CHECK(length(sha256)=64),
 state text NOT NULL DEFAULT 'UPLOADING' CHECK(state IN ('UPLOADING','READY','FAILED')),
 key_id text NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12),
 uploaded_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX report_encounter ON clinical.report_files(encounter_id);
CREATE TABLE clinical.audit_logs (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, actor_id uuid,
 action text NOT NULL, resource_type text NOT NULL, resource_id uuid NOT NULL,
 result text NOT NULL CHECK(result IN ('ALLOWED','DENIED','FAILED')),
 correlation_id uuid, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_resource ON clinical.audit_logs(resource_id,created_at DESC);

CREATE TABLE billing.payment_orders (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), appointment_id uuid NOT NULL UNIQUE, patient_id uuid NOT NULL,
 amount_vnd bigint NOT NULL CHECK(amount_vnd>=0), currency text NOT NULL DEFAULT 'VND' CHECK(currency='VND'),
 provider text NOT NULL, payment_code text NOT NULL UNIQUE,
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PAID','EXPIRED','FAILED')),
 provider_transaction_id text, created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL, paid_at timestamptz,
 CHECK(status <> 'PAID' OR paid_at IS NOT NULL), UNIQUE(provider,provider_transaction_id)
);
CREATE TABLE billing.payment_callbacks (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), order_id uuid NOT NULL REFERENCES billing.payment_orders(id),
 provider text NOT NULL, provider_event_id text NOT NULL, payload_hash text NOT NULL,
 result text NOT NULL CHECK(result IN ('ACCEPTED','REJECTED','IGNORED')),
 received_at timestamptz NOT NULL DEFAULT now(), UNIQUE(provider,provider_event_id)
);

CREATE TABLE realtime.rooms (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), appointment_id uuid NOT NULL UNIQUE,
 patient_id uuid NOT NULL, doctor_user_id uuid NOT NULL, doctor_id uuid NOT NULL,
 scheduled_at timestamptz NOT NULL, admission_version integer NOT NULL DEFAULT 1 CHECK(admission_version>0),
 state text NOT NULL DEFAULT 'WAITING' CHECK(state IN ('WAITING','OPEN','CLOSED')),
 created_at timestamptz NOT NULL DEFAULT now(), closed_at timestamptz
);
CREATE TABLE realtime.messages (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, room_id uuid NOT NULL REFERENCES realtime.rooms(id),
 sender_id uuid NOT NULL, client_message_id uuid NOT NULL,
 body_ciphertext bytea NOT NULL, key_id text NOT NULL, nonce bytea NOT NULL CHECK(octet_length(nonce)=12),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(room_id,sender_id,client_message_id)
);
CREATE INDEX messages_cursor ON realtime.messages(room_id,id);

CREATE TABLE notification.email_jobs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_id uuid NOT NULL, recipient text NOT NULL,
 template text NOT NULL CHECK(template IN ('APPOINTMENT_CONFIRMED','APPOINTMENT_RESCHEDULED')),
 payload jsonb NOT NULL, status text NOT NULL DEFAULT 'PENDING'
 CHECK(status IN ('PENDING','PROCESSING','SENT','FAILED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), next_attempt_at timestamptz NOT NULL DEFAULT now(),
 lease_until timestamptz, sent_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(event_id,recipient,template), CHECK(status <> 'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX email_pending ON notification.email_jobs(next_attempt_at) WHERE status IN ('PENDING','FAILED');
CREATE TABLE notification.delivery_attempts (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, job_id uuid NOT NULL REFERENCES notification.email_jobs(id),
 attempt_number integer NOT NULL CHECK(attempt_number>0), success boolean NOT NULL,
 provider_message_id text, error_code text, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(job_id,attempt_number)
);

-- Bảng kỹ thuật cục bộ: tạo riêng cho mỗi service, tuyệt đối không dùng chung hàng giữa service.
DO $$
DECLARE service_schema text;
BEGIN
 FOREACH service_schema IN ARRAY ARRAY['identity','appointment','clinical','billing','realtime','notification'] LOOP
 EXECUTE format('CREATE TABLE %I.outbox_events (
 event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(), event_type text NOT NULL,
 aggregate_id uuid NOT NULL, aggregate_version integer NOT NULL, schema_version integer NOT NULL DEFAULT 1,
 payload jsonb NOT NULL, correlation_id uuid, occurred_at timestamptz NOT NULL DEFAULT now(),
 published_at timestamptz, attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz)',service_schema);
 EXECUTE format('CREATE INDEX ON %I.outbox_events(next_attempt_at) WHERE published_at IS NULL',service_schema);
 EXECUTE format('CREATE TABLE %I.inbox_events (
 consumer_name text NOT NULL, event_id uuid NOT NULL, processed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(consumer_name,event_id))',service_schema);
 END LOOP;
END $$;
COMMIT;
