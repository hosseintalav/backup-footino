package com.footino.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * NotificationHelper - مدیریت کانال‌ها و نمایش اعلان‌های مسابقات و اعلان‌های عمومی.
 *
 * دو کانال مستقل:
 * 1. footino_matches_channel - اعلان 90 دقیقه قبل از شروع مسابقات (notify=true)
 * 2. footino_announcements_channel - اعلان‌های عمومی از notif.json
 */
public class NotificationHelper {

    public static final String MATCH_CHANNEL_ID = "footino_matches_channel";
    public static final String MATCH_CHANNEL_NAME = "اعلان مسابقات Footino";

    public static final String ANNOUNCEMENT_CHANNEL_ID = "footino_announcements_channel";
    public static final String ANNOUNCEMENT_CHANNEL_NAME = "اعلان‌های عمومی Footino";
    public static final String ANNOUNCEMENT_CHANNEL_DESC = "اطلاعیه‌های عمومی برنامه Footino";

    private static final int BASE_MATCH_NOTIFICATION_ID = 10000;
    private static final int BASE_ANNOUNCEMENT_NOTIFICATION_ID = 20000;

    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            NotificationChannel matchChannel = new NotificationChannel(
                MATCH_CHANNEL_ID, MATCH_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            matchChannel.setDescription("اعلان 90 دقیقه قبل از شروع مسابقات فوتبال");
            matchChannel.enableVibration(true);
            matchChannel.enableLights(true);
            manager.createNotificationChannel(matchChannel);

            NotificationChannel announcementChannel = new NotificationChannel(
                ANNOUNCEMENT_CHANNEL_ID, ANNOUNCEMENT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            announcementChannel.setDescription(ANNOUNCEMENT_CHANNEL_DESC);
            announcementChannel.enableVibration(true);
            announcementChannel.enableLights(true);
            manager.createNotificationChannel(announcementChannel);
        }
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * نمایش اعلان مسابقه - عنوان: "team1 در مقابل team2"، متن: "⚽ شروع مسابقه ساعت HH:MM"
     */
    public void showMatchNotification(int alarmId, String team1, String team2, String matchTime, String matchDate) {
        if (!hasNotificationPermission()) return;

        String title = team1 + " در مقابل " + team2;
        String body = "⚽ شروع مسابقه ساعت " + matchTime;

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, alarmId, openAppIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MATCH_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(BASE_MATCH_NOTIFICATION_ID + alarmId, builder.build());
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * نمایش اعلان عمومی - با لمس اعلان، اپ Footino باز می‌شود.
     */
    public void showAnnouncementNotification(int notificationId, String title, String message) {
        if (!hasNotificationPermission()) return;
        if (title == null || message == null) return;

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, notificationId, openAppIntent, pendingFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ANNOUNCEMENT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
                manager.notify(BASE_ANNOUNCEMENT_NOTIFICATION_ID + notificationId, builder.build());
            }
        } catch (Exception ignored) {
        }
    }
}
