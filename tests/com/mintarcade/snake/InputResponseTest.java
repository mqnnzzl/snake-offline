package com.mintarcade.snake;

import java.util.Random;

public final class InputResponseTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static SnakeGame fresh() { SnakeGame g = new SnakeGame(new Random(7)); g.start(); return g; }
    public static void main(String[] args) {
        SwipeTracker swipe = new SwipeTracker();
        swipe.begin(0, 0);
        check(swipe.move(1, 1, 3) == -1, "Contact jitter is ignored");
        check(swipe.move(3, 0, 3) == SnakeGame.RIGHT, "Three dp triggers a swipe");
        check(swipe.move(30, 0, 3) == -1, "Long swipes do not repeat commands");
        check(swipe.move(30, -3, 3) == SnakeGame.UP, "Small corner after a long swipe responds");
        check(swipe.move(27, -3, 3) == SnakeGame.LEFT, "Continuous second corner is preserved");
        swipe.begin(0, 0);
        check(swipe.move(3, 3, 3) == -1, "Ambiguous diagonal waits for a clear axis");
        check(swipe.move(3, 5, 3) == SnakeGame.DOWN, "Diagonal resolves on dominant axis");
        swipe.begin(100, 100);
        check(swipe.move(100, 97, 3) == SnakeGame.UP, "New gesture resets direction lock");

        for (int interval : new int[] {100, 150, 220}) {
            for (int offset : new int[] {1, 30, 90}) {
                FrameClock clock = new FrameClock();
                clock.advance(0, interval);
                long time = offset * 1_000_000L;
                clock.advance(time, interval);
                float before = clock.fraction(interval);
                clock.steer(true, interval);
                check(clock.fraction(interval) == before, "Input never teleports head");
                int ticks = 0;
                for (int ms = 1; ms <= 32; ms++) {
                    ticks += clock.advance(time + ms * 1_000_000L, interval);
                    if (ticks == 0) check(clock.fraction(interval) >= before, "Approach never moves backward");
                }
                check(ticks == 1, "Pending turn reaches exactly one junction within 32ms");
                check(clock.advance(time + 33_000_000L, interval) == 0, "After turn normal speed resumes");
            }
            FrameClock clock = new FrameClock(); clock.advance(0, interval);
            clock.advance(1_000_000L, interval); clock.steer(true, interval);
            clock.steer(false, interval);
            check(clock.advance(33_000_000L, interval) == 0, "Cancelled turn cancels acceleration");
            clock.steer(true, interval); clock.suspend();
            float paused = clock.fraction(interval);
            check(clock.advance(10_000_000_000L, interval) == 0 && clock.fraction(interval) == paused, "Pause preserves approach without catching up");
            check(clock.advance(10_032_000_000L, interval) == 1, "Pending turn resumes within budget");
        }
        SnakeGame g = fresh();
        check(!g.turn(SnakeGame.LEFT), "Direct reversal remains forbidden");
        check(g.turn(SnakeGame.UP), "First turn accepted");
        check(g.turn(SnakeGame.DOWN), "Correcting an unexecuted turn is accepted");
        g.applyTurn(); check(g.direction == SnakeGame.DOWN && g.turns.isEmpty(), "Latest valid correction wins");
        g = fresh(); g.turn(SnakeGame.UP); g.turn(SnakeGame.RIGHT);
        check(g.turns.isEmpty(), "Returning to current direction cancels pending turn");
        g.turn(SnakeGame.UP); g.turn(SnakeGame.LEFT);
        check(g.turns.size() == 2, "Two distinct corners may be buffered");
        g.applyTurn(); g.moveForward(); g.applyTurn();
        check(g.direction == SnakeGame.LEFT, "Rapid up-left input is preserved");
        g = fresh(); g.turn(SnakeGame.UP); g.turn(SnakeGame.LEFT); g.turn(SnakeGame.RIGHT);
        g.applyTurn(); g.moveForward(); g.applyTurn();
        check(g.direction == SnakeGame.RIGHT, "New valid second corner replaces stale second corner");
        check(g.turns.isEmpty(), "No old commands left in queue");

        // Render before the first grid tick: movement must not wait for a 100-220 ms interval.
        for (int interval : new int[] { 100, 150, 220 }) {
            g = fresh(); SnakeMotion motion = new SnakeMotion(); motion.start();
            motion.sample(g, 16f / interval);
            check(motion.x[0] > 8 && motion.x[0] < 9, "First display frame already moves");
            check(g.body.get(0).x == 8, "Logical cell stays at last reached junction");
            g.turn(SnakeGame.UP);
            g.moveForward(); g.applyTurn();
            motion.sample(g, 16f / interval);
            check(motion.x[0] == 9 && motion.y[0] < 10, "Queued turn is visible on first frame after junction");
            g.pause();
            check(!g.turn(SnakeGame.LEFT), "Pause ignores input");
        }
        g = fresh(); g.body.clear();
        g.body.add(new SnakeGame.Cell(19, 10)); g.body.add(new SnakeGame.Cell(18, 10)); g.body.add(new SnakeGame.Cell(17, 10));
        SnakeMotion motion = new SnakeMotion(); motion.start(); motion.sample(g, .9f);
        check(motion.x[0] == 19 && !g.canAdvance(), "Blocked preview never goes through the wall");
        check(g.state == SnakeGame.State.RUNNING, "Collision is not committed ahead of visible head");
        g.turn(SnakeGame.UP); g.applyTurn();
        check(g.canAdvance(), "A turn at the wall can still be accepted before collision");
        motion.sample(g, .1f); check(motion.x[0] == 19 && motion.y[0] < 10, "Wall turn starts from visible position");
        System.out.println("PASS: " + checks + " input checks (corrections, rapid turns, immediate animation, arrival timing, wall turn)");
    }
}
