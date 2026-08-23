package com.nekokata.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** handles optimistic complete + routes open-slot without generic app launch */
public class WidgetActionReceiver extends BroadcastReceiver {

    static final String ACTION_COMPLETE = "com.nekokata.app.action.WIDGET_COMPLETE";
    static final String ACTION_OPEN_SLOT = "com.nekokata.app.action.WIDGET_OPEN_SLOT";
    static final String EXTRA_CAT = "cat";
    static final String EXTRA_SLOT_ID = "slotId";
    static final String EXTRA_DATE = "dateStr";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String cat = intent.getStringExtra(EXTRA_CAT);
        String slotId = intent.getStringExtra(EXTRA_SLOT_ID);
        String dateStr = intent.getStringExtra(EXTRA_DATE);
        if (slotId == null || slotId.isEmpty()) return;
        if (dateStr == null || dateStr.isEmpty()) {
            dateStr = WidgetTaskEngine.todayIso();
        }

        if (ACTION_COMPLETE.equals(action)) {
            if (cat == null || cat.isEmpty()) cat = "train";
            WidgetStateStore.markOptimisticDone(context, cat, slotId, dateStr);
            lightHaptic(context);
            NekokataWidgetProvider.updateAll(context);
            return;
        }

        if (ACTION_OPEN_SLOT.equals(action)) {
            WidgetStateStore.saveLaunchAction(context, cat, slotId, dateStr);
            Intent launch = new Intent(context, MainActivity.class);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launch.putExtra(EXTRA_CAT, cat);
            launch.putExtra(EXTRA_SLOT_ID, slotId);
            launch.putExtra(EXTRA_DATE, dateStr);
            context.startActivity(launch);
        }
    }

    private static void lightHaptic(Context context) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = context.getSystemService(VibratorManager.class);
                vibrator = manager != null ? manager.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(18);
            }
        } catch (Exception ignored) {
            // haptic is best-effort
        }
    }
}
