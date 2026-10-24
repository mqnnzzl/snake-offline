package com.mintarcade.snake;

import java.util.Arrays;
import java.util.Random;

/** Run with Java alone: no test framework or Android device required. */
public final class SnakeGameTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static SnakeGame fresh() { SnakeGame g = new SnakeGame(new Random(42)); g.start(); return g; }
    private static void body(SnakeGame g, int... xy) {
        g.body.clear(); g.turns.clear();
        for (int i = 0; i < xy.length; i += 2) g.body.add(new SnakeGame.Cell(xy[i], xy[i + 1]));
    }
    public static void main(String[] args) {
        SnakeGame g = new SnakeGame(new Random(1));
        int[] before = g.save(); g.tick();
        check(Arrays.equals(before, g.save()), "Ready board must not move");
        g.start(); g.tick(); check(g.body.get(0).x == 9 && g.body.size() == 3, "Three-cell opening and forward movement");
        g.turn(SnakeGame.LEFT); g.tick(); check(g.direction == SnakeGame.RIGHT, "Reject direct reversal");
        g.turn(SnakeGame.UP); g.turn(SnakeGame.LEFT); g.turn(SnakeGame.DOWN);
        g.tick(); check(g.direction == SnakeGame.UP, "Buffered turn one");
        g.tick(); check(g.direction == SnakeGame.LEFT, "Buffered turn two");
        g.tick(); check(g.direction == SnakeGame.LEFT, "Buffer cannot exceed two");

        g = fresh(); for (int i = 0; i < 6; i++) g.tick();
        check(g.score == 10 && g.body.size() == 4, "Eating grows snake and scores ten");
        for (SnakeGame.Cell c : g.body) check(c.x != g.food.x || c.y != g.food.y, "Food never overlaps snake");
        g.pause(); before = g.save(); g.turn(SnakeGame.UP); g.tick();
        check(Arrays.equals(before, g.save()), "Pause stops movement and input");
        g.start(); g.tick(); check(g.state == SnakeGame.State.RUNNING, "Resume works");

        g = fresh(); body(g, 19, 3, 18, 3, 17, 3); g.tick();
        check(g.state == SnakeGame.State.OVER && g.body.get(0).x == 19, "Wall collision keeps last valid position");
        g = fresh(); body(g, 2, 2, 2, 3, 3, 3, 3, 2, 4, 2); g.tick();
        check(g.state == SnakeGame.State.OVER, "Body collision ends game");
        g = fresh(); body(g, 2, 2, 2, 3, 3, 3, 3, 2); g.tick();
        check(g.state == SnakeGame.State.RUNNING && g.body.get(0).x == 3, "Moving into vacating tail is valid");
        g = fresh(); g.turn(SnakeGame.UP); g.turn(SnakeGame.LEFT);
        SnakeGame restored = new SnakeGame(new Random(1)); restored.restore(g.save());
        check(restored.state == SnakeGame.State.PAUSED, "Restored active game must be paused");
        check(restored.body.size() == g.body.size() && restored.food.x == g.food.x, "Restore board and food");
        restored.start(); restored.tick(); restored.tick();
        check(restored.direction == SnakeGame.LEFT, "Restore queued input");

        g = fresh(); g.body.clear();
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) if (!(x == 19 && y == 19)) g.body.add(new SnakeGame.Cell(x, y));
        for (int i = 0; i < 100; i++) { g.spawnFood(); check(g.food.x == 19 && g.food.y == 19, "Last free cell chosen without retries"); }
        g.body.clear(); g.body.add(new SnakeGame.Cell(18, 19));
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) if (!(y == 19 && (x == 18 || x == 19))) g.body.add(new SnakeGame.Cell(x, y));
        g.food = new SnakeGame.Cell(19, 19); g.tick();
        check(g.state == SnakeGame.State.WON && g.food == null && g.body.size() == 400, "Full board wins without hanging");
        g.reset(); check(g.state == SnakeGame.State.READY && g.score == 0 && g.body.size() == 3, "Restart clears previous game");

        // Thousands of random input sequences exercise geometry and state invariants.
        Random inputs = new Random(2026);
        for (int round = 0; round < 150; round++) {
            g = fresh();
            for (int i = 0; i < 300 && g.state == SnakeGame.State.RUNNING; i++) {
                if (inputs.nextBoolean()) g.turn(inputs.nextInt(4));
                g.tick();
                boolean[] seen = new boolean[400];
                for (SnakeGame.Cell c : g.body) {
                    check(c.x >= 0 && c.x < 20 && c.y >= 0 && c.y < 20, "Snake within board");
                    int index = c.y * 20 + c.x;
                    check(!seen[index], "Live body has no overlapping cells"); seen[index] = true;
                }
                check(g.body.size() == 3 + g.score / 10, "Score and growth agree");
                check(g.food == null || !seen[g.food.y * 20 + g.food.x], "Food stays in free cell");
            }
        }
        System.out.println("PASS: " + checks + " checks (movement, input queue, growth, collisions, pause, restore, win, randomized games)");
    }
}
