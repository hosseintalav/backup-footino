package com.footino.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmReceiver - توسط AlarmManager در زمان مقرر فعال می‌شود.
 * وظیفه: دریافت اطلاعات مسابقه از Intent extras و نمایش اعلان.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent.getIntExtra("alarm_id", -1);
        String team1 = intent.getStringExtra("team1");
        String team2 = intent.getStringExtra("team2");
        String matchDate = intent.getStringExtra("match_date");
        String matchTime = intent.getStringExtra("match_time");

        if (alarmId == -1 || team1 == null || team2 == null || matchTime == null) {
            return;
        }

        NotificationHelper helper = new NotificationHelper(context.getApplicationContext());
        helper.showMatchNotification(alarmId, team1, team2, matchTime, matchDate);
    }
}
