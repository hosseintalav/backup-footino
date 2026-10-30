package com.footino.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * MatchScheduler - مدیریت زمان‌بندی و لغو اعلان‌های مسابقات با AlarmManager.
 *
 * زمان اعلان: 90 دقیقه (1 ساعت و 30 دقیقه) قبل از شروع مسابقه.
 * منطقه زمانی: Asia/Tehran.
 * شناسه پایدار: hash از (date + time + team1 + team2).
 * ذخیره‌سازی: SharedPreferences با نام "footino_alarms".
 */
public class MatchScheduler {

    private static final String TAG = "MatchScheduler";

    private static final String TIMEZONE_ID = "Asia/Tehran";
    private static final int NOTIFY_MINUTES_BEFORE = 90;
    private static final long MIN_NOTIFY_LEAD_MS = 90 * 60 * 1000L;

    private static final String PREF_NAME = "footino_alarms";
    private static final String KEY_SCHEDULED_ALARMS = "scheduled_alarms";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final Context context;
    private final SharedPreferences prefs;
    private final AlarmManager alarmManager;

    public MatchScheduler(Context context) {
        this.context = context;
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public int computeStableId(String date, String time, String team1, String team2) {
        String key = date + "|" + time + "|" + team1 + "|" + team2;
        return Math.abs(key.hashCode());
    }

    public long computeNotifyTimeMillis(String date, String time) {
        try {
            String dateTimeStr = date + "T" + time + ":00";
            ZonedDateTime matchTime = ZonedDateTime.parse(
                dateTimeStr,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of(TIMEZONE_ID))
            );
            ZonedDateTime notifyTime = matchTime.minusMinutes(NOTIFY_MINUTES_BEFORE);
            return notifyTime.toInstant().toEpochMilli();
        } catch (Exception e) {
            Log.e(TAG, "خطا در parse تاریخ/ساعت: " + date + " " + time, e);
            return -1;
        }
    }

    public boolean scheduleAlarm(int alarmId, String team1, String team2,
                                  String matchDate, String matchTime) {
        long notifyAtMillis = computeNotifyTimeMillis(matchDate, matchTime);
        if (notifyAtMillis < 0) return false;

        long now = System.currentTimeMillis();
        if (notifyAtMillis <= now) {
            long matchTimeMillis = notifyAtMillis + (NOTIFY_MINUTES_BEFORE * 60 * 1000L);
            if (matchTimeMillis - now < MIN_NOTIFY_LEAD_MS) {
                Log.d(TAG, "مسابقه " + team1 + "-" + team2 + " خیلی نزدیک است، اعلان نده");
                return false;
            }
            return false;
        }

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("alarm_id", alarmId);
        intent.putExtra("team1", team1);
        intent.putExtra("team2", team2);
        intent.putExtra("match_date", matchDate);
        intent.putExtra("match_time", matchTime);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent, pendingFlags);

        try { alarmManager.cancel(pendingIntent); } catch (Exception ignored) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, notifyAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, notifyAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, notifyAtMillis, pendingIntent);
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException در زمان‌بندی alarm", se);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "خطا در زمان‌بندی alarm", e);
            return false;
        }

        saveScheduledAlarm(alarmId, team1, team2, matchDate, matchTime, notifyAtMillis);
        Log.d(TAG, "زمان‌بندی شد: " + team1 + " در مقابل " + team2 + " (90 دقیقه قبل از " + matchTime + ")");
        return true;
    }

    public void cancelAlarm(int alarmId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        int pendingFlags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, alarmId, intent, pendingFlags);
        if (pendingIntent != null) {
            try { alarmManager.cancel(pendingIntent); pendingIntent.cancel(); }
            catch (Exception ignored) {}
        }
        removeScheduledAlarm(alarmId);
    }

    private void saveScheduledAlarm(int alarmId, String team1, String team2,
                                     String matchDate, String matchTime, long notifyAtMillis) {
        JSONArray alarms = getScheduledAlarmsArray();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < alarms.length(); i++) {
            try {
                JSONObject obj = alarms.getJSONObject(i);
                if (obj.getInt("alarm_id") != alarmId) updated.put(obj);
            } catch (Exception ignored) {}
        }
        JSONObject newObj = new JSONObject();
        try {
            newObj.put("alarm_id", alarmId);
            newObj.put("team1", team1);
            newObj.put("team2", team2);
            newObj.put("match_date", matchDate);
            newObj.put("match_time", matchTime);
            newObj.put("notify_at_millis", notifyAtMillis);
        } catch (Exception ignored) {}
        updated.put(newObj);
        prefs.edit().putString(KEY_SCHEDULED_ALARMS, updated.toString()).apply();
    }

    private void removeScheduledAlarm(int alarmId) {
        JSONArray alarms = getScheduledAlarmsArray();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < alarms.length(); i++) {
            try {
                JSONObject obj = alarms.getJSONObject(i);
                if (obj.getInt("alarm_id") != alarmId) updated.put(obj);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY_SCHEDULED_ALARMS, updated.toString()).apply();
    }

    public JSONArray getScheduledAlarmsArray() {
        String raw = prefs.getString(KEY_SCHEDULED_ALARMS, "[]");
        try { return new JSONArray(raw); } catch (Exception e) { return new JSONArray(); }
    }

    public Set<Integer> getScheduledAlarmIds() {
        Set<Integer> ids = new HashSet<>();
        JSONArray alarms = getScheduledAlarmsArray();
        for (int i = 0; i < alarms.length(); i++) {
            try { ids.add(alarms.getJSONObject(i).getInt("alarm_id")); }
            catch (Exception ignored) {}
        }
        return ids;
    }

    public void updateLastSyncTime() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
    }

    public void cleanupExpiredAlarms() {
        JSONArray alarms = getScheduledAlarmsArray();
        JSONArray updated = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < alarms.length(); i++) {
            try {
                JSONObject obj = alarms.getJSONObject(i);
                long notifyAt = obj.getLong("notify_at_millis");
                if (now - notifyAt < 2 * 60 * 60 * 1000L) updated.put(obj);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY_SCHEDULED_ALARMS, updated.toString()).apply();
    }
}
