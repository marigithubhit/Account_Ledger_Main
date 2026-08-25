package com.wiobank.account_ledger_core.domain;
import java.math.BigDecimal;

public record Event(
        String id,
        int day,
        EventType eventType,
        String accountId,
        String currency,
        BigDecimal amount,
        int valueDate,
        String authId,
        String targetEventId
) {
    public Event(String id, int day, EventType eventType, String accountId, String currency, BigDecimal amount, int valueDate) {
        this(id, day, eventType, accountId, currency, amount, valueDate, null, null);
    }
}