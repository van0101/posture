package com.van0101.posture;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates the beep sounds as 16-bit PCM. Same definitions as the web app (app.js),
 * so a "Double blip / Bright" sounds the same on both.
 */
final class Sounds {

    static final int RATE = 44100;

    /** A sound in one pitch: base frequency, tones as {start s, length s}, optional own partials. */
    private static final class Spec {
        final double freq;
        final double[][] tones;
        final double[][] partials;

        Spec(double freq, double[][] tones, double[][] partials) {
            this.freq = freq;
            this.tones = tones;
            this.partials = partials;
        }
    }

    /** Timbre: partials as {frequency multiple, amplitude}, envelope in seconds, exponential decay rate. */
    private static final class Voice {
        final double[][] partials;
        final double attack;
        final double release;
        final double decay;

        Voice(double[][] partials, double attack, double release, double decay) {
            this.partials = partials;
            this.attack = attack;
            this.release = release;
            this.decay = decay;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final Map<String, Voice> VOICES = new HashMap<>();
    private static final Map<String, short[]> CACHE = new HashMap<>();

    static {
        // Triangle-ish wave plus a fifth on top: the original beep.
        VOICES.put("bright", new Voice(new double[][]{{1, 1}, {3, -1 / 9.0}, {5, 1 / 25.0}, {1.5, .3}}, .005, .01, 0));
        // Fundamental + harmonics so phone speakers, which can't play deep lows, still carry the pitch.
        VOICES.put("bass", new Voice(new double[][]{{1, 1}, {2, .7}, {3, .45}, {4, .25}, {5, .12}}, .012, .05, 2.5));

        SPECS.put("pips:bright", new Spec(2400, new double[][]{{0, .05}, {.11, .05}, {.22, .05}}, null));
        SPECS.put("pips:bass", new Spec(220, new double[][]{{0, .08}, {.14, .08}, {.28, .08}}, null));
        SPECS.put("double:bright", new Spec(2000, new double[][]{{0, .09}, {.15, .09}}, null));
        SPECS.put("double:bass", new Spec(196, new double[][]{{0, .12}, {.2, .12}}, null));
        SPECS.put("long:bright", new Spec(1000, new double[][]{{0, .4}, {.6, .4}}, new double[][]{{1, 1}, {2, .15}}));
        SPECS.put("long:bass", new Spec(165, new double[][]{{0, .45}, {.65, .45}}, null));
        SPECS.put("finish:bright", new Spec(1400, new double[][]{{0, .09}, {.15, .09}, {.3, .2}}, null));
        SPECS.put("finish:bass", new Spec(147, new double[][]{{0, .12}, {.2, .12}, {.4, .3}}, null));
    }

    private Sounds() {
    }

    static synchronized short[] render(String sound, String pitch) {
        String key = sound + ":" + pitch;
        short[] cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Spec spec = SPECS.get(key);
        Voice voice = VOICES.get(pitch);
        if (spec == null || voice == null) {
            spec = SPECS.get("double:bright");
            voice = VOICES.get("bright");
        }
        double[][] partials = spec.partials != null ? spec.partials : voice.partials;

        double end = 0;
        for (double[] t : spec.tones) {
            end = Math.max(end, t[0] + t[1]);
        }
        double[] out = new double[(int) Math.ceil((end + .02) * RATE)];

        for (double[] tone : spec.tones) {
            int s0 = (int) Math.round(tone[0] * RATE);
            double dur = tone[1];
            int len = (int) Math.round(dur * RATE);
            for (int i = 0; i < len && s0 + i < out.length; i++) {
                double t = (double) i / RATE;
                double env = Math.min(1, Math.min(t / voice.attack, (dur - t) / voice.release)) * Math.exp(-voice.decay * t);
                double v = 0;
                for (double[] p : partials) {
                    if (spec.freq * p[0] < RATE * .45) {
                        v += p[1] * Math.sin(2 * Math.PI * spec.freq * p[0] * t);
                    }
                }
                out[s0 + i] += env * v;
            }
        }

        double peak = 0;
        for (double x : out) {
            peak = Math.max(peak, Math.abs(x));
        }
        double scale = peak > 0 ? .85 / peak : 0;
        short[] pcm = new short[out.length];
        for (int i = 0; i < out.length; i++) {
            pcm[i] = (short) Math.round(out[i] * scale * Short.MAX_VALUE);
        }
        CACHE.put(key, pcm);
        return pcm;
    }
}
