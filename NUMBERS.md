# Quantitative Sizing & Performance Limits

This document establishes the sizing models, throughput limits, and capacity calculations derived from the ledger design.

---

## 1. Scale & Target Performance Metrics

| Metric | Target Baseline | 100× Peak Scale | Structural Bottleneck Boundary |
| :--- | :--- | :--- | :--- |
| **Transaction Throughput** | 100 TPS | 10,000 TPS | Database write lock serialization on account balance updates |
| **P99 Read Latency** | < 15 ms | < 50 ms | B-Tree index cache misses forcing disk IOPS page faults |
| **P99 Write Latency** | < 25 ms | < 100 ms | WAL / Commit log append contention |
| **Daily Active Entries** | 8.64 M entries/day | 864 M entries/day | Secondary index memory consumption limits |

---

## 2. Storage & Memory Footprint Calculations

* **Single Journal Entry Record Payload:**
  * `entry_id` (UUIDv4): 16 bytes
  * `account_id` (UUIDv4): 16 bytes
  * `amount` (DECIMAL(18,4)): 16 bytes
  * `currency` (CHAR(3)): 3 bytes
  * `direction` (ENUM: DEBIT/CREDIT): 1 byte
  * `value_date` (TIMESTAMP WITH TIMEZONE): 8 bytes
  * `created_at` (TIMESTAMP WITH TIMEZONE): 8 bytes
  * `metadata_hash` (BYTEA / SHA-256): 32 bytes
  * **Base Row Overhead:** ~120 bytes (excluding database engine page header overheads, estimated ~200 bytes total per row).

* **Daily Data Volume Scaling:**
  * **Baseline (100 TPS):** $8.64 \text{ M entries/day} \times 200 \text{ bytes} \approx \mathbf{1.72 \text{ GB/day}}$
  * **100× Scale (10,000 TPS):** $864 \text{ M entries/day} \times 200 \text{ bytes} \approx \mathbf{172.8 \text{ GB/day}}$

* **Snapshot Checkpointing Footprint Reduction:**
  * Daily Balance Snapshots for 1,000,000 active accounts: $1,000,000 \times 64 \text{ bytes} \approx \mathbf{64 \text{ MB/day}}$.
  * **Query Cost Reduction:** Balance evaluation scans decrease from scanning up to $O(N)$ daily history rows to querying 1 snapshot row + $\Delta$ entries since epoch start ($O(k)$, where $k \ll N$).

---

## 3. Memory & Index Working Set Limits

* **Secondary Idempotency Index (72-Hour Window):**
  * Baseline (100 TPS): $25.92 \text{ M keys} \times 64 \text{ bytes} \approx \mathbf{1.65 \text{ GB RAM}}$ (Fits comfortably in-memory).
  * 100× Scale (10,000 TPS): $2.59 \text{ B keys} \times 64 \text{ bytes} \approx \mathbf{165.7 \text{ GB RAM}}$ (Exceeds single-node cache; requires index partitioning or Redis cluster key routing).
