package com.mintarcade.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;

/** Pure Java rules; deliberately independent of Android and rendering. */
public final class SnakeGame {
    public static final int SIZE = 20;
    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;
    public enum State { READY, RUNNING, PAUSED, OVER, WON }
    public static final class Cell {
        public final int x, y;
        public Cell(int x, int y) { this.x = x; this.y = y; }
    }
    final ArrayList<Cell> body = new ArrayList<>();
    final ArrayDeque<Integer> turns = new ArrayDeque<>();
    private final Random random;
    public State state;
    public Cell food;
    public int direction, score;
    public String endReason = "";

    public SnakeGame(Random random) { this.random = random; reset(); }
    public void reset() {
        body.clear();
        for (int x = 8; x >= 6; x--) body.add(new Cell(x, 10));
        direction = RIGHT;
        score = 0;
        turns.clear();
        endReason = "";
        state = State.READY;
        food = new Cell(14, 10);
    }
    public void start() { if (state == State.READY || state == State.PAUSED) state = State.RUNNING; }
    public void pause() { if (state == State.RUNNING) state = State.PAUSED; }
    public boolean turn(int next) {
        if (state != State.RUNNING || next < 0 || next > 3) return false;
        int previous = turns.isEmpty() ? direction : turns.peekLast();
        if (next == previous) return false;
        if (turns.size() == 1 && next == direction) {
            turns.clear(); return true;
        }
        if (turns.size() == 1 && (next + 2) % 4 == previous && (next + 2) % 4 != direction) {
            // Correct a pending turn before reaching the junction (e.g. right -> up -> down).
            turns.clear(); turns.addLast(next); return true;
        }
        if (turns.size() == 2) {
            int first = turns.peekFirst();
            if ((next + 2) % 4 == first) return false;
            turns.removeLast();
            if (next != first) turns.addLast(next);
            return true;
        }
        if ((next + 2) % 4 == previous) return false;
        turns.addLast(next); return true;
    }
    boolean applyTurn() {
        if (turns.isEmpty()) return false;
        direction = turns.removeFirst(); return true;
    }
    boolean canAdvance() {
        Cell head = body.get(0);
        int x = head.x + (direction == RIGHT ? 1 : direction == LEFT ? -1 : 0);
        int y = head.y + (direction == DOWN ? 1 : direction == UP ? -1 : 0);
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return false;
        boolean ate = food != null && x == food.x && y == food.y;
        int occupied = body.size() - (ate ? 0 : 1);
        for (int i = 0; i < occupied; i++) if (body.get(i).x == x && body.get(i).y == y) return false;
        return true;
    }
    /** Returns true only when a food was eaten. */
    public boolean tick() {
        if (state != State.RUNNING) return false;
        applyTurn();
        return moveForward();
    }
    // Commit the segment that has actually reached its destination, then choose the next turn.
    boolean moveForward() {
        if (state != State.RUNNING) return false;
        Cell head = body.get(0);
        int x = head.x + (direction == RIGHT ? 1 : direction == LEFT ? -1 : 0);
        int y = head.y + (direction == DOWN ? 1 : direction == UP ? -1 : 0);
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            endReason = "碰到边界了，下次转弯早一点";
            state = State.OVER;
            return false;
        }
        boolean ate = food != null && x == food.x && y == food.y;
        // The tail vacates its cell on a normal move, so moving into it is legal.
        int occupied = body.size() - (ate ? 0 : 1);
        for (int i = 0; i < occupied; i++) {
            Cell cell = body.get(i);
            if (cell.x == x && cell.y == y) {
                endReason = "碰到自己了，试试绕个大圈";
                state = State.OVER;
                return false;
            }
        }
        body.add(0, new Cell(x, y));
        if (ate) { score += 10; spawnFood(); }
        else body.remove(body.size() - 1);
        return ate;
    }
    void spawnFood() {
        int free = SIZE * SIZE - body.size();
        if (free == 0) {
            food = null;
            state = State.WON;
            endReason = "你填满了整块棋盘，太厉害了！";
            return;
        }
        boolean[] occupied = new boolean[SIZE * SIZE];
        for (Cell cell : body) occupied[cell.y * SIZE + cell.x] = true;
        int choice = random.nextInt(free);
        for (int i = 0; i < occupied.length; i++) {
            if (!occupied[i] && choice-- == 0) { food = new Cell(i % SIZE, i / SIZE); return; }
        }
    }
    public int[] save() {
        int[] data = new int[7 + body.size() * 2];
        data[0] = state.ordinal(); data[1] = direction; data[2] = score;
        data[3] = food == null ? -1 : food.x; data[4] = food == null ? -1 : food.y;
        data[5] = turns.isEmpty() ? -1 : turns.peekFirst();
        data[6] = turns.size() < 2 ? -1 : turns.peekLast();
        for (int i = 0; i < body.size(); i++) { data[7 + 2 * i] = body.get(i).x; data[8 + 2 * i] = body.get(i).y; }
        return data;
    }
    public void restore(int[] data) {
        if (data == null || data.length < 9 || data.length % 2 != 1) return;
        body.clear(); turns.clear();
        state = State.values()[data[0]];
        if (state == State.RUNNING) state = State.PAUSED;
        direction = data[1]; score = data[2];
        food = data[3] < 0 ? null : new Cell(data[3], data[4]);
        if (data[5] >= 0) turns.addLast(data[5]);
        if (data[6] >= 0) turns.addLast(data[6]);
        for (int i = 7; i < data.length; i += 2) body.add(new Cell(data[i], data[i + 1]));
        endReason = state == State.WON ? "你填满了整块棋盘，太厉害了！" : "每一次出发，都是新的挑战";
    }
}
