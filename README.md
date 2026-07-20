# Movie Ticket Booking System

A Spring Boot backend for booking movie tickets at scale: multiple cities → theaters → screens →
seats, shows with seat-level booking, time-bound seat holds that auto-release, tiered pricing with
discount codes and tax, payments (stubbed), cancellations with policy-driven refunds, and
non-blocking notifications. The central guarantee is **no double-booking of a seat under
concurrency**.

> Built as a take-home. The development direction lived in [`Claude.md`](./Claude.md); planning
> notes and a reconstructed slice-by-slice [development log](./docs/development-log.md) (not a
> verbatim prompt transcript) are in [`docs/`](./docs). This README is written from the code that
> actually exists.

---

## Tech stack (and why)

| Choice | Reason |
|---|---|
| **Java 21**, Spring Boot 3.3 | Modern LTS; records for DTOs, pattern matching. Version pinned (see below). |
| **PostgreSQL 16** | The no-double-booking design relies on real `SELECT … FOR UPDATE` row locking and a partial unique index — neither is faithfully reproducible on H2. |
| **Flyway** | Deterministic, versioned schema. `ddl-auto` is never used for generation (`validate` only). |
| **Spring Data JPA** | Repositories + `@Lock` pessimistic locking without hand-written JDBC. |
| **Spring Security (HTTP Basic, DB users)** | In-scope auth is role-based access, not federated identity. Simple, stateless, DB-backed. |
| **JUnit 5 + Testcontainers + MockMvc** | Concurrency and locking are tested against a real Postgres in a container, not a mock. |
| **No Lombok** | Constructors/getters are written explicitly (a project constraint). |

## Architecture

Layered: **controller → service → repository**. Controllers do no business logic and never expose
entities; request/response DTOs are Java **records** mapped in the service layer. `@Transactional`
lives on services only. A single `@RestControllerAdvice` returns RFC 7807 `ProblemDetail` for every
error. Constructor injection throughout.

```
com.aditya.movieticketing
├── domain         JPA entities + enums
├── repository     Spring Data JPA repositories (incl. locking queries)
├── service        business logic + pure calculators (pricing, refund, discount rules)
├── scheduler      @Scheduled hold-expiry sweep and outbox poller
├── web            REST controllers
│   └── dto        request/response records
├── exception      ApiException hierarchy + ProblemDetail advice
└── config         security, DB-backed user details, user seeder
```

---

## The core problem: no double-booking

One row per `(show, seat)` lives in **`show_seat`** with status `AVAILABLE | HELD | BOOKED`. Booking
is a three-step flow, and the guarantee rests on **two independent mechanisms**:

### 1. Application-level serialization — `SELECT … FOR UPDATE`

**Hold** (`POST /shows/{id}/holds`) runs in one transaction:
1. Lock the requested `show_seat` rows with `SELECT … FOR UPDATE` **ordered by seat id**.
2. Verify every seat is `AVAILABLE` (or a HELD row whose hold has already expired — reclaimed on
   the spot under the lock, so availability never waits for the sweep).
3. Set them `HELD` with a shared **hold token** and `hold_expires_at = now + 10 min`.

Two transactions requesting an overlapping seat cannot both pass step 2: the second blocks on the
row lock at step 1 until the first commits, then sees the seat is `HELD` and fails with
`SeatUnavailable`. **Ordering the lock by seat id** gives every transaction the same acquisition
order, which prevents deadlocks when holds span multiple seats.

**Confirm** (`POST /bookings`) re-locks the held seats by token, checks they are still `HELD` and
unexpired, transitions them to `BOOKED`, and creates the `Booking` + `BookingSeat` + (stub)
`Payment` + notification outbox row — all in one transaction.

**Cancel** (`POST /bookings/{id}/cancel`) returns the seats to `AVAILABLE`, deactivates the
`booking_seat` rows, and computes a refund from the show's refund policy.

### 2. Database-level backstop — a *partial* unique index

```sql
create unique index uq_booking_seat_active on booking_seat (show_seat_id) where active;
```

At most one **active** booking may reference a given `show_seat`, enforced by the database even if
the application logic were wrong. It is **partial** (`WHERE active`) rather than a plain unique
index on purpose: a plain index would make a seat un-bookable forever after its first cancellation
(the historical `booking_seat` row would still occupy the index). With `booking_seat.active` set
false on cancel, history is preserved *and* a freed seat can be re-booked, while a duplicate active
booking still fails at the DB.

### Expiry sweep

A `@Scheduled` job (every 60 s) releases `HELD` seats past `hold_expires_at`. It is only a mop-up
for abandoned holds — holds already reclaim their own expired rows on demand.

## Async notifications (transactional outbox)

