package com.van0101.posture;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.format.DateFormat;

import java.util.Date;

/**
 * Runs the session in the background.
 *
 * Each beep is a system alarm (AlarmManager.setAlarmClock). Alarm clocks fire on time even in
 * deep sleep (Doze) and wake this service with ACTION_BEEP, which plays the sound and schedules
 * the next one. The foreground notification shows a countdown to the next beep and a Stop button.
 */
public class PostureService extends Service {

    static final String ACTION_START = "com.van0101.posture.START";
    static final String ACTION_BEEP = "com.van0101.posture.BEEP";
    static final String ACTION_FINISH = "com.van0101.posture.FINISH";
    static final String ACTION_STOP = "com.van0101.posture.STOP";

    private static final String CHANNEL = "session";
    private static final int NOTIFICATION_ID = 1;
    private static final int REQ_BEEP = 1;
    private static final int REQ_FINISH = 2;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        long now = System.currentTimeMillis();
        Session s = Session.load(this);

        // Android requires startForeground() soon after every start, so do it first, always.
        goForeground(s, now);

        if (ACTION_STOP.equals(action) || !s.running) {
            endSession();
            return START_NOT_STICKY;
        }
        if (ACTION_FINISH.equals(action) || now >= s.finishAt() + 60_000) {
            finishSession(s);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action) || ACTION_BEEP.equals(action)) {
            beep(s.sound, s.pitch);
        }
        schedule(s, now);
        notifyRunning(s, now);
        return START_NOT_STICKY;
    }

    // ---------- Sound ----------

    private long beep(String sound, String pitch) {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock lock = pm != null
                ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "posture:beep")
                : null;
        if (lock != null) {
            lock.acquire(4_000);
        }
        return Beeper.play(this, sound, pitch);
    }

    // ---------- Alarms ----------

    private void schedule(Session s, long now) {
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        long next = s.nextBeepAt(now + 1_000); // +1s so an alarm that fires a little early isn't repeated
        if (next > 0) {
            setAlarm(am, next, servicePending(ACTION_BEEP, REQ_BEEP));
        } else {
            am.cancel(servicePending(ACTION_BEEP, REQ_BEEP));
        }
        setAlarm(am, s.finishAt(), servicePending(ACTION_FINISH, REQ_FINISH));
    }

    private void setAlarm(AlarmManager am, long at, PendingIntent operation) {
        boolean exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exactAllowed) {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, openAppPending()), operation);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation);
        }
    }

    private void cancelAlarms() {
        AlarmManager am = getSystemService(AlarmManager.class);
        if (am != null) {
            am.cancel(servicePending(ACTION_BEEP, REQ_BEEP));
            am.cancel(servicePending(ACTION_FINISH, REQ_FINISH));
        }
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent i = new Intent(this, PostureService.class).setAction(action);
        return PendingIntent.getForegroundService(this, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent openAppPending() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ---------- Session end ----------

    private void finishSession(Session s) {
        Session.stop(this);
        cancelAlarms();
        long length = beep("finish", s.pitch);
        int n = s.beepsTotal();
        Notification done = baseBuilder()
                .setContentTitle("Session done")
                .setContentText(n + " posture check" + (n == 1 ? "" : "s") + " completed")
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
        stopForeground(STOP_FOREGROUND_DETACH);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, done);
        }
        main.postDelayed(this::stopSelf, length + 500);
    }

    private void endSession() {
        Session.stop(this);
        cancelAlarms();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ---------- Notification ----------

    private void goForeground(Session s, long now) {
        Notification n = s.running ? runningNotification(s, now) : baseBuilder().setContentTitle("Posture").build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void notifyRunning(Session s, long now) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, runningNotification(s, now));
        }
    }

    private Notification runningNotification(Session s, long now) {
        long next = s.nextBeepAt(now + 1_000);
        String ends = DateFormat.getTimeFormat(this).format(new Date(s.endMs()));
        Notification.Builder b = baseBuilder()
                .setContentTitle("Posture check every " + s.intervalMin + " min")
                .setContentText(s.done(now) + " of " + s.beepsTotal() + " done · ends " + ends)
                .setOngoing(true);
        if (next > 0) {
            b.setWhen(next).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                    .setSubText("next beep");
        }
        Intent stop = new Intent(this, PostureService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 3, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat_posture), "Stop", stopPending).build());
        return b.build();
    }

    private Notification.Builder baseBuilder() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Posture session", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Countdown to the next posture check");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_posture)
                .setColor(0xFF34E07F)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openAppPending());
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b;
    }
}
