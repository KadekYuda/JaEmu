package javax.microedition.midlet;

public abstract class MIDlet {
    
    // Custom integration hooks populated by the emulator engine
    public static MIDletListener listener;
    public static PropertiesProvider propertiesProvider;

    protected MIDlet() {}

    protected abstract void startApp() throws MIDletStateChangeException;
    
    protected abstract void pauseApp();
    
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() {
        if (listener != null) {
            listener.onMIDletDestroyed();
        }
    }

    public final void notifyPaused() {
        if (listener != null) {
            listener.onMIDletPaused();
        }
    }

    public final String getAppProperty(String key) {
        if (propertiesProvider != null) {
            return propertiesProvider.getProperty(key);
        }
        return null;
    }

    public interface MIDletListener {
        void onMIDletDestroyed();
        void onMIDletPaused();
    }

    public interface PropertiesProvider {
        String getProperty(String key);
    }
}
