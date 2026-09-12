package com.example.autotapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class OverlayService extends Service {
    public static final String ACTION_RUN_SCHEDULE = "com.example.autotapper.RUN_SCHEDULE";
    public static final String EXTRA_TASK_ID = "task_id";
    private static final String CHANNEL = "autotapper_running_v25";

    private WindowManager wm;
    private View panel, bubble;
    private FrameLayout recorder;
    private WindowManager.LayoutParams panelLp, bubbleLp;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView taskTitle, statusText, guideText, bubbleText;
    private Button startButton;
    private float recStartX, recStartY;
    private long recStartAt;

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(501, buildNotification("悬浮控制已启动"));
        if (Settings.canDrawOverlays(this)) showPanel();
        handler.post(ticker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RUN_SCHEDULE.equals(intent.getAction())) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            if (taskId != null) Prefs.setCurrentTaskId(this, taskId);
            handler.postDelayed(() -> {
                if (AutoClickAccessibilityService.isConnected()) {
                    AutoClickAccessibilityService.startTask(Prefs.currentTaskId(this));
                } else {
                    Toast.makeText(this, "无障碍服务未开启，无法执行定时任务", Toast.LENGTH_LONG).show();
                }
                refreshStatus();
            }, 1800);
        }
        if (panel == null && bubble == null && recorder == null && Settings.canDrawOverlays(this)) showPanel();
        return START_STICKY;
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 500);
        }
    };

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "连点器运行状态", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_tap)
                .setContentTitle("连点器")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable panelBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(248, 255, 255, 255));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), Color.rgb(224, 228, 236));
        return g;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.argb(55, 80, 90, 110));
        return g;
    }

    private GradientDrawable bubbleBg() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.rgb(66, 92, 235));
        g.setStroke(dp(2), Color.WHITE);
        return g;
    }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(7.5f);
        b.setTextColor(Color.rgb(44, 53, 72));
        b.setBackground(pill(Color.rgb(247, 249, 253)));
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private LinearLayout.LayoutParams miniButtonLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(24), 1f);
        p.setMargins(dp(2), dp(1), dp(2), dp(1));
        return p;
    }

    private void showPanel() {
        if (!Settings.canDrawOverlays(this) || panel != null) return;
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
            bubbleText = null;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(4), dp(5), dp(4));
        root.setBackground(panelBg());
        root.setElevation(dp(5));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView dragMark = tv("☰", 9, Color.rgb(98, 105, 121), false);
        dragMark.setGravity(Gravity.CENTER);
        top.addView(dragMark, new LinearLayout.LayoutParams(dp(18), dp(20)));

        taskTitle = tv("", 8, Color.rgb(28, 34, 49), true);
        top.addView(taskTitle, new LinearLayout.LayoutParams(0, dp(20), 1f));

        TextView close = tv("×", 12, Color.rgb(110, 116, 130), false);
        close.setGravity(Gravity.CENTER);
        top.addView(close, new LinearLayout.LayoutParams(dp(20), dp(20)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(20)));

        statusText = tv("", 6.5f, Color.rgb(91, 99, 116), false);
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(15)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        startButton = miniButton("▶ 开始");
        Button record = miniButton("＋ 录制");
        Button undo = miniButton("↶ 撤销");
        actions.addView(startButton, miniButtonLp());
        actions.addView(record, miniButtonLp());
        actions.addView(undo, miniButtonLp());
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(27)));

        guideText = tv("", 6.5f, Color.rgb(102, 109, 126), false);
        guideText.setGravity(Gravity.CENTER);
        guideText.setPadding(dp(2), dp(1), dp(2), 0);
        root.addView(guideText, new LinearLayout.LayoutParams(-1, dp(20)));

        panel = root;
        panelLp = new WindowManager.LayoutParams(
                dp(160),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(10);
        panelLp.y = dp(160);
        wm.addView(panel, panelLp);

        attachDragOrClick(root, panelLp, panel, null);
        attachDragOrClick(dragMark, panelLp, panel, null);
        attachDragOrClick(taskTitle, panelLp, panel, null);
        attachDragOrClick(statusText, panelLp, panel, null);
        attachDragOrClick(guideText, panelLp, panel, null);
        attachDragOrClick(close, panelLp, panel, this::stopSelf);
        attachDragOrClick(startButton, panelLp, panel, this::toggleRun);
        attachDragOrClick(record, panelLp, panel, this::showRecorder);
        attachDragOrClick(undo, panelLp, panel, () -> {
            TaskProfile t = Prefs.current(this);
            if (!t.actions.isEmpty()) {
                t.actions.remove(t.actions.size() - 1);
                Prefs.upsert(this, t);
            }
            refreshStatus();
        });

        refreshStatus();
    }

    private void toggleRun() {
        TaskProfile t = Prefs.current(this);
        if (!AutoClickAccessibilityService.isConnected()) {
            Toast.makeText(this, "请先开启无障碍权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (t.actions.isEmpty()) {
            Toast.makeText(this, "请先录制点击位置", Toast.LENGTH_SHORT).show();
            return;
        }
        if (AutoClickAccessibilityService.isRunning()) AutoClickAccessibilityService.stopNow();
        else AutoClickAccessibilityService.startNow();
        refreshStatus();
    }

    private void refreshStatus() {
        TaskProfile t = Prefs.current(this);
        boolean basic = Prefs.BASIC_TASK_ID.equals(t.id);
        if (taskTitle != null) {
            taskTitle.setText(basic ? "基础连点" : ((t.name == null || t.name.isEmpty()) ? "预约任务" : t.name));
        }

        int taps = 0, swipes = 0;
        for (TaskProfile.Action a : t.actions) {
            if (TaskProfile.Action.SWIPE.equals(a.type)) swipes++; else taps++;
        }
        boolean running = AutoClickAccessibilityService.isRunning();
        int total = t.actions.size();
        String progress = t.maxCycles > 0
                ? AutoClickAccessibilityService.cyclesDone() + "/" + t.maxCycles + "轮"
                : total + "动作";

        if (statusText != null) {
            statusText.setText((running ? "运行中" : "已暂停") + " · " + t.ratePerMin + "次/分 · " + taps + "点" + (swipes > 0 ? "/" + swipes + "滑" : ""));
        }
        if (startButton != null) startButton.setText(running ? "Ⅱ 暂停" : "▶ 开始");
        if (guideText != null) {
            if (!AutoClickAccessibilityService.isConnected()) guideText.setText("请先开启无障碍权限");
            else if (total == 0) guideText.setText("先录制，再点目标位置");
            else if (running) guideText.setText("运行中 · 长按拖到边缘收缩");
            else guideText.setText("已录制 " + total + " 个动作");
        }
        if (bubbleText != null) {
            bubbleText.setText((running ? "▶" : "Ⅱ") + "\n" + t.ratePerMin + "/分\n" + progress);
        }
    }

    private void collapseAtCurrentEdge() {
        int x = panelLp != null ? panelLp.x : dp(6);
        int y = panelLp != null ? panelLp.y : dp(220);
        int w = panel != null ? panel.getWidth() : dp(160);
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
            panel = null;
            taskTitle = null;
            statusText = null;
            guideText = null;
            startButton = null;
        }
        showBubble(x, y, w);
    }

    private void showBubble(int oldX, int oldY, int oldWidth) {
        if (bubble != null) return;
        TextView b = tv("", 6.5f, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bubbleBg());
        b.setPadding(dp(3), dp(3), dp(3), dp(3));
        b.setLineSpacing(0, 0.95f);
        b.setElevation(dp(6));
        bubble = b;
        bubbleText = b;

        int size = dp(54);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        int screenW = dm.widthPixels;
        int margin = dp(4);
        boolean rightSide = oldX + oldWidth / 2 >= screenW / 2;

        bubbleLp = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = rightSide ? screenW - size - margin : margin;
        bubbleLp.y = Math.max(dp(30), Math.min(oldY, dm.heightPixels - size - dp(30)));
        wm.addView(bubble, bubbleLp);
        attachBubbleTouch(bubble, bubbleLp);
        refreshStatus();
    }

    private boolean nearScreenEdge(WindowManager.LayoutParams lp, View target) {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        int edge = dp(18);
        int w = target.getWidth() > 0 ? target.getWidth() : dp(160);
        int h = target.getHeight() > 0 ? target.getHeight() : dp(90);
        return lp.x <= edge
                || lp.y <= edge
                || lp.x + w >= dm.widthPixels - edge
                || lp.y + h >= dm.heightPixels - edge;
    }

    private void clampToScreen(WindowManager.LayoutParams lp, View target) {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        int w = target.getWidth() > 0 ? target.getWidth() : dp(160);
        int h = target.getHeight() > 0 ? target.getHeight() : dp(90);
        lp.x = Math.max(0, Math.min(lp.x, dm.widthPixels - w));
        lp.y = Math.max(0, Math.min(lp.y, dm.heightPixels - h));
    }

    private void attachDragOrClick(View handle, WindowManager.LayoutParams lp, View target, Runnable clickAction) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            boolean movedBeforeLong;
            final Runnable longPress = () -> dragging = true;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragging = false;
                        movedBeforeLong = false;
                        handler.postDelayed(longPress, 360);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (dragging) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            clampToScreen(lp, target);
                            try { wm.updateViewLayout(target, lp); } catch (Exception ignored) {}
                        } else if (Math.abs(dx) + Math.abs(dy) > dp(8)) {
                            movedBeforeLong = true;
                            handler.removeCallbacks(longPress);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPress);
                        if (dragging) {
                            if (nearScreenEdge(lp, target)) collapseAtCurrentEdge();
                        } else if (!movedBeforeLong && clickAction != null) {
                            clickAction.run();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        return true;
                }
                return false;
            }
        });
    }

    private void attachBubbleTouch(View target, WindowManager.LayoutParams lp) {
        target.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragging;
            boolean movedBeforeLong;
            final Runnable longPress = () -> dragging = true;

            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragging = false;
                        movedBeforeLong = false;
                        handler.postDelayed(longPress, 360);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (dragging) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            clampToScreen(lp, target);
                            try { wm.updateViewLayout(target, lp); } catch (Exception ignored) {}
                        } else if (Math.abs(dx) + Math.abs(dy) > dp(8)) {
                            movedBeforeLong = true;
                            handler.removeCallbacks(longPress);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPress);
                        if (!dragging && !movedBeforeLong) {
                            try { wm.removeView(target); } catch (Exception ignored) {}
                            bubble = null;
                            bubbleText = null;
                            showPanel();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        return true;
                }
                return false;
            }
        });
    }

    private void showRecorder() {
        if (recorder != null) return;
        if (panel != null) panel.setVisibility(View.GONE);
        if (bubble != null) bubble.setVisibility(View.GONE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        recorder = root;

        TextView hint = tv("录制模式：轻点=点击位置 · 拖动=滑动", 12, Color.WHITE, true);
        hint.setGravity(Gravity.CENTER);
        hint.setBackground(pill(Color.argb(235, 40, 47, 62)));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(310), dp(42), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = dp(22);
        root.addView(hint, hp);

        TextView sub = tv("录制完成后前台页面恢复正常操作", 11, Color.WHITE, false);
        sub.setGravity(Gravity.CENTER);
        sub.setBackground(pill(Color.argb(205, 58, 65, 80)));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(300), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sp.topMargin = dp(70);
        root.addView(sub, sp);

        Button done = miniButton("完成录制");
        done.setTextColor(Color.WHITE);
        done.setTextSize(13);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setBackground(pill(Color.rgb(66, 92, 235)));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(150), dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(28);
        root.addView(done, dlp);
        done.setOnClickListener(v -> closeRecorder());

        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (e.getY() < dp(115) || e.getY() > root.getHeight() - dp(95)) return true;
                recStartX = e.getRawX();
                recStartY = e.getRawY();
                recStartAt = System.currentTimeMillis();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (recStartAt == 0) return true;
                float ex = e.getRawX(), ey = e.getRawY();
                float distance = (float) Math.hypot(ex - recStartX, ey - recStartY);
                long duration = Math.max(100, System.currentTimeMillis() - recStartAt);
                TaskProfile t = Prefs.current(this);
                if (distance < dp(22)) t.actions.add(TaskProfile.Action.tap(recStartX, recStartY));
                else t.actions.add(TaskProfile.Action.swipe(recStartX, recStartY, ex, ey, duration));
                Prefs.upsert(this, t);
                renderActions(root, t.actions);
                recStartAt = 0;
                refreshStatus();
                return true;
            }
            return true;
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(root, lp);
        renderActions(root, Prefs.current(this).actions);
    }

    private void renderActions(FrameLayout root, List<TaskProfile.Action> actions) {
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View c = root.getChildAt(i);
            if ("marker".equals(c.getTag())) root.removeViewAt(i);
        }
        for (int i = 0; i < actions.size(); i++) {
            TaskProfile.Action a = actions.get(i);
            if (TaskProfile.Action.SWIPE.equals(a.type)) {
                addLine(root, a.x1, a.y1, a.x2, a.y2);
                addMarker(root, a.x1, a.y1, "S" + (i + 1));
                addMarker(root, a.x2, a.y2, "→");
            } else {
                addMarker(root, a.x1, a.y1, String.valueOf(i + 1));
            }
        }
    }

    private void addMarker(FrameLayout root, float x, float y, String label) {
        TextView m = tv(label, 11, Color.WHITE, true);
        m.setTag("marker");
        m.setGravity(Gravity.CENTER);
        m.setBackground(pill(Color.rgb(66, 92, 235)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(32), dp(32));
        lp.leftMargin = (int) x - dp(16);
        lp.topMargin = (int) y - dp(16);
        root.addView(m, lp);
    }

    private void addLine(FrameLayout root, float x1, float y1, float x2, float y2) {
        float dist = (float) Math.hypot(x2 - x1, y2 - y1);
        View line = new View(this);
        line.setTag("marker");
        line.setBackgroundColor(Color.rgb(91, 114, 242));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) dist, dp(3));
        lp.leftMargin = (int) x1;
        lp.topMargin = (int) y1;
        line.setPivotX(0f);
        line.setPivotY(dp(1.5f));
        line.setRotation((float) Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)));
        root.addView(line, lp);
    }

    private void closeRecorder() {
        if (recorder != null) {
            try { wm.removeView(recorder); } catch (Exception ignored) {}
            recorder = null;
        }
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        refreshStatus();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        AutoClickAccessibilityService.stopNow();
        if (recorder != null) try { wm.removeView(recorder); } catch (Exception ignored) {}
        if (panel != null) try { wm.removeView(panel); } catch (Exception ignored) {}
        if (bubble != null) try { wm.removeView(bubble); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
