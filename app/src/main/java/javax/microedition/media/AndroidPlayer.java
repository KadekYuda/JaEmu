package javax.microedition.media;

import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.control.VolumeControl;

public class AndroidPlayer implements Player {
    private MediaPlayer mediaPlayer;
    private File tempFile;
    private int state = UNREALIZED;
    private int loopCount = 1;

    public AndroidPlayer(InputStream is, String type) throws IOException {
        tempFile = File.createTempFile("j2me_audio_", ".tmp");
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
        }
        state = REALIZED;
    }

    @Override
    public void realize() throws MediaException {
        if (state == CLOSED) throw new IllegalStateException();
    }

    @Override
    public void prefetch() throws MediaException {
        if (state == CLOSED) throw new IllegalStateException();
        if (state == REALIZED) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(tempFile.getAbsolutePath());
                mediaPlayer.prepare();
                state = PREFETCHED;
            } catch (IOException e) {
                throw new MediaException("Failed to prefetch audio: " + e.getMessage());
            }
        }
    }

    @Override
    public void start() throws MediaException {
        if (state == CLOSED) throw new IllegalStateException();
        if (state == REALIZED) {
            prefetch();
        }
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(loopCount > 1 || loopCount == -1);
            mediaPlayer.start();
            state = STARTED;
        }
    }

    @Override
    public void stop() throws MediaException {
        if (state == CLOSED) throw new IllegalStateException();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            state = PREFETCHED;
        }
    }

    @Override
    public void deallocate() {
        if (state == STARTED) {
            try { stop(); } catch (Exception ignored) {}
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (state == PREFETCHED) {
            state = REALIZED;
        }
    }

    @Override
    public void close() {
        deallocate();
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
        state = CLOSED;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public void setLoopCount(int count) {
        this.loopCount = count;
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(count > 1 || count == -1);
        }
    }

    private final VolumeControl volumeControl = new VolumeControl() {
        private int level = 100;
        private boolean muted = false;

        @Override
        public int setLevel(int level) {
            this.level = Math.max(0, Math.min(100, level));
            applyVolume();
            return this.level;
        }

        @Override
        public int getLevel() {
            return level;
        }

        @Override
        public void setMute(boolean mute) {
            this.muted = mute;
            applyVolume();
        }

        @Override
        public boolean isMuted() {
            return muted;
        }

        private void applyVolume() {
            if (mediaPlayer != null) {
                float vol = muted ? 0f : level / 100f;
                mediaPlayer.setVolume(vol, vol);
            }
        }
    };

    @Override
    public Control[] getControls() {
        return new Control[]{ volumeControl };
    }

    @Override
    public Control getControl(String controlType) {
        if ("VolumeControl".equals(controlType)
                || "javax.microedition.media.control.VolumeControl".equals(controlType)) {
            return volumeControl;
        }
        return null;
    }
}
