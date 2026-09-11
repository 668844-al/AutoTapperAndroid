package com.example.autotapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(intent==null||!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        for(TaskProfile t:Prefs.tasks(context)) if(t.scheduleAt>System.currentTimeMillis()) try{ScheduleManager.schedule(context,t);}catch(Exception ignored){}
    }
}
