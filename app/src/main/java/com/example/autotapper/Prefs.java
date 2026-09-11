package com.example.autotapper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Prefs {
    private static final String NAME = "autotapper_prefs_v2";
    private static final String KEY_TASKS = "tasks_json";
    private static final String KEY_CURRENT = "current_task_id";
    public static final String KEY_PENDING_TASK = "pending_task_id";
    public static final String BASIC_TASK_ID = "basic_default";

    private Prefs() {}
    public static SharedPreferences sp(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }

    public static synchronized List<TaskProfile> tasks(Context c) {
        List<TaskProfile> out = new ArrayList<>();
        String raw = sp(c).getString(KEY_TASKS, "");
        if (raw != null && !raw.isEmpty()) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i=0;i<a.length();i++) {
                    JSONObject o = a.optJSONObject(i); if (o != null) out.add(TaskProfile.fromJson(o));
                }
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            TaskProfile d = new TaskProfile(); d.name = "预约任务 1"; out.add(d); saveTasks(c, out); setCurrentTaskId(c, d.id);
        }
        return out;
    }

    public static synchronized void saveTasks(Context c, List<TaskProfile> list) {
        JSONArray a = new JSONArray();
        for (TaskProfile t : list) try { a.put(t.toJson()); } catch(Exception ignored) {}
        sp(c).edit().putString(KEY_TASKS, a.toString()).apply();
    }

    public static synchronized TaskProfile basic(Context c) {
        List<TaskProfile> list = tasks(c);
        for (TaskProfile t : list) if (BASIC_TASK_ID.equals(t.id)) return t;
        TaskProfile t = new TaskProfile();
        t.id = BASIC_TASK_ID;
        t.name = "基础连点";
        t.ratePerMin = 120;
        list.add(0, t);
        saveTasks(c, list);
        return t;
    }

    public static synchronized List<TaskProfile> advancedTasks(Context c) {
        List<TaskProfile> all = tasks(c);
        List<TaskProfile> out = new ArrayList<>();
        for (TaskProfile t : all) if (!BASIC_TASK_ID.equals(t.id)) out.add(t);
        if (out.isEmpty()) {
            TaskProfile t = new TaskProfile(); t.name = "预约任务 1"; all.add(t); saveTasks(c, all); out.add(t);
        }
        return out;
    }

    public static synchronized TaskProfile currentAdvanced(Context c) {
        String id = sp(c).getString(KEY_CURRENT, "");
        for (TaskProfile t : advancedTasks(c)) if (t.id.equals(id)) return t;
        TaskProfile t = advancedTasks(c).get(0);
        setCurrentTaskId(c, t.id);
        return t;
    }

    public static synchronized TaskProfile current(Context c) {
        List<TaskProfile> list = tasks(c);
        String id = sp(c).getString(KEY_CURRENT, "");
        for (TaskProfile t : list) if (t.id.equals(id)) return t;
        TaskProfile t = list.get(0); setCurrentTaskId(c, t.id); return t;
    }

    public static synchronized TaskProfile byId(Context c, String id) {
        if (id == null) return null;
        for (TaskProfile t : tasks(c)) if (id.equals(t.id)) return t;
        return null;
    }

    public static synchronized void upsert(Context c, TaskProfile task) {
        List<TaskProfile> list = tasks(c); boolean found=false;
        for (int i=0;i<list.size();i++) if (list.get(i).id.equals(task.id)) { list.set(i, task); found=true; break; }
        if (!found) list.add(task);
        saveTasks(c, list); setCurrentTaskId(c, task.id);
    }

    public static synchronized void delete(Context c, String id) {
        if (BASIC_TASK_ID.equals(id)) return;
        List<TaskProfile> list = tasks(c); list.removeIf(t -> t.id.equals(id));
        boolean hasAdvanced=false;
        for (TaskProfile t:list) if (!BASIC_TASK_ID.equals(t.id)) { hasAdvanced=true; break; }
        if (!hasAdvanced) { TaskProfile t=new TaskProfile(); t.name="预约任务 1"; list.add(t); }
        saveTasks(c, list);
        for (TaskProfile t:list) if (!BASIC_TASK_ID.equals(t.id)) { setCurrentTaskId(c,t.id); break; }
    }

    public static void setCurrentTaskId(Context c, String id) { sp(c).edit().putString(KEY_CURRENT, id).apply(); }
    public static String currentTaskId(Context c) { return current(c).id; }

    public static synchronized void addAction(Context c, TaskProfile.Action action) {
        TaskProfile t = current(c); t.actions.add(action); upsert(c,t);
    }
    public static synchronized void clearActions(Context c) {
        TaskProfile t=current(c); t.actions.clear(); upsert(c,t);
    }
}
