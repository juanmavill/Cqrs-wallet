# CQRS Wallet

**A wallet service that separates writes from reads (CQRS), measured against an
equivalent monolith to find out what that separation actually buys.**

This repository is not only a CQRS implementation. It ships a **reference
monolith** solving the same problem against the same database, and a load-testing
harness that puts both architectures under identical load. The conclusion is not
that CQRS wins, but **which dimension it wins on and what it costs**.

---

## What it does

- Records credit and debit movements against accounts, with balance validation.
- Maintains a denormalised read model for balance queries.
- Propagates write-side changes to the read side through events.
- Exposes the same functionality as a monolith, for comparison.

---

## Architecture

```mermaid
flowchart LR
    C[Client] -->|POST /api/transactions| CS[command-service<br/>:8081]
    C -->|GET /api/balance/:id| QS[query-service<br/>:8082]

    CS -->|transactional write| MY[(MySQL<br/>write model)]
    CS -.->|BalanceUpdatedEvent<br/>after commit| RMQ{{RabbitMQ}}
    RMQ -.->|consume| QS
    QS -->|read| MO[(MongoDB<br/>read model)]

    C -->|comparison| MR[monolith-reference<br/>:8080]
    MR --> MY
```

The write side is the only source of truth. The read side is a projection that
may lag: the system is **eventually consistent**, not immediately consistent.

---

## Measured results

Direct monolith-vs-CQRS comparison with JMeter 5.6.3. Each load level runs for
180 s with a 30 s ramp, and the stack is reset between runs so none carries state
from the previous one. Between 230,000 and 266,000 samples per run locally.
**Error rate 0.00% across every run.**

### Reads: the separation shows, and stays flat

`GET /balance` p50 latency, local (ms):

| Threads | Monolith | CQRS |
|---:|---:|---:|
| 50 | 16 | **1** |
| 100 | 53 | **1** |
| 200 | 112 | **1** |
| 350 | 216 | **1** |
| 500 | 315 | **1** |

The monolith degrades nearly 20x between 50 and 500 concurrent users. The CQRS
read side **does not move**: 1 ms p50 and 4 ms p95 at all five levels. Reads no
longer compete with writes, because they no longer touch the same database.

### Writes: that benefit is paid for

`POST /transactions` p50 latency, local (ms):

| Threads | Monolith | CQRS | Overhead |
|---:|---:|---:|---:|
| 50 | 56 | 98 | 1.8x |
| 100 | 91 | 202 | 2.2x |
| 200 | 156 | 462 | 3.0x |
| 350 | 270 | 726 | 2.7x |
| 500 | 365 | 1042 | 2.9x |

The write path does everything the monolith does **and also** publishes an event.
The gap widens with load: at 500 threads a CQRS write takes close to a second
against the monolith's 365 ms.

### On AWS the advantage appears later

`GET /balance` p50 latency on EC2 (ms):

| Threads | Monolith | CQRS |
|---:|---:|---:|
| 50 | 90 | 88 |
| 100 | 90 | 88 |
| 200 | 99 | 89 |
| 350 | 151 | **89** |
| 500 | 189 | **89** |

At low load the two architectures are indistinguishable: network latency
(~90 ms) dominates and hides any design difference. The CQRS advantage only
emerges past 200 threads, once the monolith starts to degrade while the read side
stays flat.

**What this means:** CQRS here is not "faster". It trades write latency and
operational complexity for reads that do not degrade under load. In a
read-light system, or deployed where the network dominates, that trade does not
pay off.

Raw data: [`jmeter/sweep-20260510-214833.csv`](jmeter/sweep-20260510-214833.csv)
(local) and [`jmeter/sweep-aws-20260511-023510.csv`](jmeter/sweep-aws-20260511-023510.csv) (AWS).

These figures measure the HTTP request paths. The asynchronous projection changed
after these runs, when the stale-event guard was added; that adds one read per
event on the consumer without affecting the latency of the measured requests.

---

## Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Write model | MySQL 8.0 + Spring Data JPA |
| Read model | MongoDB 7.0 |
| Messaging | RabbitMQ 3.13 |
| Packaging | Docker + Docker Compose |
| Load testing | JMeter 5.6.3 |
| Tests | JUnit 5 + Mockito + AssertJ |

---

## Technical decisions

**Why two databases rather than one.** The goal was to isolate contention between
reads and writes. With a single database, separating the models would have
changed nothing measurable: they would still compete for the same pages and
locks. MongoDB stores the already-computed balance, so a query is a key lookup.

