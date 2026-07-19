# AI workflow

Covers the submission's "skills used during development" item, and describes the workflow that was
actually used.

## Skills

**No packaged Claude Code skills were used.** No `.claude/skills` (or equivalent packaged
instruction sets / slash-command skills) were authored or invoked during development. The workflow
instead relied on a persistent project context file and a disciplined, human-gated session loop,
described below. This note stands in for the "skills" artifact so the absence is explicit rather
than an omission.

## How it actually worked

### `Claude.md` as persistent context
[`../Claude.md`](../Claude.md) was the source of truth for *how* to build — stack, conventions, the
exact no-double-booking design, pricing formula, testing requirements, build order, and a running
decisions log. It was committed and carried across sessions, so direction and prior decisions
survived context resets rather than being re-derived each time. The README, by contrast, was written
from the finished code, not from `Claude.md`.

### Slice-by-slice sessions with review gates
Work proceeded one slice at a time (skeleton → schema → locking → pricing → security → tests →
browse/admin → docs). Each slice: implement, `./mvnw compile` (and `./mvnw test` once tests existed),
exercise endpoints against a real Postgres with curl, then **stop for review** before the next slice.
Every slice ended in a conventional commit; the review gate between slices is where scope and
correctness were checked. Under the final time-boxed run the gates were relaxed to "commit + push
after each slice, continue unless genuinely ambiguous," but the slice boundaries and per-slice
verification stayed intact.

### Assumptions surfaced, not guessed
Ambiguities were raised and resolved explicitly rather than guessed silently, and recorded as
assumptions for the README (e.g. money/precision types, weekend zone, tax as a flat configurable
rate, 10-minute holds).

## Decisions: mine vs delegated

**My decisions (author-directed):**
- **Overall technical direction** — the entire stack, conventions, and the prescriptive
  no-double-booking design captured in `Claude.md`.
- **Seat model** — chose physical `seat` per screen + `show_seat` per (show, seat) over a
  seatless `show_seat`, confirmed before the schema slice.
- **Partial-index backstop** — approved using a *partial* unique index
  `booking_seat(show_seat_id) WHERE active` instead of the brief's literal plain unique index, so
  cancel→re-book works while duplicate active bookings still fail at the DB.
- **Slice reordering under time pressure** — pulled security/error-handling ahead of admin CRUD,
  kept tests as the top priority, and authorized cutting only from the browse/admin slice (never
  from tests) when time ran short.
- **Environment calls** — installing Java 21 (Corretto) before the test slice; approving the Maven
  wrapper and the enforcer-based JDK pin.

**Delegated to Claude Code (execution within that direction):**
- Writing all entities, repositories, services, controllers, DTOs, Flyway migrations, security
  config, schedulers, and the exception advice.
- Designing and writing the test suite, including the 50-thread concurrency test and its
  lock-removed variant.
- Diagnosing and fixing environment/tooling friction: pinning Java 21, and resolving the
  Testcontainers ↔ modern-Docker API-version mismatch (bump to 1.20.4 + pin Engine API 1.44).
- Drafting the README and these notes from the actual code.
