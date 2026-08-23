package com.nekokata.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** persists widget payload synced from the WebView + schedule for offline recompute */
final class WidgetStateStore {

    private static final String PREFS = "nekokata_widget";
    private static final String KEY_TODAY = "today";
    private static final String KEY_SCHEDULE = "schedule_json";
    private static final String KEY_CURRENT = "current_json";
    private static final String KEY_NEXT = "next_json";
    private static final String KEY_DONE_KEYS = "done_keys_json";
    private static final String KEY_SYNCED_AT = "synced_at";
    private static final String KEY_PENDING_LOGS = "pending_logs_json";
    private static final String KEY_LAUNCH_SLOT = "launch_slot_id";
    private static final String KEY_LAUNCH_DATE = "launch_date";
    private static final String KEY_LAUNCH_CAT = "launch_cat";

    static final class TaskState {
        final String id;
        final String label;
        final String time;
        final String timeRange;
        final String cat;

        TaskState(String id, String label, String time, String timeRange, String cat) {
            this.id = id;
            this.label = label;
            this.time = time;
            this.timeRange = timeRange;
            this.cat = cat == null || cat.isEmpty() ? "train" : cat;
        }

        String slotKey() {
            return cat + ":" + id;
        }
    }

    static final class Snapshot {
        final List<WidgetTaskEngine.Slot> schedule;
        final TaskState current;
        final TaskState next;
        final Set<String> doneKeys;
        final boolean staleDay;

        Snapshot(
            List<WidgetTaskEngine.Slot> schedule,
            TaskState current,
            TaskState next,
            Set<String> doneKeys,
            boolean staleDay
        ) {
            this.schedule = schedule;
            this.current = current;
            this.next = next;
            this.doneKeys = doneKeys == null ? new HashSet<>() : doneKeys;
            this.staleDay = staleDay;
        }

        boolean isDone(TaskState task) {
            return task != null && doneKeys.contains(task.slotKey());
        }
    }

    static final class LaunchAction {
        final String slotId;
        final String cat;
        final String dateStr;

        LaunchAction(String slotId, String cat, String dateStr) {
            this.slotId = slotId;
            this.cat = cat;
            this.dateStr = dateStr;
        }
    }

    static final class PendingLog {
        final String cat;
        final String slotId;
        final String date;

