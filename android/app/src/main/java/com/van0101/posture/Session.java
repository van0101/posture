package com.van0101.posture;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The running session, stored in SharedPreferences so the screen, the service and
 * alarm wake-ups all read the same state, even after the system restarts the process.
 *
 * Beeps happen at start + k * interval for k = 0 .. beepsTotal().
 */
final class Session {

    private static final String PREFS = "session";

    final boolean running;
    final long startMs;
    final int totalMin;
    final int intervalMin;
    final String sound;
    final String pitch;

    private Session(boolean running, long startMs, int totalMin, int intervalMin, String sound, String pitch) {
        this.running = running;
        this.startMs = startMs;
        this.totalMin = totalMin;
        this.intervalMin = intervalMin;
        this.sound = sound;
        this.pitch = pitch;
    }

    static Session load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Session(
                p.getBoolean("running", false),
                p.getLong("startMs", 0),
                Math.max(1, p.getInt("totalMin", 60)),
                Math.max(1, p.getInt("intervalMin", 5)),
                p.getString("sound", "double"),
                p.getString("pitch", "bright"));
    }

    static void start(Context c, int totalMin, int intervalMin, String sound, String pitch, long now) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("running", true)
                .putLong("startMs", now)
                .putInt("totalMin", totalMin)
                .putInt("intervalMin", intervalMin)
                .putString("sound", sound)
                .putString("pitch", pitch)
                .commit();
    }

    static void stop(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("running", false).commit();
    }

    long totalMs() {
        return totalMin * 60_000L;
    }

    long intervalMs() {
        return intervalMin * 60_000L;
    }

    long endMs() {
        return startMs + totalMs();
    }

    /** Number of beeps after the starting one. */
    int beepsTotal() {
        return (int) (totalMs() / intervalMs());
    }

    /** When the end chime plays. If the last beep lands on the end, wait for it to finish first. */
    long finishAt() {
        return endMs() + (totalMs() % intervalMs() == 0 ? 1500 : 0);
    }

    /** Time of the first beep after {@code now}, or -1 if there are no more. */
    long nextBeepAt(long now) {
        long k = Math.max(0, (now - startMs) / intervalMs()) + 1;
        return k <= beepsTotal() ? startMs + k * intervalMs() : -1;
    }

    /** Beeps already played, not counting the starting one. */
    int done(long now) {
        long k = Math.max(0, (now - startMs) / intervalMs());
        return (int) Math.min(beepsTotal(), k);
    }

    boolean isActive(long now) {
        return running && now < finishAt() + 5_000;
    }
}
