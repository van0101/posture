package com.van0101.posture;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * Plays one beep on the ALARM channel: it uses the alarm volume, plays on the speaker
 * even when music or headphones are in use, and still sounds in silent mode.
 * Other audio (music, videos) is lowered for the length of the beep, then comes back.
 */
final class Beeper {

    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private Beeper() {
    }

    /** Plays the sound and returns its length in milliseconds. */
    static long play(Context context, String sound, String pitch) {
        short[] pcm = Sounds.render(sound, pitch);
        long lengthMs = pcm.length * 1000L / Sounds.RATE;
        Context app = context.getApplicationContext();
        AudioManager audio = app.getSystemService(AudioManager.class);

        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(ATTRS)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        if (audio != null) {
            audio.requestAudioFocus(focus);
        }

        AudioTrack track;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(ATTRS)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Sounds.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
        } catch (RuntimeException e) {
            if (audio != null) {
                audio.abandonAudioFocusRequest(focus);
            }
            return 0;
        }

        final AudioTrack playing = track;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                playing.stop();
            } catch (IllegalStateException ignored) {
                // already stopped
            }
            playing.release();
            if (audio != null) {
                audio.abandonAudioFocusRequest(focus);
            }
        }, lengthMs + 250);
        return lengthMs;
    }
}