**Why the event is published after commit.** `AfterCommitEventForwarder` listens
with `@TransactionalEventListener(AFTER_COMMIT)`. Publishing inside the
transaction would let a later rollback leave the read model holding a balance
that never existed. Publishing afterwards guarantees only committed facts
propagate.

**Why a pessimistic lock on the balance.** `AccountRepository.findByIdForUpdate`
uses `@Lock(PESSIMISTIC_WRITE)`. Without it, two concurrent debits on the same
account can read the same balance and both validate against funds that are
already committed. Under high contention on a single row, optimistic locking with
retries would burn more work than it saves.

**Why the consumer discards older events.** The queue retries with backoff before
routing to a DLQ, so an event can be redelivered late. Because the event carries
the resulting balance rather than a delta, reapplying it is harmless, but applying
one *older* than the current projection would leave a stale balance permanently.
`EventConsumer` compares timestamps and drops what arrives late.

**Why the monolith is kept.** Without a baseline measured under the same load with
the same tooling, any claim about CQRS would be a guess. `monolith-reference`
exists so the comparison is reproducible.

---

## Running it locally

Requires Docker and Docker Compose.

```bash
cp .env.example .env
docker compose up -d --build
```

The compose file ships no default credentials: if `.env` is missing, the command
fails naming the variable to define. All six containers declare a `healthcheck`,
and the applications wait for the infrastructure to be healthy before starting.
MySQL is seeded from `init.sql` with accounts `ACC001` onwards.

Available variables are listed in [`.env.example`](.env.example): MySQL and
RabbitMQ credentials and the database names.

| Service | URL |
|---|---|
| command-service | http://localhost:8081 |
| query-service | http://localhost:8082 |
| monolith-reference | http://localhost:8080 |
| RabbitMQ console | http://localhost:15672 |

Check everything came up:

```bash
curl http://localhost:8081/actuator/health
```

Stop and drop the volumes:

```bash
docker compose down -v
```

---

## API

### Record a movement

```bash
curl -X POST http://localhost:8081/api/transactions -H "Content-Type: application/json" -d '{"accountId":"ACC001","amount":250.00,"type":"DEBIT"}'
```

```json
{"transactionId":"a9e1ab3f-...","status":"SUCCESS","timestamp":"2026-08-16T20:19:05Z"}
```

### Query the projected balance

```bash
curl http://localhost:8082/api/balance/ACC001
```

```json
{"accountId":"ACC001","balance":9750.00,"lastUpdated":"2026-08-16T20:19:05Z"}
```

### Error responses

| Situation | HTTP | Code |
|---|---:|---|
| Insufficient funds | 422 | `INSUFFICIENT_FUNDS` |
| Unknown account | 404 | `ACCOUNT_NOT_FOUND` |
| Invalid amount or missing field | 400 | `VALIDATION_ERROR` |
| No projection for the account | 404 | `BALANCE_NOT_FOUND` |

---

## Tests

```bash
mvn test
```

15 tests in total.

`command-service` (9) covers the write-side rules: credit, debit, debiting the
exact balance, rejection for insufficient funds and for an unknown account,
persistence of the movement, and use of the pessimistic lock. It also pins the
behaviour of the after-commit forwarder when the broker fails.

`query-service` (6) covers the projection: creating the first projection,
applying a newer event, discarding a late one, reapplying the same event, and
querying a balance that does not exist.

Load tests need JMeter installed:

```bash
./jmeter/run-fair-benchmark.sh 180 30 "50,100,200,350,500"
```

---

## Known limitations

- **There is no transactional outbox.** The consuming side does retry and route to
  a DLQ, but the publishing side does not: if RabbitMQ is down right after the
  commit, `AfterCommitEventForwarder` logs the error and the event is lost,
  leaving the projected balance permanently stale. This is the most serious gap
  in the design, and closing it means persisting the event in the same
  transaction as the write.
- **The read model can lag.** A read immediately after a write may return the
  previous balance. That is inherent to CQRS rather than a defect, but the API
  offers no way to request a consistent read.
- **The stale-event guard assumes a single consumer.** It reads the projection and
  then writes, so with parallel consumers two events could interleave. Scaling
  consumption would require a conditional write in the database itself.
- **`monolith-reference` has no automated tests.** It exists as a comparison
  baseline, not as code to evolve.
- **`.env` credentials reach the container in clear text.** Acceptable locally; a
  real deployment should pull them from a secrets manager.
- **The AWS figures come from an academic environment** with constrained
  instances. They are useful for comparing the two architectures against each
  other, not as an absolute capacity reference.
