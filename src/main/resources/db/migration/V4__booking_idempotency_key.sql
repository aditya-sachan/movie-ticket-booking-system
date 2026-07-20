-- V4: Idempotency key for booking confirmation. A client may send an Idempotency-Key header on
-- POST /bookings; a retry with the same key returns the original booking instead of creating a
-- second one (or erroring because the hold was already consumed). The partial unique index is the
-- DB backstop: at most one booking may ever carry a given key.
alter table booking add column idempotency_key varchar(80);

create unique index uq_booking_idempotency_key
    on booking (idempotency_key) where idempotency_key is not null;
