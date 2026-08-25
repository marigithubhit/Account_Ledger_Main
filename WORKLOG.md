# Engineering Worklog & Architectural Progression

Chronological record of design iterations, technical discoveries, and strategic pivots made during development.

---

## Epoch 1: Core Domain & Double-Entry Invariants
* **Implemented:** Immutability core layer, double-entry account transaction rules ($\sum \text{Debits} = \sum \text{Credits}$).
* **Discovery:** Scanning historical journal entries on every authorization check resulted in $O(N)$ execution times, causing integration test timeouts when historical records exceeded 50,000 rows.
* **Pivot:** Introduced explicit separation between `Ledger Balance` (book balance) and `Available Balance` (cleared funds) stored as materialized account states.

---

## Epoch 2: Value-Dating & Regulatory Guardrails
* **Implemented:** Value-dated posting logic and CBUAE compliance rules.
* **Discovery:** Future-dated credit entries were inadvertently inflating customer available balances during debit checks in unit testing.
* **Pivot:** Hard-gated all debit checks at the database layer using a strict constraint: `Available Balance = Cleared Credits (Value Date <= Current Timestamp) - Active Holds`.

---

## Epoch 3: Scalability Stress Testing & Snapshot Strategy
* **Implemented:** High-concurrency benchmark harness simulating 1,000+ parallel threads.
* **Discovery:** High contention on omnibus account rows caused database deadlock failures under concurrent `SELECT ... FOR UPDATE` locks.
* **Pivot:** Decoupled continuous balance recalculation in favor of **Deterministic Epoch Checkpointing (Rollup Snapshots)**, archiving old entry partitions to defer index bloat.

---

## Epoch 4: Edge Case Isolation
* **Implemented:** Authorization expiration daemons and partial-settlement release routines.
* **Discovery:** Identified an unhandled race condition where a late-arriving clearing file settles an authorization *after* the scheme TTL daemon triggered an auto-expiration.
* **Action:** Isolated this behavior into an explicit annotated failing test (`tests/edge_cases_test.py`) to highlight the operational boundary.
