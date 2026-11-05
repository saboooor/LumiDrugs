package com.drugs.addiction;

/** Remaining time until addiction falls below the threshold, independent of potion refreshes. */
final class WithdrawalTiming {
    private WithdrawalTiming() {}

    static int remainingTicks(AddictionState state, AddictionDrugProfile profile, long now) {
        double points = state.getPoints();
        double threshold = profile.getAddictedAtPoints();
        if (!profile.isAddictive() || points < threshold || now < state.getWithdrawalBlockedUntilMillis()) return 0;
        double rate = profile.getDecayPointsPerMinute();
        // Zero is the minimum stored addiction, so a non-positive threshold cannot
        // recover by decay either. -1 is Minecraft's real infinite duration.
        if (!profile.isDecayEnabled() || !Double.isFinite(rate) || rate <= 0 || threshold <= 0
                || !Double.isFinite(points) || !Double.isFinite(threshold)) return -1;
        long elapsed = state.getLastDecayMillis() > 0 ? Math.max(0, now - state.getLastDecayMillis()) : 0;
        double remainingPoints = points - elapsed / 60_000.0 * rate - threshold;
        if (remainingPoints < 0) return 0;
        double ticks = Math.ceil(remainingPoints / rate * 1200.0);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, ticks));
    }
}
