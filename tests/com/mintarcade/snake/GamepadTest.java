package com.mintarcade.snake;

public final class GamepadTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        GamepadSteering pad = new GamepadSteering();
        check(pad.move(.1f, -.1f, 0) == -1, "Idle stick drift ignored");
        check(pad.move(.6f, .02f, 0) == SnakeGame.RIGHT, "Left stick right");
        for (int i = 0; i < 100; i++) check(pad.move(.8f, .02f, 0) == -1, "Held stick never floods input queue");
        check(pad.move(.69f, -.7f, 0) == -1, "Diagonal jitter retains axis");
        check(pad.move(.3f, -.8f, 0) == SnakeGame.UP, "Direct stick corner without releasing");
        check(pad.move(.02f, .02f, 0) == -1, "Neutral resets held direction");
        check(pad.move(0, -1, 0) == SnakeGame.UP, "Same direction can be pressed again");
        pad.reset(); check(pad.move(0, 1, 0) == SnakeGame.DOWN, "Hat down");
        pad.reset(); check(pad.move(-1, 0, 0) == SnakeGame.LEFT, "Hat left");
        pad.reset(); check(pad.move(.3f, 0, .4f) == -1, "Respect device dead zone");
        check(pad.move(.5f, 0, .4f) == SnakeGame.RIGHT, "Outside device dead zone");
        pad.reset(); check(pad.move(.5f, 0, 0) == SnakeGame.RIGHT, "Reset on pause or device change");
        System.out.println("PASS: " + checks + " gamepad checks (dead zone, hat, corners, hysteresis, held input)");
    }
}
