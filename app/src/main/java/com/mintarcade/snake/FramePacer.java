package com.mintarcade.snake;

/** Vsync-aligned rendering cap; it never invents frames beyond the display's refresh rate. */
final class FramePacer {
    private long nextFrame = -1;
    void reset() { nextFrame = -1; }
    boolean shouldDraw(long now, int fps) {
        long period = 1_000_000_000L / fps;
        if (nextFrame < 0) { nextFrame = now + period; return true; }
        long adjusted = now + 500_000L;
        if (adjusted < nextFrame) return false;
        nextFrame += ((adjusted - nextFrame) / period + 1) * period;
        return true;
    }
}
