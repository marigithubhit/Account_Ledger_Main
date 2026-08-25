package com.wiobank.account_ledger_core.runner;

import com.wiobank.account_ledger_core.domain.Event;
import com.wiobank.account_ledger_core.domain.EventType;
import com.wiobank.account_ledger_core.service.AccountLedger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class LedgerRunner implements CommandLineRunner {

    record DayEvent(int day, Event event) {}

    @Override
    public void run(String... args) {
        AccountLedger acc1 = new AccountLedger("ACC-001", "AED");
        AccountLedger acc2 = new AccountLedger("ACC-002", "BHD");

        // Largest Remainder Rule split for E10 (BHD 10.000)
        BigDecimal inst1 = new BigDecimal("3.334");
        BigDecimal inst2 = new BigDecimal("3.333");
        BigDecimal inst3 = new BigDecimal("3.333");

        List<DayEvent> eventStream = List.of(
                // Day 1
                new DayEvent(1, new Event("E1", 1, EventType.CREDIT, "ACC-001", "AED", new BigDecimal("1200.00"), 1)),
                new DayEvent(1, new Event("E2", 1, EventType.DEBIT, "ACC-001", "AED", new BigDecimal("950.00"), 1)),
                // Day 2
                new DayEvent(2, new Event("E3", 2, EventType.AUTHORIZATION, "ACC-001", "AED", new BigDecimal("200.00"), 2, "Auth-A", null)),
                // Day 3
                new DayEvent(3, new Event("E4", 3, EventType.CREDIT, "ACC-001", "AED", new BigDecimal("400.00"), 3)),
                // Day 4
                new DayEvent(4, new Event("E5", 4, EventType.SETTLEMENT, "ACC-001", "AED", new BigDecimal("185.00"), 4, "Auth-A", null)),
                new DayEvent(4, new Event("E6", 4, EventType.SETTLEMENT, "ACC-001", "AED", new BigDecimal("180.00"), 4, "Auth-Z", null)),
                // Day 5
                new DayEvent(5, new Event("E7", 5, EventType.DEBIT, "ACC-001", "AED", new BigDecimal("620.00"), 2)),
                new DayEvent(5, new Event("E8", 5, EventType.AUTHORIZATION, "ACC-001", "AED", new BigDecimal("90.00"), 5, "Auth-B", null)),
                new DayEvent(5, new Event("E10_1", 5, EventType.CREDIT, "ACC-002", "BHD", inst1, 5)),
                new DayEvent(5, new Event("E10_2", 5, EventType.CREDIT, "ACC-002", "BHD", inst2, 5)),
                new DayEvent(5, new Event("E10_3", 5, EventType.CREDIT, "ACC-002", "BHD", inst3, 5)),
                // Day 6
                new DayEvent(6, new Event("E9", 6, EventType.REVERSAL, "ACC-001", "AED", new BigDecimal("620.00"), 2, null, "E7"))
        );

        System.out.println("======================================================================");
        System.out.println("                WIO BANK LEDGER ENGINE SIMULATION (JAVA)              ");
        System.out.println("======================================================================\n");

        for (int day = 1; day <= 6; day++) {
            System.out.println("--- DAY " + day + " PROCESSING ---");
            final int currentDay = day;
            List<DayEvent> dayEvents = eventStream.stream().filter(de -> de.day() == currentDay).toList();

            for (DayEvent de : dayEvents) {
                Event ev = de.event();
                AccountLedger targetAcc = ev.accountId().equals("ACC-001") ? acc1 : acc2;
                Optional<String> err = targetAcc.processEvent(ev);
                if (err.isPresent()) {
                    System.out.println("[EVENT REJECTED] " + ev.id() + ": " + err.get());
                } else {
                    System.out.println("[EVENT PROCESSED] " + ev.id() + ": " + ev.eventType() + " " + ev.amount() + " " + ev.currency());
                }
            }

            acc1.auditEndOfDay(day);
            acc2.auditEndOfDay(day);

            if (day == 6) {
                acc1.capitalizeInterest(6);
                acc2.capitalizeInterest(6);
            }

            System.out.println("ACC-001 Closing Ledger Balance (Day " + day + "): AED " + acc1.getLedgerBalanceAsOf(day));
            System.out.println("ACC-001 Fee Assessment State: AED " + acc1.getTotalFees());
            System.out.println("ACC-001 Active Auth Holds: AED " + acc1.getActiveHolds());
            System.out.println("ACC-002 Closing Ledger Balance (Day " + day + "): BHD " + acc2.getLedgerBalanceAsOf(day) + "\n");
        }
    }
}