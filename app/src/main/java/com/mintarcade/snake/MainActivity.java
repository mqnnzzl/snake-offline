package com.mintarcade.snake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Choreographer;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xFF0C1019, INK = 0xFF10151F, MUTED = 0xFF8996AC;
    private static final int GREEN = 0xFFD7FF3F, ORANGE = 0xFFFF5268;
    private static final int[] INTERVALS = { 220, 150, 100 };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameClock frameClock = new FrameClock();
    private final SnakeMotion motion = new SnakeMotion();
    private final FramePacer framePacer = new FramePacer();
    private Choreographer choreographer;
    private SnakeGame game;
    private SharedPreferences preferences;
    private BoardView board;
    private TextView scoreText, bestText, pauseButton;
    private TextView musicButton;
    private final GamepadSteering gamepad = new GamepadSteering();
    private int controllerId = -1;
    private android.hardware.input.InputManager inputManager;
    private final android.hardware.input.InputManager.InputDeviceListener controllerListener = new android.hardware.input.InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { }
        @Override public void onInputDeviceChanged(int id) { if (id == controllerId) gamepad.reset(); }
        @Override public void onInputDeviceRemoved(int id) {
            if (id == controllerId) { controllerId = -1; gamepad.reset(); pauseGame(); }
        }
    };
    private final TextView[] levels = new TextView[3];
    private int level, best, countdown, countdownSeconds;
    private int targetFps, paper, ink, muted, cardColor, padColor;
    private boolean english, dark, reopenSettings;
    private Dialog settingsDialog;
    private GameMusic music;
    private boolean musicEnabled;
    private boolean foreground, framesActive;
    private float renderFraction = 1;
    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!framesActive || !foreground || game.state != SnakeGame.State.RUNNING) return;
            int steps = frameClock.advance(frameTimeNanos, INTERVALS[level]);
            boolean scoreChanged = false;
            for (int i = 0; i < steps; i++) {
                if (game.moveForward()) {
                    if (game.score > best) { best = game.score; preferences.edit().putInt("best_" + level, best).apply(); }
                    board.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    scoreChanged = true;
                }
                if (game.state != SnakeGame.State.RUNNING) break;
                game.applyTurn();
                frameClock.steer(!game.turns.isEmpty(), INTERVALS[level]);
            }
            if (game.state != SnakeGame.State.RUNNING) {
                stopFrames(); motion.reset(); renderFraction = 1; updateUi();
                board.announceForAccessibility(tr("游戏结束，本局得分 ", "Game over. Score: ") + game.score);
                return;
            }
            renderFraction = frameClock.fraction(INTERVALS[level]);
            if (scoreChanged) updateScoreUi();
            if (framePacer.shouldDraw(frameTimeNanos, targetFps)) board.invalidate();
            choreographer.postFrameCallback(this);
        }
    };
    private final Runnable count = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            countdown--;
            if (countdown <= 0) {
                startRound();
            } else { updateScoreUi(); board.invalidate(); handler.postDelayed(this, 1000); }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Keep the compact phone controls upright. Tablets can rotate in either direction.
        if (getResources().getConfiguration().smallestScreenWidthDp < 600)
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        choreographer = Choreographer.getInstance();
        inputManager = (android.hardware.input.InputManager)getSystemService(android.content.Context.INPUT_SERVICE);
        preferences = getSharedPreferences("snake", MODE_PRIVATE);
        english = preferences.getBoolean("english", false);
        dark = preferences.getBoolean("dark", false);
        targetFps = preferences.getInt("fps", 60) == 120 ? 120 : 60;
        musicEnabled = preferences.getBoolean("music", true);
        countdownSeconds = Math.max(0, Math.min(3, preferences.getInt("countdownSeconds", 1)));
        music = new GameMusic(this);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        paper = dark ? Color.BLACK : PAPER;
        ink = 0xFFF0F3F8;
        muted = MUTED;
        cardColor = 0xFF171F2D;
        padColor = 0xFF222E40;
        level = Math.max(0, Math.min(2, preferences.getInt("level", 0)));
        game = new SnakeGame(new Random());
        if (saved != null) {
            level = saved.getInt("level", level);
            game.restore(saved.getIntArray("game"));
        }
        best = preferences.getInt("best_" + level, 0);
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        getWindow().getDecorView().setSystemUiVisibility(0);
        applyRefreshRate();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(paper);
        root.setPadding(dp(22), dp(10), dp(22), dp(8));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(22) + insets.getSystemWindowInsetLeft(), dp(10) + insets.getSystemWindowInsetTop(),
                dp(22) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);

        LinearLayout header = row();
        LinearLayout title = column();
        title.addView(label("SNAKE_", 30, GREEN, true));
        TextView sub = label(tr("贪吃蛇 / 像素街机", "PIXEL ARCADE / OFFLINE"), 10, muted, true);
        title.addView(sub);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        musicButton = button("", 11, this::toggleMusic);
        header.addView(musicButton, new LinearLayout.LayoutParams(dp(68), dp(48)));
        TextView settings = button("≡", 26, this::showSettings);
        settings.setContentDescription(tr("设置", "Settings"));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, full(60));

        LinearLayout scores = row();
        scoreText = scoreCard(scores, tr("得分 / SCORE", "SCORE"), GREEN, cardColor);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10), 1);
        scores.addView(new View(this), gap);
        bestText = scoreCard(scores, tr("纪录 / BEST", "HI-SCORE"), ink, cardColor);
        root.addView(scores, full(66));

        LinearLayout difficulty = row();
        TextView speed = label(tr("节奏", "Speed"), 12, muted, true);
        difficulty.addView(speed, new LinearLayout.LayoutParams(dp(42), -1));
        String[] names = { tr("悠闲", "Chill"), tr("经典", "Classic"), tr("极速", "Fast") };
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            levels[i] = button(names[i], 12, () -> {
                if (game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0) return;
                level = choice; preferences.edit().putInt("level", level).apply();
                best = preferences.getInt("best_" + level, 0); resetGame(); updateUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1);
            lp.setMargins(dp(4), 0, 0, 0);
            difficulty.addView(levels[i], lp);
        }
        root.addView(difficulty, full(46));

        LinearLayout boardLabel = row();
        TextView caption = label("[ PLAYFIELD ]", 10, GREEN, false);
        boardLabel.addView(caption, new LinearLayout.LayoutParams(0, -1, 1));
        boardLabel.addView(label("20 × 20", 10, muted, false));
        root.addView(boardLabel, full(24));
        board = new BoardView();
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = label(tr("滑动 / 方向键 / Xbox 手柄", "Swipe / Arrows / Xbox controller"), 11, muted, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, full(30));

        LinearLayout controls = row();
        LinearLayout pad = column();
        LinearLayout padTop = row();
        padTop.setGravity(Gravity.CENTER);
        padTop.addView(directionButton("↑", tr("向上", "Up"), SnakeGame.UP), new LinearLayout.LayoutParams(dp(52), dp(48)));
        pad.addView(padTop, full(48));
        LinearLayout padBottom = row();
        String[] arrows = { "←", "↓", "→" };
        String[] descriptions = { tr("向左", "Left"), tr("向下", "Down"), tr("向右", "Right") };
        int[] directions = { SnakeGame.LEFT, SnakeGame.DOWN, SnakeGame.RIGHT };
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(i == 0 ? 0 : dp(4), dp(4), 0, 0);
            padBottom.addView(directionButton(arrows[i], descriptions[i], directions[i]), lp);
        }
        pad.addView(padBottom, full(52));
        int padWidth = Math.min(dp(164), getResources().getDisplayMetrics().widthPixels - dp(174));
        controls.addView(pad, new LinearLayout.LayoutParams(Math.max(dp(120), padWidth), dp(100)));
        controls.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout actions = column();
        pauseButton = button(tr("暂停", "Pause"), 14, () -> { if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else if (game.state == SnakeGame.State.PAUSED) begin(); });
        actions.addView(pauseButton, full(48));
        TextView restart = button(tr("重新开始", "Restart"), 12, this::restart);
        restart.setBackground(ripple(cardColor, 14));
        LinearLayout.LayoutParams restartLp = full(48); restartLp.topMargin = dp(4);
        actions.addView(restart, restartLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(dp(116), dp(100));
        actionsLp.leftMargin = dp(14);
        controls.addView(actions, actionsLp);
        root.addView(controls, full(104));
        TextView help = button(tr("玩法说明  ·  无广告 / 无需联网", "How to play  ·  Offline / Ad-free"), 10, () -> { pauseGame(); new AlertDialog.Builder(this)
            .setTitle(tr("游戏规则", "HOW TO PLAY"))
            .setMessage(tr("滑动棋盘或按方向键改变方向。\n\n每颗果子加 10 分并增长一格，撞到边界或自己时结束；不能直接掉头。转向会在最近的格点执行。\n\n悠闲、经典、极速各自保存最高分。右上角设置可切换语言、背景和 60/120 FPS。\n\nXbox 手柄：十字键/左摇杆转向，A 开始或继续，B 暂停，Menu 开始/暂停，X 重开，Y 音乐开关，View 设置。\n\n切到后台自动暂停，也支持方向键 / WASD，空格暂停。\n\n完全离线，无广告，无账号。", "Swipe the board or press an arrow to steer.\n\nEach fruit adds 10 points and grows your snake. Hitting a wall or your body ends the run. No direct U-turns. Turns happen at the next grid junction.\n\nChill, Classic and Fast keep separate best scores. Open Settings for language, background and 60/120 FPS.\n\nXbox: D-pad/left stick steers. A starts/resumes; B pauses; Menu starts/pauses; X restarts; Y toggles music; View opens Settings.\n\nThe game pauses in the background. Arrow keys / WASD steer; Space pauses.\n\nOffline. No ads. No account."))
            .setPositiveButton(tr("知道啦", "Got it"), null).show(); });
        help.setTextColor(muted);
        root.addView(help, full(32));
        android.content.res.Configuration screen = getResources().getConfiguration();
        if (screen.screenWidthDp >= 600 && screen.screenWidthDp > screen.screenHeightDp) {
            // A square playfield on the left, with a separate control rail on 16:10 tablets.
            root.removeAllViews(); root.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout playfield = column(), sidebar = column();
            playfield.addView(header, full(60));
            playfield.addView(boardLabel, full(24));
            playfield.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
            playfield.addView(hint, full(28));
            root.addView(playfield, new LinearLayout.LayoutParams(0, -1, 1));
            int railWidth = Math.min(300, Math.max(264, screen.screenWidthDp / 3));
            LinearLayout.LayoutParams rail = new LinearLayout.LayoutParams(dp(railWidth), -1);
            rail.leftMargin = dp(24);
            root.addView(sidebar, rail);
            sidebar.addView(label(tr("控制台 / CONTROL", "CONTROL PANEL"), 12, muted, true), full(40));
            sidebar.addView(scores, full(72));
            sidebar.addView(difficulty, full(46));
            sidebar.addView(label(tr("手柄  A 开始 · B 暂停\nX 重开 · Y 音乐 · View 设置", "PAD  A Start / B Pause\nX Retry / Y Music / View Setup"), 10, muted, false), full(42));
            sidebar.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            controls.removeAllViews(); controls.setOrientation(LinearLayout.VERTICAL);
            controls.setGravity(Gravity.CENTER_HORIZONTAL);
            controls.addView(pad, new LinearLayout.LayoutParams(dp(184), dp(100)));
            controls.addView(new View(this), full(16));
            controls.addView(actions, new LinearLayout.LayoutParams(-1, dp(100)));
            sidebar.addView(controls, full(220));
            sidebar.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            sidebar.addView(help, full(40));
        } else {
            // Equal-width sides keep the D-pad centered on the phone's play area.
            controls.removeAllViews(); actions.removeAllViews();
            controls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(72), 1));
            LinearLayout.LayoutParams centeredPad = new LinearLayout.LayoutParams(dp(156), dp(100));
            centeredPad.setMargins(dp(8), 0, dp(8), 0);
            controls.addView(pad, centeredPad);
            restart.setText(tr("重开", "Retry"));
            controls.addView(restart, new LinearLayout.LayoutParams(0, dp(72), 1));
        }
        updateUi();
        if (saved != null && saved.getBoolean("settingsOpen", false)) handler.post(this::showSettings);
    }

    private TextView scoreCard(LinearLayout parent, String title, int color, int background) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(7), dp(12), dp(5)); card.setBackground(shape(background, 0));
        TextView name = label(title, 9, muted, false);
        card.addView(name, full(16));
        TextView value = label("0000", 26, color, true); card.addView(value);
        parent.addView(card, new LinearLayout.LayoutParams(0, -1, 1)); return value;
    }
    private void updateScoreUi() {
        scoreText.setText(String.format(Locale.ROOT, "%04d", game.score));
        bestText.setText(String.format(Locale.ROOT, "%04d", best));
        board.setContentDescription(tr("贪吃蛇棋盘，本局 ", "Snake board. Score: ") + game.score + (game.state == SnakeGame.State.RUNNING ? tr(" 分，游戏中", ". Playing") : tr(" 分，点击开始或继续", ". Tap to start or resume")));
        if (countdown > 0) board.setContentDescription(tr("倒计时 ", "Starting in ") + countdown + tr(" 秒", " seconds"));
    }
    private void updateUi() {
        updateScoreUi();
        boolean active = game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0;
        for (int i = 0; i < 3; i++) {
            levels[i].setBackground(ripple(i == level ? GREEN : cardColor, 0));
            levels[i].setTextColor(i == level ? INK : muted);
            levels[i].setEnabled(!active); levels[i].setAlpha(active && i != level ? .45f : 1f);
            levels[i].setContentDescription(new String[] { tr("悠闲", "Chill"), tr("经典", "Classic"), tr("极速", "Fast") }[i] + (i == level ? tr("，已选择", ", selected") : ""));
        }
        pauseButton.setText(game.state == SnakeGame.State.PAUSED && countdown == 0 ? tr("继续", "Resume") : tr("暂停", "Pause"));
        pauseButton.setEnabled(active); pauseButton.setAlpha(active ? 1f : .4f);
        pauseButton.setTextColor(INK); pauseButton.setBackground(ripple(GREEN, 0));
        if (game.state == SnakeGame.State.RUNNING || countdown > 0) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        board.invalidate();
        syncMusic();
    }
    private void syncMusic() {
        if (musicButton != null) {
            musicButton.setText(musicEnabled ? "♪ ON" : "♪ OFF");
            musicButton.setTextColor(musicEnabled ? GREEN : muted);
            musicButton.setContentDescription(musicEnabled ? tr("关闭音乐", "Mute music") : tr("开启音乐", "Enable music"));
        }
        if (music != null) music.setActive(musicEnabled && foreground &&
            (game.state == SnakeGame.State.RUNNING || countdown > 0));
    }
    private void toggleMusic() {
        musicEnabled = !musicEnabled;
        preferences.edit().putBoolean("music", musicEnabled).apply();
        syncMusic();
    }
    private void startFrames() {
        if (framesActive || !foreground) return;
        frameClock.suspend(); framePacer.reset(); framesActive = true; choreographer.postFrameCallback(frame);
    }
    private void stopFrames() {
        framesActive = false; choreographer.removeFrameCallback(frame); frameClock.suspend();
    }
    private void resetGame() {
        stopFrames(); frameClock.reset(); motion.reset(); renderFraction = 1; game.reset();
    }
    private void requestDirection(int direction) {
        if (countdown > 0 || !game.turn(direction)) return;
        // At a cell center (including a blocked next cell), turn immediately without a jump.
        if (motion.isMoving() && (frameClock.fraction(INTERVALS[level]) == 0 || !game.canAdvance())) {
            if (game.applyTurn()) { frameClock.reset(); renderFraction = 0; }
        }
        frameClock.steer(!game.turns.isEmpty(), INTERVALS[level]);
        board.invalidate();
    }
    private void begin() {
        if (countdown > 0 || game.state == SnakeGame.State.RUNNING) return;
        if (game.state == SnakeGame.State.OVER || game.state == SnakeGame.State.WON) resetGame();
        countdown = countdownSeconds; handler.removeCallbacks(count); stopFrames();
        if (countdown == 0) startRound();
        else { updateUi(); handler.postDelayed(count, 1000); }
    }
    private void startRound() {
        countdown = 0; game.start();
        if (!motion.isMoving()) { game.applyTurn(); motion.start(); renderFraction = 0; }
        updateUi(); startFrames();
    }
    private void pauseGame() {
        gamepad.reset();
        stopFrames(); handler.removeCallbacks(count);
        if (countdown > 0 && game.state == SnakeGame.State.READY) game.start();
        countdown = 0; game.pause(); updateUi();
    }
    private void restart() {
        if (game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0) {
            pauseGame();
            new AlertDialog.Builder(this).setTitle(tr("重新开始这一局？", "Start a new run?"))
                .setMessage(tr("当前进度会结束，最高纪录会保留。", "This run will end. Your best scores will be kept."))
                .setNegativeButton(tr("保留这局", "Keep this run"), null).setPositiveButton(tr("重新开始", "Restart"), (dialog, which) -> { resetGame(); updateUi(); }).show();
        } else { resetGame(); updateUi(); }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; inputManager.registerInputDeviceListener(controllerListener, handler); applyRefreshRate(); syncMusic(); }
    @Override protected void onPause() { foreground = false; inputManager.unregisterInputDeviceListener(controllerListener); pauseGame(); super.onPause(); }
    @Override protected void onDestroy() { stopFrames(); handler.removeCallbacksAndMessages(null); if (music != null) music.release(); if (settingsDialog != null) settingsDialog.dismiss(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        pauseGame(); out.putInt("level", level); out.putIntArray("game", game.save());
        out.putBoolean("settingsOpen", reopenSettings || (settingsDialog != null && settingsDialog.isShowing()));
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else super.onBackPressed();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int direction = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) direction = SnakeGame.UP;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) direction = SnakeGame.RIGHT;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_S) direction = SnakeGame.DOWN;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A) direction = SnakeGame.LEFT;
        boolean press = event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0;
        if (press && event.isFromSource(InputDevice.SOURCE_GAMEPAD)) controllerId = event.getDeviceId();
        if (direction >= 0) { if (press) requestDirection(direction); return true; }
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (press) { if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else begin(); }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (press && game.state != SnakeGame.State.RUNNING) begin(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { if (press) pauseGame(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_X) { if (press) restart(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) { if (press) toggleMusic(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) { if (press) showSettings(); return true; }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (game.state != SnakeGame.State.RUNNING || countdown > 0) { gamepad.reset(); return true; }
            if (controllerId != event.getDeviceId()) { controllerId = event.getDeviceId(); gamepad.reset(); }
            for (int h = 0; h < event.getHistorySize(); h++) steerController(event, h);
            steerController(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
    private float axis(MotionEvent event, int axis, int history) {
        return history < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, history);
    }
    private void steerController(MotionEvent event, int history) {
        float x = axis(event, MotionEvent.AXIS_HAT_X, history), y = axis(event, MotionEvent.AXIS_HAT_Y, history);
        float flat = 0;
        if (Math.max(Math.abs(x), Math.abs(y)) < .5f) {
            x = axis(event, MotionEvent.AXIS_X, history); y = axis(event, MotionEvent.AXIS_Y, history);
            InputDevice device = event.getDevice();
            if (device != null) {
                InputDevice.MotionRange rx = device.getMotionRange(MotionEvent.AXIS_X, event.getSource());
                InputDevice.MotionRange ry = device.getMotionRange(MotionEvent.AXIS_Y, event.getSource());
                flat = Math.max(rx == null ? 0 : rx.getFlat(), ry == null ? 0 : ry.getFlat());
            }
        }
        int direction = gamepad.move(x, y, flat);
        if (direction >= 0) requestDirection(direction);
    }
    private String tr(String chinese, String translated) { return english ? translated : chinese; }
    private String endReasonText() {
        if (!english) return game.endReason;
        if (game.state == SnakeGame.State.WON) return "You filled the entire board!";
        if (game.endReason.contains("边界")) return "Hit the wall. Turn a little earlier.";
        if (game.endReason.contains("自己")) return "Hit your tail. Try a wider turn.";
        return "Every run is a fresh start.";
    }
    private void applyRefreshRate() { applyRefreshRate(getWindow()); }
    private void applyRefreshRate(android.view.Window window) {
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) return;
        Display.Mode current = display.getMode(), selected = null;
        for (Display.Mode candidate : display.getSupportedModes()) {
            if (candidate.getPhysicalWidth() != current.getPhysicalWidth() || candidate.getPhysicalHeight() != current.getPhysicalHeight()) continue;
            if (candidate.getRefreshRate() <= targetFps + .5f && (selected == null || candidate.getRefreshRate() > selected.getRefreshRate())) selected = candidate;
        }
        if (selected == null) selected = current;
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.preferredDisplayModeId = selected.getModeId();
        attributes.preferredRefreshRate = selected.getRefreshRate();
        window.setAttributes(attributes);
    }
    private interface ChoiceAction { void choose(int index); }
    private void settingGroup(LinearLayout content, String title, String[] options, int selected, ChoiceAction action) {
        TextView name = label(title, 13, muted, true);
        LinearLayout.LayoutParams nameLp = full(36); nameLp.topMargin = dp(10);
        content.addView(name, nameLp);
        LinearLayout choices = row();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView choice = button(options[i], 14, () -> { if (index != selected) action.choose(index); });
            choice.setBackground(ripple(i == selected ? GREEN : cardColor, 12));
            choice.setTextColor(i == selected ? INK : ink);
            choice.setSelected(i == selected);
            choice.setContentDescription(options[i] + (i == selected ? tr("，已选择", ", selected") : ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) lp.leftMargin = dp(8);
            choices.addView(choice, lp);
        }
        content.addView(choices, full(48));
    }
    private void changeSetting(String key, int option) {
        SharedPreferences.Editor edit = preferences.edit();
        if (key.equals("fps")) edit.putInt(key, option == 0 ? 60 : 120);
        else if (key.equals("countdownSeconds")) edit.putInt(key, option);
        else edit.putBoolean(key, option == 1);
        edit.apply();
        reopenSettings = true;
        if (settingsDialog != null) settingsDialog.dismiss();
        recreate();
    }
    private void showSettings() {
        if (isFinishing() || (settingsDialog != null && settingsDialog.isShowing())) return;
        pauseGame();
        reopenSettings = false;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        settingsDialog = dialog;
        LinearLayout content = column();
        content.setPadding(dp(22), dp(22), dp(22), dp(22));
        content.setBackgroundColor(paper);
        content.addView(label(tr("设置 / SETUP", "SETUP"), 26, GREEN, true), full(38));
        content.addView(label("[ SNAKE_ / SYSTEM ]", 12, muted, false), full(26));
        settingGroup(content, tr("语言 / Language", "Language / 语言"), new String[] { "中文", "English" }, english ? 1 : 0, choice -> changeSetting("english", choice));
        settingGroup(content, tr("背景颜色", "Background"), new String[] { tr("午夜蓝", "Midnight"), tr("纯黑", "Black") }, dark ? 1 : 0, choice -> changeSetting("dark", choice));
        settingGroup(content, tr("刷新率 / 帧率", "Refresh / frame rate"), new String[] { "60 Hz", "120 Hz" }, targetFps == 120 ? 1 : 0, choice -> changeSetting("fps", choice));
        settingGroup(content, tr("音乐 · 霓虹果园", "Music · Neon Orchard"), new String[] { tr("关闭", "Off"), tr("开启", "On") }, musicEnabled ? 1 : 0, choice -> changeSetting("music", choice));
        settingGroup(content, tr("开始 / 继续倒计时", "Start / resume countdown"), new String[] { tr("关闭", "Off"), tr("1秒", "1s"), tr("2秒", "2s"), tr("3秒", "3s") }, countdownSeconds, choice -> changeSetting("countdownSeconds", choice));
        TextView refreshInfo = label("", 12, muted, false);
        refreshInfo.setPadding(0, dp(12), 0, dp(8));
        content.addView(refreshInfo, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh = () -> {
            Display display = getWindowManager().getDefaultDisplay();
            int hz = display == null ? 60 : Math.round(display.getRefreshRate());
            refreshInfo.setText(tr("当前屏幕：", "Current display: ") + hz + " Hz\n" +
                tr("120 FPS 需要高刷屏幕支持；实际帧率受设备和系统设置影响。", "120 FPS needs a compatible display. Actual FPS depends on your device and system settings."));
        };
        refresh.run();
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException impossible) { version = "—"; }
        TextView versionLabel = label(tr("版本 ", "Version ") + version + "  /  SNAKE_", 12, muted, false);
        content.addView(versionLabel, full(38));
        TextView done = button(tr("完成", "Done"), 15, dialog::dismiss);
        done.setTextColor(INK); done.setBackground(ripple(GREEN, 14));
        content.addView(done, full(48));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false); scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(ignored -> { if (settingsDialog == dialog) settingsDialog = null; });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(shape(paper, 20));
            dialog.getWindow().setLayout(Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
            applyRefreshRate(dialog.getWindow());
        }
        handler.postDelayed(refresh, 600);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout.LayoutParams full(int height) { return new LinearLayout.LayoutParams(-1, dp(height)); }
    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false); v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL); return v;
    }
    private TextView button(String text, float size, Runnable action) {
        TextView v = label(text, size, ink, true); v.setGravity(Gravity.CENTER); v.setFocusable(true);
        v.setBackground(ripple(paper, 12)); v.setOnClickListener(w -> action.run()); return v;
    }
    private TextView directionButton(String arrow, String description, int direction) {
        TextView v = button(arrow, 25, () -> requestDirection(direction));
        v.setOnTouchListener((control, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    control.requestUnbufferedDispatch(event);
                    control.setPressed(true);
                    control.performClick(); // Submit on contact, not when the finger is lifted.
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    control.setPressed(false); return true;
                case MotionEvent.ACTION_MOVE:
                    control.setPressed(event.getX() >= 0 && event.getY() >= 0 && event.getX() < control.getWidth() && event.getY() < control.getHeight());
                    return true;
                default: return true;
            }
        });
        v.setContentDescription(description); v.setBackground(ripple(padColor, 13)); return v;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(0); d.setStroke(dp(1), color == GREEN ? GREEN : 0xFF354359); return d; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(0x558996AC), shape(color, 0), null); }

    private final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path snakePath = new Path();
        private final Typeface regularFont = Typeface.MONOSPACE;
        private final Typeface mediumFont = Typeface.create("monospace", Typeface.BOLD);
        private final RectF bounds = new RectF(), action = new RectF(), headBounds = new RectF();
        private final float[] gridLines = new float[(SnakeGame.SIZE + 1) * 8];
        private float side, cell, ox, oy;
        private float startX, startY;
        private final SwipeTracker swipeTracker = new SwipeTracker();
        private boolean gesture, swiped;
        private int pointerId = -1;
        BoardView() { super(MainActivity.this); setFocusable(true); setClickable(true); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            side = Math.min(w, h);
            float left = (w - side) / 2f, top = (h - side) / 2f;
            bounds.set(left, top, left + side, top + side);
            float inset = side * .035f;
            cell = (side - 2 * inset) / SnakeGame.SIZE; ox = left + inset; oy = top + inset;
            for (int i = 0; i <= SnakeGame.SIZE; i++) {
                int j = i * 8;
                gridLines[j] = ox + i * cell; gridLines[j + 1] = oy;
                gridLines[j + 2] = gridLines[j]; gridLines[j + 3] = oy + SnakeGame.SIZE * cell;
                gridLines[j + 4] = ox; gridLines[j + 5] = oy + i * cell;
                gridLines[j + 6] = ox + SnakeGame.SIZE * cell; gridLines[j + 7] = gridLines[j + 5];
            }
        }
        private void rounded(Canvas c, RectF r, float radius, int color) {
            paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawRect(r, paint);
        }
        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(bold ? mediumFont : regularFont); c.drawText(value, x, y, paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setAntiAlias(false);
            rounded(canvas, bounds, 0, dark ? 0xFF050709 : 0xFF080D16);
            paint.setColor(0xFF34435B); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
            canvas.drawRect(bounds.left + dp(1), bounds.top + dp(1), bounds.right - dp(1), bounds.bottom - dp(1), paint);
            paint.setColor(0xFF152030); paint.setStrokeWidth(1); paint.setStrokeCap(Paint.Cap.BUTT);
            canvas.drawLines(gridLines, paint);
            motion.sample(game, renderFraction);
            snakePath.rewind();
            float hx = ox + (motion.x[0] + .5f) * cell, hy = oy + (motion.y[0] + .5f) * cell;
            snakePath.moveTo(hx, hy);
            for (int i = 1; i < motion.count; i++) snakePath.lineTo(ox + (motion.x[i] + .5f) * cell, oy + (motion.y[i] + .5f) * cell);
            paint.setColor(0xFF8BA62C); paint.setStrokeWidth(cell * .64f); paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setStrokeJoin(Paint.Join.MITER); paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(snakePath, paint);
            paint.setStrokeWidth(cell * .38f); paint.setColor(GREEN); canvas.drawPath(snakePath, paint);
            headBounds.set(hx - cell * .45f, hy - cell * .45f, hx + cell * .45f, hy + cell * .45f);
            rounded(canvas, headBounds, cell * .25f, GREEN);
            paint.setColor(INK);
            int dx = game.direction == SnakeGame.RIGHT ? 1 : game.direction == SnakeGame.LEFT ? -1 : 0;
            int dy = game.direction == SnakeGame.DOWN ? 1 : game.direction == SnakeGame.UP ? -1 : 0;
            for (int sign = -1; sign <= 1; sign += 2) {
                float ex = hx + cell * (.18f * dx + sign * .21f * dy), ey = hy + cell * (.18f * dy + sign * .21f * dx);
                canvas.drawRect(ex - cell * .09f, ey - cell * .09f, ex + cell * .09f, ey + cell * .09f, paint);
            }
            SnakeGame.Cell visibleFood = motion.visibleFood(game, renderFraction);
            if (visibleFood != null) {
                float fx = ox + (visibleFood.x + .5f) * cell, fy = oy + (visibleFood.y + .5f) * cell;
                paint.setColor(ORANGE);
                canvas.drawRect(fx - cell * .3f, fy - cell * .22f, fx + cell * .3f, fy + cell * .32f, paint);
                canvas.drawRect(fx - cell * .4f, fy - cell * .1f, fx + cell * .4f, fy + cell * .17f, paint);
                paint.setColor(0xFFFFB0BA); canvas.drawRect(fx - cell * .22f, fy - cell * .14f, fx - cell * .07f, fy + cell * .02f, paint);
                paint.setColor(GREEN); canvas.drawRect(fx, fy - cell * .43f, fx + cell * .25f, fy - cell * .27f, paint);
            }
            paint.setAntiAlias(true);
            action.setEmpty();
            if (game.state != SnakeGame.State.RUNNING || countdown > 0) {
                rounded(canvas, bounds, 0, 0xD0080D16);
                float cx = bounds.centerX(), cy = bounds.centerY(), unit = side / 340f;
                if (countdown > 0) {
                    text(canvas, String.valueOf(countdown), cx, cy + 14 * unit, 64 * unit, GREEN, true);
                    text(canvas, tr("准备出发", "Get ready"), cx, cy + 49 * unit, 13 * unit, 0xFFD5E3D8, false);
                    return;
                }
                boolean ready = game.state == SnakeGame.State.READY, paused = game.state == SnakeGame.State.PAUSED;
                String title = ready ? tr("准备开局", "READY TO PLAY") : paused ? tr("已暂停", "PAUSED") : game.state == SnakeGame.State.WON ? tr("全盘通关", "BOARD CLEARED") : tr("游戏结束", "GAME OVER");
                String subtitle = ready ? tr("滑动转向 / 吃果子 / 刷新纪录", "SWIPE. EAT. BEAT YOUR BEST.") : paused ? tr("点击继续返回游戏", "PRESS RESUME TO CONTINUE") : endReasonText();
                text(canvas, ready ? "[ INSERT SKILL ]" : paused ? "[ STANDBY ]" : "[ RUN COMPLETE ]", cx, cy - 67 * unit, 11 * unit, GREEN, true);
                text(canvas, title, cx, cy - 29 * unit, 25 * unit, Color.WHITE, true);
                text(canvas, subtitle, cx, cy - 3 * unit, 12 * unit, 0xFFC4D6C9, false);
                if (!ready && !paused) text(canvas, english ? "Score " + game.score + "  ·  Best " + best : "本局 " + game.score + " 分  ·  最佳 " + best + " 分", cx, cy + 24 * unit, 13 * unit, GREEN, true);
                float buttonTop = cy + (ready || paused ? 24 : 43) * unit;
                action.set(cx - 88 * unit, buttonTop, cx + 88 * unit, buttonTop + 46 * unit);
                rounded(canvas, action, 15 * unit, GREEN);
                text(canvas, ready ? tr("开始游戏  →", "START  >") : paused ? tr("继续游戏  →", "RESUME  >") : tr("再来一局  →", "RETRY  >"), cx, buttonTop + 29 * unit, 15 * unit, INK, true);
            }
        }
        @Override public boolean performClick() { super.performClick(); begin(); return true; }
        private void swipe(float x, float y) {
            if (!gesture || game.state != SnakeGame.State.RUNNING) return;
            int direction = swipeTracker.move(x, y, dp(3));
            if (direction >= 0) {
                requestDirection(direction);
                swiped = true;
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int pointer = event.findPointerIndex(pointerId);
            float x = event.getX(pointer >= 0 ? pointer : 0), y = event.getY(pointer >= 0 ? pointer : 0);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gesture = bounds.contains(x, y); swiped = false; startX = x; startY = y;
                    swipeTracker.begin(x, y);
                    pointerId = event.getPointerId(0);
                    if (gesture) requestUnbufferedDispatch(event);
                    return gesture;
                case MotionEvent.ACTION_MOVE:
                    if (pointer >= 0) {
                        for (int h = 0; h < event.getHistorySize(); h++)
                            swipe(event.getHistoricalX(pointer, h), event.getHistoricalY(pointer, h));
                        swipe(x, y);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    swipe(x, y);
                    if (gesture && !swiped && action.contains(x, y) && action.contains(startX, startY)) performClick();
                    gesture = false; pointerId = -1; return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(event.getActionIndex()) == pointerId) { gesture = false; pointerId = -1; }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    gesture = false; pointerId = -1; return true;
                default: return true;
            }
        }
    }
}
