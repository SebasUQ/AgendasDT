-- Run once after 001, 002 and 003. Review the real schema before applying.
-- Assumes the original unquoted Bill/BillDetail tables resolve to public.bill and public.billdetail.
-- Fails and rolls back if legacy rows lack the new mandatory data; nothing is invented here.
BEGIN;

CREATE TABLE IF NOT EXISTS bill (
    id UUID PRIMARY KEY,
    meetingid UUID,
    date TIMESTAMP WITH TIME ZONE,
    total NUMERIC(15, 2)
);

ALTER TABLE bill ADD COLUMN IF NOT EXISTS status VARCHAR(255);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS payment_method VARCHAR(255);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bill ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bill ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Same assumption as 002: legacy timestamps without zone represent Colombian local time.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bill'
          AND column_name = 'date' AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE bill ALTER COLUMN date TYPE TIMESTAMP WITH TIME ZONE
            USING date AT TIME ZONE 'America/Bogota';
    END IF;
END $$;

-- Money is stored as exact decimals; the old Double mapping could store rounding errors.
ALTER TABLE bill ALTER COLUMN total TYPE NUMERIC(15, 2) USING total::NUMERIC(15, 2);

ALTER TABLE bill ALTER COLUMN meetingid SET NOT NULL;
ALTER TABLE bill ALTER COLUMN date SET NOT NULL;
ALTER TABLE bill ALTER COLUMN total SET NOT NULL;
ALTER TABLE bill ALTER COLUMN status SET NOT NULL;
ALTER TABLE bill ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE bill ADD CONSTRAINT fk_bill_meeting FOREIGN KEY (meetingid) REFERENCES meeting (id);
ALTER TABLE bill ADD CONSTRAINT ck_bill_total CHECK (total >= 0);
ALTER TABLE bill ADD CONSTRAINT ck_bill_status CHECK (status IN ('Pendiente', 'Pagada'));
ALTER TABLE bill ADD CONSTRAINT ck_bill_payment_method
    CHECK (payment_method IN ('Efectivo', 'Tarjeta', 'Transferencia'));
-- Paid bills have both payment method and time; pending bills have neither.
ALTER TABLE bill ADD CONSTRAINT ck_bill_payment
    CHECK ((status = 'Pagada' AND payment_method IS NOT NULL AND paid_at IS NOT NULL)
        OR (status = 'Pendiente' AND payment_method IS NULL AND paid_at IS NULL));
-- At most one bill per meeting, also against simultaneous requests.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_meeting ON bill (meetingid);

-- The legacy receptionist reference is no longer written; the actor is kept in bill_audit.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'bill' AND column_name = 'recepcionistid'
    ) THEN
        ALTER TABLE bill ALTER COLUMN recepcionistid DROP NOT NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS billdetail (
    id UUID PRIMARY KEY,
    billid UUID,
    subtotal NUMERIC(15, 2)
);

ALTER TABLE billdetail ADD COLUMN IF NOT EXISTS line_number INTEGER;
ALTER TABLE billdetail ADD COLUMN IF NOT EXISTS description VARCHAR(200);
ALTER TABLE billdetail ADD COLUMN IF NOT EXISTS quantity INTEGER;
ALTER TABLE billdetail ADD COLUMN IF NOT EXISTS unit_price NUMERIC(10, 2);
ALTER TABLE billdetail ALTER COLUMN subtotal TYPE NUMERIC(15, 2) USING subtotal::NUMERIC(15, 2);

ALTER TABLE billdetail ALTER COLUMN billid SET NOT NULL;
ALTER TABLE billdetail ALTER COLUMN line_number SET NOT NULL;
ALTER TABLE billdetail ALTER COLUMN description SET NOT NULL;
ALTER TABLE billdetail ALTER COLUMN quantity SET NOT NULL;
ALTER TABLE billdetail ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE billdetail ALTER COLUMN subtotal SET NOT NULL;
ALTER TABLE billdetail ADD CONSTRAINT fk_billdetail_bill FOREIGN KEY (billid) REFERENCES bill (id);
ALTER TABLE billdetail ADD CONSTRAINT ck_billdetail_description
    CHECK (length(trim(description)) BETWEEN 1 AND 200);
ALTER TABLE billdetail ADD CONSTRAINT ck_billdetail_quantity CHECK (quantity BETWEEN 1 AND 999);
ALTER TABLE billdetail ADD CONSTRAINT ck_billdetail_unit_price CHECK (unit_price >= 0);
ALTER TABLE billdetail ADD CONSTRAINT ck_billdetail_subtotal CHECK (subtotal = unit_price * quantity);
CREATE INDEX IF NOT EXISTS idx_billdetail_bill ON billdetail (billid);

-- The legacy treatment reference is no longer written; lines describe the charged service.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'billdetail' AND column_name = 'treatmentid'
    ) THEN
        ALTER TABLE billdetail ALTER COLUMN treatmentid DROP NOT NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS bill_audit (
    id UUID PRIMARY KEY,
    bill_id UUID NOT NULL REFERENCES bill (id),
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'PAY')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bill_audit_time ON bill_audit (bill_id, occurred_at);

COMMIT;
