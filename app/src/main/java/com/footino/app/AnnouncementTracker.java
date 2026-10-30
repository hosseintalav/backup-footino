package com.footino.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.HashSet;
import java.util.Set;

/**
 * AnnouncementTracker - ذخیره پایدار شناسه اعلان‌های عمومی ارسال‌شده.
 *
 * ویژگی کلیدی: هر "id" فقط یک بار در هر نصب برنامه ارسال می‌شود، حتی پس از
 * reboot، بسته‌شدن اپ، یا دریافت مجدد JSON.
 *
 * ذخیره‌سازی: SharedPreferences با نام "footino_announcements" (جداگانه از MatchScheduler).
 * دسترسی همگام‌سازی شده (synchronized) برای جلوگیری از ارسال دوگانه در صورت
 * اجرای همزمان چند Worker.
 */
public class AnnouncementTracker {

    private static final String PREF_NAME = "footino_announcements";
    private static final String KEY_SENT_IDS = "sent_announcement_ids";

    private final SharedPreferences prefs;
    private final Object lock = new Object();

    public AnnouncementTracker(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean wasSent(String id) {
        if (id == null || id.isEmpty()) return true;
        synchronized (lock) {
            Set<String> sentIds = getSentIdsSet();
            return sentIds.contains(id);
        }
    }

    public void markAsSent(String id) {
        if (id == null || id.isEmpty()) return;
        synchronized (lock) {
            Set<String> sentIds = getSentIdsSet();
            sentIds.add(id);
            JSONArray arr = new JSONArray();
            for (String s : sentIds) arr.put(s);
            prefs.edit().putString(KEY_SENT_IDS, arr.toString()).apply();
        }
    }

    private Set<String> getSentIdsSet() {
        String raw = prefs.getString(KEY_SENT_IDS, "[]");
        Set<String> result = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
        } catch (Exception e) {
            return new HashSet<>();
        }
        return result;
    }

    public int computeStableNumericId(String id) {
        return Math.abs((id == null ? "unknown" : id).hashCode());
    }
}
