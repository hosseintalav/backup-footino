package com.footino.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * BootReceiver - پس از راه‌اندازی مجدد گوشی، تمام alarm‌های ثبت‌شده را بازیابی می‌کند.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (!action.equals(Intent.ACTION_BOOT_COMPLETED) &&
            !action.equals(Intent.ACTION_MY_PACKAGE_REPLACED) &&
            !action.equals("android.intent.action.QUICKBOOT_POWERON") &&
            !action.equals("com.htc.intent.action.QUICKBOOT_POWERON")) {
            return;
        }

        Log.d(TAG, "BootReceiver فعال شد: " + action);

        MatchScheduler scheduler = new MatchScheduler(context.getApplicationContext());
        JSONArray alarms = scheduler.getScheduledAlarmsArray();

        int restored = 0;
        for (int i = 0; i < alarms.length(); i++) {
            try {
                JSONObject obj = alarms.getJSONObject(i);
                int alarmId = obj.getInt("alarm_id");
                String team1 = obj.getString("team1");
                String team2 = obj.getString("team2");
                String matchDate = obj.getString("match_date");
                String matchTime = obj.getString("match_time");

                boolean scheduled = scheduler.scheduleAlarm(alarmId, team1, team2, matchDate, matchTime);
                if (scheduled) restored++;
            } catch (Exception e) {
                Log.e(TAG, "خطا در بازیابی alarm", e);
            }
        }

        Log.d(TAG, "بازیابی شد: " + restored + " alarm از " + alarms.length());
    }
}
