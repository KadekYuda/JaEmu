package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    private static final Display instance = new Display();

    private Displayable current;
    private static DisplayListener listener;

    private Display() {}

    public static Display getDisplay(MIDlet m) {
        return instance;
    }

    public static void setListener(DisplayListener l) {
        listener = l;
    }

    public Displayable getCurrent() {
        return current;
    }

    public void setCurrent(Displayable next) {
        Displayable prev = this.current;
        this.current = next;
        if (listener != null) {
            listener.onCurrentDisplayableChanged(next);
        }
        // Fire J2ME lifecycle callbacks after sizeChanged() has been triggered
        if (prev instanceof Canvas) {
            ((Canvas) prev).hideNotify();
        }
        if (next instanceof Canvas) {
            ((Canvas) next).showNotify();
        }
    }

    /**
     * Runs the Runnable on the calling thread immediately.
     * Real J2ME runs it after the next paint; for emulation purposes
     * synchronous execution is safe and avoids NoSuchMethodError crashes
     * that would silently kill the game's internal loop thread.
     */
    public void callSerially(Runnable r) {
        if (r != null) r.run();
    }

    public boolean isColor() {
        return true;
    }

    public int numColors() {
        return 65536;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public boolean flashBacklight(int duration) {
        return false;
    }

    public boolean vibrate(int duration) {
        if (listener != null) {
            listener.onVibrate(duration);
            return true;
        }
        return false;
    }

    public interface DisplayListener {
        void onCurrentDisplayableChanged(Displayable next);
        void onVibrate(int duration);
    }
}
