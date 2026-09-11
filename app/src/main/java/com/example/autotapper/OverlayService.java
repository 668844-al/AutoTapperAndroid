package com.example.autotapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
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
    public static final String ACTION_RUN_SCHEDULE="com.example.autotapper.RUN_SCHEDULE";
    public static final String EXTRA_TASK_ID="task_id";
    private static final String CHANNEL="autotapper_running_v3";

    private WindowManager wm;
    private View panel,bubble;
    private FrameLayout recorder;
    private WindowManager.LayoutParams panelLp,bubbleLp;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,taskTitle,guide;
    private Button startButton;
    private float recStartX,recStartY;
    private long recStartAt;

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(501,buildNotification("悬浮控制已启动"));
        if(Settings.canDrawOverlays(this)) showPanel();
        handler.post(statusTicker);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_RUN_SCHEDULE.equals(intent.getAction())){
            String taskId=intent.getStringExtra(EXTRA_TASK_ID);
            if(taskId!=null) Prefs.setCurrentTaskId(this,taskId);
            handler.postDelayed(()->{
                if(AutoClickAccessibilityService.isConnected()) AutoClickAccessibilityService.startTask(Prefs.currentTaskId(this));
                else Toast.makeText(this,"无障碍服务未开启，无法执行定时任务",Toast.LENGTH_LONG).show();
                refreshStatus();
            },1800);
        }
        if(panel==null && bubble==null && recorder==null && Settings.canDrawOverlays(this)) showPanel();
        return START_STICKY;
    }

    private final Runnable statusTicker=new Runnable(){@Override public void run(){refreshStatus();handler.postDelayed(this,500);}};

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"连点器运行状态",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification buildNotification(String text){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(R.drawable.ic_stat_tap).setContentTitle("轻触连点器").setContentText(text).setContentIntent(pi).setOngoing(true).build();}

    private GradientDrawable panelBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(248,255,255,255));g.setCornerRadius(dp(18));g.setStroke(dp(1),Color.rgb(224,228,236));return g;}
    private GradientDrawable pill(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(13));g.setStroke(dp(1),Color.argb(70,80,90,110));return g;}
    private TextView tv(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.rgb(44,53,72));b.setBackground(pill(Color.rgb(247,249,253)));b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(8),0,dp(8),0);return b;}
    private LinearLayout.LayoutParams smallLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}

    private void showPanel(){
        if(!Settings.canDrawOverlays(this)||panel!=null)return;
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(9),dp(10),dp(9));
        root.setBackground(panelBg());
        root.setElevation(dp(8));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView drag=tv("☰",18,Color.rgb(98,105,121));drag.setGravity(Gravity.CENTER);top.addView(drag,new LinearLayout.LayoutParams(dp(34),dp(34)));
        taskTitle=tv("",14,Color.rgb(28,34,49));taskTitle.setTypeface(Typeface.DEFAULT_BOLD);top.addView(taskTitle,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView fold=tv("—",18,Color.rgb(86,94,112));fold.setGravity(Gravity.CENTER);top.addView(fold,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView close=tv("×",20,Color.rgb(110,116,130));close.setGravity(Gravity.CENTER);top.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(34)));

        status=tv("",11,Color.rgb(91,99,116));status.setGravity(Gravity.CENTER);root.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER);
        startButton=btn("▶ 开始");Button record=btn("＋ 录制");Button undo=btn("↶ 撤销");
        actions.addView(startButton,smallLp());actions.addView(record,smallLp());actions.addView(undo,smallLp());root.addView(actions);

        guide=tv("",11,Color.rgb(102,109,126));guide.setGravity(Gravity.CENTER);guide.setPadding(dp(3),dp(3),dp(3),0);root.addView(guide,new LinearLayout.LayoutParams(-1,dp(30)));

        startButton.setOnClickListener(v->{
            if(!AutoClickAccessibilityService.isConnected()){Toast.makeText(this,"请先开启无障碍权限",Toast.LENGTH_SHORT).show();return;}
            if(AutoClickAccessibilityService.isRunning()) AutoClickAccessibilityService.stopNow(); else AutoClickAccessibilityService.startNow();
            refreshStatus();
        });
        record.setOnClickListener(v->showRecorder());
        undo.setOnClickListener(v->{TaskProfile t=Prefs.current(this);if(!t.actions.isEmpty()){t.actions.remove(t.actions.size()-1);Prefs.upsert(this,t);}refreshStatus();});
        fold.setOnClickListener(v->collapse());
        close.setOnClickListener(v->stopSelf());

        panel=root;
        panelLp=new WindowManager.LayoutParams(dp(270),WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
        panelLp.gravity=Gravity.TOP|Gravity.START;panelLp.x=dp(14);panelLp.y=dp(160);
        wm.addView(panel,panelLp);
        makeDraggable(drag,panelLp,panel);
        refreshStatus();
    }

    private void refreshStatus(){
        if(taskTitle==null||status==null||guide==null)return;
        TaskProfile t=Prefs.current(this);
        boolean basic=Prefs.BASIC_TASK_ID.equals(t.id);
        taskTitle.setText(basic?"基础连点":(t.name==null||t.name.isEmpty()?"预约任务":t.name));
        int taps=0,swipes=0;for(TaskProfile.Action a:t.actions)if(TaskProfile.Action.SWIPE.equals(a.type))swipes++;else taps++;
        boolean running=AutoClickAccessibilityService.isRunning();
        String limit=t.maxCycles>0?" · "+AutoClickAccessibilityService.cyclesDone()+"/"+t.maxCycles+"轮":"";
        status.setText((running?"运行中":"已暂停")+" · "+t.ratePerMin+"次/分 · "+taps+"点"+(swipes>0?"/"+swipes+"滑":"")+limit);
        if(startButton!=null) startButton.setText(running?"Ⅱ 暂停":"▶ 开始");
        if(running) guide.setText("正在连点 · 点“暂停”即可停止");
        else if(t.actions.isEmpty()) guide.setText("先点“录制”，再点目标位置");
        else guide.setText("已录制 "+t.actions.size()+" 个动作 · 点“开始”执行");
    }

    private void collapse(){
        if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;taskTitle=null;status=null;guide=null;startButton=null;}
        TextView b=tv("▶",17,Color.WHITE);b.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(Color.rgb(66,92,235));bg.setStroke(dp(2),Color.WHITE);b.setBackground(bg);b.setElevation(dp(8));
        bubble=b;
        bubbleLp=new WindowManager.LayoutParams(dp(44),dp(44),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
        bubbleLp.gravity=Gravity.TOP|Gravity.START;bubbleLp.x=dp(12);bubbleLp.y=dp(230);wm.addView(b,bubbleLp);makeBubbleDraggable(b,bubbleLp);
    }

    private void makeDraggable(View handle,WindowManager.LayoutParams lp,View target){handle.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(target,lp);}catch(Exception ignored){}return true;}return true;}});}
    private void makeBubbleDraggable(View view,WindowManager.LayoutParams lp){view.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean moved;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;moved=false;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float x=e.getRawX()-dx,y=e.getRawY()-dy;moved|=Math.abs(x)+Math.abs(y)>dp(6);lp.x=sx+(int)x;lp.y=sy+(int)y;try{wm.updateViewLayout(view,lp);}catch(Exception ignored){}return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(!moved){try{wm.removeView(view);}catch(Exception ignored){}bubble=null;showPanel();}return true;}return true;}});}

    private void showRecorder(){
        if(recorder!=null)return;
        if(panel!=null)panel.setVisibility(View.GONE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.TRANSPARENT);recorder=root;

        TextView hint=tv("录制模式：轻点=点击位置 · 拖动=滑动",12,Color.WHITE);hint.setGravity(Gravity.CENTER);hint.setBackground(pill(Color.argb(235,40,47,62)));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(310),dp(42),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(22);root.addView(hint,hp);
        TextView sub=tv("只有录制时会接管触摸，完成后前台页面恢复正常操作",11,Color.WHITE);sub.setGravity(Gravity.CENTER);sub.setBackground(pill(Color.argb(205,58,65,80)));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(330),dp(36),Gravity.TOP|Gravity.CENTER_HORIZONTAL);sp.topMargin=dp(70);root.addView(sub,sp);

        Button done=btn("完成录制");done.setTextColor(Color.WHITE);done.setTextSize(13);done.setTypeface(Typeface.DEFAULT_BOLD);done.setBackground(pill(Color.rgb(66,92,235)));
        FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(150),dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=dp(28);root.addView(done,dlp);done.setOnClickListener(v->closeRecorder());

        root.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){if(e.getY()<dp(115)||e.getY()>root.getHeight()-dp(95))return true;recStartX=e.getRawX();recStartY=e.getRawY();recStartAt=System.currentTimeMillis();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){if(recStartAt==0)return true;float ex=e.getRawX(),ey=e.getRawY();float dd=(float)Math.hypot(ex-recStartX,ey-recStartY);long dur=Math.max(100,System.currentTimeMillis()-recStartAt);TaskProfile t=Prefs.current(this);if(dd<dp(22))t.actions.add(TaskProfile.Action.tap(recStartX,recStartY));else t.actions.add(TaskProfile.Action.swipe(recStartX,recStartY,ex,ey,dur));Prefs.upsert(this,t);renderActions(root,t.actions);recStartAt=0;return true;}return true;});

        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;wm.addView(root,lp);renderActions(root,Prefs.current(this).actions);
    }

    private void renderActions(FrameLayout root,List<TaskProfile.Action> actions){
        for(int i=root.getChildCount()-1;i>=0;i--){View c=root.getChildAt(i);Object tag=c.getTag();if("marker".equals(tag))root.removeViewAt(i);}
        for(int i=0;i<actions.size();i++){TaskProfile.Action a=actions.get(i);if(TaskProfile.Action.SWIPE.equals(a.type)){addLine(root,a.x1,a.y1,a.x2,a.y2);addMarker(root,a.x1,a.y1,"S"+(i+1));addMarker(root,a.x2,a.y2,"→");}else addMarker(root,a.x1,a.y1,String.valueOf(i+1));}
    }
    private void addMarker(FrameLayout root,float x,float y,String label){TextView m=tv(label,11,Color.WHITE);m.setTag("marker");m.setGravity(Gravity.CENTER);m.setBackground(pill(Color.rgb(66,92,235)));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(32),dp(32));lp.leftMargin=(int)x-dp(16);lp.topMargin=(int)y-dp(16);root.addView(m,lp);}
    private void addLine(FrameLayout root,float x1,float y1,float x2,float y2){float dist=(float)Math.hypot(x2-x1,y2-y1);View line=new View(this);line.setTag("marker");line.setBackgroundColor(Color.rgb(91,114,242));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)dist,dp(3));lp.leftMargin=(int)x1;lp.topMargin=(int)y1;line.setPivotX(0);line.setPivotY(dp(1.5f));line.setRotation((float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)));root.addView(line,lp);}
    private void closeRecorder(){if(recorder!=null){try{wm.removeView(recorder);}catch(Exception ignored){}recorder=null;}if(panel!=null){panel.setVisibility(View.VISIBLE);refreshStatus();}}

    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);AutoClickAccessibilityService.stopNow();if(recorder!=null)try{wm.removeView(recorder);}catch(Exception ignored){}if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(bubble!=null)try{wm.removeView(bubble);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
