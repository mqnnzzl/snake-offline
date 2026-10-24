package com.mintarcade.snake;

/** Small, direction-locked gestures. Coordinates and threshold use the same units. */
final class SwipeTracker {
    private float anchorX, anchorY;
    private int direction = -1;
    void begin(float x, float y) { anchorX = x; anchorY = y; direction = -1; }
    int move(float x, float y, float threshold) {
        float dx = x - anchorX, dy = y - anchorY;
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (Math.max(ax, ay) < threshold) return -1;
        // Avoid choosing an arbitrary axis for near-diagonal contact noise.
        if (Math.max(ax, ay) < Math.min(ax, ay) * 1.2f) return -1;
        int next = ax > ay ? (dx > 0 ? SnakeGame.RIGHT : SnakeGame.LEFT)
                          : (dy > 0 ? SnakeGame.DOWN : SnakeGame.UP);
        anchorX = x; anchorY = y;
        if (direction == next) return -1;
        direction = next;
        return next;
    }
}
