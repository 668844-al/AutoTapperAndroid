package com.example.autotapper;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.List;

public class OverlayService extends Service {
    public static final String ACTION_RUN_SCHEDULE = "com.example.autotapper.RUN_SCHEDULE";
    public static final String EXTRA_TASK_ID = "task_id";
    private static final String CHANNEL = "autotapper_running_v2";
    private WindowManager wm;
    private View panel,bubble;
    private FrameLayout recorder;
    private WindowManager.LayoutParams panelLp,bubbleLp;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView statusText,hintText,bubbleText;
    private Button startButton;
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
                if(AutoClickAccessibilityService.isConnected()) AutoClickAccessibilityService.startTask(Prefs.currentTaskId(this));
                else Toast.makeText(this,"无障碍服务未开启，无法执行定时任务",Toast.LENGTH_LONG).show();
                refreshStatus();
            },1800);
        }
        return START_STICKY;
    }

    private final Runnable ticker=new Runnable(){@Override public void run(){refreshStatus();handler.postDelayed(this,500);}};
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"连点器运行状态",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification buildNotification(String text){Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(R.drawable.ic_stat_tap).setContentTitle("轻触连点器").setContentText(text).setContentIntent(pi).setOngoing(true).build();}

    private GradientDrawable panelBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(244,248,253),Color.rgb(235,241,248),Color.rgb(231,238,247)});g.setCornerRadius(dp(26));g.setStroke(dp(1),Color.argb(165,188,214,245));return g;}
    private GradientDrawable blueBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(80,214,255),Color.rgb(17,123,255),Color.rgb(25,79,245)});g.setCornerRadius(dp(22));g.setStroke(dp(1),Color.argb(205,112,225,255));return g;}
    private GradientDrawable lightBg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(95,255,255,255));g.setCornerRadius(dp(14));return g;}
    private GradientDrawable bubbleBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(240,247,255),Color.rgb(224,236,249),Color.rgb(212,227,246)});g.setShape(GradientDrawable.OVAL);g.setStroke(dp(1),Color.argb(175,156,192,236));return g;}
    private TextView actionTv(String icon,String text){TextView t=new TextView(this);t.setText(icon+"  "+text);t.setTextColor(Color.WHITE);t.setTextSize(18);t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);t.setBackground(blueBg());t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setIncludeFontPadding(false);t.setElevation(dp(4));return t;}
    private TextView plainTv(int sp,int color){TextView t=new TextView(this);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private Button shrinkBtn(){Button b=new Button(this);b.setText("⌄⌄");b.setAllCaps(false);b.setTextSize(18);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.rgb(27,124,240));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(247,249,253),Color.rgb(226,236,248)});g.setShape(GradientDrawable.OVAL);g.setStroke(dp(1),Color.argb(165,196,215,240));b.setBackground(g);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(0,0,0,0);return b;}

    private void showPanel(){
        if(!Settings.canDrawOverlays(this)||panel!=null)return;
        if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;bubbleText=null;}
        FrameLayout outer=new FrameLayout(this);outer.setClipChildren(false);outer.setClipToPadding(false);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(panelBg());card.setElevation(dp(10));
        View dragStrip=new View(this);dragStrip.setBackgroundColor(Color.TRANSPARENT);card.addView(dragStrip,new LinearLayout.LayoutParams(-1,dp(14)));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);
        TextView recordBtn=actionTv("◎","录制");TextView undoBtn=actionTv("↶","撤销");
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(72),1);p1.rightMargin=dp(8);top.addView(recordBtn,p1);top.addView(undoBtn,new LinearLayout.LayoutParams(0,dp(72),1));card.addView(top);
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);bottom.setPadding(0,dp(10),0,0);
        startButton=new Button(this);startButton.setAllCaps(false);startButton.setTextSize(22);startButton.setTypeface(Typeface.DEFAULT_BOLD);startButton.setTextColor(Color.WHITE);startButton.setBackground(blueBg());startButton.setPadding(dp(12),dp(12),dp(12),dp(12));startButton.setElevation(dp(4));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(148),dp(88));sp.rightMargin=dp(10);bottom.addView(startButton,sp);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setBackground(lightBg());info.setPadding(dp(10),dp(10),dp(10),dp(10));LinearLayout.LayoutParams infop=new LinearLayout.LayoutParams(0,dp(88),1);
        statusText=plainTv(15,Color.rgb(75,92,125));statusText.setGravity(Gravity.CENTER_VERTICAL);info.addView(statusText,new LinearLayout.LayoutParams(-1,dp(28)));
        View line=new View(this);line.setBackgroundColor(Color.argb(120,188,202,225));info.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        hintText=plainTv(14,Color.rgb(75,92,125));hintText.setPadding(0,dp(8),0,0);hintText.setGravity(Gravity.CENTER_VERTICAL);info.addView(hintText,new LinearLayout.LayoutParams(-1,0,1));
        bottom.addView(info,infop);card.addView(bottom);outer.addView(card,new FrameLayout.LayoutParams(dp(310),FrameLayout.LayoutParams.WRAP_CONTENT));
        Button shrink=shrinkBtn();FrameLayout.LayoutParams slp=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.END|Gravity.BOTTOM);slp.rightMargin=-dp(10);slp.bottomMargin=-dp(8);outer.addView(shrink,slp);
        panel=outer;panelLp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);panelLp.gravity=Gravity.TOP|Gravity.START;panelLp.x=dp(18);panelLp.y=dp(160);wm.addView(panel,panelLp);
        attachLongPressDrag(dragStrip,panelLp,panel);
        recordBtn.setOnClickListener(v->showRecorder());
        undoBtn.setOnClickListener(v->{TaskProfile t=Prefs.current(this);if(!t.actions.isEmpty()){t.actions.remove(t.actions.size()-1);Prefs.upsert(this,t);}refreshStatus();});
        startButton.setOnClickListener(v->toggleStartPause());
        shrink.setOnClickListener(v->collapse());
        refreshStatus();
    }

    private void toggleStartPause(){TaskProfile t=Prefs.current(this);if(!AutoClickAccessibilityService.isConnected()){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_SHORT).show();return;}if(t.actions.isEmpty()){Toast.makeText(this,"请先点录制，再点目标位置",Toast.LENGTH_SHORT).show();return;}if(AutoClickAccessibilityService.isRunning())AutoClickAccessibilityService.stopNow();else AutoClickAccessibilityService.startNow();refreshStatus();}

    private void refreshStatus(){
        TaskProfile t=Prefs.current(this);int taps=0,swipes=0;for(TaskProfile.Action a:t.actions){if(TaskProfile.Action.SWIPE.equals(a.type))swipes++;else taps++;}
        boolean running=AutoClickAccessibilityService.isRunning();int total=t.actions.size();String run=running?"运行中":"已暂停";String rate=t.ratePerMin+"次/分";String count=total+"动作";String cycle=t.maxCycles>0?(AutoClickAccessibilityService.cyclesDone()+"/"+t.maxCycles+"轮"):count;
        if(startButton!=null)startButton.setText(running?"⏸  暂停":"▶  开始");
        if(statusText!=null)statusText.setText(run+"  |  "+rate+"  |  "+count);
        if(hintText!=null){if(!AutoClickAccessibilityService.isConnected())hintText.setText("请先开启无障碍权限");else if(total==0)hintText.setText("先点“录制”，再点目标位置");else if(running)hintText.setText("正在执行，可点“暂停”停止");else hintText.setText("已录制 "+taps+" 点"+(swipes>0?" + "+swipes+" 滑":"")+"，点“开始”即可执行");}
        if(bubbleText!=null)bubbleText.setText(run+"\n"+(t.maxCycles>0?cycle:count)+"\n"+rate);
    }

    private void collapse(){
        if(panel!=null){try{wm.removeView(panel);}catch(Exception ignored){}panel=null;statusText=null;hintText=null;startButton=null;}
        if(bubble!=null)return;
        TextView b=new TextView(this);b.setGravity(Gravity.CENTER);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.rgb(41,76,132));b.setBackground(bubbleBg());b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setLineSpacing(0,1.05f);b.setIncludeFontPadding(false);bubbleText=b;bubble=b;
        bubbleLp=new WindowManager.LayoutParams(dp(78),dp(78),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);bubbleLp.gravity=Gravity.TOP|Gravity.START;bubbleLp.x=dp(18);bubbleLp.y=dp(240);wm.addView(bubble,bubbleLp);attachBubbleTouch(bubble,bubbleLp);refreshStatus();
    }
    private void expand(){if(bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}bubble=null;bubbleText=null;}showPanel();}

    private void attachLongPressDrag(View handle,WindowManager.LayoutParams lp,View target){handle.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean dragging;final Runnable longPress=()->dragging=true;@Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;dragging=false;handler.postDelayed(longPress,320);return true;case MotionEvent.ACTION_MOVE:if(dragging){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(target,lp);}catch(Exception ignored){}}else if(Math.abs(e.getRawX()-dx)+Math.abs(e.getRawY()-dy)>dp(10))handler.removeCallbacks(longPress);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(longPress);return dragging;}return false;}});}
    private void attachBubbleTouch(View target,WindowManager.LayoutParams lp){target.setOnTouchListener(new View.OnTouchListener(){float dx,dy;int sx,sy;boolean dragging;final Runnable longPress=()->dragging=true;@Override public boolean onTouch(View v,MotionEvent e){switch(e.getAction()){case MotionEvent.ACTION_DOWN:dx=e.getRawX();dy=e.getRawY();sx=lp.x;sy=lp.y;dragging=false;handler.postDelayed(longPress,320);return true;case MotionEvent.ACTION_MOVE:if(dragging){lp.x=sx+(int)(e.getRawX()-dx);lp.y=sy+(int)(e.getRawY()-dy);try{wm.updateViewLayout(target,lp);}catch(Exception ignored){}}else if(Math.abs(e.getRawX()-dx)+Math.abs(e.getRawY()-dy)>dp(10))handler.removeCallbacks(longPress);return true;case MotionEvent.ACTION_UP:handler.removeCallbacks(longPress);if(!dragging)expand();return true;case MotionEvent.ACTION_CANCEL:handler.removeCallbacks(longPress);return false;}return false;}});}

    private void showRecorder(){
        if(recorder!=null)return;if(panel!=null)panel.setVisibility(View.GONE);if(bubble!=null)bubble.setVisibility(View.GONE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.argb(16,28,47,88));recorder=root;
        TextView hint=plainTv(13,Color.WHITE);hint.setText("轻点 = 点击位置   ·   拖动 = 滑动手势");hint.setGravity(Gravity.CENTER);GradientDrawable hg=new GradientDrawable();hg.setColor(Color.argb(210,36,63,115));hg.setCornerRadius(dp(16));hint.setBackground(hg);hint.setPadding(dp(14),dp(10),dp(14),dp(10));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(24);root.addView(hint,hp);
        Button done=new Button(this);done.setText("完成录制");done.setAllCaps(false);done.setTextSize(16);done.setTypeface(Typeface.DEFAULT_BOLD);done.setTextColor(Color.WHITE);done.setBackground(blueBg());FrameLayout.LayoutParams dlp=new FrameLayout.LayoutParams(dp(138),dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dlp.bottomMargin=dp(32);root.addView(done,dlp);done.setOnClickListener(v->closeRecorder());
        root.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){if(e.getY()<dp(85)||e.getY()>root.getHeight()-dp(95))return true;recStartX=e.getRawX();recStartY=e.getRawY();recStartAt=System.currentTimeMillis();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(recStartAt==0)return true;float ex=e.getRawX(),ey=e.getRawY();float dd=(float)Math.hypot(ex-recStartX,ey-recStartY);long dur=Math.max(100,System.currentTimeMillis()-recStartAt);TaskProfile t=Prefs.current(this);if(dd<dp(22))t.actions.add(TaskProfile.Action.tap(recStartX,recStartY));else t.actions.add(TaskProfile.Action.swipe(recStartX,recStartY,ex,ey,dur));Prefs.upsert(this,t);renderActions(root,t.actions);recStartAt=0;refreshStatus();return true;}return true;});
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.START;wm.addView(root,lp);renderActions(root,Prefs.current(this).actions);
    }

    private void renderActions(FrameLayout root,List<TaskProfile.Action> actions){for(int i=root.getChildCount()-1;i>=0;i--){View c=root.getChildAt(i);if("marker".equals(c.getTag()))root.removeViewAt(i);}for(int i=0;i<actions.size();i++){TaskProfile.Action a=actions.get(i);if(TaskProfile.Action.SWIPE.equals(a.type)){addLine(root,a.x1,a.y1,a.x2,a.y2);addMarker(root,a.x1,a.y1,"S"+(i+1));addMarker(root,a.x2,a.y2,"→");}else addMarker(root,a.x1,a.y1,String.valueOf(i+1));}}
    private void addMarker(FrameLayout root,float x,float y,String label){TextView m=plainTv(11,Color.WHITE);m.setTag("marker");m.setText(label);m.setGravity(Gravity.CENTER);GradientDrawable g=new GradientDrawable();g.setColor(Color.argb(235,67,121,252));g.setCornerRadius(dp(17));m.setBackground(g);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(34),dp(34));lp.leftMargin=(int)x-dp(17);lp.topMargin=(int)y-dp(17);root.addView(m,lp);}
    private void addLine(FrameLayout root,float x1,float y1,float x2,float y2){float dist=(float)Math.hypot(x2-x1,y2-y1);View line=new View(this);line.setTag("marker");line.setBackgroundColor(Color.argb(220,90,143,255));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)dist,dp(3));lp.leftMargin=(int)x1;lp.topMargin=(int)y1;line.setPivotX(0f);line.setPivotY(dp(1.5f));line.setRotation((float)Math.toDegrees(Math.atan2(y2-y1,x2-x1)));root.addView(line,lp);}
    private void closeRecorder(){if(recorder!=null){try{wm.removeView(recorder);}catch(Exception ignored){}recorder=null;}if(panel!=null)panel.setVisibility(View.VISIBLE);if(bubble!=null)bubble.setVisibility(View.VISIBLE);refreshStatus();}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);} 

    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);AutoClickAccessibilityService.stopNow();if(recorder!=null)try{wm.removeView(recorder);}catch(Exception ignored){}if(panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(bubble!=null)try{wm.removeView(bubble);}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
