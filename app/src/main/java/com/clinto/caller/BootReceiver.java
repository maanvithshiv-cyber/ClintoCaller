package com.clinto.caller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts watching again after the phone restarts. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String token = CallHelper.prefs(c).getString(WatchService.KEY_TOKEN, "");
        if (!token.isEmpty() && CallHelper.prefs(c).getBoolean(CallHelper.KEY_ENABLED, true)) {
            try { WatchService.start(c); } catch (Exception ignored) { }
        }
    }
}
