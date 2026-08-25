# Architectural Decisions & Rejected Alternatives

This document outlines the design alternatives considered during the ledger implementation, detailing the technical failure modes and trade-offs that led to their rejection.

---

## 1. Distributed Two-Phase Commit (2PC) for Cross-Service State Changes
* **Rejected Alternative:** Enforcing strict, synchronous two-phase commits across distributed microservices (e.g., core ledger, payment gateway, notification engine).
* **Why Rejected:** 2PC requires blocking locks across distributed network boundaries. In high-concurrency environments, network partitions or transient latency spikes cause tail-latency degradation and system-wide lock starvation.
* **Chosen Approach:** Single-database atomic transactions combined with a **Transactional Outbox Pattern** for asynchronous, event-driven downstream propagation.
* **Failure Mode Prevented:** Distributed deadlock scenarios and throughput drop below core operational SLAs.

---

## 2. Dynamic In-Place Historical Re-balancing for Backdated Entries
* **Rejected Alternative:** Inserting value-dated or backdated transactions into historical sequence logs and dynamically recalculating all subsequent account balances in real time.
* **Why Rejected:** Modifying historical entry states breaks append-only immutability guarantees and introduces severe race conditions when concurrent reads or writes target the same balance window.
* **Chosen Approach:** Strict append-only log immutability. All historical adjustments or value-dated reconciliations are posted as **new adjusting entries on the current wall-clock date** while maintaining distinct `value_date` metadata.
* **Failure Mode Prevented:** Audit trail corruption, non-deterministic historical state reads, and continuous dynamic balance calculation overhead ($O(N)$ execution cost).

---

## 3. Real-Time In-Line Cross-Currency (Auto-FX) Conversions
* **Rejected Alternative:** Executing live currency conversion within the ledger write pipeline during posting.
* **Why Rejected:** Coupling core ledger state writes to external foreign exchange rate feeds introduces third-party network dependency, non-deterministic conversion rates mid-transaction, and potential rounding balance errors across trial balances.
* **Chosen Approach:** Single-currency enforcement at the account layer. Foreign exchange conversion and position risk accounting are delegated to an upstream FX engine before submitting single-currency balance movement entries to the ledger.
* **Failure Mode Prevented:** Unhedged FX position leakage and ledger imbalance (debits $\neq$ credits) caused by rate fluctuations mid-execution.

---

## 4. In-Memory Real-Time Balance Aggregation (Scan-on-Read)
* **Rejected Alternative:** Computing current available balances dynamically by scanning and summing all historical journal entries for an account on every read request.
* **Why Rejected:** Balance calculation query times grow linearly ($O(N)$) with account transaction volume. High-velocity omnibus accounts quickly become unqueryable within low-latency payment pipelines.
* **Chosen Approach:** Dual-balance materialization (**Ledger Balance** vs. **Available Balance**) coupled with **Deterministic Rollup Checkpointing (Snapshots)** at epoch boundaries.
* **Failure Mode Prevented:** Query timeouts, high memory pressure, and database page-split contention under high read/write volume.
