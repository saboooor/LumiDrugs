package com.drugs.addiction;

/**
 * Settings for the addiction system.
 */
public class AddictionSettings {

    private final boolean enabled;
    private final boolean milkClearsWithdrawal;
    private final int heartbeatTicks;

    public AddictionSettings() {
        this(true, false, 20);
    }

    public AddictionSettings(boolean enabled, boolean milkClearsWithdrawal, int heartbeatTicks) {
        this.enabled = enabled;
        this.milkClearsWithdrawal = milkClearsWithdrawal;
        this.heartbeatTicks = heartbeatTicks;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMilkClearsWithdrawal() {
        return milkClearsWithdrawal;
    }


    public int getHeartbeatTicks() {
        return heartbeatTicks;
    }
}