Confirmation/cancellation notifications must not block booking. Inside the booking transaction a row
is written to **`notification_outbox`**; a separate `@Scheduled` poller drains `PENDING` rows and
delivers them via a logging stub (`NotificationSender`). Delivery therefore happens off the request
thread and can never roll back or slow down a booking. No raw `@Async` in the booking path.

**Reminders** work the same way: a `@Scheduled` `ReminderScheduler` enqueues one `REMINDER` outbox
row per confirmed booking whose show starts within a configurable lead window
(`booking.reminder.lead-hours`, default 24), using a per-booking flag so each booking is reminded
exactly once. Reminders are therefore also delivered off the booking path.

## Pricing and refunds

```
final = (base_price × seat_class_multiplier × tier_multiplier) − discount + taxes
```

- The arithmetic lives in a **pure, unit-tested** `PriceCalculator` (no Spring, no DB). Each seat's
  class multiplier is applied, so a mix of regular/premium seats prices correctly.
- **Tier by show day**: a show starting on Sat/Sun (Asia/Kolkata) uses the `WEEKEND` multiplier;
  otherwise its configured tier (REGULAR/PREMIUM).
- **Discount codes** (percentage or flat; minimum order value; max redemptions; validity window)
  are validated at hold time and **re-validated at confirm** under a row lock, with an atomic
  `times_redeemed` increment to prevent over-redemption. A flat discount never exceeds the subtotal.
- **Tax** is a configurable flat rate on the discounted subtotal (`pricing.tax-rate`, default 0.18).
- **Refunds** come from the show's `RefundPolicy` rules (keyed by hours-before-show); the pure
  `RefundCalculator` picks the applicable tier. Seed "Standard" policy: ≥48 h → 100%, ≥24 h → 50%,
  ≥2 h → 25%, else 0%.

The full breakdown (subtotal / discount / tax / total) is persisted on the `booking`.

---

## Running it

### Prerequisites
- **Java 21** (the build fails loudly on any other version via `maven-enforcer-plugin`). If a bare
  shell resolves to a different JDK, set `JAVA_HOME` to a 21, e.g.
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **PostgreSQL 16** on `localhost:5432`, database `movieticketing`, user/password `postgres`/`postgres`
  (see `src/main/resources/application.yml`).
- **Docker** — only for the integration tests (Testcontainers).

### Start the app
```bash
./mvnw spring-boot:run
```
Flyway migrates the schema and seeds demo data on startup; `UserSeeder` creates the demo users with
bcrypt passwords.

### Demo users
| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `alice` | `alice123` | CUSTOMER |
| `bob`   | `bob123`   | CUSTOMER |

### Seed data
2 cities (Bengaluru, Mumbai), theaters/screens, 50 seats on PVR Forum / Screen 1 (rows A–B premium,
C–E regular), 2 movies, 2 future shows, per-show `show_seat` rows, and discount codes `WELCOME10`
(10%, min ₹200) and `FLAT50` (₹50, min ₹300).

### Example flow (curl)
```bash
B=http://localhost:8080
# browse
curl -u alice:alice123 "$B/shows?cityId=1"
# hold two seats (optionally with a discount code)
curl -u alice:alice123 -X POST "$B/shows/1/holds" -H 'Content-Type: application/json' \
     -d '{"seatIds":[10,11],"discountCode":"WELCOME10"}'
# confirm using the returned holdToken
curl -u alice:alice123 -X POST "$B/bookings" -H 'Content-Type: application/json' \
     -d '{"showId":1,"holdToken":"<token>","discountCode":"WELCOME10"}'
# my history / cancel
curl -u alice:alice123 "$B/bookings"
curl -u alice:alice123 -X POST "$B/bookings/1/cancel"
```

---

## API reference

All endpoints require HTTP Basic auth. Roles enforced with `@PreAuthorize`.

| Method | Path | Role | Description |
|---|---|---|---|
| `GET` | `/shows?cityId&movieId&date` | any | Browse/search shows (filters optional; `date` = `YYYY-MM-DD`) |
| `GET` | `/shows/{id}/seats` | any | Seat map for a show (label, class, status) |
| `POST` | `/shows/{id}/holds` | CUSTOMER | Hold seats → returns hold token + price estimate |
| `POST` | `/bookings` | CUSTOMER | Confirm a hold into a booking (optional `Idempotency-Key` header — a retry returns the original booking) |
| `GET` | `/bookings` | CUSTOMER | The caller's own booking history |
| `POST` | `/bookings/{id}/cancel` | CUSTOMER | Cancel own booking → refund |
| `POST` / `GET` | `/admin/cities` | ADMIN | Create / list cities |
| `POST` / `GET` | `/admin/theaters` | ADMIN | Create / list theaters |
| `POST` / `GET` | `/admin/screens` | ADMIN | Create / list screens |
| `POST` / `GET` | `/admin/screens/{id}/seats` | ADMIN | Add / list a screen's physical seat layout |
| `POST` | `/admin/shows` | ADMIN | Create a show (materializes its `show_seat` rows) |
| `POST` / `GET` / `PUT` | `/admin/pricing-tiers` | ADMIN | Create / list / update (multiplier) pricing tiers |
| `POST` / `GET` | `/admin/refund-policies` | ADMIN | Create (with rules) / list refund policies |

