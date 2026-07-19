# Claude.md — Movie Ticket Booking System

Persistent technical direction for this project. This file is the source of truth for
*how* to build; it overrides any default choice. The README is drafted separately from the
actual code, not from this file.

## Product
A movie ticket booking system at scale: multiple cities, multiple theaters per city, multiple
shows per theater, seat-level booking. Time-bound seat holds that auto-release on expiry,
pricing tiers (regular/premium/weekend) and discount codes, payment, booking confirmation,
and refunds on cancellation under configurable refund policies. Concurrent booking of the same
seat must serialize with no double-allocation. Confirmation/reminder notifications must not
block the booking flow.

Roles: **admin** (manage cities, theaters, shows, seat layouts, pricing tiers, refund policies)
and **customer** (browse shows, book and cancel seats, view booking history).

## Stack (fixed — do not substitute)
- Java 21, Spring Boot 3.3.x, Maven
- PostgreSQL 16
- Flyway for schema (never `ddl-auto`; `ddl-auto=validate` at most)
- Spring Data JPA
- Spring Security: HTTP Basic, users stored in the DB (NOT OAuth/JWT/SSO)
- JUnit 5, Mockito, Testcontainers, MockMvc
- **No Lombok** — write constructors and getters explicitly
- groupId `com.aditya`, artifactId `movie-ticket-booking-system`, base package
  `com.aditya.movieticketing`

## Out of scope — do not build
Frontend, Docker, CI/CD, microservices, OAuth/SSO/MFA, metrics/tracing, real payment gateway,
real email/SMS. Payment and notification delivery are **stubs**.

## Scope discipline
Interpret the brief as **depth on the core flows**, not breadth into new features. Do NOT add
seat maps, waitlists, loyalty programs, recommendations, or analytics. If something extra seems
worth building, **propose it — don't build it.**

## Core problem: no double-booking (implement exactly this design)
- `show_seat`: one row per (show_id, seat_id), created when a show is created.
  Status `AVAILABLE | HELD | BOOKED`.
- **Hold**: in ONE transaction — lock the requested rows with `SELECT ... FOR UPDATE`
  **ordered by seat id** (consistent ordering avoids deadlock), verify all AVAILABLE, set HELD
  with `hold_expires_at = now() + 10 minutes` and a hold token.
- **Confirm**: verify hold token and non-expiry, transition to BOOKED, create Booking + Payment
  in the same transaction.
- **Expiry**: `@Scheduled` job every 60s releases HELD rows past `hold_expires_at`.
- **Backstop**: unique index on `booking_seat(show_seat_id)` so a duplicate booking fails at the
  DB level even if application logic is wrong.
- **Cancel**: return seats to AVAILABLE, compute refund from the applicable RefundPolicy.

## Async notifications (must not block booking)
Transactional **outbox**: write a notification row inside the booking transaction; a `@Scheduled`
poller picks up PENDING rows and delivers via a logging stub. Do NOT use a raw `@Async` call in
the booking path.

## Conventions
- Layers: controller → service → repository. No business logic in controllers, no entities
  crossing the controller boundary.
- Request/response DTOs as Java **records**, mapped in the service layer.
- Bean Validation on all request bodies.
- ONE `@RestControllerAdvice` returning RFC 7807 `ProblemDetail`, with custom exceptions
  (`SeatUnavailable`, `HoldExpired`, `ShowNotFound`, `InvalidDiscount`).
- Constructor injection everywhere; no field `@Autowired`.
- `@Transactional` on service methods, never on controllers.
- Enforce roles with `@PreAuthorize`. A customer must never read or mutate another customer's
  booking.

## Pricing
```
final = (base_price × seat_class_multiplier × tier_multiplier) − discount + taxes
```
Discount codes: percentage or flat, minimum order value, max redemptions, validity window.
Validate at hold time and re-validate at confirm time.

## Testing (heavily weighted — do not skip)
1. **Concurrency integration test**: 50 threads attempt the same seat on the same show
   simultaneously; exactly one booking succeeds, 49 get `SeatUnavailable`. Testcontainers
   Postgres, NOT H2 (H2 won't reproduce real row locking). Also demonstrate it FAILS if the
   row lock is removed.
2. **Hold expiry**: hold seats, expire them, assert release.
3. **Unit tests** for pricing and refund calculation — table-driven, covering boundaries.
4. **RBAC**: customer cannot access another customer's booking; customer cannot hit admin
   endpoints.
5. **Happy path**: browse → hold → confirm → cancel.

## Build order — one slice per session (STOP after each for review)
1. Skeleton, Flyway schema, entities, seed data
2. Hold → confirm → cancel with locking
3. Pricing tiers, discount codes, refund policies
4. Admin CRUD, browse/search shows, booking history
5. Security, validation, exception handling
6. Tests
7. README (drafted from the actual code, not from this file)

## Working agreement
- Build one slice at a time; STOP after each for review. Do not scaffold ahead.
- Run `./mvnw -q compile` before declaring a slice done; `./mvnw -q test` once tests exist.
- Conventional commit at the end of each slice (multiple commits are a submission requirement).
- State assumptions explicitly as you go (needed for the README).
- Prefer boring, obviously-correct code over clever code.
- Ask when something is ambiguous rather than guessing silently.
- Keep planning notes and prompt artifacts in `docs/` (submission requires them).

## Decisions log
- **Seat model:** physical `seat` belongs to a `screen` (layout defined once, reusable); a
  `show` references a screen; `show_seat` is one row per (show_id, seat_id) created when the show
  is created. Confirmed by the user before slice 2.

## Environment notes / assumptions
- Only a JDK 26 is installed locally; there is no Java 21 JDK. The build targets Java 21 via
  `--release 21` (JDK 26 can do this). **Risk:** running Spring Boot 3.3.x tests on a JVM 26
  runtime (ByteBuddy/CGLIB, Testcontainers) is not something Spring Boot 3.3 was released
  against. Installing a real Java 21 JDK (e.g. `brew install openjdk@21` or SDKMAN
  `sdk install java 21-tem`) before the test slice is strongly recommended.
- Maven is not on PATH; a Maven wrapper (`./mvnw`) is committed so the build is self-contained.
- Docker is required for Testcontainers (concurrency + expiry integration tests).
