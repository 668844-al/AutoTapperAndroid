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
import android.widget.FrameLayout;
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
    private static final int BLUE = Color.rgb(54, 139, 255);
    private static final int DARK = Color.rgb(27, 42, 72);
    private static final int MUTED = Color.rgb(118, 137, 168);

    private final List<AppItem> apps = new ArrayList<>();
    private LinearLayout pageRoot;
    private int currentPage = 0;
    private boolean loading = false;

    private SeekBar basicRateSeek, advancedRateSeek;
    private TextView basicRateValue, basicIntervalValue, basicActionValue;
    private ImageView basicAppIcon, advancedAppIcon;
    private TextView basicAppName, basicAppPkg, advancedAppName, advancedAppPkg;

    private EditText taskNameEdit, randomMinEdit, randomMaxEdit, cyclesEdit, durationEdit;
    private TextView advancedRateValue, advancedActionValue, scheduleSummary, dateButton, timeButton;
    private Spinner repeatSpinner;
    private Calendar scheduleCalendar = Calendar.getInstance();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadApps();
        Prefs.basic(this);
        setContentView(buildShell());
        showHome();
        requestNotificationPermission();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshCurrentPage();
    }

    @Override public void onBackPressed() {
        if (currentPage != 0) showHome(); else super.onBackPressed();
    }

    private View buildShell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackground(pageGradient());
        pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setPadding(dp(16), dp(14), dp(16), dp(32));
        scroll.addView(pageRoot, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private void resetPage() {
        pageRoot.removeAllViews();
        pageRoot.setPadding(dp(16), dp(14), dp(16), dp(32));
    }

    private void showHome() {
        currentPage = 0;
        resetPage();

        LinearLayout header = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titles = col();
        titles.setPadding(dp(12), 0, 0, 0);
        TextView title = text("连点器", 25, DARK, true);
        TextView sub = text("简单 · 高效 · 由你掌控", 12, MUTED, false);
        titles.addView(title);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, dp(58), 1));

        TextView settingsBtn = circleText("⚙", 18, Color.rgb(74, 112, 180), Color.argb(150, 255, 255, 255));
        settingsBtn.setOnClickListener(v -> showPermissionDialog());
        header.addView(settingsBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));
        pageRoot.addView(header, gapLp(0, 8));

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(iosCardBg());
        hero.setElevation(dp(6));
        hero.setClipToOutline(true);
        ImageView heroImage = new ImageView(this);
        heroImage.setImageResource(R.drawable.app_icon);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroImage, new FrameLayout.LayoutParams(-1, dp(192)));
        TextView bubble = text("让重复的操作，\n变得更简单～ ♡", 15, Color.rgb(54, 82, 138), true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackground(round(Color.argb(225, 255, 255, 255), 18, Color.argb(110, 180, 210, 245)));
        bubble.setPadding(dp(14), dp(9), dp(14), dp(9));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(175), dp(70), Gravity.START | Gravity.TOP);
        bp.leftMargin = dp(14); bp.topMargin = dp(14);
        hero.addView(bubble, bp);
        pageRoot.addView(hero, gapLp(0, 14));

        pageRoot.addView(entryCard("◉", "基础连点", "快速设置 · 立即开始", v -> showBasic()), gapLp(0, 10));
        pageRoot.addView(entryCard("◷", "进阶预约", "定时执行 · 自动完成", v -> showAdvanced()), gapLp(0, 16));

        LinearLayout quick = row();
        quick.setGravity(Gravity.CENTER);
        quick.addView(quickTile("▣", "使用教程", v -> showHelp()), weightGap());
        quick.addView(quickTile("?", "常见问题", v -> showHelp()), weightGap());
        quick.addView(quickTile("♢", "权限设置", v -> showPermissionDialog()), weightGap());
        quick.addView(quickTile("⌘", "更多工具", v -> toast("更多工具后续继续加入")), weightGap());
        pageRoot.addView(quick, gapLp(0, 16));

        TextView footer = text("♡  连点器 · 让生活更轻松  ♡", 12, Color.rgb(142, 164, 201), false);
        footer.setGravity(Gravity.CENTER);
        pageRoot.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private void showBasic() {
        currentPage = 1;
        resetPage();
        pageRoot.addView(topBar("基础连点"), gapLp(0, 10));

        TaskProfile t = Prefs.basic(this);
        Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID);
        AppItem currentApp = appByPkg(t.pkg);

        LinearLayout appCard = card();
        appCard.addView(sectionTitle("目标应用"));
        LinearLayout appRow = appSelectorRow(currentApp, true);
        appCard.addView(appRow);
        appRow.setOnClickListener(v -> showAppPicker(app -> {
            TaskProfile task = Prefs.basic(this);
            task.pkg = app.pkg; task.label = app.label; Prefs.upsert(this, task); Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID);
            showBasic();
        }));
        pageRoot.addView(appCard, gapLp(0, 10));

        LinearLayout params = card();
        params.addView(sectionTitle("点击参数"));
        basicRateValue = valueText("");
        basicIntervalValue = valueText("");
        basicActionValue = valueText("");
        params.addView(settingRow("◴", "点击速率", basicRateValue, null));
        params.addView(divider());
        params.addView(settingRow("⏱", "点击间隔", basicIntervalValue, null));
        params.addView(divider());
        params.addView(settingRow("◎", "点击位置", basicActionValue, null));
        params.addView(divider());
        params.addView(settingRow("⌁", "点击类型", valueText(t.actions.size() > 1 ? "多点循环" : "单点点击"), null));
        basicRateSeek = new SeekBar(this);
        basicRateSeek.setMax(999);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(-1, dp(48)); seekLp.topMargin = dp(8);
        params.addView(basicRateSeek, seekLp);
        basicRateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!loading) {
                    int r = p + 1;
                    basicRateValue.setText(r + " 次/分");
                    basicIntervalValue.setText(intervalLabel(r));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { saveBasicRate(); }
        });
        pageRoot.addView(params, gapLp(0, 10));

        LinearLayout permissions = card();
        permissions.addView(sectionTitle("运行状态"));
        permissions.addView(statusRow());
        pageRoot.addView(permissions, gapLp(0, 12));

        Button main = primaryButton("▶  打开悬浮窗");
        main.setOnClickListener(v -> {
            saveBasicRate();
            Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID);
            startOverlay();
            TaskProfile bt = Prefs.basic(this);
            if (bt.pkg != null && !bt.pkg.isEmpty()) openPackage(bt.pkg);
        });
        pageRoot.addView(main, bigButtonLp());

        TextView hint = text("提示：悬浮窗中点“录制”后，再点击目标位置；录制完成后点“开始”即可。", 12, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), dp(10), dp(12), 0);
        pageRoot.addView(hint);
        refreshBasic();
    }

    private void showAdvanced() {
        currentPage = 2;
        resetPage();
        pageRoot.addView(topBar("进阶预约"), gapLp(0, 10));
        TaskProfile t = Prefs.currentAdvanced(this);
        Prefs.setCurrentTaskId(this, t.id);
        scheduleCalendar = Calendar.getInstance();
        if (t.scheduleAt > System.currentTimeMillis()) scheduleCalendar.setTimeInMillis(t.scheduleAt);
        else scheduleCalendar.add(Calendar.MINUTE, 5);

        LinearLayout taskCard = card();
        taskCard.addView(sectionTitle("预约方案"));
        LinearLayout taskTop = row();
        TextView scheme = text(t.name, 16, DARK, true);
        taskTop.addView(scheme, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView switchBtn = pillText("切换方案", 12, BLUE, Color.argb(120, 228, 240, 255));
        taskTop.addView(switchBtn, new LinearLayout.LayoutParams(dp(88), dp(36)));
        taskCard.addView(taskTop);
        taskNameEdit = edit(t.name, "任务名称，例如：每日签到");
        taskCard.addView(taskNameEdit, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout taskButtons = row();
        Button add = secondaryButton("＋ 新建");
        Button del = secondaryButton("删除方案");
        taskButtons.addView(add, weightGap()); taskButtons.addView(del, weightGap());
        taskCard.addView(taskButtons);
        switchBtn.setOnClickListener(v -> { saveAdvanced(false); showTaskPicker(); });
        add.setOnClickListener(v -> newAdvancedTask());
        del.setOnClickListener(v -> deleteAdvancedTask());
        pageRoot.addView(taskCard, gapLp(0, 10));

        LinearLayout appCard = card();
        appCard.addView(sectionTitle("选择应用"));
        LinearLayout appRow = appSelectorRow(appByPkg(t.pkg), false);
        appCard.addView(appRow);
        appRow.setOnClickListener(v -> showAppPicker(app -> {
            TaskProfile task = Prefs.currentAdvanced(this);
            task.pkg = app.pkg; task.label = app.label; Prefs.upsert(this, task); Prefs.setCurrentTaskId(this, task.id);
            showAdvanced();
        }));
        pageRoot.addView(appCard, gapLp(0, 10));

        LinearLayout execution = card();
        execution.addView(sectionTitle("执行参数"));
        advancedRateValue = valueText("");
        advancedActionValue = valueText("");
        execution.addView(settingRow("◴", "点击速率", advancedRateValue, null));
        execution.addView(divider());
        execution.addView(settingRow("◎", "已录制动作", advancedActionValue, null));
        advancedRateSeek = new SeekBar(this); advancedRateSeek.setMax(999);
        execution.addView(advancedRateSeek, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView moreTitle = text("高级设置", 13, MUTED, true); moreTitle.setPadding(0, dp(8), 0, dp(6)); execution.addView(moreTitle);
        LinearLayout rr = row(); randomMinEdit = numEdit(String.valueOf(t.randomMinMs), "随机最小ms"); randomMaxEdit = numEdit(String.valueOf(t.randomMaxMs), "随机最大ms"); rr.addView(randomMinEdit, weightGap()); rr.addView(randomMaxEdit, weightGap()); execution.addView(rr);
        LinearLayout lr = row(); cyclesEdit = numEdit(String.valueOf(t.maxCycles), "循环次数 0=不限"); durationEdit = numEdit(String.valueOf(t.maxDurationSec), "运行秒数 0=不限"); lr.addView(cyclesEdit, weightGap()); lr.addView(durationEdit, weightGap()); execution.addView(lr);
        advancedRateSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int p,boolean fromUser){ if(!loading) advancedRateValue.setText((p+1)+" 次/分 · "+intervalLabel(p+1)); }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){ saveAdvanced(false); }
        });
        pageRoot.addView(execution, gapLp(0, 10));

        LinearLayout schedule = card();
        schedule.addView(sectionTitle("预约时间"));
        scheduleSummary = text("", 12, MUTED, false); scheduleSummary.setPadding(0, 0, 0, dp(8)); schedule.addView(scheduleSummary);
        LinearLayout dt = row();
        dateButton = selectableText(""); timeButton = selectableText("");
        dt.addView(dateButton, weightGap()); dt.addView(timeButton, weightGap()); schedule.addView(dt);
        dateButton.setOnClickListener(v -> chooseDate()); timeButton.setOnClickListener(v -> chooseTime());
        repeatSpinner = new Spinner(this);
        repeatSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"仅一次","每天","工作日（周一至周五）","每周"}));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(50)); rp.topMargin = dp(8); schedule.addView(repeatSpinner, rp);
        pageRoot.addView(schedule, gapLp(0, 12));

        Button record = secondaryButton("打开应用并录制动作");
        record.setOnClickListener(v -> {
            saveAdvanced(false);
            TaskProfile at = Prefs.currentAdvanced(this);
            if (at.pkg == null || at.pkg.isEmpty()) { toast("请先选择应用"); return; }
            Prefs.setCurrentTaskId(this, at.id); startOverlay(); openPackage(at.pkg);
        });
        pageRoot.addView(record, bigButtonLp());

        Button save = primaryButton("◷  保存预约任务");
        save.setOnClickListener(v -> scheduleCurrent());
        LinearLayout.LayoutParams sbp = bigButtonLp(); sbp.topMargin = dp(8); pageRoot.addView(save, sbp);

        TextView cancel = pillText("取消当前预约", 13, Color.rgb(217, 80, 96), Color.argb(110, 255, 236, 239));
        cancel.setGravity(Gravity.CENTER); cancel.setOnClickListener(v -> cancelSchedule());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(46)); cp.topMargin = dp(8); pageRoot.addView(cancel, cp);
        refreshAdvanced();
    }

    private View topBar(String title) {
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = circleText("‹", 30, DARK, Color.argb(120,255,255,255));
        back.setOnClickListener(v -> showHome());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = text(title, 20, DARK, true); t.setGravity(Gravity.CENTER);
        bar.addView(t, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView help = circleText("?", 16, BLUE, Color.argb(120,255,255,255)); help.setOnClickListener(v -> showHelp());
        bar.addView(help, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }

    private LinearLayout appSelectorRow(AppItem app, boolean basic) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), dp(10), dp(10), dp(10)); row.setBackground(round(Color.rgb(249, 252, 255), 18, Color.rgb(226, 235, 247)));
        ImageView icon = new ImageView(this); icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        TextView name = text(app == null ? "选择应用" : app.label, 15, DARK, true);
        TextView pkg = text(app == null ? (basic ? "可选：选择后可一键打开目标应用" : "搜索应用名称并绑定") : app.pkg, 11, MUTED, false);
        if (app != null) icon.setImageDrawable(app.icon); else icon.setImageResource(R.drawable.app_icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout labels = col(); labels.setPadding(dp(10), 0, 0, 0); labels.addView(name); labels.addView(pkg);
        row.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1));
        TextView arrow = text("›", 27, Color.rgb(129, 155, 194), false); arrow.setGravity(Gravity.CENTER); row.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(46)));
        if (basic) { basicAppIcon = icon; basicAppName = name; basicAppPkg = pkg; }
        else { advancedAppIcon = icon; advancedAppName = name; advancedAppPkg = pkg; }
        return row;
    }

    private View statusRow() {
        LinearLayout r = row();
        boolean over = Settings.canDrawOverlays(this), acc = AutoClickAccessibilityService.isConnected();
        TextView a = pillText((over?"✓ ":"○ ")+"悬浮窗", 12, over?Color.rgb(42,145,95):Color.rgb(184,108,52), over?Color.argb(110,229,250,240):Color.argb(110,255,241,224));
        TextView b = pillText((acc?"✓ ":"○ ")+"无障碍", 12, acc?Color.rgb(42,145,95):Color.rgb(184,108,52), acc?Color.argb(110,229,250,240):Color.argb(110,255,241,224));
        a.setGravity(Gravity.CENTER); b.setGravity(Gravity.CENTER);
        a.setOnClickListener(v -> openOverlaySettings()); b.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        r.addView(a, weightGap()); r.addView(b, weightGap());
        return r;
    }

    private void refreshCurrentPage() {
        if (currentPage == 1) refreshBasic(); else if (currentPage == 2) refreshAdvanced();
    }

    private void refreshBasic() {
        if (basicRateSeek == null) return;
        loading = true;
        TaskProfile t = Prefs.basic(this);
        basicRateSeek.setProgress(Math.max(0, t.ratePerMin - 1));
        basicRateValue.setText(t.ratePerMin + " 次/分");
        basicIntervalValue.setText(intervalLabel(t.ratePerMin));
        basicActionValue.setText(t.actions.isEmpty() ? "未录制" : t.actions.size() + " 个动作");
        loading = false;
    }

    private void refreshAdvanced() {
        if (advancedRateSeek == null) return;
        loading = true;
        TaskProfile t = Prefs.currentAdvanced(this);
        advancedRateSeek.setProgress(Math.max(0, t.ratePerMin - 1));
        advancedRateValue.setText(t.ratePerMin + " 次/分 · " + intervalLabel(t.ratePerMin));
        advancedActionValue.setText(t.actions.isEmpty() ? "未录制" : t.actions.size() + " 个动作");
        if (taskNameEdit != null) taskNameEdit.setText(t.name);
        if (randomMinEdit != null) randomMinEdit.setText(String.valueOf(t.randomMinMs));
        if (randomMaxEdit != null) randomMaxEdit.setText(String.valueOf(t.randomMaxMs));
        if (cyclesEdit != null) cyclesEdit.setText(String.valueOf(t.maxCycles));
        if (durationEdit != null) durationEdit.setText(String.valueOf(t.maxDurationSec));
        if (repeatSpinner != null) repeatSpinner.setSelection(repeatIndex(t.repeat));
        if (dateButton != null) dateButton.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(scheduleCalendar.getTime()));
        if (timeButton != null) timeButton.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(scheduleCalendar.getTime()));
        if (scheduleSummary != null) scheduleSummary.setText(t.scheduleAt > 0 ? "已预约：" + fmt(t.scheduleAt) + " · " + repeatName(t.repeat) : "尚未设置预约时间");
        loading = false;
    }

    private void saveBasicRate() {
        if (basicRateSeek == null) return;
        TaskProfile t = Prefs.basic(this); t.ratePerMin = basicRateSeek.getProgress() + 1; Prefs.upsert(this, t); Prefs.setCurrentTaskId(this, Prefs.BASIC_TASK_ID); refreshBasic();
    }

    private void saveAdvanced(boolean includeRepeat) {
        TaskProfile t = Prefs.currentAdvanced(this);
        if (taskNameEdit != null) { String name = taskNameEdit.getText().toString().trim(); if (!name.isEmpty()) t.name = name; }
        if (advancedRateSeek != null) t.ratePerMin = advancedRateSeek.getProgress() + 1;
        t.randomMinMs = intOf(randomMinEdit, 0);
        t.randomMaxMs = Math.max(t.randomMinMs, intOf(randomMaxEdit, t.randomMinMs));
        t.maxCycles = Math.max(0, intOf(cyclesEdit, 0));
        t.maxDurationSec = Math.max(0, intOf(durationEdit, 0));
        if (includeRepeat && repeatSpinner != null) t.repeat = repeatCode(repeatSpinner.getSelectedItemPosition());
        Prefs.upsert(this, t); Prefs.setCurrentTaskId(this, t.id);
    }

    private void scheduleCurrent() {
        saveAdvanced(true);
        TaskProfile t = Prefs.currentAdvanced(this);
        if (t.pkg == null || t.pkg.isEmpty()) { toast("请先选择应用"); return; }
        if (t.actions.isEmpty()) { toast("请先打开应用并录制至少一个动作"); return; }
        if (!ScheduleManager.canExact(this)) {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)); }
            toast("请允许“闹钟和提醒”后再次保存预约"); return;
        }
        long when = scheduleCalendar.getTimeInMillis();
        if (when <= System.currentTimeMillis()) { toast("预约时间必须晚于当前时间"); return; }
        ScheduleManager.cancel(this, t);
        t.scheduleAt = when; t.repeat = repeatCode(repeatSpinner.getSelectedItemPosition()); Prefs.upsert(this, t); ScheduleManager.schedule(this, t);
        refreshAdvanced(); toast("预约已保存");
    }

    private void cancelSchedule() {
        TaskProfile t = Prefs.currentAdvanced(this); ScheduleManager.cancel(this, t); t.scheduleAt = 0; Prefs.upsert(this, t); refreshAdvanced(); toast("已取消预约");
    }

    private void chooseDate() {
        DatePickerDialog d = new DatePickerDialog(this, (v,y,m,day) -> { scheduleCalendar.set(Calendar.YEAR,y); scheduleCalendar.set(Calendar.MONTH,m); scheduleCalendar.set(Calendar.DAY_OF_MONTH,day); refreshAdvanced(); }, scheduleCalendar.get(Calendar.YEAR), scheduleCalendar.get(Calendar.MONTH), scheduleCalendar.get(Calendar.DAY_OF_MONTH)); d.show();
    }

    private void chooseTime() {
        TimePickerDialog d = new TimePickerDialog(this, (v,h,min) -> { scheduleCalendar.set(Calendar.HOUR_OF_DAY,h); scheduleCalendar.set(Calendar.MINUTE,min); scheduleCalendar.set(Calendar.SECOND,0); scheduleCalendar.set(Calendar.MILLISECOND,0); refreshAdvanced(); }, scheduleCalendar.get(Calendar.HOUR_OF_DAY), scheduleCalendar.get(Calendar.MINUTE), true); d.show();
    }

    private void showTaskPicker() {
        List<TaskProfile> list = Prefs.advancedTasks(this);
        String[] names = new String[list.size()]; for (int i=0;i<list.size();i++) names[i]=list.get(i).name;
        new AlertDialog.Builder(this).setTitle("选择预约方案").setItems(names,(d,w)->{Prefs.setCurrentTaskId(this,list.get(w).id);showAdvanced();}).show();
    }

    private void newAdvancedTask() {
        saveAdvanced(false); TaskProfile t = new TaskProfile(); t.name = "预约任务 " + (Prefs.advancedTasks(this).size() + 1); Prefs.upsert(this,t); Prefs.setCurrentTaskId(this,t.id); showAdvanced();
    }

    private void deleteAdvancedTask() {
        TaskProfile t = Prefs.currentAdvanced(this); ScheduleManager.cancel(this,t); Prefs.delete(this,t.id); showAdvanced();
    }

    private interface AppChosen { void onChosen(AppItem app); }
    private void showAppPicker(AppChosen callback) {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = col(); root.setPadding(dp(16), dp(14), dp(16), dp(16)); root.setBackgroundColor(Color.rgb(248,251,255));
        LinearLayout bar = row();
        TextView cancel = text("‹", 30, DARK, false); cancel.setGravity(Gravity.CENTER); cancel.setOnClickListener(v -> d.dismiss()); bar.addView(cancel,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView title = text("选择应用", 18, DARK, true); title.setGravity(Gravity.CENTER); bar.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView ok = text("",14,BLUE,true); bar.addView(ok,new LinearLayout.LayoutParams(dp(42),dp(42))); root.addView(bar);
        EditText search = edit("", "搜索应用名称"); search.setSingleLine(true); search.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_search,0); root.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView label = text("全部应用", 13, DARK, true); label.setPadding(dp(4),dp(12),0,dp(6)); root.addView(label);
        List<AppItem> filtered = new ArrayList<>(apps);
        AppAdapter adapter = new AppAdapter(filtered);
        ListView list = new ListView(this); list.setDividerHeight(0); list.setAdapter(adapter); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((p,v,pos,id)->{AppItem app=adapter.getItem(pos);callback.onChosen(app);d.dismiss();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){String q=s.toString().toLowerCase(Locale.ROOT).trim();filtered.clear();for(AppItem a:apps)if(q.isEmpty()||a.label.toLowerCase(Locale.ROOT).contains(q)||a.pkg.toLowerCase(Locale.ROOT).contains(q))filtered.add(a);adapter.notifyDataSetChanged();}public void afterTextChanged(Editable e){}});
        d.setContentView(root); d.setOnShowListener(x->{Window win=d.getWindow();if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}}); d.show();
    }

    private class AppAdapter extends BaseAdapter {
        private final List<AppItem> data; AppAdapter(List<AppItem> d){data=d;}
        @Override public int getCount(){return data.size();}
        @Override public AppItem getItem(int p){return data.get(p);}
        @Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View convert,ViewGroup parent){
            LinearLayout r = new LinearLayout(MainActivity.this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(10),dp(7),dp(10),dp(7));
            ImageView icon = new ImageView(MainActivity.this); icon.setImageDrawable(data.get(p).icon); r.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout labels=col();labels.setPadding(dp(10),0,0,0);labels.addView(text(data.get(p).label,14,DARK,true));labels.addView(text(data.get(p).pkg,10,MUTED,false));r.addView(labels,new LinearLayout.LayoutParams(0,dp(48),1));
            TextView circle=text("○",22,Color.rgb(157,180,213),false);circle.setGravity(Gravity.CENTER);r.addView(circle,new LinearLayout.LayoutParams(dp(34),dp(48)));
            if(p%2==0)r.setBackgroundColor(Color.argb(65,235,244,255));
            return r;
        }
    }

    private void showPermissionDialog() {
        LinearLayout box = col(); box.setPadding(dp(18),dp(8),dp(18),dp(8));
        TextView status = text((Settings.canDrawOverlays(this)?"✓ 悬浮窗已开启":"○ 悬浮窗未开启")+"\n"+(AutoClickAccessibilityService.isConnected()?"✓ 无障碍已开启":"○ 无障碍未开启"),14,DARK,false);status.setLineSpacing(dp(7),1);box.addView(status);
        new AlertDialog.Builder(this).setTitle("权限设置").setView(box).setPositiveButton("悬浮窗权限",(d,w)->openOverlaySettings()).setNeutralButton("无障碍权限",(d,w)->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("关闭",null).show();
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("使用教程").setMessage("基础连点：\n1. 开启悬浮窗和无障碍权限\n2. 进入基础连点，设置速度\n3. 打开悬浮窗，点“录制”并点击目标位置\n4. 完成录制后点“开始”\n\n进阶预约：\n选择应用 → 录制动作 → 设置预约时间 → 保存预约。\n\n安全锁屏状态下 Android 不允许应用绕过密码、指纹或面容解锁。").setPositiveButton("知道了",null).show();
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) { openOverlaySettings(); toast("请先开启悬浮窗权限"); return; }
        Intent i = new Intent(this, OverlayService.class); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }
    private void openOverlaySettings(){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void openPackage(String pkg){Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)startActivity(i);else toast("无法打开目标应用");}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},33);}

    private void loadApps(){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> rs=getPackageManager().queryIntentActivities(i,0);for(ResolveInfo r:rs){String p=r.activityInfo.packageName;if(p.equals(getPackageName()))continue;apps.add(new AppItem(r.loadLabel(getPackageManager()).toString(),p,r.loadIcon(getPackageManager())));}Collections.sort(apps, Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));}
    private AppItem appByPkg(String pkg){if(pkg==null)return null;for(AppItem a:apps)if(pkg.equals(a.pkg))return a;return null;}

    private View entryCard(String icon,String title,String sub,View.OnClickListener listener){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(13),dp(12),dp(13));r.setBackground(iosCardBg());r.setElevation(dp(4));TextView i=circleText(icon,24,Color.rgb(55,134,255),Color.rgb(229,241,255));r.addView(i,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout labels=col();labels.setPadding(dp(14),0,0,0);labels.addView(text(title,17,DARK,true));labels.addView(text(sub,12,MUTED,false));r.addView(labels,new LinearLayout.LayoutParams(0,dp(58),1));TextView arrow=text("›",30,Color.rgb(72,135,233),false);arrow.setGravity(Gravity.CENTER);r.addView(arrow,new LinearLayout.LayoutParams(dp(34),dp(58)));r.setOnClickListener(listener);return r;}
    private View quickTile(String icon,String label,View.OnClickListener l){LinearLayout c=col();c.setGravity(Gravity.CENTER);c.setPadding(dp(4),dp(9),dp(4),dp(8));c.setBackground(round(Color.argb(150,255,255,255),16,Color.argb(110,210,226,248)));TextView i=circleText(icon,17,Color.rgb(69,116,204),Color.rgb(234,243,255));c.addView(i,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView t=text(label,10,Color.rgb(76,93,124),true);t.setGravity(Gravity.CENTER);c.addView(t,new LinearLayout.LayoutParams(-1,dp(24)));c.setOnClickListener(l);return c;}
    private LinearLayout card(){LinearLayout l=col();l.setPadding(dp(14),dp(13),dp(14),dp(13));l.setBackground(iosCardBg());l.setElevation(dp(4));return l;}
    private GradientDrawable iosCardBg(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(250,255,255,255),Color.argb(238,247,251,255)});g.setCornerRadius(dp(22));g.setStroke(dp(1),Color.argb(130,211,226,246));return g;}
    private GradientDrawable pageGradient(){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(242,248,255),Color.rgb(235,245,255),Color.rgb(249,252,255)});return g;}
    private GradientDrawable round(int color,int radiusDp,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));if(Color.alpha(stroke)>0)g.setStroke(dp(1),stroke);return g;}
    private TextView sectionTitle(String s){TextView t=text(s,14,DARK,true);t.setPadding(0,0,0,dp(9));return t;}
    private View settingRow(String icon,String label,TextView value,View.OnClickListener click){LinearLayout r=row();r.setGravity(Gravity.CENTER_VERTICAL);TextView i=circleText(icon,15,BLUE,Color.rgb(235,245,255));r.addView(i,new LinearLayout.LayoutParams(dp(36),dp(36)));TextView l=text(label,14,DARK,false);l.setPadding(dp(10),0,0,0);r.addView(l,new LinearLayout.LayoutParams(0,dp(46),1));value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);r.addView(value,new LinearLayout.LayoutParams(dp(138),dp(46)));if(click!=null)r.setOnClickListener(click);return r;}
    private View divider(){View v=new View(this);v.setBackgroundColor(Color.rgb(232,238,247));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.leftMargin=dp(45);v.setLayoutParams(p);return v;}
    private TextView valueText(String s){return text(s,13,Color.rgb(82,121,185),false);}
    private Button primaryButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setBackground(round(BLUE,18,Color.TRANSPARENT));b.setElevation(dp(5));return b;}
    private Button secondaryButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(Color.rgb(66,100,159));b.setBackground(round(Color.rgb(245,249,255),16,Color.rgb(218,230,246)));b.setMinHeight(0);return b;}
    private TextView selectableText(String s){TextView t=text(s,14,DARK,false);t.setGravity(Gravity.CENTER);t.setBackground(round(Color.rgb(248,251,255),16,Color.rgb(221,232,246)));return t;}
    private EditText edit(String value,String hint){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextSize(14);e.setSingleLine(true);e.setTextColor(DARK);e.setHintTextColor(Color.rgb(158,173,198));e.setPadding(dp(13),0,dp(13),0);e.setBackground(round(Color.rgb(249,252,255),16,Color.rgb(221,232,246)));return e;}
    private EditText numEdit(String value,String hint){EditText e=edit(value,hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private TextView pillText(String s,int sp,int color,int bg){TextView t=text(s,sp,color,true);t.setGravity(Gravity.CENTER);t.setBackground(round(bg,16,Color.TRANSPARENT));t.setPadding(dp(8),0,dp(8),0);return t;}
    private TextView circleText(String s,int sp,int color,int bg){TextView t=text(s,sp,color,true);t.setGravity(Gravity.CENTER);GradientDrawable g=round(bg,99,Color.TRANSPARENT);t.setBackground(g);return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout.LayoutParams gapLp(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);p.bottomMargin=dp(bottom);return p;}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams bigButtonLp(){return new LinearLayout.LayoutParams(-1,dp(56));}
    private String intervalLabel(int rate){long ms=Math.max(60L,Math.round(60000.0/Math.max(1,rate)));return ms+" 毫秒";}
    private int intOf(EditText e,int d){if(e==null)return d;try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return d;}}
    private String repeatCode(int i){return i==1?TaskProfile.REPEAT_DAILY:i==2?TaskProfile.REPEAT_WEEKDAYS:i==3?TaskProfile.REPEAT_WEEKLY:TaskProfile.REPEAT_ONCE;}
    private int repeatIndex(String s){if(TaskProfile.REPEAT_DAILY.equals(s))return 1;if(TaskProfile.REPEAT_WEEKDAYS.equals(s))return 2;if(TaskProfile.REPEAT_WEEKLY.equals(s))return 3;return 0;}
    private String repeatName(String s){int i=repeatIndex(s);return i==1?"每天":i==2?"工作日":i==3?"每周":"仅一次";}
    private String fmt(long t){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(t));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private int dp(float v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}

    private static class AppItem {
        final String label,pkg; final Drawable icon;
        AppItem(String l,String p,Drawable i){label=l;pkg=p;icon=i;}
    }
}
