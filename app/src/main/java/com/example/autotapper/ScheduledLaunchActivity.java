package com.example.autotapper;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class ScheduledLaunchActivity extends Activity {
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=27){setTurnScreenOn(true);setShowWhenLocked(false);}
        String taskId=getIntent().getStringExtra("task_id");
        TaskProfile t=Prefs.byId(this,taskId);
        if(t==null){finish();return;}
        ScheduleManager.advanceAndReschedule(this,t);

        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(km!=null&&km.isKeyguardLocked()){showUnlockNotification(t);finish();return;}

        Prefs.setCurrentTaskId(this,t.id);
        Prefs.sp(this).edit().putString(Prefs.KEY_PENDING_TASK,t.id).apply();
        if(t.pkg!=null&&!t.pkg.isEmpty()){
            Intent launch=getPackageManager().getLaunchIntentForPackage(t.pkg);
            if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);try{startActivity(launch);}catch(Exception ignored){}}
        }
        Intent s=new Intent(this,OverlayService.class).setAction(OverlayService.ACTION_RUN_SCHEDULE).putExtra(OverlayService.EXTRA_TASK_ID,t.id);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);
        finish();
    }

    private void showUnlockNotification(TaskProfile t){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);String ch="autotapper_alerts_v2";
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(ch,"连点器提醒",NotificationManager.IMPORTANCE_HIGH));
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,9,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(this,ch):new android.app.Notification.Builder(this);
        android.app.Notification n=b.setSmallIcon(R.drawable.ic_stat_tap).setContentTitle("定时任务已到点："+t.name).setContentText("设备处于安全锁屏状态，请先解锁后手动开始").setContentIntent(pi).setAutoCancel(true).build();
        nm.notify(ScheduleManager.requestCode(t.id),n);
    }
}
