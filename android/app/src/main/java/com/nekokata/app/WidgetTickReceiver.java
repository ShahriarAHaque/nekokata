package com.nekokata.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

/** keeps widget current/next in sync with the clock when the app is closed */
public class WidgetTickReceiver extends BroadcastReceiver {

    static final String ACTION_TICK = "com.nekokata.app.action.WIDGET_TICK";

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, WidgetTickReceiver.class);
        intent.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static void schedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        Calendar next = Calendar.getInstance();
        next.add(Calendar.MINUTE, 1);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        alarm.setInexactRepeating(
            AlarmManager.RTC,
            next.getTimeInMillis(),
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(app)
        );
    }

    static void cancel(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarm = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            alarm.cancel(pendingIntent(app));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)
            || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WidgetTickReceiver.schedule(context);
        }
        if (ACTION_TICK.equals(action)
            || Intent.ACTION_TIME_CHANGED.equals(action)
            || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
            || Intent.ACTION_DATE_CHANGED.equals(action)
            || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WidgetStateStore.recomputeFromSchedule(context);
            NekokataWidgetProvider.updateAll(context);
        }
    }
}
