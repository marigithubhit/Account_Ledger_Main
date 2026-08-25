package com.wiobank.account_ledger_core.domain;
import java.math.BigDecimal;

public class AuthState {
    private final String authId;
    private final BigDecimal amount;
    private final int day;
    private boolean active;

    public AuthState(String authId, BigDecimal amount, int day) {
        this.authId = authId;
        this.amount = amount;
        this.day = day;
        this.active = true;
    }

    public String getAuthId() { return authId; }
    public BigDecimal getAmount() { return amount; }
    public int getDay() { return day; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}