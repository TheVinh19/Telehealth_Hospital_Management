ALTER TABLE appointments ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE appointments ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE appointments ADD COLUMN deleted_by UUID;

-- These are the two dedicated seeded demo accounts, not names/emails supplied by users.
UPDATE appointments SET demo = TRUE
WHERE patient_id IN ('33333333-3333-3333-3333-333333333333',
                     '44444444-4444-4444-4444-444444444444');
