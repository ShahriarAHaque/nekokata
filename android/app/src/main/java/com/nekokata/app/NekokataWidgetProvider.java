package com.nekokata.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.util.List;

public class NekokataWidgetProvider extends AppWidgetProvider {

    private enum Tier { SMALL, MEDIUM, LARGE }

    private static final int[] TILE_IDS = {
        R.id.widget_tile_0,
        R.id.widget_tile_1,
        R.id.widget_tile_2,
        R.id.widget_tile_3,
        R.id.widget_tile_4,
        R.id.widget_tile_5,
    };
    private static final int[] TILE_LABEL_IDS = {
        R.id.widget_tile_0_label,
        R.id.widget_tile_1_label,
        R.id.widget_tile_2_label,
        R.id.widget_tile_3_label,
        R.id.widget_tile_4_label,
        R.id.widget_tile_5_label,
    };
    private static final int[] TILE_META_IDS = {
        R.id.widget_tile_0_meta,
        R.id.widget_tile_1_meta,
        R.id.widget_tile_2_meta,
        R.id.widget_tile_3_meta,
        R.id.widget_tile_4_meta,
        R.id.widget_tile_5_meta,
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(
        Context context,
        AppWidgetManager manager,
        int appWidgetId,
        Bundle newOptions
    ) {
        updateWidget(context, manager, appWidgetId);
    }

    @Override
    public void onEnabled(Context context) {
        WidgetTickReceiver.schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetTickReceiver.cancel(context);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, NekokataWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        WidgetStateStore.Snapshot snap = WidgetStateStore.load(context);
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        Tier tier = tierFor(options);
        int layout = layoutFor(tier);
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);

        switch (tier) {
            case LARGE:
                bindLarge(context, views, snap);
                break;
            case MEDIUM:
                bindMedium(context, views, snap);
                break;
            default:
                bindSmall(context, views, snap);
                break;
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    private static Tier tierFor(Bundle options) {
        int minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        int minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40);
        if (minH >= 180 && minW >= 220) return Tier.LARGE;
        if (minH >= 88 || minW >= 250) return Tier.MEDIUM;
        return Tier.SMALL;
    }

    private static int layoutFor(Tier tier) {
        switch (tier) {
            case LARGE: return R.layout.widget_nekokata_large;
            case MEDIUM: return R.layout.widget_nekokata_medium;
            default: return R.layout.widget_nekokata_small;
        }
    }

    private static void bindSmall(Context context, RemoteViews views, WidgetStateStore.Snapshot snap) {
        if (snap.current == null) {
            views.setViewVisibility(R.id.widget_current, View.GONE);
            views.setViewVisibility(R.id.widget_current_empty, View.VISIBLE);
            views.setTextViewText(
                R.id.widget_current_empty,
                snap.staleDay
                    ? context.getString(R.string.widget_no_schedule)
                    : context.getString(R.string.widget_no_current)
            );
            tintRoot(views, "train");
            return;
        }

        WidgetStateStore.TaskState task = snap.current;
        boolean done = snap.isDone(task);
        tintRoot(views, task.cat);

        views.setViewVisibility(R.id.widget_current, View.VISIBLE);
        views.setViewVisibility(R.id.widget_current_empty, View.GONE);
        views.setTextViewText(R.id.widget_current_label, task.label);
        views.setTextViewText(
            R.id.widget_current_meta,
            context.getString(R.string.widget_now_meta, task.time)
        );

        bindCheckButton(context, views, R.id.widget_check, task, done, 11);
        views.setOnClickPendingIntent(R.id.widget_body, openSlotIntent(context, task, 21));
        views.setOnClickPendingIntent(R.id.widget_chevron, openSlotIntent(context, task, 22));
    }

    private static void bindMedium(Context context, RemoteViews views, WidgetStateStore.Snapshot snap) {
        if (snap.current == null) {
            views.setViewVisibility(R.id.widget_current, View.GONE);
            views.setViewVisibility(R.id.widget_next_line, View.GONE);
            views.setViewVisibility(R.id.widget_current_empty, View.VISIBLE);
            views.setTextViewText(
                R.id.widget_current_empty,
                snap.staleDay
                    ? context.getString(R.string.widget_no_schedule)
                    : context.getString(R.string.widget_no_current)
            );
            tintRoot(views, "train");
            return;
        }

        WidgetStateStore.TaskState task = snap.current;
        boolean done = snap.isDone(task);
        WidgetTaskEngine.Slot slot = findSlot(snap.schedule, task.id);
        tintRoot(views, task.cat);

        views.setViewVisibility(R.id.widget_current, View.VISIBLE);
        views.setViewVisibility(R.id.widget_current_empty, View.GONE);
        views.setTextViewText(R.id.widget_current_label, task.label);
        views.setTextViewText(
            R.id.widget_current_meta,
            context.getString(
                R.string.widget_now_range,
                task.time,
                endTime(task.timeRange, task.time)
            )
        );
        views.setTextViewText(R.id.widget_icon, WidgetTaskEngine.categoryEmoji(task.cat));
        views.setInt(R.id.widget_icon, "setTextColor", WidgetTaskEngine.categoryColor(task.cat));

        int progress = slot != null
            ? WidgetTaskEngine.slotTimeProgress(slot, WidgetTaskEngine.todayIso())
            : 0;
        views.setProgressBar(R.id.widget_progress, 100, progress, false);

        bindCheckButton(context, views, R.id.widget_check, task, done, 31);
        views.setOnClickPendingIntent(R.id.widget_body, openSlotIntent(context, task, 32));

        if (snap.next != null) {
            views.setViewVisibility(R.id.widget_next_line, View.VISIBLE);
            views.setTextViewText(
                R.id.widget_next_line,
                context.getString(R.string.widget_next_line, snap.next.time, snap.next.label)
            );
            views.setOnClickPendingIntent(
                R.id.widget_next_line,
                openSlotIntent(context, snap.next, 33)
            );
        } else {
            views.setViewVisibility(R.id.widget_next_line, View.GONE);
        }
    }

    private static void bindLarge(Context context, RemoteViews views, WidgetStateStore.Snapshot snap) {
        List<WidgetTaskEngine.Slot> grid = WidgetTaskEngine.gridSlots(
            snap.schedule,
            findSlot(snap.schedule, snap.current != null ? snap.current.id : null),
            TILE_IDS.length
        );
        WidgetTaskEngine.DayProgress progress = WidgetTaskEngine.dayProgress(snap.schedule, snap.doneKeys);

        if (grid.isEmpty()) {
            views.setViewVisibility(R.id.widget_grid, View.GONE);
            views.setViewVisibility(R.id.widget_current_empty, View.VISIBLE);
            views.setTextViewText(
                R.id.widget_current_empty,
                snap.staleDay
                    ? context.getString(R.string.widget_no_schedule)
                    : context.getString(R.string.widget_no_current)
            );
            return;
        }

        views.setViewVisibility(R.id.widget_grid, View.VISIBLE);
        views.setViewVisibility(R.id.widget_current_empty, View.GONE);
        views.setTextViewText(
            R.id.widget_header,
            context.getString(R.string.widget_day_header, progress.done, progress.total)
        );
        views.setProgressBar(R.id.widget_day_progress, 100, progress.percent(), false);

        String currentId = snap.current != null ? snap.current.id : null;
        for (int i = 0; i < TILE_IDS.length; i++) {
            if (i >= grid.size()) {
                views.setViewVisibility(TILE_IDS[i], View.GONE);
                continue;
            }
            WidgetTaskEngine.Slot slot = grid.get(i);
            WidgetStateStore.TaskState tileTask = new WidgetStateStore.TaskState(
                slot.id,
                slot.label,
                WidgetTaskEngine.formatSlotStartTime(slot.t),
                WidgetTaskEngine.formatTimeRange(slot.t),
                slot.cat
            );
            boolean isNow = slot.id.equals(currentId);
            boolean done = snap.doneKeys.contains(slot.slotKey());

            views.setViewVisibility(TILE_IDS[i], View.VISIBLE);
            views.setInt(
                TILE_IDS[i],
                "setBackgroundResource",
                isNow ? R.drawable.widget_tile_now_ring : R.drawable.widget_tile_bg
            );
            views.setInt(TILE_IDS[i], "setBackgroundColor", WidgetTaskEngine.categoryTileColor(slot.cat));
            views.setTextViewText(TILE_LABEL_IDS[i], slot.label);
            if (isNow) {
                views.setTextViewText(
                    TILE_META_IDS[i],
                    context.getString(R.string.widget_tile_now, WidgetTaskEngine.formatSlotStartTime(slot.t))
                );
            } else if (done) {
                views.setTextViewText(TILE_META_IDS[i], "Done");
            } else {
                views.setTextViewText(TILE_META_IDS[i], WidgetTaskEngine.formatSlotStartTime(slot.t));
            }
            views.setOnClickPendingIntent(TILE_IDS[i], openSlotIntent(context, tileTask, 40 + i));
        }
    }

    private static void bindCheckButton(
        Context context,
        RemoteViews views,
        int viewId,
        WidgetStateStore.TaskState task,
        boolean done,
        int requestCode
    ) {
        if (done) {
            views.setInt(viewId, "setBackgroundResource", R.drawable.widget_check_filled);
            views.setTextViewText(viewId, "✓");
            views.setTextColor(viewId, 0xFF0D0D0F);
        } else {
            views.setInt(viewId, "setBackgroundResource", R.drawable.widget_check_ring);
            views.setTextViewText(viewId, "");
        }
        views.setOnClickPendingIntent(viewId, completeIntent(context, task, requestCode));
    }

    private static void tintRoot(RemoteViews views, String cat) {
        views.setInt(R.id.widget_root, "setBackgroundColor", WidgetTaskEngine.categoryContainerColor(cat));
    }

    private static WidgetTaskEngine.Slot findSlot(List<WidgetTaskEngine.Slot> schedule, String id) {
        if (id == null || schedule == null) return null;
        for (WidgetTaskEngine.Slot slot : schedule) {
            if (id.equals(slot.id)) return slot;
        }
        return null;
    }

    private static String endTime(String timeRange, String fallbackStart) {
        if (timeRange == null || timeRange.isEmpty()) return fallbackStart;
        String[] parts = timeRange.split("—");
        if (parts.length < 2) parts = timeRange.split("-");
        if (parts.length >= 2) return parts[1].trim();
        return fallbackStart;
    }

    private static PendingIntent completeIntent(Context context, WidgetStateStore.TaskState task, int requestCode) {
        Intent intent = new Intent(context, WidgetActionReceiver.class);
        intent.setAction(WidgetActionReceiver.ACTION_COMPLETE);
        intent.putExtra(WidgetActionReceiver.EXTRA_CAT, task.cat);
        intent.putExtra(WidgetActionReceiver.EXTRA_SLOT_ID, task.id);
        intent.putExtra(WidgetActionReceiver.EXTRA_DATE, WidgetTaskEngine.todayIso());
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent openSlotIntent(Context context, WidgetStateStore.TaskState task, int requestCode) {
        Intent intent = new Intent(context, WidgetActionReceiver.class);
        intent.setAction(WidgetActionReceiver.ACTION_OPEN_SLOT);
        intent.putExtra(WidgetActionReceiver.EXTRA_CAT, task.cat);
        intent.putExtra(WidgetActionReceiver.EXTRA_SLOT_ID, task.id);
        intent.putExtra(WidgetActionReceiver.EXTRA_DATE, WidgetTaskEngine.todayIso());
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
