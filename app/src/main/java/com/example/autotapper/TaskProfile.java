package com.example.autotapper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskProfile {
    public static final String REPEAT_ONCE = "once";
    public static final String REPEAT_DAILY = "daily";
    public static final String REPEAT_WEEKDAYS = "weekdays";
    public static final String REPEAT_WEEKLY = "weekly";

    public String id = UUID.randomUUID().toString();
    public String name = "新任务";
    public String pkg = "";
    public String label = "";
    public int ratePerMin = 120;
    public int randomMinMs = 0;
    public int randomMaxMs = 0;
    public int maxCycles = 0;       // 0 = unlimited
    public int maxDurationSec = 0;  // 0 = unlimited
    public long scheduleAt = 0L;
    public String repeat = REPEAT_ONCE;
    public final List<Action> actions = new ArrayList<>();

    public static class Action {
        public static final String TAP = "tap";
        public static final String SWIPE = "swipe";
        public String type = TAP;
        public float x1, y1, x2, y2;
        public long durationMs = 1;

        public static Action tap(float x, float y) {
            Action a = new Action();
            a.type = TAP; a.x1 = x; a.y1 = y; a.x2 = x; a.y2 = y; a.durationMs = 1;
            return a;
        }

        public static Action swipe(float x1, float y1, float x2, float y2, long durationMs) {
            Action a = new Action();
            a.type = SWIPE; a.x1=x1; a.y1=y1; a.x2=x2; a.y2=y2;
            a.durationMs = Math.max(80, Math.min(3000, durationMs));
            return a;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("type", type); o.put("x1", x1); o.put("y1", y1); o.put("x2", x2); o.put("y2", y2); o.put("durationMs", durationMs);
            return o;
        }

        static Action fromJson(JSONObject o) {
            Action a = new Action();
            a.type = o.optString("type", TAP);
            a.x1 = (float)o.optDouble("x1", 0); a.y1=(float)o.optDouble("y1",0);
            a.x2 = (float)o.optDouble("x2", a.x1); a.y2=(float)o.optDouble("y2", a.y1);
            a.durationMs = o.optLong("durationMs", SWIPE.equals(a.type) ? 300 : 1);
            return a;
        }
    }

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("pkg", pkg); o.put("label", label);
        o.put("ratePerMin", ratePerMin); o.put("randomMinMs", randomMinMs); o.put("randomMaxMs", randomMaxMs);
        o.put("maxCycles", maxCycles); o.put("maxDurationSec", maxDurationSec);
        o.put("scheduleAt", scheduleAt); o.put("repeat", repeat);
        JSONArray arr = new JSONArray();
        for (Action a : actions) arr.put(a.toJson());
        o.put("actions", arr);
        return o;
    }

    static TaskProfile fromJson(JSONObject o) {
        TaskProfile t = new TaskProfile();
        t.id = o.optString("id", UUID.randomUUID().toString());
        t.name = o.optString("name", "任务"); t.pkg=o.optString("pkg",""); t.label=o.optString("label","");
        t.ratePerMin = Math.max(1, Math.min(1000, o.optInt("ratePerMin",120)));
        t.randomMinMs = Math.max(0, o.optInt("randomMinMs",0));
        t.randomMaxMs = Math.max(t.randomMinMs, o.optInt("randomMaxMs",t.randomMinMs));
        t.maxCycles = Math.max(0, o.optInt("maxCycles",0));
        t.maxDurationSec = Math.max(0, o.optInt("maxDurationSec",0));
        t.scheduleAt = o.optLong("scheduleAt",0L); t.repeat=o.optString("repeat",REPEAT_ONCE);
        JSONArray arr = o.optJSONArray("actions");
        if (arr != null) for (int i=0;i<arr.length();i++) {
            JSONObject a = arr.optJSONObject(i); if (a != null) t.actions.add(Action.fromJson(a));
        }
        return t;
    }

    public TaskProfile copyAsNew() {
        try {
            TaskProfile t = fromJson(this.toJson());
            t.id = UUID.randomUUID().toString();
            t.name = this.name + " 副本";
            t.scheduleAt = 0L;
            return t;
        } catch (Exception e) {
            return new TaskProfile();
        }
    }

    @Override public String toString() {
        return name + (label == null || label.isEmpty() ? "" : "  ·  " + label);
    }
}
