package com.wiobank.account_ledger_core;

import com.wiobank.account_ledger_core.domain.Event;
import com.wiobank.account_ledger_core.domain.EventType;
import com.wiobank.account_ledger_core.service.AccountLedger;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FailingLedgerTest {

    @Test
    void testE8AuthorizationShouldPassIfE7WasIgnored() {
        /*
         * FAILING TEST ANNOTATION FOR LIVE DEFENSE:
         * This test intentionally fails under our core banking design.
         * It demonstrates what happens if an engineer incorrectly assumes E8 (Auth-B AED 90.00)
         * should succeed by ignoring the backdated entry E7.
         *
         * Real-time available balance enforcement mandates that because E7 (AED 620 debit)
         * is processed prior to E8 on Day 5, the available balance drops to -AED 360.00,
         * forcing E8 to be REJECTED.
         */
        AccountLedger acc = new AccountLedger("ACC-001", "AED");

        // Day 1 to Day 4 setup
        acc.processEvent(new Event("E1", 1, EventType.CREDIT, "ACC-001", "AED", new BigDecimal("1200.00"), 1));
        acc.processEvent(new Event("E2", 1, EventType.DEBIT, "ACC-001", "AED", new BigDecimal("950.00"), 1));
        acc.processEvent(new Event("E4", 3, EventType.CREDIT, "ACC-001", "AED", new BigDecimal("400.00"), 3));
        acc.processEvent(new Event("E5", 4, EventType.SETTLEMENT, "ACC-001", "AED", new BigDecimal("185.00"), 4, "Auth-A", null));
        acc.processEvent(new Event("E6", 4, EventType.SETTLEMENT, "ACC-001", "AED", new BigDecimal("180.00"), 4, "Auth-Z", null));

        // Process E7 (Backdated Debit AED 620.00)
        acc.processEvent(new Event("E7", 5, EventType.DEBIT, "ACC-001", "AED", new BigDecimal("620.00"), 2));

        // Process E8 Authorization
        Optional<String> result = acc.processEvent(new Event("E8", 5, EventType.AUTHORIZATION, "ACC-001", "AED", new BigDecimal("90.00"), 5, "Auth-B", null));

        // Intentionally assertion expecting approval (isEmpty), which FAILS because E8 is REJECTED
        assertTrue(result.isEmpty(), "Expected E8 to be approved, but it was rejected due to backdated E7!");
    }
}