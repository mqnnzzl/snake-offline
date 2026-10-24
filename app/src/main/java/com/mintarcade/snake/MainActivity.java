package com.mintarcade.snake;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int PAPER = 0xFFF5F4EE, INK = 0xFF193F35, MUTED = 0xFF7A877D;
    private static final int GREEN = 0xFFBFE88A, ORANGE = 0xFFF6A36A;
    private static final int[] INTERVALS = { 220, 150, 100 };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SnakeGame game;
    private SharedPreferences preferences;
    private BoardView board;
    private TextView scoreText, bestText, pauseButton;
    private final TextView[] levels = new TextView[3];
    private int level, best, countdown;
    private boolean foreground;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!foreground || game.state != SnakeGame.State.RUNNING) return;
            boolean ate = game.tick();
            if (ate) {
                if (game.score > best) { best = game.score; preferences.edit().putInt("best_" + level, best).apply(); }
                board.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            updateUi();
            if (game.state == SnakeGame.State.RUNNING) handler.postDelayed(this, INTERVALS[level]);
            else board.announceForAccessibility("游戏结束，本局得分 " + game.score);
        }
    };
    private final Runnable count = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            countdown--;
            if (countdown <= 0) {
                game.start(); updateUi(); handler.postDelayed(tick, INTERVALS[level]);
            } else { board.invalidate(); handler.postDelayed(this, 700); }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("snake", MODE_PRIVATE);
        level = Math.max(0, Math.min(2, preferences.getInt("level", 0)));
        game = new SnakeGame(new Random());
        if (saved != null) {
            level = saved.getInt("level", level);
            game.restore(saved.getIntArray("game"));
        }
        best = preferences.getInt("best_" + level, 0);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(22), dp(10), dp(22), dp(8));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(22) + insets.getSystemWindowInsetLeft(), dp(10) + insets.getSystemWindowInsetTop(),
                dp(22) + insets.getSystemWindowInsetRight(), dp(8) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);

        LinearLayout header = row();
        LinearLayout title = column();
        title.addView(label("贪吃蛇", 27, INK, true));
        TextView sub = label("S N A K E   C L U B", 10, MUTED, true);
        title.addView(sub);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView offline = label("●  离线畅玩", 11, INK, false);
        offline.setPadding(dp(12), dp(7), dp(12), dp(7));
        offline.setBackground(shape(0xFFE7EBDD, 20));
        header.addView(offline);
        root.addView(header, full(60));

        LinearLayout scores = row();
        scoreText = scoreCard(scores, "本局得分", INK, GREEN);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(10), 1);
        scores.addView(new View(this), gap);
        bestText = scoreCard(scores, "最佳纪录", INK, 0xFFE9E9DF);
        root.addView(scores, full(66));

        LinearLayout difficulty = row();
        TextView speed = label("节奏", 12, MUTED, true);
        difficulty.addView(speed, new LinearLayout.LayoutParams(dp(42), -1));
        String[] names = { "悠闲", "经典", "极速" };
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            levels[i] = button(names[i], 12, () -> {
                if (game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0) return;
                level = choice; preferences.edit().putInt("level", level).apply();
                best = preferences.getInt("best_" + level, 0); game.reset(); updateUi();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1);
            lp.setMargins(dp(4), 0, 0, 0);
            difficulty.addView(levels[i], lp);
        }
        root.addView(difficulty, full(46));

        LinearLayout boardLabel = row();
        TextView caption = label("●  小小一口，大大快乐", 10, MUTED, false);
        boardLabel.addView(caption, new LinearLayout.LayoutParams(0, -1, 1));
        boardLabel.addView(label("20 × 20", 10, MUTED, false));
        root.addView(boardLabel, full(24));
        board = new BoardView();
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = label("滑动棋盘，或点击方向键控制", 11, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, full(30));

        LinearLayout controls = row();
        LinearLayout pad = column();
        LinearLayout padTop = row();
        padTop.setGravity(Gravity.CENTER);
        padTop.addView(directionButton("↑", "向上", SnakeGame.UP), new LinearLayout.LayoutParams(dp(52), dp(48)));
        pad.addView(padTop, full(48));
        LinearLayout padBottom = row();
        String[] arrows = { "←", "↓", "→" };
        String[] descriptions = { "向左", "向下", "向右" };
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
        pauseButton = button("暂停", 14, () -> { if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else if (game.state == SnakeGame.State.PAUSED) begin(); });
        actions.addView(pauseButton, full(48));
        TextView restart = button("重新开始", 12, this::restart);
        restart.setBackground(ripple(0xFFE9E9DF, 14));
        LinearLayout.LayoutParams restartLp = full(48); restartLp.topMargin = dp(4);
        actions.addView(restart, restartLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(dp(116), dp(100));
        actionsLp.leftMargin = dp(14);
        controls.addView(actions, actionsLp);
        root.addView(controls, full(104));
        TextView help = button("玩法说明  ·  无广告 / 无需联网", 10, () -> { pauseGame(); new AlertDialog.Builder(this)
            .setTitle("慢慢吃，慢慢长")
            .setMessage("滑动棋盘或点击方向键改变方向。\n\n每吃一颗果子得 10 分，身体也会变长。撞到边界或自己，本局结束。不能直接反向掉头。\n\n悠闲、经典、极速三档固定速度，各自保存最高分。新的一局开始前可以切换。\n\n切到后台会自动暂停，回来后点击继续。也支持键盘方向键 / WASD，空格暂停。\n\n完全离线，无广告，无账号。")
            .setPositiveButton("知道啦", null).show(); });
        help.setTextColor(MUTED);
        root.addView(help, full(32));
        updateUi();
    }

    private TextView scoreCard(LinearLayout parent, String title, int color, int background) {
        LinearLayout card = row();
        card.setPadding(dp(14), dp(5), dp(14), dp(5)); card.setBackground(shape(background, 16));
        TextView name = label(title, 11, color, false);
        card.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        TextView value = label("00", 27, color, true); card.addView(value);
        parent.addView(card, new LinearLayout.LayoutParams(0, -1, 1)); return value;
    }
    private void updateUi() {
        scoreText.setText(String.format(Locale.ROOT, "%02d", game.score));
        bestText.setText(String.format(Locale.ROOT, "%02d", best));
        boolean active = game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0;
        for (int i = 0; i < 3; i++) {
            levels[i].setBackground(ripple(i == level ? INK : 0xFFE9E9DF, 10));
            levels[i].setTextColor(i == level ? Color.WHITE : MUTED);
            levels[i].setEnabled(!active); levels[i].setAlpha(active && i != level ? .45f : 1f);
            levels[i].setContentDescription(new String[] { "悠闲", "经典", "极速" }[i] + (i == level ? "，已选择" : ""));
        }
        pauseButton.setText(game.state == SnakeGame.State.PAUSED && countdown == 0 ? "继续" : "暂停");
        pauseButton.setEnabled(active); pauseButton.setAlpha(active ? 1f : .4f);
        pauseButton.setTextColor(Color.WHITE); pauseButton.setBackground(ripple(INK, 14));
        if (game.state == SnakeGame.State.RUNNING || countdown > 0) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        board.setContentDescription("贪吃蛇棋盘，本局 " + game.score + " 分，" + (game.state == SnakeGame.State.RUNNING ? "游戏中" : "点击开始或继续"));
        board.invalidate();
    }
    private void begin() {
        if (countdown > 0 || game.state == SnakeGame.State.RUNNING) return;
        if (game.state == SnakeGame.State.OVER || game.state == SnakeGame.State.WON) game.reset();
        countdown = 3; handler.removeCallbacks(count); handler.removeCallbacks(tick);
        updateUi(); handler.postDelayed(count, 700);
    }
    private void pauseGame() {
        handler.removeCallbacks(tick); handler.removeCallbacks(count);
        if (countdown > 0 && game.state == SnakeGame.State.READY) game.start();
        countdown = 0; game.pause(); updateUi();
    }
    private void restart() {
        if (game.state == SnakeGame.State.RUNNING || game.state == SnakeGame.State.PAUSED || countdown > 0) {
            pauseGame();
            new AlertDialog.Builder(this).setTitle("重新开始这一局？").setMessage("当前进度会结束，最高纪录会保留。")
                .setNegativeButton("保留这局", null).setPositiveButton("重新开始", (dialog, which) -> { game.reset(); updateUi(); }).show();
        } else { game.reset(); updateUi(); }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; }
    @Override protected void onPause() { foreground = false; pauseGame(); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        pauseGame(); out.putInt("level", level); out.putIntArray("game", game.save()); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else super.onBackPressed();
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) direction = SnakeGame.UP;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) direction = SnakeGame.RIGHT;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_S) direction = SnakeGame.DOWN;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A) direction = SnakeGame.LEFT;
        if (direction >= 0) { game.turn(direction); return true; }
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.getRepeatCount() == 0) {
            if (game.state == SnakeGame.State.RUNNING || countdown > 0) pauseGame(); else begin(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout.LayoutParams full(int height) { return new LinearLayout.LayoutParams(-1, dp(height)); }
    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false); if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return v;
    }
    private TextView button(String text, float size, Runnable action) {
        TextView v = label(text, size, INK, true); v.setGravity(Gravity.CENTER); v.setFocusable(true);
        v.setBackground(ripple(PAPER, 12)); v.setOnClickListener(w -> action.run()); return v;
    }
    private TextView directionButton(String arrow, String description, int direction) {
        TextView v = button(arrow, 25, () -> game.turn(direction));
        v.setContentDescription(description); v.setBackground(ripple(0xFFE3E7DA, 13)); return v;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(0x223A7050), shape(color, radius), null); }

    private final class BoardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF(), action = new RectF();
        private float startX, startY;
        private boolean gesture, swiped;
        BoardView() { super(MainActivity.this); setFocusable(true); setClickable(true); }
        private void rounded(Canvas c, RectF r, float radius, int color) {
            paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawRoundRect(r, radius, radius, paint);
        }
        private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
            paint.setColor(color); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); c.drawText(value, x, y, paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float side = Math.min(getWidth(), getHeight());
            float left = (getWidth() - side) / 2f, top = (getHeight() - side) / 2f;
            bounds.set(left, top, left + side, top + side);
            rounded(canvas, bounds, dp(20), INK);
            float inset = side * .035f, cell = (side - 2 * inset) / SnakeGame.SIZE;
            float ox = left + inset, oy = top + inset;
            paint.setColor(0xFF285044); paint.setStrokeWidth(1);
            for (int i = 0; i <= SnakeGame.SIZE; i++) {
                canvas.drawLine(ox + i * cell, oy, ox + i * cell, oy + SnakeGame.SIZE * cell, paint);
                canvas.drawLine(ox, oy + i * cell, ox + SnakeGame.SIZE * cell, oy + i * cell, paint);
            }
            // Connect adjacent segments first so the body reads as one continuous snake.
            paint.setColor(GREEN); paint.setStrokeWidth(cell * .73f); paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = game.body.size() - 1; i > 0; i--) {
                SnakeGame.Cell a = game.body.get(i), b = game.body.get(i - 1);
                canvas.drawLine(ox + (a.x + .5f) * cell, oy + (a.y + .5f) * cell,
                    ox + (b.x + .5f) * cell, oy + (b.y + .5f) * cell, paint);
            }
            SnakeGame.Cell head = game.body.get(0);
            float hx = ox + (head.x + .5f) * cell, hy = oy + (head.y + .5f) * cell;
            rounded(canvas, new RectF(hx - cell * .45f, hy - cell * .45f, hx + cell * .45f, hy + cell * .45f), cell * .25f, GREEN);
            paint.setColor(INK);
            int dx = game.direction == SnakeGame.RIGHT ? 1 : game.direction == SnakeGame.LEFT ? -1 : 0;
            int dy = game.direction == SnakeGame.DOWN ? 1 : game.direction == SnakeGame.UP ? -1 : 0;
            for (int sign : new int[] { -1, 1 }) canvas.drawCircle(hx + cell * (.18f * dx + sign * .21f * dy), hy + cell * (.18f * dy + sign * .21f * dx), cell * .085f, paint);
            if (game.food != null) {
                float fx = ox + (game.food.x + .5f) * cell, fy = oy + (game.food.y + .5f) * cell;
                paint.setColor(0x22F6A36A); canvas.drawCircle(fx, fy, cell * .72f, paint);
                paint.setColor(ORANGE); canvas.drawCircle(fx, fy + cell * .06f, cell * .36f, paint);
                paint.setColor(GREEN); canvas.drawOval(fx, fy - cell * .44f, fx + cell * .3f, fy - cell * .20f, paint);
            }
            action.setEmpty();
            if (game.state != SnakeGame.State.RUNNING || countdown > 0) {
                rounded(canvas, bounds, dp(20), 0xA615352C);
                float cx = bounds.centerX(), cy = bounds.centerY(), unit = side / 340f;
                if (countdown > 0) {
                    text(canvas, String.valueOf(countdown), cx, cy + 14 * unit, 64 * unit, GREEN, true);
                    text(canvas, "准备出发", cx, cy + 49 * unit, 13 * unit, 0xFFD5E3D8, false);
                    return;
                }
                boolean ready = game.state == SnakeGame.State.READY, paused = game.state == SnakeGame.State.PAUSED;
                String title = ready ? "准备好开吃了吗？" : paused ? "歇一会儿" : game.state == SnakeGame.State.WON ? "完美通关！" : "下次，再长一点";
                String subtitle = ready ? "一颗果子，一点小快乐。" : paused ? "你的小蛇正在等你回来" : game.endReason;
                text(canvas, ready ? "LET’S PLAY" : paused ? "TAKE A BREAK" : "NICE RUN", cx, cy - 67 * unit, 11 * unit, GREEN, true);
                text(canvas, title, cx, cy - 29 * unit, 25 * unit, Color.WHITE, true);
                text(canvas, subtitle, cx, cy - 3 * unit, 12 * unit, 0xFFC4D6C9, false);
                if (!ready && !paused) text(canvas, "本局 " + game.score + " 分  ·  最佳 " + best + " 分", cx, cy + 24 * unit, 13 * unit, GREEN, true);
                float buttonTop = cy + (ready || paused ? 24 : 43) * unit;
                action.set(cx - 88 * unit, buttonTop, cx + 88 * unit, buttonTop + 46 * unit);
                rounded(canvas, action, 15 * unit, GREEN);
                text(canvas, ready ? "开始游戏  →" : paused ? "继续游戏  →" : "再来一局  →", cx, buttonTop + 29 * unit, 15 * unit, INK, true);
            }
        }
        @Override public boolean performClick() { super.performClick(); begin(); return true; }
        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gesture = bounds.contains(x, y); swiped = false; startX = x; startY = y;
                    return gesture;
                case MotionEvent.ACTION_MOVE:
                    if (!gesture || game.state != SnakeGame.State.RUNNING) return gesture;
                    float dx = x - startX, dy = y - startY;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) >= dp(18)) {
                        game.turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? SnakeGame.RIGHT : SnakeGame.LEFT) : (dy > 0 ? SnakeGame.DOWN : SnakeGame.UP));
                        startX = x; startY = y; swiped = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (gesture && !swiped && action.contains(x, y) && action.contains(startX, startY)) performClick();
                    gesture = false; return true;
                case MotionEvent.ACTION_CANCEL:
                    gesture = false; return true;
                default: return true;
            }
        }
    }
}
