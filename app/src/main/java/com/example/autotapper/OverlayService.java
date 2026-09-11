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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class OverlayService extends Service {
    public static final String ACTION_RUN_SCHEDULE="com.example.autotapper.RUN_SCHEDULE";
    public static final String EXTRA_TASK_ID="task_id";
    private static final String CHANNEL="autotapper_running_ios";
    private static final int BLUE=Color.rgb(65,139,255);
    private static final int DARK=Color.rgb(38,53,82);
    private static final int MUTED=Color.rgb(111,128,157);

    private WindowManager wm;
    private View panel,bubble;
    private FrameLayout recorder;
    private WindowManager.LayoutParams panelLp,bubbleLp;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private TextView stateText,clickCountText,durationText,guideText,startLabel,bubbleBadge;
    private long runStartedAt=0L;
    private float recStartX,recStartY;
    private long recStartAt;

    @Override public void onCreate(){
        super.onCreate();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        createChannel();
        startForeground(501,buildNotification("悬浮控制已启动"));
        if(Settings.canDrawOverlays(this))showPanel();
        handler.post(ticker);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_RUN_SCHEDULE.equals(intent.getAction())){
            String taskId=intent.getStringExtra(EXTRA_TASK_ID);
            if(taskId!=null)Prefs.setCurrentTaskId(this,taskId);
            handler.postDelayed(()->{
                if(AutoClickAccessibilityService.isConnected()){
                    runStartedAt=System.currentTimeMillis();
                    AutoClickAccessibilityService.startTask(Prefs.currentTaskId(this));
                }else Toast.makeText(this,"无障碍服务未开启，无法执行定时任务",Toast.LENGTH_LONG).show();
                refreshStatus();
            },1800);
        }
        if(panel==null&&bubble==null&&recorder==null&&Settings.canDrawOverlays(this))showPanel();
        return START_STICKY;
    }

    private final Runnable ticker=new Runnable(){@Override public void run(){refreshStatus();handler.postDelayed(this,500);}};

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"连点器运行状态",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification buildNotification(String text){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(R.drawable.ic_stat_tap).setContentTitle("连点器").setContentText(text).setContentIntent(pi).setOngoing(true).build();}

    private GradientDrawable panelBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(250,255,255,255),Color.argb(242,244,250,255)});g.setCornerRadius(dp(23));g.setStroke(dp(1),Color.argb(135,202,220,245));return g;}
    private GradientDrawable softBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(170,242,247,255));g.setCornerRadius(dp(16));g.setStroke(dp(1),Color.argb(100,210,224,245));return g;}
    private GradientDrawable roundBg(int color){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setShape(GradientDrawable.OVAL);return g;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}

    private void showPanel(){
        if(!Settings.canDrawOverlays(this)||panel!=null)return;
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;bubbleBadge=null;}

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(12),dp(14),dp(12));root.setBackground(panelBg());root.setElevation(dp(10));

        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("悬浮窗",15,DARK,true);header.addView(title,new LinearLayout.LayoutParams(-2,dp(30)));
        TextView dot=text("●",10,Color.rgb(83,200,120),true);dot.setPadding(dp(6),0,dp(3),0);header.addView(dot,new LinearLayout.LayoutParams(-2,dp(30)));
        stateText=text("已暂停",11,MUTED,false);header.addView(stateText,new LinearLayout.LayoutParams(0,dp(30),1));
        TextView close=text("×",20,Color.rgb(90,104,132),false);close.setGravity(Gravity.CENTER);header.addView(close,new LinearLayout.LayoutParams(dp(34),dp(30)));
        root.addView(header);

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.setPadding(0,dp(8),0,dp(8));
        LinearLayout clicks=statBox("⌁","已点击");clickCountText=(TextView)((LinearLayout)clicks.getChildAt(1)).getChildAt(1);stats.addView(clicks,weightStat());
        LinearLayout duration=statBox("◷","运行时长");durationText=(TextView)((LinearLayout)duration.getChildAt(1)).getChildAt(1);stats.addView(duration,weightStat());root.addView(stats);

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.setGravity(Gravity.CENTER);
        LinearLayout start=control("Ⅱ","暂停",Color.rgb(232,242,255),Color.rgb(58,126,239));
        LinearLayout stop=control("■","停止",Color.rgb(255,236,239),Color.rgb(232,93,112));
        LinearLayout settings=control("⚙","设置",Color.rgb(235,244,255),Color.rgb(65,116,210));
        LinearLayout fold=control("⌄","收起",Color.rgb(237,246,255),Color.rgb(63,123,221));
        controls.addView(start,weightControl());controls.addView(stop,weightControl());controls.addView(settings,weightControl());controls.addView(fold,weightControl());root.addView(controls);
        startLabel=(TextView)start.getChildAt(1);

        LinearLayout editRow=new LinearLayout(this);editRow.setOrientation(LinearLayout.HORIZONTAL);editRow.setPadding(0,dp(8),0,0);
        TextView record=chip("◎  录制");TextView undo=chip("↶  撤销");editRow.addView(record,weightChip());editRow.addView(undo,weightChip());root.addView(editRow);
        guideText=text("先点“录制”，再点目标位置",11,MUTED,false);guideText.setGravity(Gravity.CENTER);guideText.setPadding(0,dp(8),0,0);root.addView(guideText,new LinearLayout.LayoutParams(-1,dp(28)));

        close.setOnClickListener(v->stopSelf());
        start.setOnClickListener(v->toggleRun());
        stop.setOnClickListener(v->{AutoClickAccessibilityService.stopNow();runStartedAt=0;refreshStatus();});
        settings.setOnClickListener(v->{Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(i);});
        fold.setOnClickListener(v->collapse());
        record.setOnClickListener(v->showRecorder());
        undo.setOnClickListener(v->{TaskProfile t=Prefs.current(this);if(!t.actions.isEmpty()){t.actions.remove(t.actions.size()-1);Prefs.upsert(this,t);}refreshStatus();});

        panel=root;
        panelLp=new WindowManager.LayoutParams(dp(300),WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        panelLp.gravity=Gravity.TOP|Gravity.START;panelLp.x=dp(14);panelLp.y=dp(150);wm.addView(panel,panelLp);
        attachLongPressDrag(root,panelLp,panel);
        refreshStatus();
    }

    private LinearLayout statBox(String icon,String label){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(10),dp(8),dp(8),dp(8));box.setBackground(softBg());TextView i=text(icon,18,BLUE,true);i.setGravity(Gravity.CENTER);box.addView(i,new LinearLayout.LayoutParams(dp(32),dp(38)));LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);TextView top=text(label,10,MUTED,false);TextView value=text("0",14,DARK,true);labels.addView(top);labels.addView(value);box.addView(labels,new LinearLayout.LayoutParams(0,dp(38),1));return box;}
    private LinearLayout.LayoutParams weightStat(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout control(String icon,String label,int bg,int fg){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);TextView i=text(icon,18,fg,true);i.setGravity(Gravity.CENTER);i.setBackground(roundBg(bg));c.addView(i,new LinearLayout.LayoutParams(dp(42),dp(42)));TextView l=text(label,10,DARK,false);l.setGravity(Gravity.CENTER);c.addView(l,new LinearLayout.LayoutParams(-1,dp(24)));return c;}
    private LinearLayout.LayoutParams weightControl(){return new LinearLayout.LayoutParams(0,dp(70),1);}
    private TextView chip(String label){TextView t=text(label,11,Color.rgb(72,104,160),true);t.setGravity(Gravity.CENTER);t.setBackground(softBg());return t;}
    private LinearLayout.LayoutParams weightChip(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1);p.setMargins(dp(3),0,dp(3),0);return p;}

    private void toggleRun(){
        TaskProfile t=Prefs.current(this);
        if(!AutoClickAccessibilityService.isConnected()){Toast.makeText(this,"请先开启无障碍权限",Toast.LENGTH_SHORT).show();return;}
        if(t.actions.isEmpty()){Toast.makeText(this,"请先录制点击位置",Toast.LENGTH_SHORT).show();return;}
        if(AutoClickAccessibilityService.isRunning()){AutoClickAccessibilityService.stopNow();runStartedAt=0;}else{runStartedAt=System.currentTimeMillis();AutoClickAccessibilityService.startNow();}
        refreshStatus();
    }

    private void refreshStatus(){
        TaskProfile t=Prefs.current(this);int taps=0,swipes=0;for(TaskProfile.Action a:t.actions)if(TaskProfile.Action.SWIPE.equals(a.type))swipes++;else taps++;
        boolean running=AutoClickAccessibilityService.isRunning();if(running&&runStartedAt==0)runStartedAt=System.currentTimeMillis();if(!running)runStartedAt=0;
        long cycles=AutoClickAccessibilityService.cyclesDone();long approx=Math.max(0,cycles*Math.max(1,t.actions.size()));
        if(stateText!=null)stateText.setText(running?"运行中":"已暂停");
        if(clickCountText!=null)clickCountText.setText(approx+" 次");
        if(durationText!=null)durationText.setText(formatDuration(running?System.currentTimeMillis()-runStartedAt:0));
        if(startLabel!=null)startLabel.setText(running?"暂停":"开始");
        if(guideText!=null){if(t.actions.isEmpty())guideText.setText("先点“录制”，再点目标位置");else if(running)guideText.setText(t.ratePerMin+"次/分 · 正在执行");else guideText.setText("已录制 "+taps+" 点"+(swipes>0?" + "+swipes+" 滑":"")+" · 点“开始”执行");}
        if(bubbleBadge!=null)bubbleBadge.setText(running?String.valueOf(approx):"Ⅱ");
    }

    private String formatDuration(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.getDefault(),"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}

    private void collapse(){
        if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;stateText=null;clickCountText=null;durationText=null;guideText=null;startLabel=null;}
        if(bubble!=null)return;
        FrameLayout root=new FrameLayout(this);root.setBackground(roundBg(Color.argb(245,240,247,255)));root.setElevation(dp(10));
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.app_icon);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(avatar,new FrameLayout.LayoutParams(dp(68),dp(68)));
        TextView statusDot=text("●",9,Color.rgb(64,205,117),true);statusDot.setGravity(Gravity.CENTER);FrameLayout.LayoutParams dp1=new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.END|Gravity.TOP);dp1.rightMargin=-dp(2);dp1.topMargin=-dp(2);root.addView(statusDot,dp1);
        bubbleBadge=text("Ⅱ",10,Color.WHITE,true);bubbleBadge.setGravity(Gravity.CENTER);GradientDrawable badge=new GradientDrawable();badge.setColor(Color.argb(210,51,118,236));badge.setCornerRadius(dp(10));bubbleBadge.setBackground(badge);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(42),dp(20),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);bp.bottomMargin=dp(4);root.addView(bubbleBadge,bp);
        bubble=root;
        bubbleLp=new WindowManager.LayoutParams(dp(68),dp(68),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);bubbleLp.gravity=Gravity.TOP|Gravity.START;bubbleLp.x=dp(14);bubbleLp.y=dp(230);wm.addView(bubble,bubbleLp);attachBubbleTouch(bubble,bubbleLp);refreshStatus();
    }

    private void expand(){if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;bubbleBadge=null;}showPanel();}

    private void attachLongPressDrag(View target,WindowManager.LayoutParams lp,View view){target.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean dragging;final Runnable longPress=()->dragging=true;@Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;dragging=false;handler.postDelayed(longPress,320);return true;case MotionEvent.ACTION_MOVE:if(dragging){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(view,lp);}catch(Exception ignored){}}else if(Math.abs(e.getRawX()-dx)+Math.abs(e.getRawY()-dy)>dp(12))handler.removeCallbacks(longPress);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(longPress);return dragging;}return false;}});}
    private void attachBubbleTouch(View target,WindowManager.LayoutParams lp){target.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean dragging;final Runnable longPress=()->dragging=true;@Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;dragging=false;handler.postDelayed(longPress,320);return true;case MotionEvent.ACTION_MOVE:if(dragging){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(target,lp);}catch(Exception ignored){}}else if(Math.abs(e.getRawX()-dx)+Math.abs(e.getRawY()-dy)>dp(10))handler.removeCallbacks(longPress);return true;case MotionEvent.ACTION_UP:handler.removeCallbacks(longPress);if(!dragging)expand();return true;case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(longPress);return false;}return false;}});}

    private void showRecorder(){
        if(recorder!=null)return;if(panel!=null)panel.setVisibility(View.GONE);if(bubble!=null)bubble.setVisibility(View.GONE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.argb(10,45,75,125));recorder=root;
        TextView hint=text("录制模式：轻点=点击位置 · 拖动=滑动",12,Color.WHITE,true);hint.setGravity(Gravity.CENTER);GradientDrawable hg=new GradientDrawable();hg.setColor(Color.argb(220,48,71,111));hg.setCornerRadius(dp(18));hint.setBackground(hg);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(320),dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(24);root.addView(hint,hp);
        Button done=new Button(this);done.setText("完成录制");done.setAllCaps(false);done.setTextSize(14);done.setTypeface(Typeface.DEFAULT_BOLD);done.setTextColor(Color.WHITE);GradientDrawable dg=new GradientDrawable();dg.setColor(BLUE);dg.setCornerRadius(dp(18));done.setBackground(dg);FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(150),dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=dp(32);root.addView(done,dlp);done.setOnClickListener(v->closeRecorder());
        root.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){if(e.getY()<dp(85)||e.getY()>root.getHeight()-dp(95))return true;recStartX=e.getRawX();recStartY=e.getRawY();recStartAt=System.currentTimeMillis();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(recStartAt==0)return true;float ex=e.getRawX(),ey=e.getRawY();float dd=(float)Math.hypot(ex-recStartX,ey-recStartY);long dur=Math.max(100,System.currentTimeMillis()-recStartAt);TaskProfile t=Prefs.current(this);if(dd<dp(22))t.actions.add(TaskProfile.Action.tap(recStartX,recStartY));else t.actions.add(TaskProfile.Action.swipe(recStartX,recStartY,ex,ey,dur));Prefs.upsert(this,t);renderActions(root,t.actions);recStartAt=0;refreshStatus();return true;}return true;});
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;wm.addView(root,lp);renderActions(root,Prefs.current(this).actions);
    }

    private void renderActions(FrameLayout root,List<TaskProfile.Action> actions){for(int i=root.getChildCount()-1;i>=0;i--){View c=root.getChildAt(i);if("marker".equals(c.getTag()))root.removeViewAt(i);}for(int i=0;i<actions.size();i++){TaskProfile.Action a=actions.get(i);if(TaskProfile.Action.SWIPE.equals(a.type)){addLine(root,a.x1,a.y1,a.x2,a.y2);addMarker(root,a.x1,a.y1,"S"+(i+1));addMarker(root,a.x2,a.y2,"→");}else addMarker(root,a.x1,a.y1,String.valueOf(i+1));}}
    private void addMarker(FrameLayout root,float x,float y,String label){TextView m=text(label,11,Color.WHITE,true);m.setTag("marker");m.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(235,65,139,255));g.setShape(GradientDrawable.OVAL);m.setBackground(g);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(34),dp(34));lp.leftMargin=(int)x-dp(17);lp.topMargin=(int)y-dp(17);root.addView(m,lp);}
    private void addLine(FrameLayout root,float x1,float y1,float x2,float y2){float dist=(float)Math.hypot(x2-x1,y2-y1);View line=new View(this);line.setTag("marker");line.setBackgroundColor(Color.rgb(73,148,255));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)dist,dp(3));lp.leftMargin=(int)x1;lp.topMargin=(int)y1;line.setPivotX(0);line.setPivotY(dp(1.5f));line.setRotation((float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)));root.addView(line,lp);}
    private void closeRecorder(){if(recorder!=null){try{wm.removeView(recorder);}catch(Exception ignored){}recorder=null;}if(panel!=null)panel.setVisibility(View.VISIBLE);if(bubble!=null)bubble.setVisibility(View.VISIBLE);refreshStatus();}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);AutoClickAccessibilityService.stopNow();if(recorder!=null)try{wm.removeView(recorder);}catch(Exception ignored){}if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(bubble!=null)try{wm.removeView(bubble);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
