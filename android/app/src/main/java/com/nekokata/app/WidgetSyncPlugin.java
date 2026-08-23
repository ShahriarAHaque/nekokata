package com.nekokata.app;

import android.content.Context;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.List;

@CapacitorPlugin(name = "WidgetSync")
public class WidgetSyncPlugin extends Plugin {

    @PluginMethod
    public void sync(PluginCall call) {
        JSObject data = call.getData();
        Context ctx = getContext();
        if (ctx == null) {
            call.reject("No context");
            return;
        }
        WidgetStateStore.saveFromJs(ctx, data);
        NekokataWidgetProvider.updateAll(ctx);
        WidgetTickReceiver.schedule(ctx);
        call.resolve();
    }

    @PluginMethod
    public void consumeLaunchAction(PluginCall call) {
        Context ctx = getContext();
        if (ctx == null) {
            call.reject("No context");
            return;
        }
        WidgetStateStore.captureLaunchIntent(ctx, getActivity() != null ? getActivity().getIntent() : null);
        WidgetStateStore.LaunchAction action = WidgetStateStore.consumeLaunchAction(ctx);
        if (action == null) {
            call.resolve(new JSObject());
            return;
        }
        JSObject out = new JSObject();
        out.put("slotId", action.slotId);
        out.put("cat", action.cat);
        out.put("dateStr", action.dateStr);
        call.resolve(out);
    }

    @PluginMethod
    public void consumePendingLogs(PluginCall call) {
        Context ctx = getContext();
        if (ctx == null) {
            call.reject("No context");
            return;
        }
        List<WidgetStateStore.PendingLog> pending = WidgetStateStore.consumePendingLogs(ctx);
        JSArray arr = new JSArray();
        for (WidgetStateStore.PendingLog log : pending) {
            JSObject item = new JSObject();
            item.put("cat", log.cat);
            item.put("slotId", log.slotId);
            item.put("date", log.date);
            arr.put(item);
        }
        JSObject out = new JSObject();
        out.put("logs", arr);
        call.resolve(out);
    }
}
