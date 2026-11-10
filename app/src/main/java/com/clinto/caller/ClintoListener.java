package com.clinto.caller;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Watches phone notifications (e.g. Gmail). When a Clinto request mail arrives, starts the call. */
public class ClintoListener extends NotificationListenerService {

    private static String lastText = "";
    private static long lastTime = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        if (!CallHelper.prefs(this).getBoolean(CallHelper.KEY_ENABLED, true)) return;
        // Netlify direct mode is on -> don't ring twice from the Gmail notification
        if (!CallHelper.prefs(this).getString(WatchService.KEY_TOKEN, "").isEmpty()) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        // skip Gmail's "summary" notifications that group several mails
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String all = (title + "\n" + (big.length() > text.length() ? big : text) + "\n" + sub).trim();

        String keyword = CallHelper.prefs(this)
                .getString(CallHelper.KEY_KEYWORD, CallHelper.DEFAULT_KEYWORD)
                .toLowerCase().trim();
        if (keyword.isEmpty() || !all.toLowerCase().contains(keyword)) return;

        // Gmail sometimes posts the same mail twice; ignore repeats within 2 minutes
        long now = System.currentTimeMillis();
        if (all.equals(lastText) && now - lastTime < 120_000) return;
        lastText = all;
        lastTime = now;

        CallHelper.startCall(this, all);
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
