package com.footino.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * MatchSyncWorker - بررسی دوره‌ای فایل‌های JSON در پس‌زمینه.
 *
 * وظایف:
 * 1. دریافت matches.json و زمان‌بندی/لغو اعلان‌های مسابقات
 *    (فقط مسابقات با notify=true، 90 دقیقه قبل از شروع)
 * 2. دریافت notif.json و ارسال اعلان برای هر announcement جدید
 *    (با جلوگیری از ارسال تکراری بر اساس فیلد id)
 */
public class MatchSyncWorker extends Worker {

    private static final String TAG = "MatchSyncWorker";

    private static final String MATCHES_JSON_URL =
        "https://raw.githubusercontent.com/hosseintalav/Footino/refs/heads/main/matches.json";

    private static final String NOTIF_JSON_URL =
        "https://raw.githubusercontent.com/hosseintalav/Footino/refs/heads/main/notif.json";

    public MatchSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "شروع بررسی دوره‌ای JSON در پس‌زمینه");
        Context ctx = getApplicationContext();

        try {
            String matchesJson = fetchJson(MATCHES_JSON_URL);
            if (matchesJson != null) {
                processMatchesJson(ctx, matchesJson);
            }

            String notifJson = fetchJson(NOTIF_JSON_URL);
            if (notifJson != null) {
                processAnnouncementsJson(ctx, notifJson);
            }

            MatchScheduler scheduler = new MatchScheduler(ctx);
            scheduler.cleanupExpiredAlarms();
            scheduler.updateLastSyncTime();

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "خطای غیرمنتظره در Worker", e);
            return Result.retry();
        }
    }

    private void processMatchesJson(Context ctx, String jsonString) {
        try {
            JSONArray matchesArray = new JSONArray(jsonString);
            MatchScheduler scheduler = new MatchScheduler(ctx);
            Set<Integer> previouslyScheduled = scheduler.getScheduledAlarmIds();
            Set<Integer> newScheduledIds = new HashSet<>();

            for (int i = 0; i < matchesArray.length(); i++) {
                try {
                    JSONObject match = matchesArray.getJSONObject(i);
                    if (!match.has("notify") || !match.optBoolean("notify", false)) continue;

                    String date = match.optString("date", "");
                    String time = match.optString("time", "");
                    String team1 = match.optString("team1", "");
                    String team2 = match.optString("team2", "");
                    if (date.isEmpty() || time.isEmpty() || team1.isEmpty() || team2.isEmpty()) continue;

                    int alarmId = scheduler.computeStableId(date, time, team1, team2);
                    newScheduledIds.add(alarmId);
                    scheduler.scheduleAlarm(alarmId, team1, team2, date, time);
                } catch (Exception e) {
                    Log.e(TAG, "خطا در parse مسابقه " + i, e);
                }
            }

            for (Integer oldId : previouslyScheduled) {
                if (!newScheduledIds.contains(oldId)) {
                    scheduler.cancelAlarm(oldId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در parse matches.json", e);
        }
    }

    /**
     * پردازش notif.json و ارسال اعلان برای هر announcement جدید.
     * جلوگیری از ارسال تکراری با AnnouncementTracker (synchronized).
     */
    private void processAnnouncementsJson(Context ctx, String jsonString) {
        try {
            JSONObject root = new JSONObject(jsonString);
            JSONArray announcements = root.optJSONArray("announcements");
            if (announcements == null) {
                Log.d(TAG, "هیچ announcements در notif.json یافت نشد");
                return;
            }

            AnnouncementTracker tracker = new AnnouncementTracker(ctx);
            NotificationHelper notifHelper = new NotificationHelper(ctx);

            for (int i = 0; i < announcements.length(); i++) {
                try {
                    JSONObject ann = announcements.getJSONObject(i);
                    String id = ann.optString("id", "");
                    String title = ann.optString("title", "");
                    String message = ann.optString("message", "");

                    if (id.isEmpty() || title.isEmpty() || message.isEmpty()) continue;

                    if (tracker.wasSent(id)) {
                        Log.d(TAG, "اعلان عمومی " + id + " قبلاً ارسال شده، رد شد");
                        continue;
                    }

                    int numericId = tracker.computeStableNumericId(id);
                    notifHelper.showAnnouncementNotification(numericId, title, message);

                    tracker.markAsSent(id);
                    Log.d(TAG, "اعلان عمومی جدید ارسال شد: " + id);

                } catch (Exception e) {
                    Log.e(TAG, "خطا در parse announcement " + i, e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در parse notif.json", e);
        }
    }

    /**
     * دریافت محتوای JSON از URL. در صورت 404 یا خطا، null برمی‌گرداند (بدون کرش).
     */
    private String fetchJson(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Footino-Android-App/1.6");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + responseCode + " برای " + urlString);
                return null;
            }

            StringBuilder sb = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();

        } catch (Exception e) {
            Log.w(TAG, "خطا در دریافت JSON: " + urlString, e);
            return null;
        } finally {
            if (reader != null) { try { reader.close(); } catch (Exception ignored) {} }
            if (connection != null) connection.disconnect();
        }
    }
}
