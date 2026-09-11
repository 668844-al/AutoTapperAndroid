package com.example.autotapper;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
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
    private static final int BLUE = Color.rgb(64, 91, 235);
    private final List<AppItem> apps = new ArrayList<>();

    private boolean basicMode = true;
    private boolean loading = false;
    private LinearLayout basicPanel, advancedPanel, morePanel;
    private TextView basicTab, advancedTab, permissionText;

    private SeekBar basicRateSeek, advancedRateSeek;
    private TextView basicRateText, advancedRateText, basicActionText, advancedActionText;

    private EditText taskNameEdit, randomMinEdit, randomMaxEdit, cyclesEdit, durationEdit;
    private TextView taskSummaryText, selectedAppName, selectedAppPkg, dateButton, timeButton, scheduleText;
    private ImageView selectedAppIcon;
    private Spinner repeatSpinner;
    private AppItem selectedApp;
    private Calendar scheduleCalendar = Calendar.getInstance();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadApps();
        Prefs.basic(this);
        setContentView(buildUi());
        showMode(true);
        refreshAll();
        requestNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(246, 248, 252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(32));
        scroll.addView(root);

        TextView title = text("轻触连点器", 28, Color.rgb(25, 31, 46));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = text("普通连点和预约自动连点已分开，按步骤操作即可", 13, Color.rgb(104, 112, 130));
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        LinearLayout tabs = row();
        basicTab = modeTab("基础连点\n手动打开页面后连点");
        advancedTab = modeTab("进阶预约\n绑定应用，到点自动执行");
        tabs.addView(basicTab, tabLp());
        tabs.addView(advancedTab, tabLp());
        root.addView(tabs, cardLp());
        basicTab.setOnClickListener(v -> showMode(true));
        advancedTab.setOnClickListener(v -> showMode(false));

        LinearLayout perm = card();
        LinearLayout ph = row();
        TextView ptitle = section("运行权限");
        permissionText = text("", 12, Color.rgb(93, 101, 120));
        ph.addView(ptitle, new LinearLayout.LayoutParams(0, -2, 1));
        ph.addView(permissionText);
        perm.addView(ph);
        TextView ptip = text("首次使用只需开启悬浮窗和无障碍权限。", 12, Color.rgb(119, 126, 142));
        ptip.setPadding(0, 0, 0, dp(8));
        perm.addView(ptip);
        LinearLayout pr = row();
        Button over = secondaryButton("悬浮窗权限");
        Button acc = secondaryButton("无障碍权限");
        pr.addView(over, weightButton());
        pr.addView(acc, weightButton());
        perm.addView(pr);
        over.setOnClickListener(v -> openOverlaySettings());
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(perm, cardLp());

        basicPanel = buildBasicPanel();
        advancedPanel = buildAdvancedPanel();
        root.addView(basicPanel);
        root.addView(advancedPanel);

        LinearLayout help = card();
        help.addView(section("操作提示"));
        TextView tips = text("① 第一次先开好两个权限\n② 基础连点：打开悬浮控制 → 进入目标页面 → 点“录制” → 点目标位置 → 完成 → 开始\n③ 进阶预约：选择应用 → 录制动作 → 选择日期和时间 → 保存预约\n④ 只有进入“录制模式”时会暂时接管屏幕触摸；平时悬浮框外的区域可以正常点击。", 13, Color.rgb(66, 75, 96));
        tips.setLineSpacing(dp(3), 1.18f);
        help.addView(tips);
        root.addView(help, cardLp());
        return scroll;
    }

    private LinearLayout buildBasicPanel() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout speed = card();
        speed.addView(stepTitle("1", "设置连点速度"));
        basicRateText = text("", 16, BLUE);
        basicRateText.setTypeface(Typeface.DEFAULT_BOLD);
        speed.addView(basicRateText);
        basicRateSeek = new SeekBar(this);
        basicRateSeek.setMax(999);
        speed.addView(basicRateSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        basicRateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (!loading) basicRateText.setText(rateLabel(p + 1)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { saveBasic(); }
        });
        wrap.addView(speed, cardLp());

        LinearLayout run = card();
        run.addView(stepTitle("2", "打开悬浮控制并录制点击位置"));
        basicActionText = text("", 13, Color.rgb(86, 95, 116));
        basicActionText.setPadding(0, 0, 0, dp(8));
        run.addView(basicActionText);
        Button start = primaryButton("打开悬浮控制");
        run.addView(start, fullButtonLp());
        Button clear = secondaryButton("清空已录制动作");
        run.addView(clear, fullButtonLp());
        start.setOnClickListener(v -> { saveBasic(); Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID); startOverlay(); });
        clear.setOnClickListener(v -> {
            TaskProfile t = Prefs.basic(this); t.actions.clear(); Prefs.upsert(this, t); Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID); refreshBasic();
        });
        TextView note = text("悬浮控制不会锁住前台页面；拖到不挡内容的位置即可。", 12, Color.rgb(118, 125, 142));
        note.setPadding(0, dp(8), 0, 0);
        run.addView(note);
        wrap.addView(run, cardLp());
        return wrap;
    }

    private LinearLayout buildAdvancedPanel() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout profile = card();
        profile.addView(stepTitle("1", "选择预约方案"));
        taskSummaryText = text("", 13, Color.rgb(80, 89, 109));
        taskSummaryText.setPadding(0, 0, 0, dp(8));
        profile.addView(taskSummaryText);
        LinearLayout tr = row();
        Button choose = secondaryButton("切换方案");
        Button add = secondaryButton("＋ 新建");
        Button del = secondaryButton("删除");
        tr.addView(choose, weightButton()); tr.addView(add, weightButton()); tr.addView(del, weightButton());
        profile.addView(tr);
        taskNameEdit = edit("任务名称，例如：每日签到");
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50)); ep.topMargin = dp(8); profile.addView(taskNameEdit, ep);
        choose.setOnClickListener(v -> showTaskPicker());
        add.setOnClickListener(v -> newAdvancedTask());
        del.setOnClickListener(v -> deleteAdvancedTask());
        wrap.addView(profile, cardLp());

        LinearLayout bind = card();
        bind.addView(stepTitle("2", "选择要自动打开的应用"));
        LinearLayout appBox = new LinearLayout(this);
        appBox.setOrientation(LinearLayout.HORIZONTAL);
        appBox.setGravity(Gravity.CENTER_VERTICAL);
        appBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        appBox.setBackground(roundRect(Color.rgb(247, 249, 253), 16, Color.rgb(225, 229, 238)));
        selectedAppIcon = new ImageView(this);
        appBox.addView(selectedAppIcon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(10), 0, 0, 0);
        selectedAppName = text("点击选择应用", 14, Color.rgb(38, 45, 62)); selectedAppName.setTypeface(Typeface.DEFAULT_BOLD);
        selectedAppPkg = text("支持搜索应用名称", 11, Color.rgb(115, 122, 139));
        labels.addView(selectedAppName); labels.addView(selectedAppPkg);
        appBox.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 28, Color.rgb(124, 132, 150)); arrow.setGravity(Gravity.CENTER); appBox.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(44)));
        bind.addView(appBox, new LinearLayout.LayoutParams(-1, dp(66)));
        appBox.setOnClickListener(v -> showAppPicker());
        Button openRecord = primaryButton("打开应用并显示悬浮控制");
        bind.addView(openRecord, fullButtonLp());
        advancedActionText = text("", 13, Color.rgb(84, 93, 114)); advancedActionText.setPadding(0, dp(8), 0, 0); bind.addView(advancedActionText);
        openRecord.setOnClickListener(v -> { saveAdvanced(false); if (selectedApp == null) { toast("请先选择应用"); return; } startOverlay(); openCurrentApp(); });
        wrap.addView(bind, cardLp());

        LinearLayout execution = card();
        execution.addView(stepTitle("3", "设置执行速度"));
        advancedRateText = text("", 16, BLUE); advancedRateText.setTypeface(Typeface.DEFAULT_BOLD); execution.addView(advancedRateText);
        advancedRateSeek = new SeekBar(this); advancedRateSeek.setMax(999); execution.addView(advancedRateSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        advancedRateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int p,boolean fromUser){ if(!loading) advancedRateText.setText(rateLabel(p+1)); }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){ saveAdvanced(false); }
        });
        Button more = secondaryButton("更多执行设置（随机间隔 / 循环次数 / 运行时长）");
        execution.addView(more, fullButtonLp());
        morePanel = new LinearLayout(this); morePanel.setOrientation(LinearLayout.VERTICAL); morePanel.setVisibility(View.GONE);
        morePanel.addView(text("随机附加间隔（毫秒）", 12, Color.rgb(105, 113, 130)));
        LinearLayout rr=row(); randomMinEdit=numEdit("最小 0"); randomMaxEdit=numEdit("最大 0"); rr.addView(randomMinEdit, weightField()); rr.addView(randomMaxEdit, weightField()); morePanel.addView(rr);
        morePanel.addView(text("停止条件（0 表示不限）", 12, Color.rgb(105, 113, 130)));
        LinearLayout lr=row(); cyclesEdit=numEdit("循环次数"); durationEdit=numEdit("运行秒数"); lr.addView(cyclesEdit, weightField()); lr.addView(durationEdit, weightField()); morePanel.addView(lr);
        execution.addView(morePanel);
        more.setOnClickListener(v -> morePanel.setVisibility(morePanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        wrap.addView(execution, cardLp());

        LinearLayout sch = card();
        sch.addView(stepTitle("4", "设置预约时间"));
        scheduleText = text("", 13, Color.rgb(82, 92, 113)); scheduleText.setPadding(0, 0, 0, dp(8)); sch.addView(scheduleText);
        LinearLayout dt = row();
        dateButton = selectableText("选择日期"); timeButton = selectableText("选择时间");
        dt.addView(dateButton, weightField()); dt.addView(timeButton, weightField()); sch.addView(dt);
        dateButton.setOnClickListener(v -> chooseDate()); timeButton.setOnClickListener(v -> chooseTime());
        repeatSpinner = new Spinner(this);
        String[] repeats = {"仅一次", "每天", "工作日（周一至周五）", "每周"};
        repeatSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, repeats));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(50)); sp.topMargin=dp(8); sch.addView(repeatSpinner, sp);
        Button saveSchedule = primaryButton("保存预约任务"); sch.addView(saveSchedule, fullButtonLp());
        Button cancelSchedule = secondaryButton("取消当前预约"); sch.addView(cancelSchedule, fullButtonLp());
        saveSchedule.setOnClickListener(v -> scheduleCurrent());
        cancelSchedule.setOnClickListener(v -> cancelSchedule());
        TextView note = text("到点后会尝试打开绑定应用并自动执行。手机处于密码/指纹锁屏时，Android 不允许应用绕过安全锁。", 12, Color.rgb(117, 124, 141)); note.setPadding(0, dp(8), 0, 0); sch.addView(note);
        wrap.addView(sch, cardLp());
        return wrap;
    }

    private void showMode(boolean basic) {
        basicMode = basic;
        if (basicPanel != null) basicPanel.setVisibility(basic ? View.VISIBLE : View.GONE);
        if (advancedPanel != null) advancedPanel.setVisibility(basic ? View.GONE : View.VISIBLE);
        styleModeTab(basicTab, basic);
        styleModeTab(advancedTab, !basic);
        if (basic) Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID);
        else Prefs.setCurrentTaskId(this, Prefs.currentAdvanced(this).id);
        refreshAll();
    }

    private void styleModeTab(TextView t, boolean active) {
        if (t == null) return;
        t.setTextColor(active ? Color.WHITE : Color.rgb(64, 72, 93));
        t.setBackground(roundRect(active ? BLUE : Color.WHITE, 18, active ? BLUE : Color.rgb(224, 228, 237)));
        t.setElevation(active ? dp(5) : 0);
    }

    private void refreshAll() {
        if (permissionText == null) return;
        loading = true;
        permissionText.setText((Settings.canDrawOverlays(this) ? "悬浮窗 ✓" : "悬浮窗未开") + "   " + (AutoClickAccessibilityService.isConnected() ? "无障碍 ✓" : "无障碍未开"));
        refreshBasic();
        refreshAdvanced();
        loading = false;
    }

    private void refreshBasic() {
        if (basicRateSeek == null) return;
        TaskProfile t = Prefs.basic(this);
        basicRateSeek.setProgress(Math.max(0, t.ratePerMin - 1));
        basicRateText.setText(rateLabel(t.ratePerMin));
        basicActionText.setText(t.actions.isEmpty() ? "还没有录制点击位置。打开悬浮控制后点“录制”即可。" : "已录制 " + t.actions.size() + " 个动作，可直接在悬浮窗点“开始”。");
    }

    private void refreshAdvanced() {
        if (taskNameEdit == null) return;
        TaskProfile t = Prefs.currentAdvanced(this);
        taskNameEdit.setText(t.name);
        taskSummaryText.setText("当前方案：" + t.name + (t.scheduleAt > 0 ? "  ·  " + fmt(t.scheduleAt) : ""));
        selectedApp = findApp(t.pkg);
        updateSelectedAppView();
        advancedRateSeek.setProgress(Math.max(0, t.ratePerMin - 1));
        advancedRateText.setText(rateLabel(t.ratePerMin));
        randomMinEdit.setText(String.valueOf(t.randomMinMs)); randomMaxEdit.setText(String.valueOf(t.randomMaxMs));
        cyclesEdit.setText(String.valueOf(t.maxCycles)); durationEdit.setText(String.valueOf(t.maxDurationSec));
        int taps=0, swipes=0; for(TaskProfile.Action a:t.actions) if(TaskProfile.Action.SWIPE.equals(a.type)) swipes++; else taps++;
        advancedActionText.setText(t.actions.isEmpty() ? "尚未录制动作。进入目标应用后，在悬浮窗点“录制”。" : "已录制："+taps+" 个点击"+(swipes>0?" + "+swipes+" 个滑动":""));
        repeatSpinner.setSelection(repeatIndex(t.repeat));
        if (t.scheduleAt > System.currentTimeMillis()) scheduleCalendar.setTimeInMillis(t.scheduleAt); else { scheduleCalendar = Calendar.getInstance(); scheduleCalendar.add(Calendar.MINUTE, 5); }
        updateDateTimeButtons();
        scheduleText.setText(t.scheduleAt > 0 ? "已预约：" + fmt(t.scheduleAt) + " · " + repeatName(t.repeat) : "当前方案还没有预约");
    }

    private void saveBasic() {
        TaskProfile t = Prefs.basic(this);
        t.ratePerMin = basicRateSeek.getProgress() + 1;
        Prefs.upsert(this, t);
        Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID);
    }

    private void saveAdvanced(boolean includeSchedule) {
        TaskProfile t = Prefs.currentAdvanced(this);
        t.name = taskNameEdit.getText().toString().trim(); if (t.name.isEmpty()) t.name = "未命名预约";
        if (selectedApp != null) { t.pkg = selectedApp.pkg; t.label = selectedApp.label; }
        t.ratePerMin = advancedRateSeek.getProgress() + 1;
        t.randomMinMs = Math.max(0, intOf(randomMinEdit, 0));
        t.randomMaxMs = Math.max(t.randomMinMs, intOf(randomMaxEdit, t.randomMinMs));
        t.maxCycles = Math.max(0, intOf(cyclesEdit, 0));
        t.maxDurationSec = Math.max(0, intOf(durationEdit, 0));
        if (includeSchedule) t.repeat = repeatCode(repeatSpinner.getSelectedItemPosition());
        Prefs.upsert(this, t);
    }

    private void showTaskPicker() {
        saveAdvanced(false);
        List<TaskProfile> list = Prefs.advancedTasks(this);
        String[] names = new String[list.size()];
        for (int i=0;i<list.size();i++) names[i] = list.get(i).name + (list.get(i).label.isEmpty()?"":"  ·  "+list.get(i).label);
        new AlertDialog.Builder(this).setTitle("切换预约方案").setItems(names, (d, which) -> { Prefs.setCurrentTaskId(this, list.get(which).id); refreshAdvanced(); }).setNegativeButton("取消", null).show();
    }

    private void newAdvancedTask() {
        saveAdvanced(false);
        TaskProfile t = new TaskProfile(); t.name = "预约任务 " + (Prefs.advancedTasks(this).size() + 1); Prefs.upsert(this, t); refreshAdvanced();
    }

    private void deleteAdvancedTask() {
        TaskProfile t = Prefs.currentAdvanced(this);
        new AlertDialog.Builder(this).setTitle("删除当前方案？").setMessage(t.name).setPositiveButton("删除", (d,w) -> { ScheduleManager.cancel(this,t); Prefs.delete(this,t.id); refreshAdvanced(); }).setNegativeButton("取消",null).show();
    }

    private void showAppPicker() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(14), dp(16), dp(14)); root.setBackgroundColor(Color.WHITE);
        TextView title = text("选择应用", 20, Color.rgb(28,34,48)); title.setTypeface(Typeface.DEFAULT_BOLD); root.addView(title);
        EditText search = edit("搜索应用名称"); search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0); search.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52)); slp.setMargins(0,dp(10),0,dp(8)); root.addView(search,slp);
        ListView list = new ListView(this); AppSearchAdapter adapter = new AppSearchAdapter(apps); list.setAdapter(adapter); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView cancel = selectableText("取消"); cancel.setGravity(Gravity.CENTER); root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(50))); cancel.setOnClickListener(v->dialog.dismiss());
        search.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){} @Override public void onTextChanged(CharSequence s,int st,int b,int c){adapter.filter(s.toString());} @Override public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{ selectedApp=adapter.getItem(pos); updateSelectedAppView(); saveAdvanced(false); dialog.dismiss(); });
        dialog.setContentView(root); Window w=dialog.getWindow(); if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);} dialog.show();
        if(dialog.getWindow()!=null) dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void updateSelectedAppView() {
        if (selectedApp == null) {
            selectedAppIcon.setImageDrawable(null); selectedAppName.setText("点击选择应用"); selectedAppPkg.setText("支持搜索应用名称");
        } else {
            selectedAppIcon.setImageDrawable(selectedApp.icon); selectedAppName.setText(selectedApp.label); selectedAppPkg.setText(selectedApp.pkg);
        }
    }

    private void chooseDate() {
        DatePickerDialog d = new DatePickerDialog(this, (v,y,m,day)->{ scheduleCalendar.set(Calendar.YEAR,y); scheduleCalendar.set(Calendar.MONTH,m); scheduleCalendar.set(Calendar.DAY_OF_MONTH,day); updateDateTimeButtons(); }, scheduleCalendar.get(Calendar.YEAR), scheduleCalendar.get(Calendar.MONTH), scheduleCalendar.get(Calendar.DAY_OF_MONTH)); d.show();
    }
    private void chooseTime() {
        TimePickerDialog d = new TimePickerDialog(this, (v,h,m)->{ scheduleCalendar.set(Calendar.HOUR_OF_DAY,h); scheduleCalendar.set(Calendar.MINUTE,m); scheduleCalendar.set(Calendar.SECOND,0); scheduleCalendar.set(Calendar.MILLISECOND,0); updateDateTimeButtons(); }, scheduleCalendar.get(Calendar.HOUR_OF_DAY), scheduleCalendar.get(Calendar.MINUTE), true); d.show();
    }
    private void updateDateTimeButtons() {
        if(dateButton==null)return;
        dateButton.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(scheduleCalendar.getTime()));
        timeButton.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(scheduleCalendar.getTime()));
    }

    private void scheduleCurrent() {
        saveAdvanced(true);
        TaskProfile t = Prefs.currentAdvanced(this);
        if (selectedApp == null || t.pkg.isEmpty()) { toast("请先选择要绑定的应用"); return; }
        if (t.actions.isEmpty()) { toast("请先打开应用，通过悬浮窗录制至少一个动作"); return; }
        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName()))); }
            catch(Exception e) { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); }
            toast("请允许“闹钟和提醒”后再次保存"); return;
        }
        if (scheduleCalendar.getTimeInMillis() <= System.currentTimeMillis()) { toast("预约时间必须晚于当前时间"); return; }
        ScheduleManager.cancel(this, t);
        t.scheduleAt = scheduleCalendar.getTimeInMillis(); t.repeat = repeatCode(repeatSpinner.getSelectedItemPosition()); Prefs.upsert(this,t); ScheduleManager.schedule(this,t); refreshAdvanced(); toast("预约已保存");
    }

    private void cancelSchedule() {
        TaskProfile t = Prefs.currentAdvanced(this); ScheduleManager.cancel(this,t); t.scheduleAt=0; Prefs.upsert(this,t); refreshAdvanced(); toast("已取消预约");
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); toast("请先允许悬浮窗权限"); return; }
        Intent i = new Intent(this, OverlayService.class); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }

    private void openCurrentApp() {
        TaskProfile t = Prefs.current(this); if(t.pkg==null||t.pkg.isEmpty()){toast("当前方案未绑定应用");return;} Intent i=getPackageManager().getLaunchIntentForPackage(t.pkg); if(i!=null)startActivity(i); else toast("无法打开目标应用");
    }

    private void loadApps() {
        Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> rs=getPackageManager().queryIntentActivities(i,0);
        for(ResolveInfo r:rs){String p=r.activityInfo.packageName;if(p.equals(getPackageName()))continue; try{apps.add(new AppItem(r.loadLabel(getPackageManager()).toString(),p,r.loadIcon(getPackageManager())));}catch(Exception ignored){}}
        Collections.sort(apps, Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));
    }
    private AppItem findApp(String pkg){if(pkg==null)return null;for(AppItem a:apps)if(pkg.equals(a.pkg))return a;return null;}

    private void openOverlaySettings(){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},33);}

    private String repeatCode(int i){return i==1?TaskProfile.REPEAT_DAILY:i==2?TaskProfile.REPEAT_WEEKDAYS:i==3?TaskProfile.REPEAT_WEEKLY:TaskProfile.REPEAT_ONCE;}
    private int repeatIndex(String s){if(TaskProfile.REPEAT_DAILY.equals(s))return 1;if(TaskProfile.REPEAT_WEEKDAYS.equals(s))return 2;if(TaskProfile.REPEAT_WEEKLY.equals(s))return 3;return 0;}
    private String repeatName(String s){return repeatIndex(s)==1?"每天":repeatIndex(s)==2?"工作日":repeatIndex(s)==3?"每周":"仅一次";}
    private String rateLabel(int r){long ms=Math.max(60L,Math.round(60000.0/Math.max(1,r)));return r+" 次/分钟  ·  约 "+ms+" ms/次";}
    private int intOf(EditText e,int d){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return d;}}
    private String fmt(long t){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(t));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(15),dp(14),dp(15),dp(14));l.setBackground(roundRect(Color.WHITE,20,Color.rgb(229,232,239)));l.setElevation(dp(2));return l;}
    private GradientDrawable roundRect(int color,float radiusDp,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams tabLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(72),1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams weightButton(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(3),dp(3),dp(3),0);return p;}
    private LinearLayout.LayoutParams weightField(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(3),dp(4),dp(3),0);return p;}
    private LinearLayout.LayoutParams fullButtonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(8);return p;}
    private TextView modeTab(String s){TextView t=text(s,13,Color.rgb(65,73,92));t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);return t;}
    private TextView stepTitle(String no,String s){TextView t=text(no+"  "+s,16,Color.rgb(31,38,55));t.setTypeface(Typeface.DEFAULT_BOLD);t.setPadding(0,0,0,dp(10));return t;}
    private TextView section(String s){TextView t=text(s,16,Color.rgb(31,38,55));t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView text(String s,int sp,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(c);t.setLineSpacing(0,1.16f);return t;}
    private Button primaryButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(roundRect(BLUE,15,BLUE));return b;}
    private Button secondaryButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(Color.rgb(57,67,91));b.setBackground(roundRect(Color.rgb(247,249,253),14,Color.rgb(224,228,237)));return b;}
    private EditText edit(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(Color.rgb(35,42,58));e.setHintTextColor(Color.rgb(143,149,162));e.setPadding(dp(12),0,dp(12),0);e.setBackground(roundRect(Color.rgb(248,249,252),14,Color.rgb(225,229,237)));return e;}
    private EditText numEdit(String hint){EditText e=edit(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setGravity(Gravity.CENTER_VERTICAL);return e;}
    private TextView selectableText(String s){TextView t=text(s,14,Color.rgb(47,57,80));t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(12),0,dp(12),0);t.setBackground(roundRect(Color.rgb(248,249,252),14,Color.rgb(225,229,237)));return t;}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    static class AppItem {
        final String label,pkg; final Drawable icon;
        AppItem(String l,String p,Drawable d){label=l;pkg=p;icon=d;}
    }

    class AppSearchAdapter extends BaseAdapter {
        private final List<AppItem> all = new ArrayList<>();
        private final List<AppItem> shown = new ArrayList<>();
        AppSearchAdapter(List<AppItem> source){all.addAll(source);shown.addAll(source);}
        void filter(String q){String x=q==null?"":q.trim().toLowerCase(Locale.ROOT);shown.clear();for(AppItem a:all)if(x.isEmpty()||a.label.toLowerCase(Locale.ROOT).contains(x)||a.pkg.toLowerCase(Locale.ROOT).contains(x))shown.add(a);notifyDataSetChanged();}
        @Override public int getCount(){return shown.size();}
        @Override public AppItem getItem(int p){return shown.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p, View old, ViewGroup parent){
            LinearLayout row;
            if(old instanceof LinearLayout) row=(LinearLayout)old; else {row=new LinearLayout(MainActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));ImageView icon=new ImageView(MainActivity.this);icon.setId(android.R.id.icon);row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout tx=new LinearLayout(MainActivity.this);tx.setId(android.R.id.content);tx.setOrientation(LinearLayout.VERTICAL);tx.setPadding(dp(12),0,0,0);TextView n=text("",14,Color.rgb(35,42,58));n.setId(android.R.id.text1);n.setTypeface(Typeface.DEFAULT_BOLD);TextView pkg=text("",11,Color.rgb(116,123,140));pkg.setId(android.R.id.text2);tx.addView(n);tx.addView(pkg);row.addView(tx,new LinearLayout.LayoutParams(0,dp(56),1));}
            AppItem a=getItem(p);((ImageView)row.findViewById(android.R.id.icon)).setImageDrawable(a.icon);((TextView)row.findViewById(android.R.id.text1)).setText(a.label);((TextView)row.findViewById(android.R.id.text2)).setText(a.pkg);return row;
        }
    }
}
