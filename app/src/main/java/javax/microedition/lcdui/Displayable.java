package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public abstract class Displayable {
    protected int width = 240; 
    protected int height = 320; 
    private final List<Command> commands = new ArrayList<>();
    private CommandListener listener;

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isShown() {
        return Display.getDisplay(null).getCurrent() == this;
    }

    public synchronized void addCommand(Command cmd) {
        if (cmd == null) return;
        if (!commands.contains(cmd)) {
            commands.add(cmd);
        }
    }

    public synchronized void removeCommand(Command cmd) {
        commands.remove(cmd);
    }

    public synchronized void setCommandListener(CommandListener l) {
        this.listener = l;
    }

    public synchronized CommandListener getCommandListener() {
        return listener;
    }

    public synchronized List<Command> getCommands() {
        return new ArrayList<>(commands);
    }

    public void setDimensions(int w, int h) {
        this.width = w;
        this.height = h;
        sizeChanged(w, h);
    }

    protected void sizeChanged(int w, int h) {
        // Subclasses (like Canvas) override this
    }
}
