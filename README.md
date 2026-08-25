# Financial Ledger Core Engine

High-performance, append-only double-entry core banking ledger implementation designed for strict regulatory compliance, low-latency transaction processing, and deterministic auditability.

---

## Technical Overview & Invariants

This system implements an immutable core accounting ledger adhering to core financial principles:

1. **Double-Entry Invariant:** Every financial transaction consists of balanced entries where $\sum \text{Debits} = \sum \text{Credits}$.
2. **Append-Only Immutability:** Posted ledger entries are immutable. Corrections, adjustments, and value-dated reconciliations are handled exclusively through net-new reversing/adjusting journal entries.
3. **Dual-Balance Isolation:** Maintains distinct separation between:
   * **Ledger Balance (Book Balance):** Absolute sum of all posted journal entries.
   * **Available Balance (Cleared Balance):** Cleared funds available for immediate execution ($Value\ Date \le Current\ Time - Active\ Holds$).
4. **Deterministic Checkpointing:** Employs epoch balance snapshots combined with incremental delta logs to maintain $O(k)$ query performance, bypassing linear $O(N)$ historical scans.

---

## Architectural Decision Records & Artifacts

Per production architecture defense requirements, deep technical decisions, quantitative sizing models, and scope trade-offs are documented in dedicated artifacts:

* **[`NUMBERS.md`](./NUMBERS.md):** Quantitative sizing, throughput target models (10,000 TPS scale), database write-amplification boundaries, and memory footprint calculations.
* **[`REJECTED.md`](./REJECTED.md):** Alternatives considered and rejected (e.g., Distributed 2PC, dynamic historical re-balancing, in-line auto-FX) along with explicit technical failure modes.
* **[`AMBIGUITIES.md`](./AMBIGUITIES.md):** Explicit resolution of unstated domain requirements, scheme authorization TTL assumptions, and UAE business-day clearing cutoffs.
* **[`WORKLOG.md`](./WORKLOG.md):** Chronological build log detailing architectural iterations, design discoveries, and pivots.
* **[`EdgeCasesTest.java`](./src/test/java/com/bank/ledger/EdgeCasesTest.java):** Annotated failing test isolating the known boundary condition between late merchant clearing and scheme TTL expirations.

---

## System Architecture

```text
[ Incoming Payment / Gateway ]
              │
              ▼
    ┌──────────────────┐
    │  Dual-Balance    │ <── Evaluates against Available Balance
    │  Guard Logic     │
    └─────────┬────────┘
              │
              ▼
    ┌──────────────────┐      ┌─────────────────────────┐
    │ Core Ledger      ├─────►│ Materialized Accounts   │
    │ Write Engine     │      │ (Ledger vs. Available)  │
    └─────────┬────────┘      └─────────────────────────┘
              │
              ▼
    ┌──────────────────┐      ┌─────────────────────────┐
    │ Append-Only Log  ├─────►│ Epoch Rollup Checkpoints│
    │ (Journal Entries)│      │ (Daily Balance State)   │
    └──────────────────┘      └─────────────────────────┘
