# Development log

A written summary of how this build progressed, reconstructed from the commit history and
[`../Claude.md`](../Claude.md). **This is not a verbatim prompt transcript** — it is a description,
in order, of what each slice did and why. The commits and code bear it out.

The project was developed in reviewable slices: one concern at a time, compiled (and tested, once
tests existed) before moving on, with a conventional commit at the end of each and assumptions
recorded as they surfaced. Standing technical direction lived in `Claude.md` (stack, conventions,
the no-double-booking design, build order) and was carried across sessions so prior decisions did
not have to be re-derived.

The brief was deliberately open-ended, so the guiding choice was to treat it as depth on the core
booking flows rather than breadth into new features — anything extra was proposed rather than built.

---

## 1. Skeleton and build setup

The first slice established the project shell: Spring Boot 3.3.x, Java 21, groupId `com.aditya`,
artifact `movie-ticket-booking-system`, and the dependency set (web, data-jpa, validation, security,
postgresql, flyway, and Testcontainers for tests), deliberately without Lombok. It added the
`MovieTicketBookingApplication` entry point, a `.gitignore`, and a committed Maven wrapper so the
build was self-contained. Nothing beyond the shell was scaffolded, so the structure could be
reviewed before real code landed.

## 2. Pinning the Java 21 toolchain

Because the machine's default JDK was newer than 21, a build-level guard was added so a later
session could not silently compile on the wrong JDK. `maven-enforcer-plugin` requires Java `[21,22)`
in the validate phase and fails the build loudly otherwise. Enforcer was chosen over Maven
toolchains to keep a clean checkout building without a machine-specific `toolchains.xml`. It went in
as its own commit.

## 3. Schema, entities, and seed data

This slice modeled the full domain up front, even though behaviour came later. It used the
physical-seat model: a `seat` belongs to a `screen` (layout defined once, reusable), a `show`
references a screen, and `show_seat` is one row per (show, seat) created with the show. Flyway V1
created the schema (users, cities/theaters/screens/seats, movies, shows, show_seats, discount codes,
refund policies, bookings, payments, notification outbox), V2 seeded demo data, and the JPA entities
(field access, explicit constructors/getters) were mapped against it. Enums were stored as
`VARCHAR`+`CHECK`, money as `NUMERIC`, and timestamps as `TIMESTAMPTZ`. With `ddl-auto: validate`,
booting the app proved both that the migrations applied cleanly against real Postgres and that every
entity validated against the schema.

One design decision was recorded here: the brief specified a plain unique index on
`booking_seat(show_seat_id)`, but that would break cancel→re-book (the historical row would occupy
the index forever). The build instead used a partial unique index `WHERE active`, preserving the
"at most one active booking per seat" backstop while allowing a freed seat to be re-booked. The
reasoning was written into `Claude.md` for the README and video.

## 4. Hold, confirm, and cancel with row locking

This was the core of the project. A hold (`POST /shows/{id}/holds`) locks the requested `show_seat`
rows with `SELECT ... FOR UPDATE` ordered by seat id — consistent ordering avoids deadlock — verifies
they are available, and sets them HELD with a ten-minute expiry and a shared hold token. Confirm
(`POST /bookings`) re-checks the token and non-expiry under lock, transitions the seats to BOOKED,
and creates the Booking, BookingSeat rows, a stub Payment, and a notification outbox row in one
transaction. Cancel (`POST /bookings/{id}/cancel`) returns the seats to AVAILABLE, deactivates the
booking_seat rows, and computes a refund from the show's policy. A `@Scheduled` sweep releases
expired holds every 60 seconds, and an outbox poller delivers notifications via a logging stub so
delivery never blocks booking. Pricing was deferred behind a `PricingService` seam (base price
only), and the whole flow was exercised manually against Postgres before the slice was committed.

## 5. Pricing, discount codes, and refund policies

This slice filled the pricing seam left by the previous step. The formula
`final = (base_price × seat_class_multiplier × tier_multiplier) − discount + taxes` went into a pure
`PriceCalculator` so it could be unit-tested table-driven, with per-seat class multipliers and the
WEEKEND tier applied by the show's day. Discount codes (percentage or flat, minimum order value, max
redemptions, validity window) were validated at hold time and re-validated at confirm under a row
lock, with an atomic `times_redeemed` increment to prevent over-redemption. Tax became a configurable
flat rate on the discounted subtotal, refunds were wired into cancel through a pure `RefundCalculator`
reading the policy's tiers, and the full breakdown (subtotal/discount/tax/total) was persisted on the
booking.

## 6. Security and error handling

