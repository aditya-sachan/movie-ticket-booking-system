-- V1: Core schema for the movie ticket booking system.
-- Conventions: snake_case, TIMESTAMPTZ for time, NUMERIC(12,2) for money,
-- NUMERIC(6,4) for multipliers. Enums are VARCHAR + CHECK for portability and
-- map to @Enumerated(STRING) on the JPA side. Surrogate identity keys everywhere.

-- ---------------------------------------------------------------------------
-- Users (DB-backed, used by Spring Security HTTP Basic in a later slice)
-- ---------------------------------------------------------------------------
create table app_user (
    id            bigint generated always as identity primary key,
    username      varchar(100) not null unique,
    password_hash varchar(200) not null,
    role          varchar(20)  not null check (role in ('ADMIN', 'CUSTOMER')),
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now()
);

-- ---------------------------------------------------------------------------
-- Geography / venue layout: city -> theater -> screen -> seat
-- ---------------------------------------------------------------------------
create table city (
    id         bigint generated always as identity primary key,
    name       varchar(120) not null,
    state      varchar(120),
    created_at timestamptz  not null default now(),
    unique (name, state)
);

create table theater (
    id         bigint generated always as identity primary key,
    city_id    bigint       not null references city (id),
    name       varchar(150) not null,
    address    varchar(300),
    created_at timestamptz  not null default now()
);
create index idx_theater_city on theater (city_id);

create table screen (
    id         bigint generated always as identity primary key,
    theater_id bigint       not null references theater (id),
    name       varchar(100) not null,
    created_at timestamptz  not null default now(),
    unique (theater_id, name)
);

-- Seat classification with its price multiplier (config, admin-managed later).
create table seat_class (
    id         bigint generated always as identity primary key,
    name       varchar(40)  not null unique,
    multiplier numeric(6, 4) not null check (multiplier > 0)
);

-- Physical seat: defined once per screen, reused by every show on that screen.
create table seat (
    id            bigint generated always as identity primary key,
    screen_id     bigint      not null references screen (id),
    seat_class_id bigint      not null references seat_class (id),
    row_label     varchar(4)  not null,
    seat_number   int         not null check (seat_number > 0),
    created_at    timestamptz not null default now(),
    unique (screen_id, row_label, seat_number)
);
create index idx_seat_screen on seat (screen_id);

-- ---------------------------------------------------------------------------
-- Catalog: movies, pricing tiers, shows
-- ---------------------------------------------------------------------------
create table movie (
    id               bigint generated always as identity primary key,
    title            varchar(200) not null,
    language         varchar(60)  not null,
    duration_minutes int          not null check (duration_minutes > 0),
    certification    varchar(20),
    created_at       timestamptz  not null default now()
);

-- Pricing tier with its multiplier: regular / premium / weekend (config).
create table pricing_tier (
    id         bigint generated always as identity primary key,
    name       varchar(40)  not null unique,
    multiplier numeric(6, 4) not null check (multiplier > 0)
);

-- Configurable refund policy: a set of rules keyed by hours-before-show.
create table refund_policy (
    id         bigint generated always as identity primary key,
    name       varchar(100) not null unique,
    active     boolean      not null default true,
    created_at timestamptz  not null default now()
);

create table refund_rule (
    id                    bigint generated always as identity primary key,
    refund_policy_id      bigint       not null references refund_policy (id),
    min_hours_before_show int          not null check (min_hours_before_show >= 0),
    refund_percentage     numeric(5, 2) not null check (refund_percentage >= 0 and refund_percentage <= 100),
    unique (refund_policy_id, min_hours_before_show)
);

-- A scheduled show of a movie on a screen. Named movie_show to avoid the SQL
-- SHOW keyword; the JPA entity is Show.
create table movie_show (
    id               bigint generated always as identity primary key,
    movie_id         bigint       not null references movie (id),
    screen_id        bigint       not null references screen (id),
    pricing_tier_id  bigint       not null references pricing_tier (id),
    refund_policy_id bigint       references refund_policy (id),
    starts_at        timestamptz  not null,
    ends_at          timestamptz  not null,
    base_price       numeric(12, 2) not null check (base_price >= 0),
    created_at       timestamptz  not null default now(),
    check (ends_at > starts_at)
);
create index idx_movie_show_movie on movie_show (movie_id);
create index idx_movie_show_starts_at on movie_show (starts_at);

