-- Reset user with ID 1 (HARSHIT TIWARI) to allow complaints
-- This user has strike_count = 2 which should allow complaints

UPDATE users 
SET complaint_banned_until = NULL, 
    ban_reason = NULL, 
    is_banned = false, 
    enabled = true
WHERE id = 1;

-- Verify the update
SELECT id, name, email, strike_count, is_banned, ban_reason, complaint_banned_until, enabled 
FROM users 
WHERE id = 1;
