package com.nokia.mid.sound;
 
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.util.Log;
 
import java.io.File;
import java.io.FileOutputStream;
 
/**
 * Compatibility implementation of the legacy Nokia sound API
 * ({@code com.nokia.mid.sound.Sound}).
 *
 * Older Nokia-targeted J2ME games play audio through this class instead of the
 * standard {@code javax.microedition.media} stack. Without it on the classpath
 * the game class fails to link ({@code NoClassDefFoundError:
 * [Lcom/nokia/mid/sound/Sound;}), which is why titles such as God of War,
 * Bikini Volleyball and Spider-Man 3 crashed on launch.
 *
 * WAV data ({@link #FORMAT_WAV}) is played via {@link MediaPlayer}; OTA ringtone
 * / tone-sequence data ({@link #FORMAT_TONE}) is rendered as a single sine tone
 * via {@link AudioTrack}. Playback failures are swallowed so that audio issues
 * never take the game down.
 */
public class Sound {
 
    private static final String TAG = "NokiaSound";
 
    public static final int FORMAT_TONE = 1;
    public static final int FORMAT_WAV = 5;
 
    public static final int SOUND_PLAYING = 0;
    public static final int SOUND_STOPPED = 1;
    public static final int SOUND_UNINITIALIZED = 3;
 
    private int state = SOUND_UNINITIALIZED;
    private int gain = 255;
 
    private byte[] wavData;
    private int toneFreq;
    private long toneDuration;
    private boolean isTone;
 
    private MediaPlayer player;
    private AudioTrack toneTrack;
    private SoundListener listener;
 
    public Sound(byte[] data, int type) {
        init(data, type);
    }
 
    public Sound(int freq, long duration) {
        init(freq, duration);
    }
 
    public void init(byte[] data, int type) {
        stop();
        this.isTone = (type == FORMAT_TONE);
        this.wavData = data;
        setState(SOUND_STOPPED);
    }
 
    public void init(int freq, long duration) {
        stop();
        this.isTone = true;
        this.toneFreq = freq;
        this.toneDuration = duration;
        this.wavData = null;
        setState(SOUND_STOPPED);
    }
 
    public void play(int loop) {
        try {
            if (isTone && wavData == null) {
                playTone(loop);
            } else if (wavData != null) {
                playWav(loop);
            } else {
                return;
            }
            setState(SOUND_PLAYING);
        } catch (Exception e) {
            Log.w(TAG, "play failed: " + e.getMessage());
            setState(SOUND_STOPPED);
        }
    }
 
    public void stop() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (toneTrack != null) {
                toneTrack.stop();
                toneTrack.release();
                toneTrack = null;
            }
        } catch (Exception ignored) {
        }
        if (state == SOUND_PLAYING) {
            setState(SOUND_STOPPED);
        }
    }
 
    public void resume() {
        if (state == SOUND_STOPPED) {
            play(1);
        }
    }
 
    public void release() {
        stop();
        wavData = null;
        setState(SOUND_UNINITIALIZED);
    }
 
    public int getState() {
        return state;
    }
 
    public void setGain(int gain) {
        this.gain = Math.max(0, Math.min(255, gain));
        float vol = this.gain / 255f;
        if (player != null) {
            try { player.setVolume(vol, vol); } catch (Exception ignored) {}
        }
    }
 
    public int getGain() {
        return gain;
    }
 
    public void setSoundListener(SoundListener listener) {
        this.listener = listener;
    }
 
    public static int getConcurrentSoundCount(int type) {
        return 1;
    }
 
    public static int[] getSupportedFormats() {
        return new int[]{ FORMAT_TONE, FORMAT_WAV };
    }
 
    private void setState(int newState) {
        this.state = newState;
        SoundListener l = listener;
        if (l != null) {
            try { l.soundStateChanged(this, newState); } catch (Exception ignored) {}
        }
    }
 
    private void playWav(int loop) throws Exception {
        if (player != null) {
            player.release();
            player = null;
        }
        File tmp = File.createTempFile("nokia_snd_", ".wav");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(wavData);
        }
        player = new MediaPlayer();
        player.setDataSource(tmp.getAbsolutePath());
        player.setLooping(loop == 0);
        float vol = gain / 255f;
        player.setVolume(vol, vol);
        player.setOnCompletionListener(mp -> setState(SOUND_STOPPED));
        player.prepare();
        player.start();
    }
 
    private void playTone(int loop) {
        final int sampleRate = 16000;
        int durationMs = (int) (toneDuration > 0 ? toneDuration : 200);
        int numSamples = sampleRate * durationMs / 1000;
        if (numSamples <= 0) numSamples = sampleRate / 5;
        final short[] samples = new short[numSamples];
        double freq = toneFreq > 0 ? toneFreq : 440;
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * freq / sampleRate;
            samples[i] = (short) (Math.sin(angle) * Short.MAX_VALUE * (gain / 255f));
        }
        int bytes = numSamples * 2;
        toneTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bytes,
                AudioTrack.MODE_STATIC);
        toneTrack.write(samples, 0, numSamples);
        if (loop == 0) {
            toneTrack.setLoopPoints(0, numSamples, -1);
        }
        toneTrack.play();
    }
}