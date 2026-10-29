-- Apply after previous migrations, before deploying this version. No real database is modified automatically.
BEGIN;
-- NULL preserves existing accounts that do not have credentials yet. Never backfill shared passwords.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE meeting_audit ADD COLUMN IF NOT EXISTS previous_status VARCHAR(255);
ALTER TABLE meeting_audit ADD COLUMN IF NOT EXISTS new_status VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_meeting_doctor_start ON meeting (doctorid, date, id);
CREATE INDEX IF NOT EXISTS idx_meeting_doctor_patient_start ON meeting (doctorid, patientid, date, id);
COMMIT;