Authentication moved to real HTTP Basic backed by `app_user` rows, with bcrypt passwords seeded
through the `PasswordEncoder` rather than hardcoded hashes. The `userId`-in-body seam from the
earlier slices was removed in favour of the authenticated principal, `@PreAuthorize` was applied to
every endpoint (customers hold/book/cancel; browsing requires authentication), and the ownership
rule — a customer may only act on their own booking — was enforced in the service. A single
`@RestControllerAdvice` returned RFC 7807 `ProblemDetail` for the domain exceptions plus validation,
malformed JSON, access-denied, and an unexpected-error fallback.

## 7. Tests

The most heavily weighted slice. The centrepiece was a 50-thread concurrency test on a single seat,
backed by a real PostgreSQL 16 in Testcontainers because H2 does not reproduce `SELECT ... FOR
UPDATE` locking: with the row lock, exactly one booking wins and the other 49 get `SeatUnavailable`;
with the lock removed, multiple threads pass the availability check (the race) while the partial
unique index still prevents any double-allocation. Alongside it were a hold-expiry release test,
table-driven unit tests for the pricing and refund calculators, RBAC tests (a customer cannot reach
another customer's booking or an admin endpoint), and a happy-path browse→hold→confirm→cancel test.
This slice also resolved a Testcontainers-versus-modern-Docker issue where `docker-java` negotiated
too low an API version, by bumping Testcontainers and pinning the Docker Engine API version.

## 8. Browse/search, booking history, and admin CRUD

A reduced-scope slice added the remaining read paths and the initial admin surface: `GET /shows`
with optional city/movie/date filters, `GET /bookings` returning the caller's own history scoped to
the principal, and admin create/list endpoints for cities, theaters, and shows — where creating a
show materializes one AVAILABLE `show_seat` per physical seat on its screen.

## 9. README and development notes

The README was drafted from the finished code (setup, an API table, the locking design and why,
every recorded assumption, and an explicit "what I left out and why" section), and the slice-by-slice
planning notes were written into `docs/`. The requirements PDF and `Claude.md` were confirmed to be
committed.

---

## Post-submission work

With the core submission complete, further work fell into two kinds: additions beyond the brief, and
fixes that closed genuine gaps against it. In order, the idempotency work came first, then a
compliance review surfaced two gaps (reminders and admin management) which were fixed, and finally
the seat-selection rules were added.

### Idempotent booking — addition beyond the brief

The first follow-up added idempotent confirmation. `POST /bookings` accepts an optional
`Idempotency-Key` header; a retry with the same key returns the original booking instead of creating
a duplicate or failing with `HoldExpired` (the hold having been consumed on the first attempt). A
partial unique index on `booking(idempotency_key)` is the database backstop, and a test covered both
the replay and its dependence on the key. This was not required by the brief — it was added because
it reinforces the same correctness story as the concurrency work.

### Compliance review against the brief

A pass back over the requirements PDF then compared the build to the brief and surfaced two genuine
gaps. First, the brief calls for *confirmation and reminder* notifications, but only confirmation and
cancellation were being emitted. Second, the admin role is defined as managing seat layouts, pricing
tiers, and refund policies, none of which had endpoints — only cities, theaters, and shows did. The
next two slices closed those gaps.

### Reminder notifications — compliance fix

To close the first gap, a `@Scheduled` `ReminderScheduler` enqueues exactly one `REMINDER` outbox
row per confirmed booking whose show starts within a configurable lead window (default 24 hours),
reusing the existing outbox and poller so reminders are delivered off the booking path. A
per-booking flag makes each booking remind exactly once across sweeps, and a test confirmed a second
sweep does not duplicate it.

### Admin management of seat layouts, pricing tiers, and refund policies — compliance fix

To close the second gap, the admin surface was extended to everything the role owns: create/list
screens and a screen's physical seats (per row, per class), create/list/update pricing tiers, and
create/list refund policies with their rules. A test tied it together end to end — a freshly
laid-out screen feeds through to a bookable show whose seat map shows the new seats — and confirmed a
customer is forbidden from these endpoints.

### Seat-selection rules — addition beyond the brief

A final addition enforced BookMyShow-style seat rules at hold time, in a pure `SeatSelectionRules`
class: a configurable maximum seats per booking, and a no-orphan-seat rule that rejects a hold which
would strand a lone available seat between two occupied seats in a row (a seat against the row
boundary is not an orphan). It came with table-driven unit tests and an integration test on isolated
single-row shows. Adding a stateful rule also surfaced that a couple of MockMvc tests had been
relying on shared seat state; they were moved onto their own fresh shows so the rule stays
deterministic under the shared Testcontainers database.