        PendingLog(String cat, String slotId, String date) {
            this.cat = cat;
            this.slotId = slotId;
            this.date = date;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void saveFromJs(Context ctx, JSObject data) {
        if (data == null) return;
        SharedPreferences.Editor editor = prefs(ctx).edit();
        String today = data.getString(KEY_TODAY, WidgetTaskEngine.todayIso());
        editor.putString(KEY_TODAY, today);
        editor.putString(KEY_SCHEDULE, data.getString("scheduleJson", "[]"));
        putTask(editor, KEY_CURRENT, data.getJSObject("current"));
        putTask(editor, KEY_NEXT, data.getJSObject("next"));
        if (data.has("doneKeys")) {
            try {
                editor.putString(KEY_DONE_KEYS, data.getJSONArray("doneKeys").toString());
            } catch (Exception e) {
                editor.putString(KEY_DONE_KEYS, "[]");
            }
        }
        editor.putLong(KEY_SYNCED_AT, System.currentTimeMillis());
        editor.apply();
    }

    private static void putTask(SharedPreferences.Editor editor, String key, JSObject task) {
        if (task == null) {
            editor.remove(key);
            return;
        }
        editor.putString(key, task.toString());
    }

    static Snapshot load(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String today = WidgetTaskEngine.todayIso();
        String storedDay = p.getString(KEY_TODAY, "");
        boolean staleDay = !today.equals(storedDay);
        String scheduleJson = p.getString(KEY_SCHEDULE, "[]");
        List<WidgetTaskEngine.Slot> schedule = WidgetTaskEngine.parseSchedule(scheduleJson);
        Set<String> doneKeys = readDoneKeys(p.getString(KEY_DONE_KEYS, "[]"));

        TaskState current = readTask(p.getString(KEY_CURRENT, null));
        TaskState next = readTask(p.getString(KEY_NEXT, null));

        if (staleDay || (current == null && next == null)) {
            WidgetTaskEngine.TaskPair pair = WidgetTaskEngine.resolveForToday(scheduleJson);
            if (pair.current != null) current = fromSlot(pair.current);
            if (pair.next != null) next = fromSlot(pair.next);
        } else if (current != null && next == null) {
            WidgetTaskEngine.TaskPair pair = WidgetTaskEngine.resolveForToday(scheduleJson);
            if (pair.next != null) next = fromSlot(pair.next);
        }

        return new Snapshot(schedule, current, next, doneKeys, staleDay);
    }

    static void recomputeFromSchedule(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String scheduleJson = p.getString(KEY_SCHEDULE, "[]");
        WidgetTaskEngine.TaskPair pair = WidgetTaskEngine.resolveForToday(scheduleJson);
        SharedPreferences.Editor editor = p.edit();
        editor.putString(KEY_TODAY, WidgetTaskEngine.todayIso());
        putTask(editor, KEY_CURRENT, pair.current == null ? null : slotToJson(pair.current));
        putTask(editor, KEY_NEXT, pair.next == null ? null : slotToJson(pair.next));
        editor.putLong(KEY_SYNCED_AT, System.currentTimeMillis());
        editor.apply();
    }

    static void markOptimisticDone(Context ctx, String cat, String slotId, String date) {
        SharedPreferences p = prefs(ctx);
        Set<String> doneKeys = readDoneKeys(p.getString(KEY_DONE_KEYS, "[]"));
        doneKeys.add(cat + ":" + slotId);
        JSONArray pending;
        try {
            pending = new JSONArray(p.getString(KEY_PENDING_LOGS, "[]"));
        } catch (Exception e) {
            pending = new JSONArray();
        }
        JSONObject entry = new JSONObject();
        try {
            entry.put("cat", cat);
            entry.put("slotId", slotId);
            entry.put("date", date == null || date.isEmpty() ? WidgetTaskEngine.todayIso() : date);
            pending.put(entry);
        } catch (Exception ignored) {
            // ignore malformed queue write
        }
        JSONArray doneArr = new JSONArray();
        for (String key : doneKeys) doneArr.put(key);
        p.edit()
            .putString(KEY_DONE_KEYS, doneArr.toString())
            .putString(KEY_PENDING_LOGS, pending.toString())
            .apply();
    }

    static void saveLaunchAction(Context ctx, String cat, String slotId, String dateStr) {
        prefs(ctx).edit()
            .putString(KEY_LAUNCH_CAT, cat == null ? "" : cat)
            .putString(KEY_LAUNCH_SLOT, slotId == null ? "" : slotId)
            .putString(KEY_LAUNCH_DATE, dateStr == null ? WidgetTaskEngine.todayIso() : dateStr)
            .apply();
    }

    static LaunchAction consumeLaunchAction(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String slotId = p.getString(KEY_LAUNCH_SLOT, "");
        if (slotId.isEmpty()) return null;
        String cat = p.getString(KEY_LAUNCH_CAT, "");
        String date = p.getString(KEY_LAUNCH_DATE, WidgetTaskEngine.todayIso());
        p.edit()
            .remove(KEY_LAUNCH_SLOT)
            .remove(KEY_LAUNCH_CAT)
            .remove(KEY_LAUNCH_DATE)
            .apply();
        return new LaunchAction(slotId, cat, date);
    }

    static List<PendingLog> consumePendingLogs(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String raw = p.getString(KEY_PENDING_LOGS, "[]");
        p.edit().remove(KEY_PENDING_LOGS).apply();
        List<PendingLog> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String cat = o.optString("cat", "");
                String slotId = o.optString("slotId", "");
                String date = o.optString("date", WidgetTaskEngine.todayIso());
                if (!cat.isEmpty() && !slotId.isEmpty()) {
                    out.add(new PendingLog(cat, slotId, date));
                }
            }
        } catch (Exception ignored) {
            // ignore malformed queue
        }
        return out;
    }

    static void captureLaunchIntent(Context ctx, Intent intent) {
        if (intent == null) return;
        String slotId = intent.getStringExtra(WidgetActionReceiver.EXTRA_SLOT_ID);
        if (slotId == null || slotId.isEmpty()) return;
        saveLaunchAction(
            ctx,
            intent.getStringExtra(WidgetActionReceiver.EXTRA_CAT),
            slotId,
            intent.getStringExtra(WidgetActionReceiver.EXTRA_DATE)
        );
    }

    private static Set<String> readDoneKeys(String json) {
        try {
            return WidgetTaskEngine.parseDoneKeys(new JSONArray(json));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private static JSObject slotToJson(WidgetTaskEngine.Slot slot) {
        JSObject o = new JSObject();
        o.put("id", slot.id);
        o.put("label", slot.label);
        o.put("time", WidgetTaskEngine.formatSlotStartTime(slot.t));
        o.put("timeRange", WidgetTaskEngine.formatTimeRange(slot.t));
        o.put("cat", slot.cat);
        return o;
    }

    private static TaskState fromSlot(WidgetTaskEngine.Slot slot) {
        return new TaskState(
            slot.id,
            slot.label,
            WidgetTaskEngine.formatSlotStartTime(slot.t),
            WidgetTaskEngine.formatTimeRange(slot.t),
            slot.cat
        );
    }

    private static TaskState readTask(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            String id = o.optString("id", "");
            if (id.isEmpty()) return null;
            String timeRange = o.optString("timeRange", "");
            if (timeRange.isEmpty()) timeRange = o.optString("time", "");
            return new TaskState(
                id,
                o.optString("label", ""),
                o.optString("time", ""),
                timeRange,
                o.optString("cat", "train")
            );
        } catch (Exception e) {
            return null;
        }
    }
}
