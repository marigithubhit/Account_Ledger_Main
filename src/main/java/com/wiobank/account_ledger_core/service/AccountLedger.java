package com.wiobank.account_ledger_core.service;

import com.wiobank.account_ledger_core.domain.AuthState;
import com.wiobank.account_ledger_core.domain.Event;
import com.wiobank.account_ledger_core.domain.EventType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class AccountLedger {
    private final String accountId;
    private final String currency;
    private final int precision;

    // Append-only event store
    private final List<Event> events = new ArrayList<>();
    private final Map<String, AuthState> authorizations = new HashMap<>();
    private final Map<Integer, BigDecimal> dailyInterestAccruals = new HashMap<>();
    private final Map<Integer, BigDecimal> dailyOverdraftFees = new HashMap<>();

    public AccountLedger(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
        this.precision = currency.equals("BHD") ? 3 : 2;
        for (int i = 1; i <= 6; i++) {
            dailyInterestAccruals.put(i, BigDecimal.ZERO.setScale(precision, RoundingMode.HALF_UP));
            dailyOverdraftFees.put(i, BigDecimal.ZERO.setScale(precision, RoundingMode.HALF_UP));
        }
    }

    public BigDecimal quantize(BigDecimal val) {
        return val.setScale(precision, RoundingMode.HALF_UP);
    }

    public BigDecimal getLedgerBalanceAsOf(int valueDay) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Event ev : events) {
            if (ev.valueDate() <= valueDay) {
                if (ev.eventType() == EventType.CREDIT) {
                    balance = balance.add(ev.amount());
                } else if (ev.eventType() == EventType.DEBIT || ev.eventType() == EventType.SETTLEMENT) {
                    balance = balance.subtract(ev.amount());
                } else if (ev.eventType() == EventType.REVERSAL) {
                    Optional<Event> target = events.stream().filter(e -> e.id().equals(ev.targetEventId())).findFirst();
                    if (target.isPresent() && target.get().valueDate() <= valueDay) {
                        balance = balance.add(target.get().amount());
                    }
                }
            }
        }
        return quantize(balance);
    }

    public BigDecimal getActiveHolds() {
        return authorizations.values().stream()
                .filter(AuthState::isActive)
                .map(AuthState::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getAvailableBalance(int currentDay) {
        return getLedgerBalanceAsOf(currentDay).subtract(getActiveHolds());
    }

    public Optional<String> processEvent(Event event) {
        if (!event.currency().equals(this.currency)) {
            return Optional.of("Error: Currency mismatch " + event.currency() + " vs " + this.currency);
        }

        if (event.eventType() == EventType.AUTHORIZATION) {
            BigDecimal avail = getAvailableBalance(event.day());
            if (avail.subtract(event.amount()).compareTo(BigDecimal.ZERO) < 0) {
                return Optional.of("REJECTED: Insufficient available balance for " + event.id() + " (" + event.authId() + ")");
            }
            authorizations.put(event.authId(), new AuthState(event.authId(), event.amount(), event.day()));
            events.add(event);
            return Optional.empty();
        } else if (event.eventType() == EventType.SETTLEMENT) {
            if (event.authId() != null && authorizations.containsKey(event.authId())) {
                authorizations.get(event.authId()).setActive(false);
            }
            events.add(event);
            return Optional.empty();
        } else if (event.eventType() == EventType.CREDIT || event.eventType() == EventType.DEBIT || event.eventType() == EventType.REVERSAL) {
            events.add(event);
            return Optional.empty();
        }
        return Optional.of("Unknown Event Type");
    }

    public void auditEndOfDay(int day) {
        // 1. Daily Interest Accrual (0.04% per day on positive closing ledger balance)
        BigDecimal closingBal = getLedgerBalanceAsOf(day);
        if (closingBal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestRate = new BigDecimal("0.0004");
            BigDecimal dailyInterest = quantize(closingBal.multiply(interestRate));
            dailyInterestAccruals.put(day, dailyInterest);
        } else {
            dailyInterestAccruals.put(day, quantize(BigDecimal.ZERO));
        }

        // 2. Overdraft fee audit across all days up to current day (AED 25.00)
        BigDecimal feeAmount = quantize(new BigDecimal("25.00"));
        for (int d = 1; d <= day; d++) {
            BigDecimal balD = getLedgerBalanceAsOf(d);
            if (balD.compareTo(BigDecimal.ZERO) < 0) {
                dailyOverdraftFees.put(d, feeAmount);
            } else {
                dailyOverdraftFees.put(d, quantize(BigDecimal.ZERO));
            }
        }
    }

    public void capitalizeInterest(int day) {
        BigDecimal totalInterest = dailyInterestAccruals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalInterest.compareTo(BigDecimal.ZERO) > 0) {
            Event capEvent = new Event("INT_CAP", day, EventType.CREDIT, this.accountId, this.currency, totalInterest, day);
            events.add(capEvent);
        }
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getTotalFees() {
        return dailyOverdraftFees.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}