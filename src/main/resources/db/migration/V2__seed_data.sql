-- V2: Seed reference + demo data. FKs are resolved via natural-key subqueries
-- so this stays correct regardless of generated identity values.
-- NOTE: app_user rows are intentionally seeded in the security slice, where the
-- PasswordEncoder is defined and hashes can be verified against a real login.

-- --- Config: seat classes and pricing tiers ---------------------------------
insert into seat_class (name, multiplier) values
    ('REGULAR', 1.0000),
    ('PREMIUM', 1.5000);

insert into pricing_tier (name, multiplier) values
    ('REGULAR', 1.0000),
    ('PREMIUM', 1.2000),
    ('WEEKEND', 1.3000);

-- --- Config: a standard refund policy with time-based rules -----------------
insert into refund_policy (name, active) values ('Standard', true);

insert into refund_rule (refund_policy_id, min_hours_before_show, refund_percentage)
select rp.id, r.hours, r.pct
from refund_policy rp
cross join (values (48, 100.00), (24, 50.00), (2, 25.00), (0, 0.00)) as r(hours, pct)
where rp.name = 'Standard';

-- --- Geography / venue layout ----------------------------------------------
insert into city (name, state) values
    ('Bengaluru', 'Karnataka'),
    ('Mumbai', 'Maharashtra');

insert into theater (city_id, name, address)
select id, 'PVR Forum', '21 Hosur Rd, Koramangala' from city where name = 'Bengaluru';
insert into theater (city_id, name, address)
select id, 'INOX Nexus', 'Western Express Hwy, Goregaon' from city where name = 'Mumbai';

insert into screen (theater_id, name)
select id, 'Screen 1' from theater where name = 'PVR Forum';
insert into screen (theater_id, name)
select id, 'Screen 1' from theater where name = 'INOX Nexus';

-- Seats for PVR Forum / Screen 1: rows A-E, seats 1-10 = 50 seats.
-- Rows A-B are PREMIUM, rows C-E are REGULAR.
insert into seat (screen_id, seat_class_id, row_label, seat_number)
select sc.id,
       (select id from seat_class where name = case when g.row_label in ('A', 'B') then 'PREMIUM' else 'REGULAR' end),
       g.row_label,
       g.seat_number
from screen sc
join theater t on t.id = sc.theater_id
cross join (
    select r.row_label, n.seat_number
    from (values ('A'), ('B'), ('C'), ('D'), ('E')) as r(row_label)
    cross join generate_series(1, 10) as n(seat_number)
) g
where t.name = 'PVR Forum' and sc.name = 'Screen 1';

-- --- Catalog: movies --------------------------------------------------------
insert into movie (title, language, duration_minutes, certification) values
    ('Inception', 'English', 148, 'UA'),
    ('Dangal', 'Hindi', 161, 'U');

-- --- Shows (in the future so refund windows are meaningful) -----------------
-- Show 1: Inception, WEEKEND tier, base 250, Standard refund policy, +3 days.
insert into movie_show (movie_id, screen_id, pricing_tier_id, refund_policy_id, starts_at, ends_at, base_price)
select (select id from movie where title = 'Inception'),
       sc.id,
       (select id from pricing_tier where name = 'WEEKEND'),
       (select id from refund_policy where name = 'Standard'),
       now() + interval '3 days',
       now() + interval '3 days' + interval '148 minutes',
       250.00
from screen sc
join theater t on t.id = sc.theater_id
where t.name = 'PVR Forum' and sc.name = 'Screen 1';

-- Show 2: Dangal, REGULAR tier, base 200, Standard refund policy, +1 day.
insert into movie_show (movie_id, screen_id, pricing_tier_id, refund_policy_id, starts_at, ends_at, base_price)
select (select id from movie where title = 'Dangal'),
       sc.id,
       (select id from pricing_tier where name = 'REGULAR'),
       (select id from refund_policy where name = 'Standard'),
       now() + interval '1 day',
       now() + interval '1 day' + interval '161 minutes',
       200.00
from screen sc
join theater t on t.id = sc.theater_id
where t.name = 'PVR Forum' and sc.name = 'Screen 1';

-- --- show_seat: one AVAILABLE row per (show, seat) on the show's screen -----
insert into show_seat (show_id, seat_id, status)
select ms.id, s.id, 'AVAILABLE'
from movie_show ms
join seat s on s.screen_id = ms.screen_id;

-- --- Discount codes ---------------------------------------------------------
insert into discount_code (code, discount_type, value, min_order_value, max_redemptions, times_redeemed, valid_from, valid_until, active) values
    ('WELCOME10', 'PERCENTAGE', 10.00, 200.00, 1000, 0, now() - interval '1 day', now() + interval '365 days', true),
    ('FLAT50',    'FLAT',       50.00, 300.00,  100, 0, now() - interval '1 day', now() + interval '90 days',  true);
