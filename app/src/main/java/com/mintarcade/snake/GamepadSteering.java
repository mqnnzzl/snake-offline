package com.mintarcade.snake;

/** Four-way stick/hat input with a dead zone, diagonal hysteresis and no held repeats. */
final class GamepadSteering {
    private int held = -1;
    void reset() { held = -1; }
    int move(float x, float y, float deviceFlat) {
        float threshold = Math.max(.22f, deviceFlat);
        float ax = Math.abs(x), ay = Math.abs(y);
        if (Math.max(ax, ay) < threshold * .65f) { held = -1; return -1; }
        if (Math.max(ax, ay) < threshold) return -1;
        // Keep the current axis near a diagonal until the other axis clearly dominates.
        boolean horizontal = ax > ay;
        if (held >= 0 && Math.max(ax, ay) < Math.min(ax, ay) * 1.2f)
            horizontal = held == SnakeGame.LEFT || held == SnakeGame.RIGHT;
        int next = horizontal ? (x > 0 ? SnakeGame.RIGHT : SnakeGame.LEFT)
                              : (y > 0 ? SnakeGame.DOWN : SnakeGame.UP);
        if (next == held) return -1;
        held = next;
        return next;
    }
}
