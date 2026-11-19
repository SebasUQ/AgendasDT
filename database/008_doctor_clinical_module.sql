-- Run once after 001-007, before deploying the doctor module. Existing legacy clinical tables are preserved.
BEGIN;

CREATE TABLE doctor_profile (
    id BIGINT PRIMARY KEY REFERENCES "User"(id),
    professional_registration VARCHAR(100) NOT NULL CHECK (length(trim(professional_registration)) > 0),
    specialty VARCHAR(150), updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE clinical_record (
    id UUID PRIMARY KEY, meeting_id UUID NOT NULL UNIQUE REFERENCES meeting(id),
    patient_id BIGINT NOT NULL REFERENCES "User"(id), doctor_id BIGINT NOT NULL REFERENCES "User"(id),
    doctor_name VARCHAR(201) NOT NULL, patient_name VARCHAR(201) NOT NULL,
    professional_registration VARCHAR(100) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000) NOT NULL CHECK(length(trim(reason)) > 0),
    findings VARCHAR(4000) NOT NULL CHECK(length(trim(findings)) > 0),
    diagnosis VARCHAR(2000) NOT NULL CHECK(length(trim(diagnosis)) > 0),
    performed_procedure VARCHAR(4000) NOT NULL CHECK(length(trim(performed_procedure)) > 0),
    plan VARCHAR(4000) NOT NULL CHECK(length(trim(plan)) > 0),
    medical_background VARCHAR(4000), allergies VARCHAR(2000), chronic_conditions VARCHAR(2000)
);
CREATE INDEX idx_clinical_record_patient_time ON clinical_record(patient_id, recorded_at DESC, id DESC);
CREATE TABLE clinical_tooth_finding (
    record_id UUID NOT NULL REFERENCES clinical_record(id), position INTEGER NOT NULL CHECK(position >= 0),
    tooth_fdi INTEGER NOT NULL CHECK (
        (tooth_fdi / 10 BETWEEN 1 AND 4 AND tooth_fdi % 10 BETWEEN 1 AND 8) OR
        (tooth_fdi / 10 BETWEEN 5 AND 8 AND tooth_fdi % 10 BETWEEN 1 AND 5)),
    surface VARCHAR(30) NOT NULL CHECK(surface IN ('Vestibular','Lingual','Mesial','Distal','Oclusal')),
    type VARCHAR(30) NOT NULL CHECK(type IN ('Patologia','TratamientoRealizado')),
    description VARCHAR(500) NOT NULL CHECK(length(trim(description)) > 0),
    PRIMARY KEY(record_id, position), UNIQUE(record_id, tooth_fdi, surface)
);
CREATE TABLE clinical_addendum (
    id UUID PRIMARY KEY, record_id UUID NOT NULL REFERENCES clinical_record(id),
    doctor_id BIGINT NOT NULL REFERENCES "User"(id), doctor_name VARCHAR(201) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL, reason VARCHAR(1000) NOT NULL CHECK(length(trim(reason)) > 0),
    content VARCHAR(8000) NOT NULL CHECK(length(trim(content)) > 0)
);
CREATE INDEX idx_clinical_addendum_record ON clinical_addendum(record_id, recorded_at, id);
CREATE TABLE clinical_prescription (
    id UUID PRIMARY KEY, record_id UUID NOT NULL REFERENCES clinical_record(id), issued_at TIMESTAMPTZ NOT NULL,
    doctor_name VARCHAR(201) NOT NULL, professional_registration VARCHAR(100) NOT NULL,
    supersedes_id UUID UNIQUE, correction_reason VARCHAR(1000), UNIQUE(id,record_id),
    FOREIGN KEY(supersedes_id,record_id) REFERENCES clinical_prescription(id,record_id),
    CHECK(supersedes_id IS NULL OR (supersedes_id <> id AND length(trim(correction_reason)) > 0 AND correction_reason IS NOT NULL))
);
CREATE INDEX idx_clinical_prescription_record ON clinical_prescription(record_id,issued_at DESC,id DESC);
CREATE TABLE clinical_prescription_item (
    prescription_id UUID NOT NULL REFERENCES clinical_prescription(id), position INTEGER NOT NULL CHECK(position >= 0),
    medicine VARCHAR(200) NOT NULL CHECK(length(trim(medicine)) > 0),
    dose VARCHAR(200) NOT NULL CHECK(length(trim(dose)) > 0),
    frequency VARCHAR(200) NOT NULL CHECK(length(trim(frequency)) > 0),
    duration VARCHAR(200) NOT NULL CHECK(length(trim(duration)) > 0), instructions VARCHAR(1000),
    PRIMARY KEY(prescription_id,position)
);
CREATE TABLE clinical_audit (
    id UUID PRIMARY KEY, doctor_id BIGINT NOT NULL REFERENCES "User"(id), patient_id BIGINT REFERENCES "User"(id),
    record_id UUID REFERENCES clinical_record(id), action VARCHAR(50) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_clinical_audit_patient ON clinical_audit(patient_id,occurred_at);
CREATE TABLE clinical_procedure (
    id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
    name VARCHAR(150) NOT NULL CHECK(length(trim(name)) > 0), price NUMERIC(10,2) NOT NULL CHECK(price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE, valid_from TIMESTAMPTZ NOT NULL, valid_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0, CHECK(valid_until IS NULL OR valid_until > valid_from)
);
CREATE TABLE clinical_procedure_supply (
    procedure_id UUID NOT NULL REFERENCES clinical_procedure(id), position INTEGER NOT NULL CHECK(position >= 0),
    supply_code VARCHAR(80) NOT NULL, name VARCHAR(150) NOT NULL, unit VARCHAR(40) NOT NULL,
    units INTEGER NOT NULL CHECK(units BETWEEN 1 AND 1000000), PRIMARY KEY(procedure_id,position),
    UNIQUE(procedure_id,supply_code,unit)
);
ALTER TABLE bill ADD COLUMN clinical_order BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE billdetail ADD COLUMN procedure_id UUID REFERENCES clinical_procedure(id);
CREATE TABLE clinical_bill_supply (
    id UUID PRIMARY KEY, bill_id UUID NOT NULL REFERENCES bill(id), supply_code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL, unit VARCHAR(40) NOT NULL, units BIGINT NOT NULL CHECK(units > 0),
    UNIQUE(bill_id,supply_code,unit)
);

-- Finalized clinical information is append-only, including child rows and audit logs.
CREATE FUNCTION reject_clinical_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Los registros clínicos son inmutables. Registre una adenda o una nueva prescripción.'
        USING ERRCODE = '23514';
END;
$$;
DO $$
DECLARE clinical_table TEXT;
BEGIN
    FOREACH clinical_table IN ARRAY ARRAY['clinical_record','clinical_tooth_finding','clinical_addendum',
        'clinical_prescription','clinical_prescription_item','clinical_audit','clinical_bill_supply']
    LOOP
        EXECUTE format('CREATE TRIGGER clinical_append_only BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION reject_clinical_mutation()', clinical_table);
    END LOOP;
END $$;

-- Supabase REST access remains blocked; the backend's table-owner connection is unchanged.
ALTER TABLE doctor_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_tooth_finding ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_addendum ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_prescription ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_prescription_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_procedure ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_procedure_supply ENABLE ROW LEVEL SECURITY;
ALTER TABLE clinical_bill_supply ENABLE ROW LEVEL SECURITY;
COMMIT;

-- Administrative tariff setup uses actual institutional values, not demonstration prices.
-- INSERT INTO clinical_procedure(id,code,name,price,valid_from)
-- VALUES ('<uuid>','<codigo>','<nombre>',<precio>,CURRENT_TIMESTAMP);
-- INSERT INTO clinical_procedure_supply(procedure_id,position,supply_code,name,unit,units)
-- VALUES ('<uuid>',0,'<codigo-insumo>','<nombre>','<unidad>',<cantidad>);
