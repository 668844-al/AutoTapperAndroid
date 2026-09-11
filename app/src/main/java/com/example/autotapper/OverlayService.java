package com.example.autotapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
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
    private static final String CHANNEL="autotapper_running_v2";
    private WindowManager wm;
    private View panel,bubble;
    private FrameLayout recorder;
    private WindowManager.LayoutParams panelLp,bubbleLp;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status,taskTitle;
    private float recStartX,recStartY;
    private long recStartAt;

    @Override public void onCreate(){
        super.onCreate(); wm=(WindowManager)getSystemService(WINDOW_SERVICE); createChannel(); startForeground(501,buildNotification("悬浮控制已启动"));
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
        return START_STICKY;
    }

    private final Runnable statusTicker=new Runnable(){@Override public void run(){refreshStatus();handler.postDelayed(this,500);}};

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"连点器运行状态",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification buildNotification(String text){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(R.drawable.ic_stat_tap).setContentTitle("轻触连点器").setContentText(text).setContentIntent(pi).setOngoing(true).build();}

    private GradientDrawable glass(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(238,255,255,255),Color.argb(220,244,248,255),Color.argb(230,255,255,255)});g.setCornerRadius(dp(24));g.setStroke(dp(1),Color.argb(165,255,255,255));return g;}
    private GradientDrawable pill(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(18));g.setStroke(dp(1),Color.argb(110,255,255,255));return g;}
    private TextView tv(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.rgb(40,53,82));b.setBackground(pill(Color.argb(155,238,244,255)));b.setMinHeight(0);b.setMinWidth(0);b.setPadding(dp(11),dp(6),dp(11),dp(6));return b;}
    private LinearLayout.LayoutParams smallLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));return p;}

    private void showPanel(){
        if(!Settings.canDrawOverlays(this)||panel!=null)return;
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(13),dp(10),dp(13),dp(11));root.setBackground(glass());root.setElevation(dp(18));
        TextView drag=tv("⠿  AUTO TAP",12,Color.rgb(84,94,116));drag.setGravity(Gravity.CENTER);drag.setLetterSpacing(.12f);root.addView(drag,new LinearLayout.LayoutParams(dp(268),dp(28)));
        taskTitle=tv("",16,Color.rgb(22,31,51));taskTitle.setGravity(Gravity.CENTER);taskTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(taskTitle,new LinearLayout.LayoutParams(-1,dp(31)));
        status=tv("",11,Color.rgb(90,101,125));status.setGravity(Gravity.CENTER);root.addView(status,new LinearLayout.LayoutParams(-1,dp(25)));

        LinearLayout r1=new LinearLayout(this);r1.setGravity(Gravity.CENTER);
        Button start=btn("▶ 开始/暂停"),record=btn("＋ 录制动作"),undo=btn("↶ 撤销");
        r1.addView(start,smallLp());r1.addView(record,smallLp());r1.addView(undo,smallLp());root.addView(r1);
        LinearLayout r2=new LinearLayout(this);r2.setGravity(Gravity.CENTER);
        Button prev=btn("‹ 上一任务"),open=btn("打开 App"),next=btn("下一任务 ›");
        r2.addView(prev,smallLp());r2.addView(open,smallLp());r2.addView(next,smallLp());root.addView(r2);
        LinearLayout r3=new LinearLayout(this);r3.setGravity(Gravity.CENTER);
        Button clear=btn("清空动作"),fold=btn("折叠"),close=btn("关闭");r3.addView(clear,smallLp());r3.addView(fold,smallLp());r3.addView(close,smallLp());root.addView(r3);

        start.setOnClickListener(v->{if(!AutoClickAccessibilityService.isConnected()){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show();return;}if(AutoClickAccessibilityService.isRunning())AutoClickAccessibilityService.stopNow();else AutoClickAccessibilityService.startNow();refreshStatus();});
        record.setOnClickListener(v->showRecorder());
        undo.setOnClickListener(v->{TaskProfile t=Prefs.current(this);if(!t.actions.isEmpty()){t.actions.remove(t.actions.size()-1);Prefs.upsert(this,t);}refreshStatus();});
        clear.setOnClickListener(v->{Prefs.clearActions(this);Toast.makeText(this,"动作已清空",Toast.LENGTH_SHORT).show();refreshStatus();});
        prev.setOnClickListener(v->switchTask(-1));next.setOnClickListener(v->switchTask(1));open.setOnClickListener(v->openCurrentApp());fold.setOnClickListener(v->collapse());close.setOnClickListener(v->stopSelf());

        panel=root;panelLp=new WindowManager.LayoutParams(dp(294),WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);panelLp.gravity=Gravity.TOP|Gravity.START;panelLp.x=dp(16);panelLp.y=dp(150);
        if(Build.VERSION.SDK_INT>=31){try{panelLp.flags|=WindowManager.LayoutParams.FLAG_BLUR_BEHIND;panelLp.setBlurBehindRadius(dp(14));}catch(Exception ignored){}}
        wm.addView(panel,panelLp);makeDraggable(drag,panelLp,panel);refreshStatus();
    }

    private void refreshStatus(){
        if(taskTitle==null||status==null)return;TaskProfile t=Prefs.current(this);taskTitle.setText(t.name==null||t.name.isEmpty()?"未命名任务":t.name);
        int taps=0,swipes=0;for(TaskProfile.Action a:t.actions)if(TaskProfile.Action.SWIPE.equals(a.type))swipes++;else taps++;
        String run=AutoClickAccessibilityService.isRunning()?"运行中":"已暂停";String limit=t.maxCycles>0?(" · "+AutoClickAccessibilityService.cyclesDone()+"/"+t.maxCycles+"轮"):"";
        status.setText(run+" · "+taps+"点/"+swipes+"滑 · "+t.ratePerMin+"次/分"+limit);
    }

    private void switchTask(int dir){List<TaskProfile> list=Prefs.tasks(this);if(list.isEmpty())return;String id=Prefs.currentTaskId(this);int idx=0;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id)){idx=i;break;}idx=(idx+dir+list.size())%list.size();if(AutoClickAccessibilityService.isRunning())AutoClickAccessibilityService.stopNow();Prefs.setCurrentTaskId(this,list.get(idx).id);refreshStatus();Toast.makeText(this,"已切换："+list.get(idx).name,Toast.LENGTH_SHORT).show();}
    private void openCurrentApp(){TaskProfile t=Prefs.current(this);if(t.pkg==null||t.pkg.isEmpty()){Toast.makeText(this,"当前任务未绑定 App",Toast.LENGTH_SHORT).show();return;}Intent i=getPackageManager().getLaunchIntentForPackage(t.pkg);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}else Toast.makeText(this,"无法打开目标 App",Toast.LENGTH_SHORT).show();}

    private void collapse(){if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;taskTitle=null;status=null;}TextView b=tv("●",26,Color.WHITE);b.setGravity(Gravity.CENTER);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(122,151,255),Color.rgb(84,111,244),Color.rgb(134,109,255)});bg.setShape(GradientDrawable.OVAL);bg.setStroke(dp(2),Color.argb(210,255,255,255));b.setBackground(bg);b.setElevation(dp(18));bubble=b;bubbleLp=new WindowManager.LayoutParams(dp(50),dp(50),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);bubbleLp.gravity=Gravity.TOP|Gravity.START;bubbleLp.x=dp(15);bubbleLp.y=dp(240);wm.addView(b,bubbleLp);makeBubbleDraggable(b,bubbleLp);}

    private void makeDraggable(View handle,WindowManager.LayoutParams lp,View target){handle.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(target,lp);}catch(Exception ignored){}return true;}return true;}});}
    private void makeBubbleDraggable(View view,WindowManager.LayoutParams lp){view.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean moved;@Override public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;moved=false;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float x=e.getRawX()-dx,y=e.getRawY()-dy;moved|=Math.abs(x)+Math.abs(y)>dp(6);lp.x=sx+(int)x;lp.y=sy+(int)y;try{wm.updateViewLayout(view,lp);}catch(Exception ignored){}return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(!moved){try{wm.removeView(view);}catch(Exception ignored){}bubble=null;showPanel();}return true;}return true;}});}

    private void showRecorder(){
        if(recorder!=null)return;if(panel!=null)panel.setVisibility(View.GONE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.argb(24,30,40,70));recorder=root;
        TextView hint=tv("轻点 = 连点位置   ·   拖动 = 滑动手势",13,Color.WHITE);hint.setGravity(Gravity.CENTER);hint.setBackground(pill(Color.argb(215,44,52,72)));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(300),dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(22);root.addView(hint,hp);
        Button done=btn("完成");done.setTextColor(Color.WHITE);done.setBackground(pill(Color.argb(235,79,107,245)));FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(118),dp(46),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=dp(30);root.addView(done,dlp);done.setOnClickListener(v->closeRecorder());
        root.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){if(e.getY()<dp(80)||e.getY()>root.getHeight()-dp(100))return true;recStartX=e.getRawX();recStartY=e.getRawY();recStartAt=System.currentTimeMillis();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){if(recStartAt==0)return true;float ex=e.getRawX(),ey=e.getRawY();float dd=(float)Math.hypot(ex-recStartX,ey-recStartY);long dur=Math.max(100,System.currentTimeMillis()-recStartAt);TaskProfile t=Prefs.current(this);if(dd<dp(22))t.actions.add(TaskProfile.Action.tap(recStartX,recStartY));else t.actions.add(TaskProfile.Action.swipe(recStartX,recStartY,ex,ey,dur));Prefs.upsert(this,t);renderActions(root,t.actions);recStartAt=0;return true;}return true;});
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;wm.addView(root,lp);renderActions(root,Prefs.current(this).actions);
    }

    private void renderActions(FrameLayout root,List<TaskProfile.Action> actions){
        for(int i=root.getChildCount()-1;i>=0;i--){View c=root.getChildAt(i);Object tag=c.getTag();if("marker".equals(tag))root.removeViewAt(i);}
        for(int i=0;i<actions.size();i++){TaskProfile.Action a=actions.get(i);if(TaskProfile.Action.SWIPE.equals(a.type)){addLine(root,a.x1,a.y1,a.x2,a.y2);addMarker(root,a.x1,a.y1,"S"+(i+1));addMarker(root,a.x2,a.y2,"→");}else addMarker(root,a.x1,a.y1,String.valueOf(i+1));}
    }
    private void addMarker(FrameLayout root,float x,float y,String label){TextView m=tv(label,11,Color.WHITE);m.setTag("marker");m.setGravity(Gravity.CENTER);m.setBackground(pill(Color.argb(235,85,112,246)));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(34),dp(34));lp.leftMargin=(int)x-dp(17);lp.topMargin=(int)y-dp(17);root.addView(m,lp);}
    private void addLine(FrameLayout root,float x1,float y1,float x2,float y2){float dist=(float)Math.hypot(x2-x1,y2-y1);View line=new View(this);line.setTag("marker");line.setBackgroundColor(Color.argb(220,117,139,255));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)dist,dp(3));lp.leftMargin=(int)x1;lp.topMargin=(int)y1;line.setPivotX(0);line.setPivotY(dp(1.5f));line.setRotation((float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)));root.addView(line,lp);}
    private void closeRecorder(){if(recorder!=null){try{wm.removeView(recorder);}catch(Exception ignored){}recorder=null;}if(panel!=null){panel.setVisibility(View.VISIBLE);refreshStatus();}}

    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);AutoClickAccessibilityService.stopNow();if(recorder!=null)try{wm.removeView(recorder);}catch(Exception ignored){}if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(bubble!=null)try{wm.removeView(bubble);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