-- ---------------------------------------------------------------------------
-- Concurrency-critical: one row per (show, seat). SELECT ... FOR UPDATE here.
-- ---------------------------------------------------------------------------
create table show_seat (
    id              bigint generated always as identity primary key,
    show_id         bigint      not null references movie_show (id),
    seat_id         bigint      not null references seat (id),
    status          varchar(20) not null default 'AVAILABLE'
        check (status in ('AVAILABLE', 'HELD', 'BOOKED')),
    hold_token      uuid,
    hold_expires_at timestamptz,
    created_at      timestamptz not null default now(),
    unique (show_id, seat_id)
);
create index idx_show_seat_show on show_seat (show_id);
-- Supports the @Scheduled expiry sweep of stale holds.
create index idx_show_seat_hold_expiry on show_seat (status, hold_expires_at);

-- ---------------------------------------------------------------------------
-- Discounts
-- ---------------------------------------------------------------------------
create table discount_code (
    id              bigint generated always as identity primary key,
    code            varchar(40)  not null unique,
    discount_type   varchar(20)  not null check (discount_type in ('PERCENTAGE', 'FLAT')),
    value           numeric(12, 2) not null check (value >= 0),
    min_order_value numeric(12, 2) not null default 0 check (min_order_value >= 0),
    max_redemptions int,                       -- null = unlimited
    times_redeemed  int          not null default 0 check (times_redeemed >= 0),
    valid_from      timestamptz  not null,
    valid_until     timestamptz  not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    check (valid_until > valid_from)
);

-- ---------------------------------------------------------------------------
-- Bookings, seats booked, payments
-- ---------------------------------------------------------------------------
create table booking (
    id               bigint generated always as identity primary key,
    user_id          bigint       not null references app_user (id),
    show_id          bigint       not null references movie_show (id),
    status           varchar(20)  not null check (status in ('CONFIRMED', 'CANCELLED')),
    discount_code_id bigint       references discount_code (id),
    subtotal_amount  numeric(12, 2) not null check (subtotal_amount >= 0),
    discount_amount  numeric(12, 2) not null default 0 check (discount_amount >= 0),
    tax_amount       numeric(12, 2) not null default 0 check (tax_amount >= 0),
    total_amount     numeric(12, 2) not null check (total_amount >= 0),
    created_at       timestamptz  not null default now(),
    cancelled_at     timestamptz
);
create index idx_booking_user on booking (user_id);
create index idx_booking_show on booking (show_id);

create table booking_seat (
    id           bigint generated always as identity primary key,
    booking_id   bigint  not null references booking (id),
    show_seat_id bigint  not null references show_seat (id),
    active       boolean not null default true
);
create index idx_booking_seat_booking on booking_seat (booking_id);
-- BACKSTOP: a show_seat can belong to at most one ACTIVE booking. A partial
-- unique index (rather than a plain one) is used so that cancelling a booking
-- and re-booking the freed seat still works, while a duplicate ACTIVE booking
-- of the same seat fails at the DB level even if application logic is wrong.
create unique index uq_booking_seat_active on booking_seat (show_seat_id) where active;

create table payment (
    id              bigint generated always as identity primary key,
    booking_id      bigint       not null references booking (id),
    amount          numeric(12, 2) not null check (amount >= 0),
    status          varchar(20)  not null check (status in ('SUCCESS', 'REFUNDED', 'FAILED')),
    provider_ref    varchar(100),
    refunded_amount numeric(12, 2) not null default 0 check (refunded_amount >= 0),
    created_at      timestamptz  not null default now()
);
create index idx_payment_booking on payment (booking_id);

-- ---------------------------------------------------------------------------
-- Transactional outbox: notification rows written inside the booking txn,
-- drained by a @Scheduled poller (logging stub) so delivery never blocks booking.
-- ---------------------------------------------------------------------------
create table notification_outbox (
    id         bigint generated always as identity primary key,
    booking_id bigint       references booking (id),
    type       varchar(40)  not null
        check (type in ('BOOKING_CONFIRMATION', 'BOOKING_CANCELLATION', 'REMINDER')),
    recipient  varchar(200) not null,
    payload    text         not null,
    status     varchar(20)  not null default 'PENDING'
        check (status in ('PENDING', 'SENT', 'FAILED')),
    attempts   int          not null default 0 check (attempts >= 0),
    created_at timestamptz  not null default now(),
    sent_at    timestamptz
);
create index idx_outbox_status on notification_outbox (status, created_at);
