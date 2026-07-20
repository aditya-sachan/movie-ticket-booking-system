# Development prompt log

The sequence of instructions used to drive this AI-assisted build. The project was developed in
reviewable slices — one concern at a time, compiled and tested before moving on, and committed at
each step — so this log doubles as a record of how the work progressed and why decisions were made.
It mirrors the commit history one-to-one.

Standing direction throughout lived in [`../Claude.md`](../Claude.md) (stack, conventions, the
no-double-booking design, build order) and was treated as the source of truth for *how* to build.

---

## 0. Framing

> Read `docs/requirements.pdf` in full first. Treat the brief as depth on the core booking flows,
> not breadth into new features — if something extra seems worth building, propose it, don't build
> it. Everything I give below is technical direction and overrides any default you'd pick. Build one
> slice at a time and STOP after each for review; run a compile before declaring a slice done, and
> tests once they exist; make a conventional commit at the end of each slice; state assumptions
> explicitly as you go; keep planning notes in `docs/`. Ask when something is ambiguous rather than
> guessing.

## 1. Skeleton & build setup

> Set up the project skeleton: Spring Boot 3.3.x parent, Java 21 (not whatever the machine
> defaults to), groupId `com.aditya`, artifact `movie-ticket-booking-system`. Dependencies: web,
> data-jpa, validation, security, postgresql, flyway-core, flyway-database-postgresql; test scope:
> starter-test, security-test, Testcontainers postgresql + junit-jupiter. No Lombok. Add a
> `.gitignore`, the `MovieTicketBookingApplication` entry point, and commit. Confirm the plan back
> to me before scaffolding anything further.

## 2. Persist the Java 21 toolchain guarantee

> Before we go further, make the Java 21 requirement enforceable so a later session can't silently
> build on the wrong JDK. Add a build-level guard that fails loudly on anything other than 21.x, and
> keep it portable — don't force every reviewer to maintain a machine-specific toolchains file.
> Commit separately.

## 3. Schema, entities, seed data

> Slice: the Flyway schema, JPA entities, and seed data. Use the physical-seat model — a `seat`
> belongs to a `screen` (layout defined once, reusable), a `show` references a screen, and
> `show_seat` is one row per (show, seat) created when the show is created. Model the full domain
> now (users, cities/theaters/screens/seats, movies, shows, show_seats, discount codes, refund
> policies, bookings, payments, notification outbox) even though behaviour comes later. Enums as
> `VARCHAR`+`CHECK`, money as `NUMERIC`, timestamps as `TIMESTAMPTZ`. Point `application.yml` at the
> local Postgres with `ddl-auto: validate`, and prove the migrations apply cleanly and every entity
> validates by booting the app before you call the slice done.
>
> On the booking backstop: the brief says a plain unique index on `booking_seat(show_seat_id)`, but
> that breaks cancel→re-book. Use a partial unique index `WHERE active` instead and record the
> reasoning — I want it in the README and the video.

## 4. Hold → confirm → cancel with row locking (the core)

> This is the most important part — take the time to get it right.
> - `POST /shows/{id}/holds`: lock the requested `show_seat` rows with `SELECT ... FOR UPDATE`
>   ordered by seat id (consistent ordering avoids deadlock), verify all AVAILABLE, set HELD with a
>   10-minute expiry and a hold token, return the token.
> - `POST /bookings`: verify the token and non-expiry, transition HELD → BOOKED, and create
>   Booking + BookingSeat + a stub Payment + a notification outbox row, all in one transaction.
> - `POST /bookings/{id}/cancel`: seats back to AVAILABLE, deactivate the booking_seat rows, refund
>   from the applicable RefundPolicy.
> - A `@Scheduled` sweep (60s) releases expired holds; an outbox poller delivers via a logging stub.
>
> Skip pricing for now — use `base_price` only and leave a clear seam where the pricing service will
> plug in. Exercise the flow manually against Postgres before committing.

## 5. Pricing, discount codes, refund policies

> Plug real pricing into the seam: `final = (base_price × seat_class_multiplier × tier_multiplier) −
> discount + taxes`, with WEEKEND tier applied by show day. Discount codes: percentage or flat, min
> order value, max redemptions, validity window — validate at hold and re-validate at confirm, and
> guard redemptions atomically. Wire RefundPolicy fully into cancel. Persist the full breakdown onto
> the booking. Keep the arithmetic in a pure, unit-testable class.

## 6. Security & error handling

> Real HTTP Basic backed by DB users with bcrypt hashes seeded through the PasswordEncoder. Remove
> the userId-in-body seam and take the principal from the security context. `@PreAuthorize` on every
> endpoint (customers book/cancel/browse; the ownership rule that a customer can't touch another's
> booking stays enforced). One `@RestControllerAdvice` returning RFC 7807 `ProblemDetail` for all
> domain exceptions plus validation, access-denied, and an unexpected-error fallback.

## 7. Tests — highest priority, do not compress

> - A 50-thread concurrency test on a single seat, Testcontainers Postgres (not H2 — it won't
>   reproduce real row locking): exactly one booking wins, 49 get `SeatUnavailable`. Also demonstrate
>   it fails (the race appears) if the row lock is removed.
> - A hold-expiry release test.
> - Table-driven unit tests for pricing and refund calculation covering boundaries.
> - RBAC tests: a customer can't reach another customer's booking or an admin endpoint.
> - Happy path: browse → hold → confirm → cancel.

## 8. Browse/search, booking history, admin CRUD

> `GET /shows` with city/movie/date filters, `GET /bookings` for the caller's own history, and admin
> create/list for city, theater, and show (creating a show should materialize its show_seat rows).
> Keep it scoped — this is the reduced-scope slice.

## 9. README and raw development notes

> Draft the README from the actual code, not from Claude.md: setup, an API table, the locking design
> and why, every assumption, and an explicit "what I left out and why" section. Write the
> slice-by-slice planning notes into `docs/`. Confirm the requirements PDF and Claude.md are both
> committed.

---

## Enhancements (after the core submission was complete)

## 10. Idempotent booking

> Add idempotent confirmation: an optional `Idempotency-Key` header on `POST /bookings`, so a retry
> returns the original booking instead of a duplicate or a HoldExpired error. Back it with a DB
> unique constraint and add a test.

## 11. Showtime reminders

> Close the "reminder notifications" requirement: a `@Scheduled` job that enqueues exactly one
> REMINDER outbox row per confirmed booking whose show starts within a configurable lead window,
> reusing the existing outbox and poller. Add a test proving it fires once and doesn't duplicate.

## 12. Admin management of the remaining entities

> The admin role is defined as managing seat layouts, pricing tiers, and refund policies too — add
> those endpoints: create/list screens and seats, create/list/update pricing tiers, create/list
> refund policies with their rules. Add a test that a laid-out screen feeds through to a bookable
> show, and that a customer is forbidden.

## 13. Seat-selection rules

> Add BookMyShow-style seat rules at hold time: a max seats-per-booking cap and a no-orphan-seat
> rule (don't strand a lone available seat between two occupied seats in a row). Keep the logic in a
> pure class with table-driven unit tests, plus an integration test on isolated single-row shows.

---

## Compliance pass

> Go through the requirements PDF once more and compare it against what's built — call out anything
> that doesn't fully satisfy the brief, and fix the genuine gaps.
