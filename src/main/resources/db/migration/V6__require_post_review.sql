-- Existing reviewed/public posts keep their status. New inserts require review.
ALTER TABLE bms_post ALTER COLUMN moderation_status SET DEFAULT 'PENDING';
