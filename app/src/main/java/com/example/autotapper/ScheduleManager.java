package com.example.autotapper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public final class ScheduleManager {
    private ScheduleManager() {}

    public static int requestCode(String id) { return 1000 + (id == null ? 0 : (id.hashCode() & 0x3fffffff) % 500000); }

    public static PendingIntent pending(Context c, TaskProfile t) {
        Intent i = new Intent(c, ScheduledLaunchActivity.class).putExtra("task_id", t.id);
        return PendingIntent.getActivity(c, requestCode(t.id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canExact(Context c) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    public static void schedule(Context c, TaskProfile t) {
        if (t.scheduleAt <= System.currentTimeMillis()) return;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.scheduleAt, pending(c,t));
    }

    public static void cancel(Context c, TaskProfile t) {
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi=pending(c,t); am.cancel(pi); pi.cancel();
    }

    public static long nextOccurrence(long previous, String repeat) {
        if (TaskProfile.REPEAT_ONCE.equals(repeat)) return 0L;
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(previous);
        long now=System.currentTimeMillis();
        do {
            if (TaskProfile.REPEAT_DAILY.equals(repeat)) c.add(Calendar.DAY_OF_YEAR,1);
            else if (TaskProfile.REPEAT_WEEKLY.equals(repeat)) c.add(Calendar.WEEK_OF_YEAR,1);
            else if (TaskProfile.REPEAT_WEEKDAYS.equals(repeat)) {
                do { c.add(Calendar.DAY_OF_YEAR,1); }
                while (c.get(Calendar.DAY_OF_WEEK)==Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY);
            } else return 0L;
        } while (c.getTimeInMillis() <= now);
        return c.getTimeInMillis();
    }

    public static void advanceAndReschedule(Context c, TaskProfile t) {
        long next=nextOccurrence(t.scheduleAt,t.repeat);
        t.scheduleAt=next; Prefs.upsert(c,t);
        if (next>0) schedule(c,t);
    }
}
