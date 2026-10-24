package com.mintarcade.snake;

import java.util.Random;

public final class RenderingTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .0001f, message); }
    private static void orthogonal(SnakeMotion motion) {
        for (int i = 1; i < motion.count; i++) check(motion.x[i] == motion.x[i - 1] || motion.y[i] == motion.y[i - 1], "No diagonal shortcut at corners");
    }
    private static float length(SnakeMotion motion) {
        float length = 0;
        for (int i = 1; i < motion.count; i++) length += Math.abs(motion.x[i] - motion.x[i - 1]) + Math.abs(motion.y[i] - motion.y[i - 1]);
        return length;
    }
    public static void main(String[] args) {
        // A minute of display time must advance exactly the same game time at every refresh rate.
        for (int hz : new int[] { 30, 60, 90, 120, 144 }) {
            for (int interval : new int[] { 100, 150, 220 }) {
                FrameClock clock = new FrameClock();
                long base = 5_000_000_000L; int ticks = clock.advance(base, interval);
                for (int i = 1; i <= hz * 60; i++) ticks += clock.advance(base + Math.round(i * 1_000_000_000.0 / hz), interval);
                check(ticks == 60000 / interval, "Display rate must not change gameplay speed");
                near(clock.fraction(interval), (60000 % interval) / (float) interval, "No accumulated timing drift");
            }
        }
        FrameClock clock = new FrameClock();
        clock.advance(0, 100); clock.advance(53_000_000, 100); clock.suspend();
        check(clock.advance(20_000_000_000L, 100) == 0, "Background time must not catch up");
        near(clock.fraction(100), .53f, "Pause preserves partial movement");
        check(clock.advance(20_020_000_000L, 100) == 0, "Resume continues same step");
        near(clock.fraction(100), .73f, "Resume keeps animation continuous");
        clock.reset(); clock.advance(0, 100);
        check(clock.advance(5_000_000_000L, 100) == 2, "Long stall has bounded catch-up");
        near(clock.fraction(100), .5f, "Clamped stall remainder");
        clock.reset(); near(clock.fraction(100), 0, "Restart clears old time");

        for (int displayHz : new int[] { 60, 90, 120, 144 }) {
            for (int cap : new int[] { 60, 120 }) {
                FramePacer pacer = new FramePacer(); int drawn = 0;
                for (int i = 0; i < displayHz; i++) if (pacer.shouldDraw(Math.round(i * 1_000_000_000.0 / displayHz), cap)) drawn++;
                check(Math.abs(drawn - Math.min(displayHz, cap)) <= 1, "Rendering obeys selected cap and physical refresh rate");
            }
        }
        FramePacer pacer = new FramePacer();
        check(pacer.shouldDraw(0, 60), "First frame is never delayed");
        check(!pacer.shouldDraw(8_333_333, 60), "60 FPS skips alternate 120 Hz vsync");
        check(pacer.shouldDraw(16_666_667, 60), "60 FPS renders next eligible vsync");
        pacer.reset(); check(pacer.shouldDraw(20_000_000, 120), "Changing cap takes effect immediately");

        SnakeGame game = new SnakeGame(new Random(42)); game.start();
        SnakeMotion motion = new SnakeMotion();
        motion.sample(game, 0); near(motion.x[0], 8, "Initial board uses current head");
        motion.start();
        motion.sample(game, .5f); near(motion.x[0], 8.5f, "Head is interpolated between cells");
        near(length(motion), 2, "Normal move keeps body length constant"); orthogonal(motion);
        motion.sample(game, 1); near(motion.x[0], 9, "Animation reaches logical destination");
        game.moveForward(); motion.sample(game, 0); near(motion.x[0], 9, "Commit preserves visual position");
        for (int turn : new int[] { SnakeGame.UP, SnakeGame.LEFT, SnakeGame.UP }) {
            game.turn(turn); game.applyTurn();
            check(game.state == SnakeGame.State.RUNNING, "Corner fixture stays alive");
            for (float fraction : new float[] { 0, .25f, .5f, .75f, 1 }) {
                motion.sample(game, fraction); orthogonal(motion);
                near(length(motion), 2, "Turning preserves rendered body length");
            }
            game.moveForward();
        }
        game.reset(); game.start();
        for (int i = 0; i < 5; i++) game.tick();
        SnakeGame.Cell fruit = game.food;
        motion.sample(game, .5f); near(motion.x[0], 13.5f, "Growth head interpolation");
        near(length(motion), 2.5f, "Growing tail stays in place"); orthogonal(motion);
        check(motion.visibleFood(game, .5f) == fruit, "Fruit stays visible until head reaches it");
        check(game.score == 0, "Fruit is not scored before visual arrival");
        check(game.moveForward(), "Growth fixture eats fruit on arrival");
        check(motion.visibleFood(game, 1) == game.food, "New fruit appears after movement completes");
        motion.reset(); motion.sample(game, .5f); near(motion.x[0], 14, "Reset discards stale interpolation");

        // A full board must fit in the fixed rendering buffers, including the moving tail point.
        game.body.clear();
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) game.body.add(new SnakeGame.Cell(y % 2 == 0 ? x : 19 - x, y));
        motion.reset(); motion.sample(game, 1);
        check(motion.count == 401, "Maximum body length is renderable"); orthogonal(motion);
        System.out.println("PASS: " + checks + " rendering checks (30-144 Hz timing, pause, stalls, interpolation, corners, growth, full board)");
    }
}
