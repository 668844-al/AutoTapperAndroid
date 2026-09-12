from pathlib import Path

p = Path('app/src/main/java/com/example/autotapper/OverlayService.java')
s = p.read_text(encoding='utf-8')

old = '    private float recStartX, recStartY;\n    private long recStartAt;\n'
new = '    private float recStartX, recStartY;\n    private long recStartAt;\n    private final int[] recorderOrigin = new int[]{0, 0};\n'
assert old in s
s = s.replace(old, new, 1)

old = '        FrameLayout root = new FrameLayout(this);\n        root.setBackgroundColor(Color.TRANSPARENT);\n        recorder = root;\n'
new = '        FrameLayout root = new FrameLayout(this);\n        root.setBackgroundColor(Color.TRANSPARENT);\n        root.setClickable(true);\n        root.setFocusable(false);\n        recorder = root;\n'
assert old in s
s = s.replace(old, new, 1)

old = '        TextView sub = tv("录制完成后前台页面恢复正常操作", 11, Color.WHITE, false);\n'
new = '        TextView sub = tv("点击后会立即显示标记，录制坐标按屏幕位置保存", 11, Color.WHITE, false);\n'
assert old in s
s = s.replace(old, new, 1)

old = '''        root.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (e.getY() < dp(115) || e.getY() > root.getHeight() - dp(95)) return true;
                recStartX = e.getRawX();
                recStartY = e.getRawY();
                recStartAt = System.currentTimeMillis();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (recStartAt == 0) return true;
                float ex = e.getRawX(), ey = e.getRawY();
                float distance = (float) Math.hypot(ex - recStartX, ey - recStartY);
                long duration = Math.max(100, System.currentTimeMillis() - recStartAt);
                TaskProfile t = Prefs.current(this);
                if (distance < dp(22)) t.actions.add(TaskProfile.Action.tap(recStartX, recStartY));
                else t.actions.add(TaskProfile.Action.swipe(recStartX, recStartY, ex, ey, duration));
                Prefs.upsert(this, t);
                renderActions(root, t.actions);
                recStartAt = 0;
                refreshStatus();
                return true;
            }
            return true;
        });
'''
new = '''        root.setOnTouchListener((v, e) -> {
            updateRecorderOrigin(root);
            float screenX = e.getX() + recorderOrigin[0];
            float screenY = e.getY() + recorderOrigin[1];

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                recStartX = screenX;
                recStartY = screenY;
                recStartAt = System.currentTimeMillis();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (recStartAt == 0) return true;
                float ex = screenX, ey = screenY;
                float distance = (float) Math.hypot(ex - recStartX, ey - recStartY);
                long duration = Math.max(100, System.currentTimeMillis() - recStartAt);
                TaskProfile t = Prefs.current(this);
                if (distance < dp(22)) t.actions.add(TaskProfile.Action.tap(recStartX, recStartY));
                else t.actions.add(TaskProfile.Action.swipe(recStartX, recStartY, ex, ey, duration));
                Prefs.upsert(this, t);
                renderActions(root, t.actions);
                recStartAt = 0;
                sub.setText("已录制 " + t.actions.size() + " 个动作 · 可继续点击或完成录制");
                refreshStatus();
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_CANCEL) {
                recStartAt = 0;
                return true;
            }
            return true;
        });
'''
assert old in s
s = s.replace(old, new, 1)

old = '                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,\n'
new = '                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,\n'
assert old in s
s = s.replace(old, new, 1)

old = '        wm.addView(root, lp);\n        renderActions(root, Prefs.current(this).actions);\n    }\n\n    private void renderActions(FrameLayout root, List<TaskProfile.Action> actions) {\n'
new = '''        wm.addView(root, lp);
        root.post(() -> {
            updateRecorderOrigin(root);
            renderActions(root, Prefs.current(this).actions);
        });
    }

    private void updateRecorderOrigin(View root) {
        try {
            root.getLocationOnScreen(recorderOrigin);
        } catch (Exception ignored) {
            recorderOrigin[0] = 0;
            recorderOrigin[1] = 0;
        }
    }

    private void renderActions(FrameLayout root, List<TaskProfile.Action> actions) {
        updateRecorderOrigin(root);
'''
assert old in s
s = s.replace(old, new, 1)

old = '''        for (int i = 0; i < actions.size(); i++) {
            TaskProfile.Action a = actions.get(i);
            if (TaskProfile.Action.SWIPE.equals(a.type)) {
                addLine(root, a.x1, a.y1, a.x2, a.y2);
                addMarker(root, a.x1, a.y1, "S" + (i + 1));
                addMarker(root, a.x2, a.y2, "→");
            } else {
                addMarker(root, a.x1, a.y1, String.valueOf(i + 1));
            }
        }
'''
new = '''        for (int i = 0; i < actions.size(); i++) {
            TaskProfile.Action a = actions.get(i);
            float x1 = a.x1 - recorderOrigin[0];
            float y1 = a.y1 - recorderOrigin[1];
            float x2 = a.x2 - recorderOrigin[0];
            float y2 = a.y2 - recorderOrigin[1];
            if (TaskProfile.Action.SWIPE.equals(a.type)) {
                addLine(root, x1, y1, x2, y2);
                addMarker(root, x1, y1, "S" + (i + 1));
                addMarker(root, x2, y2, "→");
            } else {
                addMarker(root, x1, y1, String.valueOf(i + 1));
            }
        }
'''
assert old in s
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

p2 = Path('app/src/main/java/com/example/autotapper/AutoClickAccessibilityService.java')
s2 = p2.read_text(encoding='utf-8')
old2 = '        else gestureDuration=1;\n'
new2 = '        else gestureDuration=50;\n'
assert old2 in s2
s2 = s2.replace(old2, new2, 1)
p2.write_text(s2, encoding='utf-8')
