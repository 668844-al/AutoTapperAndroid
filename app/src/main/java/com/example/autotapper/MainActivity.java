package com.example.autotapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final List<AppItem> apps=new ArrayList<>();
    private Spinner appSpinner,repeatSpinner;
    private EditText nameEdit,randomMinEdit,randomMaxEdit,cyclesEdit,durationEdit;
    private TextView permissionText,rateText,actionText,scheduleText,currentTaskText;
    private SeekBar rateSeek;
    private DatePicker datePicker; private TimePicker timePicker;
    private LinearLayout taskListBox;
    private boolean loading=false;

    @Override protected void onCreate(Bundle b){super.onCreate(b);loadApps();setContentView(buildUi());refreshAll();requestNotificationPermission();}
    @Override protected void onResume(){super.onResume();refreshAll();}

    private View buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        GradientDrawable page=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(244,247,255),Color.rgb(250,252,255),Color.rgb(241,246,255)});scroll.setBackground(page);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(24),dp(16),dp(34));scroll.addView(root);
        TextView title=text("轻触连点器",29,Color.rgb(19,27,46));title.setTypeface(Typeface.DEFAULT_BOLD);root.addView(title);
        TextView sub=text("任务化自动点击 · 点击/滑动录制 · 定时执行 · 液态玻璃悬浮窗",13,Color.rgb(103,113,136));sub.setPadding(0,dp(4),0,dp(16));root.addView(sub);

        LinearLayout perm=card();perm.addView(section("权限状态"));permissionText=text("",13,Color.rgb(83,93,115));perm.addView(permissionText);
        LinearLayout pr=row();Button over=button("悬浮窗权限"),acc=button("无障碍权限");pr.addView(over,weight());pr.addView(acc,weight());perm.addView(pr);over.setOnClickListener(v->openOverlaySettings());acc.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(perm,cardLp());

        LinearLayout tasks=card();tasks.addView(section("任务列表 / 多套绑定方案"));currentTaskText=text("",13,Color.rgb(87,101,132));tasks.addView(currentTaskText);taskListBox=new LinearLayout(this);taskListBox.setOrientation(LinearLayout.VERTICAL);tasks.addView(taskListBox);
        LinearLayout tr=row();Button add=button("＋ 新建"),copy=button("复制当前"),del=button("删除当前");tr.addView(add,weight());tr.addView(copy,weight());tr.addView(del,weight());tasks.addView(tr);add.setOnClickListener(v->newTask());copy.setOnClickListener(v->copyTask());del.setOnClickListener(v->deleteTask());root.addView(tasks,cardLp());

        LinearLayout bind=card();bind.addView(section("当前任务绑定"));nameEdit=edit("任务名称，例如：签到 / 抢票 / 测试页面");bind.addView(nameEdit,new LinearLayout.LayoutParams(-1,dp(50)));appSpinner=new Spinner(this);appSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,apps));bind.addView(appSpinner,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout br=row();Button save=button("保存当前任务"),open=button("保存并打开 App");br.addView(save,weight());br.addView(open,weight());bind.addView(br);actionText=text("",13,Color.rgb(89,99,122));actionText.setPadding(0,dp(8),0,dp(4));bind.addView(actionText);LinearLayout ar=row();Button overlay=button("显示悬浮窗"),clear=button("清空动作");ar.addView(overlay,weight());ar.addView(clear,weight());bind.addView(ar);
        save.setOnClickListener(v->{saveCurrentForm(false);toast("任务已保存");});open.setOnClickListener(v->{saveCurrentForm(false);startOverlay();openCurrentApp();});overlay.setOnClickListener(v->startOverlay());clear.setOnClickListener(v->{Prefs.clearActions(this);refreshAll();});root.addView(bind,cardLp());

        LinearLayout execution=card();execution.addView(section("执行参数"));rateText=text("",17,Color.rgb(77,105,236));rateText.setTypeface(Typeface.DEFAULT_BOLD);execution.addView(rateText);rateSeek=new SeekBar(this);rateSeek.setMax(999);execution.addView(rateSeek,new LinearLayout.LayoutParams(-1,dp(46)));rateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(!loading){rateText.setText(rateLabel(p+1));}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){saveCurrentForm(false);}});
        execution.addView(text("随机附加间隔（毫秒）",12,Color.rgb(98,108,130)));LinearLayout rr=row();randomMinEdit=numEdit("最小 0");randomMaxEdit=numEdit("最大 0");rr.addView(randomMinEdit,weightTall());rr.addView(randomMaxEdit,weightTall());execution.addView(rr);
        execution.addView(text("停止条件：0 表示不限",12,Color.rgb(98,108,130)));LinearLayout lr=row();cyclesEdit=numEdit("循环次数");durationEdit=numEdit("运行秒数");lr.addView(cyclesEdit,weightTall());lr.addView(durationEdit,weightTall());execution.addView(lr);root.addView(execution,cardLp());

        LinearLayout sch=card();sch.addView(section("定时任务 / 重复规则"));scheduleText=text("",13,Color.rgb(87,98,121));sch.addView(scheduleText);repeatSpinner=new Spinner(this);String[] repeats={"仅一次","每天","工作日（周一至周五）","每周"};repeatSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,repeats));sch.addView(repeatSpinner,new LinearLayout.LayoutParams(-1,dp(50)));datePicker=new DatePicker(this);sch.addView(datePicker);timePicker=new TimePicker(this);timePicker.setIs24HourView(true);sch.addView(timePicker);LinearLayout sr=row();Button set=button("保存预约"),cancel=button("取消预约");sr.addView(set,weight());sr.addView(cancel,weight());sch.addView(sr);TextView note=text("到点会尝试启动绑定 App 并执行当前任务。Android 不允许应用绕过安全锁屏；部分系统还会限制后台自动拉起其他 App。",12,Color.rgb(108,116,136));note.setPadding(0,dp(8),0,0);sch.addView(note);set.setOnClickListener(v->scheduleCurrent());cancel.setOnClickListener(v->cancelSchedule());root.addView(sch,cardLp());

        LinearLayout help=card();help.addView(section("悬浮窗操作"));help.addView(text("录制动作时：轻点屏幕会保存点击位置；按住拖动会保存滑动轨迹。悬浮窗可折叠成玻璃球，也可直接切换上一/下一任务。",13,Color.rgb(72,82,105)));root.addView(help,cardLp());
        return scroll;
    }

    private void loadApps(){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> rs=getPackageManager().queryIntentActivities(i,0);for(ResolveInfo r:rs){String p=r.activityInfo.packageName;if(p.equals(getPackageName()))continue;apps.add(new AppItem(r.loadLabel(getPackageManager()).toString(),p));}Collections.sort(apps,Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));}

    private void refreshAll(){if(permissionText==null)return;loading=true;TaskProfile t=Prefs.current(this);permissionText.setText("悬浮窗："+(Settings.canDrawOverlays(this)?"已允许":"未允许")+"    无障碍："+(AutoClickAccessibilityService.isConnected()?"已连接":"未开启"));
        nameEdit.setText(t.name);int pos=0;for(int i=0;i<apps.size();i++)if(apps.get(i).pkg.equals(t.pkg)){pos=i;break;}if(!apps.isEmpty())appSpinner.setSelection(pos);rateSeek.setProgress(Math.max(0,t.ratePerMin-1));rateText.setText(rateLabel(t.ratePerMin));randomMinEdit.setText(String.valueOf(t.randomMinMs));randomMaxEdit.setText(String.valueOf(t.randomMaxMs));cyclesEdit.setText(String.valueOf(t.maxCycles));durationEdit.setText(String.valueOf(t.maxDurationSec));
        int taps=0,swipes=0;for(TaskProfile.Action a:t.actions)if(TaskProfile.Action.SWIPE.equals(a.type))swipes++;else taps++;actionText.setText("已录制："+taps+" 个点击 + "+swipes+" 个滑动，共 "+t.actions.size()+" 个动作");
        scheduleText.setText(t.scheduleAt>0?"已预约："+fmt(t.scheduleAt)+" · "+repeatName(t.repeat):"当前任务未设置预约");repeatSpinner.setSelection(repeatIndex(t.repeat));Calendar c=Calendar.getInstance();if(t.scheduleAt>System.currentTimeMillis())c.setTimeInMillis(t.scheduleAt);else c.add(Calendar.MINUTE,5);datePicker.updateDate(c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));timePicker.setHour(c.get(Calendar.HOUR_OF_DAY));timePicker.setMinute(c.get(Calendar.MINUTE));renderTaskList();loading=false;}

    private void renderTaskList(){taskListBox.removeAllViews();List<TaskProfile> list=Prefs.tasks(this);String current=Prefs.currentTaskId(this);currentTaskText.setText("共 "+list.size()+" 套方案 · 点击下方任务可切换");for(TaskProfile t:list){TextView v=text((t.id.equals(current)?"●  ":"○  ")+t.name+"   "+(t.label==null?"":t.label)+"\n    "+t.actions.size()+" 动作"+(t.scheduleAt>0?" · "+fmt(t.scheduleAt):""),13,t.id.equals(current)?Color.rgb(64,90,220):Color.rgb(73,84,108));v.setPadding(dp(10),dp(9),dp(10),dp(9));v.setBackground(pill(t.id.equals(current)?Color.argb(95,214,225,255):Color.argb(115,247,249,255)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);taskListBox.addView(v,lp);v.setOnClickListener(x->{saveCurrentForm(false);Prefs.setCurrentTaskId(this,t.id);refreshAll();});}}

    private void newTask(){saveCurrentForm(false);TaskProfile t=new TaskProfile();t.name="新任务 "+(Prefs.tasks(this).size()+1);Prefs.upsert(this,t);refreshAll();}
    private void copyTask(){saveCurrentForm(false);TaskProfile t=Prefs.current(this).copyAsNew();Prefs.upsert(this,t);refreshAll();}
    private void deleteTask(){TaskProfile t=Prefs.current(this);ScheduleManager.cancel(this,t);Prefs.delete(this,t.id);refreshAll();}

    private void saveCurrentForm(boolean includeSchedule){TaskProfile t=Prefs.current(this);t.name=nameEdit.getText().toString().trim();if(t.name.isEmpty())t.name="未命名任务";if(appSpinner.getSelectedItem()!=null){AppItem a=(AppItem)appSpinner.getSelectedItem();t.pkg=a.pkg;t.label=a.label;}t.ratePerMin=rateSeek.getProgress()+1;t.randomMinMs=Math.max(0,intOf(randomMinEdit,0));t.randomMaxMs=Math.max(t.randomMinMs,intOf(randomMaxEdit,t.randomMinMs));t.maxCycles=Math.max(0,intOf(cyclesEdit,0));t.maxDurationSec=Math.max(0,intOf(durationEdit,0));if(includeSchedule)t.repeat=repeatCode(repeatSpinner.getSelectedItemPosition());Prefs.upsert(this,t);}

    private void scheduleCurrent(){saveCurrentForm(true);TaskProfile t=Prefs.current(this);if(t.actions.isEmpty()){toast("请先通过悬浮窗录制至少一个动作");return;}AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));}toast("请允许“闹钟和提醒”后再次保存预约");return;}Calendar c=Calendar.getInstance();c.set(datePicker.getYear(),datePicker.getMonth(),datePicker.getDayOfMonth(),timePicker.getHour(),timePicker.getMinute(),0);c.set(Calendar.MILLISECOND,0);if(c.getTimeInMillis()<=System.currentTimeMillis()){toast("首次预约时间必须晚于当前时间");return;}ScheduleManager.cancel(this,t);t.scheduleAt=c.getTimeInMillis();t.repeat=repeatCode(repeatSpinner.getSelectedItemPosition());Prefs.upsert(this,t);ScheduleManager.schedule(this,t);refreshAll();toast("预约已保存");}
    private void cancelSchedule(){TaskProfile t=Prefs.current(this);ScheduleManager.cancel(this,t);t.scheduleAt=0;Prefs.upsert(this,t);refreshAll();}

    private void startOverlay(){saveCurrentForm(false);if(!Settings.canDrawOverlays(this)){openOverlaySettings();toast("请先允许悬浮窗权限");return;}Intent i=new Intent(this,OverlayService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void openCurrentApp(){TaskProfile t=Prefs.current(this);if(t.pkg==null||t.pkg.isEmpty()){toast("当前任务未绑定 App");return;}Intent i=getPackageManager().getLaunchIntentForPackage(t.pkg);if(i!=null)startActivity(i);else toast("无法打开目标 App");}
    private void openOverlaySettings(){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},33);}

    private String repeatCode(int i){return i==1?TaskProfile.REPEAT_DAILY:i==2?TaskProfile.REPEAT_WEEKDAYS:i==3?TaskProfile.REPEAT_WEEKLY:TaskProfile.REPEAT_ONCE;}
    private int repeatIndex(String s){if(TaskProfile.REPEAT_DAILY.equals(s))return 1;if(TaskProfile.REPEAT_WEEKDAYS.equals(s))return 2;if(TaskProfile.REPEAT_WEEKLY.equals(s))return 3;return 0;}
    private String repeatName(String s){return repeatIndex(s)==1?"每天":repeatIndex(s)==2?"工作日":repeatIndex(s)==3?"每周":"仅一次";}
    private String rateLabel(int r){long ms=Math.max(60L,Math.round(60000.0/Math.max(1,r)));return r+" 次/分钟 · 基础间隔约 "+ms+" ms";}
    private int intOf(EditText e,int d){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return d;}}
    private String fmt(long t){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(t));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(15),dp(14),dp(15),dp(14));GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(242,255,255,255),Color.argb(225,246,249,255)});g.setCornerRadius(dp(22));g.setStroke(dp(1),Color.WHITE);l.setBackground(g);l.setElevation(dp(5));return l;}
    private GradientDrawable pill(int c){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(15));g.setStroke(dp(1),Color.argb(170,255,255,255));return g;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(4),dp(3),0);return p;}
    private LinearLayout.LayoutParams weightTall(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(3),dp(4),dp(3),0);return p;}
    private TextView section(String s){TextView t=text(s,16,Color.rgb(30,39,61));t.setTypeface(Typeface.DEFAULT_BOLD);t.setPadding(0,0,0,dp(8));return t;}
    private TextView text(String s,int sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setLineSpacing(0,1.18f);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(Color.rgb(47,61,93));b.setBackground(pill(Color.argb(155,232,239,255)));return b;}
    private EditText edit(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextSize(14);return e;}
    private EditText numEdit(String hint){EditText e=edit(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setGravity(Gravity.CENTER_VERTICAL);return e;}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    static class AppItem{final String label,pkg;AppItem(String l,String p){label=l;pkg=p;}@Override public String toString(){return label+"  ·  "+pkg;}}
}
