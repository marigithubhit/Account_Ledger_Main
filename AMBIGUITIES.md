# Scope Assumptions & Domain Ambiguities

This document records explicit assumptions made to resolve unstated business, technical, and regulatory requirements in the problem specification.

---

## 1. Network Scheme Time-to-Live (TTL) Defaults
* **Ambiguity:** The problem statement does not specify standard expiration windows for pending authorization holds across transaction types.
* **Assumption Made:** Standard scheme operational defaults are enforced:
  * POS / Point-of-Sale authorizations expire after **7 calendar days**.
  * Card-Not-Present (e-commerce) authorizations expire after **5 calendar days**.
  * Pre-authorization holds (e.g., hotel reservations, car rentals) expire after **30 calendar days**.
* **Impact:** An automated background daemon runs periodically to execute auto-expirations based on these boundaries.

---

## 2. Handling Mismatched Value-Dated Entries vs. Clearing Cutoffs
* **Ambiguity:** Clarification was missing on how to handle credit entries where `value_date` falls on a weekend or public holiday relative to Central Bank clearing cutoffs.
* **Assumption Made:** The ledger system operates on UTC wall-clock posting time, but enforces business-day validation rules based on UAE Gulf Standard Time (GST / UTC+4). Future-dated credits posted over non-clearing days remain in **Ledger Balance** and are gated from **Available Balance** until the next active CBUAE business day start-of-day (08:00 GST).
* **Impact:** Prevents intraday credit exposure during clearing system downtime (e.g., UAEFT settlement windows).

---

## 3. Microsecond Order Resolution & Idempotency Scope
* **Ambiguity:** Resolution for duplicate request detection under concurrent execution was unstated.
* **Assumption Made:** Idempotency keys are scoped per `(account_id, idempotency_key)` tuple with a strict rolling retention window of **72 hours**. Requests presenting the exact same key within this window receive the cached transaction result without re-executing state mutations.
* **Impact:** Prevents duplicate debits during network retries while bounding the growth of the active idempotency index.

---

## 4. Single-Currency Ledger Granularity
* **Ambiguity:** Whether multi-currency sub-accounts share a unified ledger account ID was unspecified.
* **Assumption Made:** Each account entity is strictly bound to an ISO 4217 currency code (e.g., `AED`, `USD`). Multi-currency wallets are modeled as distinct account entities linked at the customer entity layer.
* **Impact:** Ensures mathematical balance integrity ($\sum \text{Debits} = \sum \text{Credits}$) within single-currency ledger boundary checks.
