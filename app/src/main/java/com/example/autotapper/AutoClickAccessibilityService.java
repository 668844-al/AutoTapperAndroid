package com.example.autotapper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Random;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static AutoClickAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean running = false;
    private String runningTaskId = "";
    private int actionIndex = 0;
    private int cycles = 0;
    private long startedElapsed = 0L;

    public static boolean isConnected() { return instance != null; }
    public static boolean isRunning() { return instance != null && instance.running; }
    public static String runningTaskId() { return instance == null ? "" : instance.runningTaskId; }
    public static int cyclesDone() { return instance == null ? 0 : instance.cycles; }

    public static void startNow() { if (instance != null) instance.startTaskInternal(Prefs.currentTaskId(instance)); }
    public static void startTask(String taskId) { if (instance != null) instance.startTaskInternal(taskId); }
    public static void stopNow() { if (instance != null) instance.stopLoop(); }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance=this;
        String pending=Prefs.sp(this).getString(Prefs.KEY_PENDING_TASK,"");
        if (pending != null && !pending.isEmpty()) {
            Prefs.sp(this).edit().putString(Prefs.KEY_PENDING_TASK,"").apply();
            handler.postDelayed(() -> startTaskInternal(pending), 1700);
        }
    }

    private void startTaskInternal(String taskId) {
        TaskProfile t=Prefs.byId(this,taskId);
        if (t==null) t=Prefs.current(this);
        if (t.actions.isEmpty()) { Toast.makeText(this,"当前任务还没有录制点击/滑动动作",Toast.LENGTH_SHORT).show(); return; }
        Prefs.setCurrentTaskId(this,t.id);
        running=true; runningTaskId=t.id; actionIndex=0; cycles=0; startedElapsed=SystemClock.elapsedRealtime();
        handler.removeCallbacksAndMessages(null); handler.post(this::performNext);
    }

    private void stopLoop() {
        running=false; runningTaskId=""; handler.removeCallbacksAndMessages(null);
    }

    private void performNext() {
        if (!running) return;
        TaskProfile t=Prefs.byId(this,runningTaskId);
        if (t==null || t.actions.isEmpty()) { stopLoop(); return; }
        if (t.maxDurationSec>0 && SystemClock.elapsedRealtime()-startedElapsed >= t.maxDurationSec*1000L) { stopLoop(); return; }
        if (t.maxCycles>0 && cycles>=t.maxCycles) { stopLoop(); return; }

        TaskProfile.Action a=t.actions.get(actionIndex);
        Path p=new Path(); p.moveTo(a.x1,a.y1);
        long gestureDuration;
        if (TaskProfile.Action.SWIPE.equals(a.type)) { p.lineTo(a.x2,a.y2); gestureDuration=Math.max(80,a.durationMs); }
        else gestureDuration=1;
        GestureDescription gd=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,gestureDuration)).build();

        dispatchGesture(gd,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription); afterAction(t);
            }
            @Override public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription); if(running) handler.postDelayed(AutoClickAccessibilityService.this::performNext,120);
            }
        },handler);
    }

    private void afterAction(TaskProfile t) {
        if (!running) return;
        actionIndex++;
        if (actionIndex>=t.actions.size()) { actionIndex=0; cycles++; }
        if (t.maxCycles>0 && cycles>=t.maxCycles) { stopLoop(); return; }
        if (t.maxDurationSec>0 && SystemClock.elapsedRealtime()-startedElapsed >= t.maxDurationSec*1000L) { stopLoop(); return; }
        long base=Math.max(60L,Math.round(60000.0/Math.max(1,Math.min(1000,t.ratePerMin))));
        int lo=Math.max(0,t.randomMinMs), hi=Math.max(lo,t.randomMaxMs);
        long jitter=lo + (hi>lo ? random.nextInt(hi-lo+1) : 0);
        handler.postDelayed(this::performNext,base+jitter);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stopLoop(); }
    @Override public void onDestroy() { stopLoop(); if(instance==this) instance=null; super.onDestroy(); }
}
