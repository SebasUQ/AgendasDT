-- Run once after 001 to 005. Supabase publishes the public schema through its REST API:
-- row level security without policies blocks that access. The backend connects as the
-- table owner, which row level security does not restrict.
BEGIN;

ALTER TABLE "User" ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE dental_chair ENABLE ROW LEVEL SECURITY;
ALTER TABLE meeting ENABLE ROW LEVEL SECURITY;
ALTER TABLE meeting_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill ENABLE ROW LEVEL SECURITY;
ALTER TABLE billdetail ENABLE ROW LEVEL SECURITY;
ALTER TABLE bill_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification ENABLE ROW LEVEL SECURITY;

COMMIT;
