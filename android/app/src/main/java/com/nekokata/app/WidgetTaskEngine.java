package com.nekokata.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** mirrors bottom-bar slot selection in www/index.html (getNowSlot / getNextSlot) */
final class WidgetTaskEngine {

    static final class Slot {
        final String id;
        final String t;
        final String label;
        final String cat;

        Slot(String id, String t, String label, String cat) {
            this.id = id;
            this.t = t;
            this.label = label;
            this.cat = cat == null || cat.isEmpty() ? "train" : cat;
        }

        String slotKey() {
            return cat + ":" + id;
        }
    }

    static final class TaskPair {
        final Slot current;
        final Slot next;

        TaskPair(Slot current, Slot next) {
            this.current = current;
            this.next = next;
        }
    }

    static final class DayProgress {
        final int done;
        final int total;

        DayProgress(int done, int total) {
            this.done = done;
            this.total = total;
        }

        int percent() {
            if (total <= 0) return 0;
            return Math.round((done * 100f) / total);
        }
    }

    private static final Pattern TIME_START = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static List<Slot> parseSchedule(String json) {
        List<Slot> out = new ArrayList<>();
        if (json == null || json.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String t = o.optString("t", "");
                String label = o.optString("l", "");
                if (id.isEmpty() || t.isEmpty() || label.isEmpty()) continue;
                out.add(new Slot(id, t, label, o.optString("cat", "train")));
            }
        } catch (Exception ignored) {
            // ignore malformed schedule
        }
        return out;
    }

    static TaskPair resolveForToday(String scheduleJson) {
        List<Slot> slots = parseSchedule(scheduleJson);
        Slot current = getNowSlot(slots, todayIso());
        Slot next = getNextSlot(slots, current);
        return new TaskPair(current, next);
    }

    static List<Slot> gridSlots(List<Slot> slots, Slot current, int max) {
        List<Slot> out = new ArrayList<>();
        if (slots == null || slots.isEmpty() || max <= 0) return out;
        int start = 0;
        if (current != null) {
            for (int i = 0; i < slots.size(); i++) {
                if (current.id.equals(slots.get(i).id)) {
                    start = i;
                    break;
                }
            }
        }
        for (int i = start; i < slots.size() && out.size() < max; i++) {
            out.add(slots.get(i));
        }
        return out;
    }

    static DayProgress dayProgress(List<Slot> slots, Set<String> doneKeys) {
        int total = slots == null ? 0 : slots.size();
        int done = 0;
        if (slots != null && doneKeys != null) {
            for (Slot slot : slots) {
                if (doneKeys.contains(slot.slotKey())) done++;
            }
        }
        return new DayProgress(done, total);
    }

    static Set<String> parseDoneKeys(JSONArray arr) {
        Set<String> out = new HashSet<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String key = arr.optString(i, "");
            if (!key.isEmpty()) out.add(key);
        }
        return out;
    }

    static Slot getNowSlot(List<Slot> slots, String dateStr) {
        int ref = referenceMinutesForDate(dateStr);
        if (ref < 0 || slots == null) return null;
        Slot last = null;
        int lastMin = -1;
        int lastIdx = -1;
        for (int idx = 0; idx < slots.size(); idx++) {
            Slot slot = slots.get(idx);
            Integer min = parseSlotStartMinutes(slot.t);
            if (min == null || min > ref) continue;
            if (min > lastMin || (min.equals(lastMin) && idx > lastIdx)) {
                last = slot;
                lastMin = min;
                lastIdx = idx;
            }
        }
        return last;
    }

    static Slot getNextSlot(List<Slot> slots, Slot afterSlot) {
        if (afterSlot == null || slots == null || slots.isEmpty()) return null;
        int idx = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (afterSlot.id.equals(slots.get(i).id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= slots.size() - 1) return null;
        return slots.get(idx + 1);
    }

    static int referenceMinutesForDate(String dateStr) {
        String today = todayIso();
        if (today.equals(dateStr)) {
            Date now = new Date();
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
            String[] parts = fmt.format(now).split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        if (dateStr.compareTo(today) < 0) return 24 * 60 - 1;
        return -1;
    }

    static Integer parseSlotStartMinutes(String timeStr) {
        String start = formatSlotStartTime(timeStr);
        Matcher m = TIME_START.matcher(start);
        if (!m.matches()) return null;
        return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
    }

    static Integer parseSlotEndMinutes(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        String[] parts = timeStr.split("[\u2013\u2014-]");
        if (parts.length < 2) return null;
        Matcher m = TIME_START.matcher(parts[1].trim());
        if (!m.matches()) return null;
        return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
    }

    static int slotTimeProgress(Slot slot, String dateStr) {
        if (slot == null) return 0;
        Integer start = parseSlotStartMinutes(slot.t);
        Integer end = parseSlotEndMinutes(slot.t);
        if (start == null || end == null || end <= start) return 0;
        int ref = referenceMinutesForDate(dateStr);
        if (ref < 0) return 0;
        if (ref <= start) return 0;
        if (ref >= end) return 100;
        return Math.round(((ref - start) * 100f) / (end - start));
    }

    static String formatSlotStartTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        String[] parts = timeStr.split("[\u2013\u2014-]");
        return parts[0].trim();
    }

    static String formatTimeRange(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        String[] parts = timeStr.split("[\u2013\u2014-]");
        if (parts.length < 2) return parts[0].trim();
        return parts[0].trim() + " — " + parts[1].trim();
    }

    static String categoryEmoji(String cat) {
        if (cat == null) cat = "train";
        switch (cat) {
            case "body": return "💪";
            case "nutri": return "🍽";
            case "water": return "💧";
            case "sleep": return "😴";
            case "skin": return "✨";
            case "posture": return "🧍";
            case "focus": return "🎯";
            case "knowledge": return "📚";
            case "talent": return "🎨";
            case "train":
            default: return "🏋";
        }
    }

    static int categoryColor(String cat) {
        if (cat == null) cat = "train";
        switch (cat) {
            case "body": return 0xFFFF4D6D;
            case "nutri": return 0xFF4DFF9D;
            case "water": return 0xFF4DA6FF;
            case "sleep": return 0xFF8BB8FF;
            case "skin": return 0xFFE8A0BF;
            case "posture": return 0xFFA78BFA;
            case "focus": return 0xFFFBBF24;
            case "knowledge": return 0xFF60A5FA;
            case "talent": return 0xFFF472B6;
            case "train":
            default: return 0xFFFF8C42;
        }
    }

    static int categoryContainerColor(String cat) {
        return blend(0xFF0D0D0F, categoryColor(cat), 0.18f);
    }

    static int categoryTileColor(String cat) {
        return blend(0xFF17171C, categoryColor(cat), 0.14f);
    }

    private static int blend(int base, int accent, float accentWeight) {
        float inv = 1f - accentWeight;
        int r = (int) (((base >> 16) & 0xFF) * inv + ((accent >> 16) & 0xFF) * accentWeight);
        int g = (int) (((base >> 8) & 0xFF) * inv + ((accent >> 8) & 0xFF) * accentWeight);
        int b = (int) ((base & 0xFF) * inv + (accent & 0xFF) * accentWeight);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
