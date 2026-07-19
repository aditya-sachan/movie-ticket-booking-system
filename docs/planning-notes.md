# Planning notes — slice by slice

Raw development artifact. The project was built in reviewable slices, one commit (or a few) per
slice, each compiled and — once it existed — tested before moving on. Technical direction lived in
[`../Claude.md`](../Claude.md) and was treated as the source of truth for *how* to build.

## Approach

The product brief is deliberately open-ended. I interpreted it as **depth on the core booking
flows** rather than breadth into new features, because the hard, interesting part is correctness
under concurrency, not surface area. So the plan front-loaded the no-double-booking design and its
tests, and treated payment/notification as stubs behind seams.

Working agreement: build one slice, `./mvnw compile` (and `./mvnw test` once tests exist), state
assumptions as I go, conventional commit, stop for review. Prefer boring, obviously-correct code.

## Slice log

1. **Skeleton & build** — Spring Boot 3.3.5, Java 21, `com.aditya` coordinates, dependencies, a
   committed Maven wrapper (Maven wasn't on PATH), `.gitignore`, `Claude.md`.
2. **Schema, entities, seed** — Flyway V1 (17 tables) + V2 seed. Physical `seat` per screen +
   `show_seat` per (show, seat). Verified by booting against real Postgres with `ddl-auto: validate`
   so every entity is checked against the migrated schema.
   - **Decision:** backstop is a **partial** unique index `booking_seat(show_seat_id) WHERE active`,
     not a plain unique index, so cancel→re-book works while duplicate active bookings still fail at
     the DB. Approved before implementing.
3. **Hold → confirm → cancel with locking** — the core. `SELECT … FOR UPDATE` ordered by seat id;
   10-minute holds with a token; expiry reclaimed on demand under lock plus a 60 s sweep;
   transactional outbox + logging-stub poller; stub payment. Exercised manually against Postgres
   (conflict 409s, ownership 403, cancel refund, re-book after cancel, async delivery on the
   scheduler thread).
4. **Pricing, discounts, refunds** — pure `PriceCalculator` / `RefundCalculator`; weekend-by-day
   tier; discount validation at hold and re-validation at confirm under a row lock with atomic
   redemption increment; configurable tax; full breakdown persisted on the booking.
5. **Security & error handling** (pulled ahead of admin CRUD) — HTTP Basic backed by `app_user`
   rows, bcrypt via a `PasswordEncoder`-driven seeder; removed the `userId`-in-body seam in favour
   of the authenticated principal; `@PreAuthorize` on every endpoint; full `ProblemDetail` advice.
6. **Tests** (highest priority) — 50-thread concurrency test on one seat with Testcontainers
   Postgres (exactly one wins; plus a lock-removed variant proving the race and that the DB backstop
   still holds); hold-expiry release; table-driven pricing/refund/discount unit tests; RBAC + happy
   path via MockMvc.
7. **Browse/history/admin (reduced)** — `GET /shows` with city/movie/date filters, `GET /bookings`
   history scoped to the principal, admin create/list for city/theater/show (creating a show
   materializes its `show_seat` rows).
8. **README + these notes.**

## Key decisions & why

- **Postgres, not H2, for tests** — H2 does not reproduce `SELECT … FOR UPDATE` row locking, which
  is the whole point of the concurrency test.
- **Two-layer no-double-booking** — pessimistic row lock for clean application-level serialization,
  *and* a DB partial unique index as a backstop that holds even if the app logic is wrong. The
  concurrency test demonstrates both.
- **Pure calculators** — pricing/refund/discount math is Spring-free and DB-free so it is
  exhaustively unit-testable in a table-driven way; services only resolve data and delegate.
- **Transactional outbox over `@Async`** — notification delivery is decoupled from and cannot roll
  back the booking, without a message broker.
- **Seams over stubs-in-place** — `PricingService`, `NotificationSender`, and the payment step are
  seams so real implementations drop in later without touching the booking path.

## Environment friction encountered (worth recording)

- Only a JDK 26 was initially available; the build pins Java 21 and an enforcer rule fails loudly on
  anything else. A real Corretto 21 was installed before the test slice.
- Testcontainers vs. a very new Docker Desktop (Engine API 1.55): `docker-java` negotiated too low
  an API version (1.32) and the engine returned HTTP 400. Fixed by bumping Testcontainers to 1.20.4
  and pinning the API version to 1.44 in the surefire config.

## AI workflow

Claude Code drove implementation against the direction in `Claude.md`, one slice at a time, running
`./mvnw compile`/`test` and exercising endpoints with curl after each slice, committing
conventionally and pushing. Ambiguous decisions (seat model, the partial-index backstop) were raised
and confirmed before building rather than guessed.