Errors are RFC 7807 `ProblemDetail`: `SeatUnavailable`/`HoldExpired` → 409, `ShowNotFound`/
`BookingNotFound` → 404, `InvalidDiscount` → 422, validation → 400, wrong role/ownership → 403,
unauthenticated → 401.

---

## Testing

```bash
./mvnw test
```
Requires Docker. If Testcontainers cannot locate the Docker socket, set
`DOCKER_HOST=unix:///var/run/docker.sock`. The Docker Engine API version is pinned to 1.44 in the
surefire config so `docker-java` works against modern engines.

**44 tests, all green.** Coverage:
- **`SeatBookingConcurrencyTest`** — the headline. 50 threads race for one seat on a real Postgres:
  with the row lock, exactly one booking wins and the other 49 get `SeatUnavailable`; with the lock
  removed, multiple threads pass the availability check (the race) yet the partial unique index
  still prevents any double-allocation.
- **`HoldExpiryReleaseTest`** — an expired hold is released by the sweep.
- **`PriceCalculatorTest` / `RefundCalculatorTest` / `DiscountRulesTest`** — table-driven unit tests
  over the pure calculators (multipliers, discount capping, rounding, tax, refund boundaries,
  discount validity/limits).
- **`BookingFlowAndRbacTest`** (MockMvc) — happy path browse→hold→confirm→cancel; 401
  unauthenticated; a customer cannot cancel another's booking (403); an admin cannot use a
  customer-only endpoint and a customer cannot use an admin endpoint (403).

---

## Assumptions

- **Backstop is a *partial* unique index**, not the plain one the brief literally described — a plain
  index would break cancel→re-book (rationale above and in `Claude.md`).
- The **`show` table is named `movie_show`** to avoid the SQL `SHOW` keyword; the entity is `Show`.
- **Money** is `NUMERIC(12,2)`, **multipliers** `NUMERIC(6,4)`, timestamps `TIMESTAMPTZ` (JDBC UTC);
  enums stored as `VARCHAR` + `CHECK`.
- **Weekend** is determined in **Asia/Kolkata** (Indian theaters).
- **Tax** is a single configurable flat rate; there is no per-jurisdiction tax model.
- **Payment and notification delivery are stubs** (no real gateway / email / SMS).
- **Hold duration is 10 minutes**; sweep (60 s) and outbox poll (5 s) intervals are configurable.
- Demo users are seeded with fixed passwords for evaluation convenience.
- **Idempotent booking**: `POST /bookings` accepts an optional `Idempotency-Key` header. A repeat
  with the same key returns the original booking instead of a duplicate (or a `HoldExpired` error
  because the hold was consumed on the first call); a partial unique index on
  `booking(idempotency_key)` is the DB backstop.

## What I left out, and why

- **Seat maps, waitlists, loyalty, recommendations, analytics** — deliberately out of scope; the
  brief rewards *depth* on the core flows, not breadth. Would propose before building.
- **Real payment gateway / email / SMS** — explicitly out of scope; stubbed behind seams
  (`Payment`, `NotificationSender`) so a real provider drops in without touching the booking path.
- **OAuth/SSO/MFA, frontend, Docker packaging, CI/CD, metrics/tracing, microservices** — out of scope.
- **Delete/most update operations on admin entities** — admin can create and list cities,
  theaters, screens, seat layouts, shows, pricing tiers (with multiplier update), and refund
  policies; delete and the remaining update operations were left out as low-value for the brief.
  The schema supports them.
- **Pagination on browse/history** — endpoints return full lists; pagination would be the first
  production addition.
- **Seat-selection rules (max seats, no orphan seat)** — built as an extra, then **removed**. It is
  not in the brief, and without a seat-map UI to grey out invalid choices, rejecting a customer's
  otherwise-valid seat request with a 422 is worse than the gap it prevents. Real platforms enforce
  this visually at selection time, not as an API rejection, so it was reverted.

## Build & JDK notes

- Maven wrapper (`./mvnw`) is committed — Maven need not be installed.
- `maven-enforcer-plugin` requires Java `[21,22)` in the `validate` phase, so the build refuses to
  run on the wrong JDK instead of silently compiling on it. (Enforcer was chosen over toolchains so
  a clean checkout builds without a machine-specific `~/.m2/toolchains.xml`.)
