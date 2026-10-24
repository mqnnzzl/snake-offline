package com.mintarcade.snake;

/** Fixed game time steps independent of display refresh rate. All times are monotonic. */
final class FrameClock {
    private static final long MAX_DELTA_NS = 250_000_000L;
    private long lastFrame = -1, accumulated;
    private long turnBudget = -1;

    void reset() { accumulated = 0; turnBudget = -1; suspend(); }
    // Finish the current segment smoothly within 32 ms after input. Never teleport
    // the head or commit food/collisions before reaching the next junction.
    void steer(boolean pending, int intervalMillis) {
        if (!pending) { turnBudget = -1; return; }
        long remaining = intervalMillis * 1_000_000L - accumulated;
        long budget = Math.min(32_000_000L, remaining);
        turnBudget = turnBudget < 0 ? budget : Math.min(turnBudget, budget);
    }
    // Keep fractional progress when pausing, but never count time spent in the background.
    void suspend() { lastFrame = -1; }
    int advance(long frameTimeNanos, int intervalMillis) {
        if (lastFrame < 0) { lastFrame = frameTimeNanos; return 0; }
        long elapsed = Math.max(0, Math.min(MAX_DELTA_NS, frameTimeNanos - lastFrame));
        lastFrame = frameTimeNanos;
        long step = intervalMillis * 1_000_000L;
        if (turnBudget > 0) {
            long used = Math.min(elapsed, turnBudget);
            accumulated += (step - accumulated) * used / turnBudget;
            turnBudget -= used;
            elapsed -= used;
            if (turnBudget == 0) { accumulated = step; turnBudget = -1; }
        }
        accumulated += elapsed;
        int steps = (int) (accumulated / step);
        accumulated %= step;
        return steps;
    }
    float fraction(int intervalMillis) { return (float) accumulated / (intervalMillis * 1_000_000L); }
}
