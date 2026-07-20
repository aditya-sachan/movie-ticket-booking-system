-- V5: Track whether a showtime reminder has been enqueued for a booking, so the reminder
-- scheduler emits exactly one REMINDER notification per booking (idempotent across sweeps).
alter table booking add column reminder_enqueued boolean not null default false;

-- Supports the scheduler's "confirmed, not yet reminded, show starting soon" query.
create index idx_booking_reminder on booking (reminder_enqueued, status);
