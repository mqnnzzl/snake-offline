package com.mintarcade.snake;

/** Render from the last reached cell towards the next, without a one-step visual backlog. */
final class SnakeMotion {
    final float[] x = new float[SnakeGame.SIZE * SnakeGame.SIZE + 1];
    final float[] y = new float[x.length];
    int count;
    private boolean moving;

    void reset() { moving = false; }
    void start() { moving = true; }
    boolean isMoving() { return moving; }
    void sample(SnakeGame game, float fraction) {
        float t = moving && game.canAdvance() ? Math.max(0, Math.min(1, fraction)) : 0;
        SnakeGame.Cell head = game.body.get(0), tail = game.body.get(game.body.size() - 1);
        int dx = game.direction == SnakeGame.RIGHT ? 1 : game.direction == SnakeGame.LEFT ? -1 : 0;
        int dy = game.direction == SnakeGame.DOWN ? 1 : game.direction == SnakeGame.UP ? -1 : 0;
        x[0] = head.x + dx * t; y[0] = head.y + dy * t;
        boolean growing = game.food != null && head.x + dx == game.food.x && head.y + dy == game.food.y;
        // Preserve every interior corner; only the two endpoints move.
        int corners = game.body.size() - (growing ? 0 : 1);
        for (int i = 0; i < corners; i++) {
            x[i + 1] = game.body.get(i).x; y[i + 1] = game.body.get(i).y;
        }
        int end = game.body.size();
        if (!growing) {
            SnakeGame.Cell nextTail = game.body.get(Math.max(0, game.body.size() - 2));
            x[end] = tail.x + (nextTail.x - tail.x) * t;
            y[end] = tail.y + (nextTail.y - tail.y) * t;
        }
        count = end + 1;
    }
    SnakeGame.Cell visibleFood(SnakeGame game, float fraction) {
        return game.food;
    }
}
