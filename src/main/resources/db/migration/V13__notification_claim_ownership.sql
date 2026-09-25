-- Fence stale workers after a lease expires or an administrator retries delivery.
ALTER TABLE payment_notifications ADD COLUMN claim_token VARCHAR(36);
