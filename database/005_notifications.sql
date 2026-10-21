-- Run once after 001, 002 and 003. The previous Notification class was never mapped to a table.
BEGIN;

CREATE TABLE IF NOT EXISTS notification (
    id UUID PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meeting (id),
    type VARCHAR(255) NOT NULL CHECK (type IN ('Confirmacion', 'Reprogramacion', 'Recordatorio')),
    channel VARCHAR(255) NOT NULL CHECK (channel IN ('Correo', 'SMS')),
    recipient VARCHAR(254) NOT NULL CHECK (length(trim(recipient)) > 0),
    message VARCHAR(500) NOT NULL CHECK (length(trim(message)) > 0),
    meeting_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255) NOT NULL
        CHECK (status IN ('Pendiente', 'Enviada', 'Entregada', 'Leida', 'Fallida', 'Descartada')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    -- Delivered notifications always have a send time; the others never do.
    CONSTRAINT ck_notification_sent CHECK ((status IN ('Enviada', 'Entregada', 'Leida')) = (sent_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_notification_due ON notification (scheduled_at) WHERE status = 'Pendiente';
CREATE INDEX IF NOT EXISTS idx_notification_meeting ON notification (meeting_id, created_at);
-- One active reminder per channel for each appointment time, also against simultaneous schedulers.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_reminder ON notification (meeting_id, channel, meeting_starts_at)
    WHERE type = 'Recordatorio' AND status <> 'Descartada';

COMMIT;
