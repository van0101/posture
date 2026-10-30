package com.van0101.posture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String CHANGELOG_URL = "https://github.com/van0101/posture/blob/main/CHANGELOG.md";

    private static final int[] TOTALS = {30, 45, 60, 90, 120};
    private static final String[] TOTAL_LABELS = {"30m", "45m", "1h", "1h 30", "2h"};
    private static final int[] INTERVALS = {2, 5, 10, 15, 20, 30};
    private static final String[] INTERVAL_LABELS = {"2m", "5m", "10m", "15m", "20m", "30m"};
    private static final String[] SOUNDS = {"pips", "double", "long"};
    private static final String[] SOUND_LABELS = {"Short pips", "Double blip", "Two long"};
    private static final String[] PITCHES = {"bright", "bass"};
    private static final String[] PITCH_LABELS = {"Bright", "Bass note"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences settings;

    private int total;
    private int interval;
    private String sound;
    private String pitch;
    private boolean quiet; // true while code (not the user) changes controls

    private RadioGroup groupTotal, groupInterval, groupSound, groupPitch;
    private EditText customTotal, customInterval;
    private TextView left, next, count, status, cueTitle, cueText;
    private View cue, setup;
    private LinearLayout leds;
    private Button go, battery;

    private String message;
    private boolean messageIsError;
    private boolean wasActive;
    private long lastStart = -1;
    private int lastDone = -1;
    private boolean flashing;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            ui.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = getSharedPreferences("settings", MODE_PRIVATE);
        total = settings.getInt("total", 60);
        interval = settings.getInt("interval", 5);
        sound = settings.getString("sound", "double");
        pitch = settings.getString("pitch", "bright");

        TextView title = findViewById(R.id.title);
        SpannableString t = new SpannableString("POSTURE.");
        t.setSpan(new ForegroundColorSpan(getColor(R.color.go)), 7, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(t);

        groupTotal = findViewById(R.id.groupTotal);
        groupInterval = findViewById(R.id.groupInterval);
        groupSound = findViewById(R.id.groupSound);
        groupPitch = findViewById(R.id.groupPitch);
        customTotal = findViewById(R.id.customTotal);
        customInterval = findViewById(R.id.customInterval);
        left = findViewById(R.id.left);
        next = findViewById(R.id.next);
        count = findViewById(R.id.count);
        status = findViewById(R.id.status);
        cue = findViewById(R.id.cue);
        cueTitle = findViewById(R.id.cueTitle);
        cueText = findViewById(R.id.cueText);
        setup = findViewById(R.id.setup);
        leds = findViewById(R.id.leds);
        go = findViewById(R.id.go);
        battery = findViewById(R.id.battery);

        quiet = true;
        numberGroup(groupTotal, customTotal, TOTALS, TOTAL_LABELS, "total", total, 480);
        numberGroup(groupInterval, customInterval, INTERVALS, INTERVAL_LABELS, "interval", interval, 60);
        textGroup(groupSound, SOUNDS, SOUND_LABELS, "sound", sound);
        textGroup(groupPitch, PITCHES, PITCH_LABELS, "pitch", pitch);
        quiet = false;

        findViewById(R.id.test).setOnClickListener(v -> {
            Beeper.play(this, sound, pitch);
            flash("NOW");
        });
        go.setOnClickListener(v -> onGo());
        battery.setOnClickListener(v -> askForBackground());

        TextView ver = findViewById(R.id.ver);
        ver.setText("v" + BuildConfig.VERSION_NAME);
        TextView changelog = findViewById(R.id.changelog);
        changelog.setPaintFlags(changelog.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        changelog.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CHANGELOG_URL))));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    // ---------- Start / stop ----------

    private void onGo() {
        long now = System.currentTimeMillis();
        if (Session.load(this).isActive(now)) {
            startService(new Intent(this, PostureService.class).setAction(PostureService.ACTION_STOP));
            Session.stop(this);
            setMessage("Stopped.", false);
            render();
            return;
        }
        String error = validate();
        if (error != null) {
            setMessage(error, true);
            render();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Session.start(this, total, interval, sound, pitch, now);
        startForegroundService(new Intent(this, PostureService.class).setAction(PostureService.ACTION_START));
        setMessage(null, false);
        flash("NOW");
        render();
    }

    private String validate() {
        if (total < 1 || total > 480) return "Session length must be between 1 and 480 minutes.";
        if (interval < 1 || interval > 60) return "Beep interval must be between 1 and 60 minutes.";
        if (interval > total) return "Beep interval can't be longer than the session.";
        return null;
    }

    private void askForBackground() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void setMessage(String text, boolean error) {
        message = text;
        messageIsError = error;
    }

    // ---------- Screen ----------

    private void render() {
        long now = System.currentTimeMillis();
        Session s = Session.load(this);
        boolean active = s.isActive(now);

        if (active) {
            left.setText(fmt(s.endMs() - now));
            long nx = s.nextBeepAt(now);
            next.setText(nx > 0 ? fmt(nx - now) : "—");
            int done = s.done(now);
            count.setText(done + " / " + s.beepsTotal());
            renderLeds(done, s.beepsTotal());
            if (s.startMs != lastStart) {
                lastStart = s.startMs;
                lastDone = done;
            } else if (done > lastDone) {
                lastDone = done;
                if (now - (s.startMs + done * s.intervalMs()) < 3_000) flash("NOW");
            }
            String ends = DateFormat.getTimeFormat(this).format(new Date(s.endMs()));
            showStatus("Running until " + ends + ". You can close the app or lock the phone.", false);
        } else {
            if (wasActive && message == null) {
                int n = s.beepsTotal();
                setMessage("Session done. " + n + " posture check" + (n == 1 ? "" : "s") + " completed.", false);
            }
            long totalMs = total * 60_000L;
            long intervalMs = interval * 60_000L;
            left.setText(fmt(totalMs));
            next.setText(fmt(Math.min(intervalMs, totalMs)));
            int beeps = (int) (totalMs / Math.max(1, intervalMs));
            count.setText("0 / " + beeps);
            renderLeds(0, beeps);
            showStatus(message != null ? message : "Set your session and tap Start.", messageIsError);
        }
        if (active && !wasActive) setMessage(null, false);
        wasActive = active;

        setEnabledDeep(setup, !active);
        setup.setAlpha(active ? 0.45f : 1f);
        go.setText(active ? "Stop" : "Start");
        go.setBackgroundResource(active ? R.drawable.stop_bg : R.drawable.go_bg);
        go.setTextColor(getColor(active ? R.color.danger : R.color.amber_ink));
        if (!flashing) cueText.setText(cueIdle(active ? s.intervalMin : interval));

        PowerManager pm = getSystemService(PowerManager.class);
        boolean exempt = pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
        battery.setVisibility(exempt ? View.GONE : View.VISIBLE);
    }

    private void showStatus(String text, boolean error) {
        status.setText(text);
        status.setTextColor(getColor(error ? R.color.danger : R.color.mute));
    }

    private static String cueIdle(int minutes) {
        return "EVERY " + minutes + " MIN";
    }

    private void flash(String text) {
        flashing = true;
        cue.setBackgroundResource(R.drawable.cue_flash);
        cueTitle.setTextColor(getColor(R.color.go_ink));
        cueText.setTextColor(getColor(R.color.go_ink));
        cueText.setText(text);
        ui.removeCallbacks(unflash);
        ui.postDelayed(unflash, 1_500);
    }

    private final Runnable unflash = () -> {
        flashing = false;
        cue.setBackgroundResource(R.drawable.cue_bg);
        cueTitle.setTextColor(getColor(R.color.mute));
        cueText.setTextColor(getColor(R.color.mute));
        render();
    };

    private void renderLeds(int done, int beeps) {
        int n = Math.min(Math.max(beeps, 1), 20);
        if (leds.getChildCount() != n) {
            leds.removeAllViews();
            for (int i = 0; i < n; i++) {
                View led = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                lp.setMargins(dp(2), 0, dp(2), 0);
                led.setLayoutParams(lp);
                leds.addView(led);
            }
        }
        int lit = beeps > 0 ? (int) Math.floor((double) done / beeps * n) : 0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            int on = getColor(p < 0.45 ? R.color.go : p < 0.8 ? R.color.led_red : R.color.led_blue);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(3));
            if (i < lit) {
                d.setColor(on);
            } else {
                d.setColor(getColor(R.color.panel2));
                d.setStroke(dp(1), getColor(R.color.line));
            }
            leds.getChildAt(i).setBackground(d);
        }
    }

    // ---------- Controls ----------

    private void numberGroup(RadioGroup group, EditText custom, int[] values, String[] labels,
                             String key, int current, int max) {
        for (int i = 0; i < values.length; i++) {
            RadioButton chip = chip(labels[i], true);
            chip.setTag(values[i]);
            group.addView(chip);
            if (values[i] == current) group.check(chip.getId());
        }
        if (group.getCheckedRadioButtonId() == View.NO_ID) custom.setText(String.valueOf(current));

        group.setOnCheckedChangeListener((g, id) -> {
            if (id == View.NO_ID) return;
            setNumber(key, (Integer) g.findViewById(id).getTag());
            if (custom.length() > 0 && !quiet) {
                quiet = true;
                custom.setText("");
                quiet = false;
            }
        });
        custom.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable e) {
                if (quiet) return;
                int v;
                try {
                    v = Integer.parseInt(e.toString());
                } catch (NumberFormatException ex) {
                    return;
                }
                if (v >= 1 && v <= max) {
                    quiet = true;
                    group.clearCheck();
                    quiet = false;
                    setNumber(key, v);
                }
            }
        });
    }

    private void setNumber(String key, int value) {
        if ("total".equals(key)) total = value; else interval = value;
        settings.edit().putInt(key, value).apply();
        setMessage(null, false);
        render();
    }

    private void textGroup(RadioGroup group, String[] values, String[] labels, String key, String current) {
        for (int i = 0; i < values.length; i++) {
            RadioButton chip = chip(labels[i], false);
            chip.setTag(values[i]);
            group.addView(chip);
            if (values[i].equals(current)) group.check(chip.getId());
        }
        group.setOnCheckedChangeListener((g, id) -> {
            if (id == View.NO_ID) return;
            String v = (String) g.findViewById(id).getTag();
            if ("sound".equals(key)) sound = v; else pitch = v;
            settings.edit().putString(key, v).apply();
            if (!quiet) Beeper.play(this, sound, pitch);
        });
    }

    private RadioButton chip(String label, boolean numeric) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(label);
        rb.setButtonDrawable(null);
        rb.setBackgroundResource(R.drawable.chip_bg);
        rb.setTextColor(getColorStateList(R.color.chip_text));
        rb.setTextSize(14);
        rb.setGravity(Gravity.CENTER);
        rb.setMinHeight(dp(40));
        rb.setMinimumHeight(dp(40));
        rb.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (numeric) rb.setTypeface(Typeface.MONOSPACE);
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        rb.setLayoutParams(lp);
        return rb;
    }

    private static void setEnabledDeep(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String fmt(long ms) {
        long sec = Math.max(0, (ms + 999) / 1000);
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }
}
