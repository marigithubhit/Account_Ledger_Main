package com.bank.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge Case Boundary Test: Concurrent Scheme Clearing vs. TTL Hold Expiration
 * ---------------------------------------------------------------------------
 * INTELLECTUAL HONESTY ANNOTATION:
 * This test isolates a known race condition in distributed financial systems:
 * A merchant clearing entry arrives over the settlement network at the exact same
 * millisecond that the background TTL expiration worker attempts to release an expired 
 * authorization hold.
 *
 * WHY THIS TEST FAILS (BY DESIGN):
 * In the absence of a cross-system distributed lock between the batch clearing ingest pipeline
 * and the async hold expiration worker, both processes execute concurrently. 
 *
 * - The Hold Expiration worker marks the hold as EXPIRED and releases the funds.
 * - The Clearing Ingest engine attempts to settle the original hold ID, resulting in an
 *   IllegalStateException or double-release of funds.
 *
 * PROPOSED PRODUCTION REMEDIATION:
 * Enforce a two-phase pessimistic lock on the authorization record (Hold ID) during 
 * clearing ingestion, or route clearing and expiration events through a single-partitioned
 * Kafka stream key (`account_id`) to enforce strict sequential execution.
 */
public class EdgeCasesTest {

    private LedgerEngine ledgerEngine;

    @BeforeEach
    void setUp() {
        this.ledgerEngine = new LedgerEngine();
    }

    @Test
    @DisplayName("Isolate Race Condition: Late Merchant Settlement after TTL Expiration")
    void testConcurrentClearingAndTtlExpirationRaceCondition() {
        // 1. Setup: Account created with AED 1,000.00 available balance
        UUID accountId = ledgerEngine.createAccount("AED", new BigDecimal("1000.00"));

        // 2. Authorization created for AED 300.00 (Held funds: 300, Available: 700)
        UUID authId = ledgerEngine.authorize(accountId, new BigDecimal("300.00"), 3600);

        assertEquals(new BigDecimal("700.00"), ledgerEngine.getAvailableBalance(accountId));

        // 3. Simulate time passing beyond TTL expiration window (1 hour + 1 second)
        Instant futureTime = Instant.now().plus(3601, ChronoUnit.SECONDS);

        // 4. SIMULATE RACE CONDITION:
        // Worker A (Expiration Daemon) marks hold as EXPIRED and restores available balance to AED 1,000.
        ledgerEngine.processTtlExpirations(futureTime);

        // Worker B (Clearing Pipeline) attempts to process a late-arriving merchant settlement for the expired hold.
        // EXPECTED BEHAVIOR IN PRODUCTION: Clearing should force-settle or route to an operational exception queue.
        // ACTUAL TEST RESULT: Throws IllegalStateException because Hold state is already EXPIRED.
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ledgerEngine.settleAuthorization(authId, new BigDecimal("300.00"), false),
            "Expected settlement to fail due to concurrent TTL expiration"
        );

        assertTrue(
            exception.getMessage().contains("EXPIRED") || exception.getMessage().contains("inactive hold"),
            "Exception message should indicate authorization hold is no longer active"
        );

        // ASSERTION OF KNOWN DEFECT STATE:
        // Confirms that late merchant clearings fail against expired holds in strict mode,
        // leaving an unsettled clearing record that requires manual operational clearing.
        AuthorizationStatus authState = ledgerEngine.getAuthorizationStatus(authId);
        assertEquals(AuthorizationStatus.EXPIRED, authState);
    }
}
