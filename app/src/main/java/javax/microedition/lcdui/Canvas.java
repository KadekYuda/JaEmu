package javax.microedition.lcdui;

public abstract class Canvas extends Displayable {
    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;
    public static final int GAME_A = 9;
    public static final int GAME_B = 10;
    public static final int GAME_C = 11;
    public static final int GAME_D = 12;

    public static final int KEY_UP_ARROW = -1;
    public static final int KEY_DOWN_ARROW = -2;
    public static final int KEY_LEFT_ARROW = -3;
    public static final int KEY_RIGHT_ARROW = -4;
    public static final int KEY_SELECT_FIRE = -5;
    public static final int KEY_SOFTKEY_LEFT = -6;
    public static final int KEY_SOFTKEY_RIGHT = -7;
    public static final int KEY_CLEAR = -8;

    private RepaintListener repaintListener;
    private boolean fullScreen = false;

    protected Canvas() {}

    public void setRepaintListener(RepaintListener listener) {
        this.repaintListener = listener;
    }

    public abstract void paint(Graphics g);

    public void repaint() {
        if (repaintListener != null) {
            repaintListener.onRequestRepaint();
        }
    }

    public void repaint(int x, int y, int width, int height) {
        repaint();
    }

    public void serviceRepaints() {
        repaint();
    }

    public boolean isDoubleBuffered() {
        return true;
    }

    public boolean hasPointerEvents() {
        return true;
    }

    public boolean hasPointerMotionEvents() {
        return true;
    }

    public boolean hasRepeatEvents() {
        return true;
    }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case KEY_NUM2:
            case KEY_UP_ARROW:
                return UP;
            case KEY_NUM8:
            case KEY_DOWN_ARROW:
                return DOWN;
            case KEY_NUM4:
            case KEY_LEFT_ARROW:
                return LEFT;
            case KEY_NUM6:
            case KEY_RIGHT_ARROW:
                return RIGHT;
            case KEY_NUM5:
            case KEY_SELECT_FIRE:
                return FIRE;
            default:
                return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return KEY_UP_ARROW;
            case DOWN:
                return KEY_DOWN_ARROW;
            case LEFT:
                return KEY_LEFT_ARROW;
            case RIGHT:
                return KEY_RIGHT_ARROW;
            case FIRE:
                return KEY_SELECT_FIRE;
            default:
                return 0;
        }
    }

    public String getKeyName(int keyCode) {
        switch (keyCode) {
            case KEY_NUM0: return "0";
            case KEY_NUM1: return "1";
            case KEY_NUM2: return "2";
            case KEY_NUM3: return "3";
            case KEY_NUM4: return "4";
            case KEY_NUM5: return "5";
            case KEY_NUM6: return "6";
            case KEY_NUM7: return "7";
            case KEY_NUM8: return "8";
            case KEY_NUM9: return "9";
            case KEY_STAR: return "*";
            case KEY_POUND: return "#";
            case KEY_UP_ARROW: return "UP";
            case KEY_DOWN_ARROW: return "DOWN";
            case KEY_LEFT_ARROW: return "LEFT";
            case KEY_RIGHT_ARROW: return "RIGHT";
            case KEY_SELECT_FIRE: return "FIRE";
            case KEY_SOFTKEY_LEFT: return "SOFT1";
            case KEY_SOFTKEY_RIGHT: return "SOFT2";
            default: return "KEY_" + keyCode;
        }
    }

    public void setFullScreenMode(boolean mode) {
        this.fullScreen = mode;
        if (repaintListener != null) {
            repaintListener.onFullScreenModeChanged(mode);
        }
    }

    public boolean isFullScreen() {
        return fullScreen;
    }

    public void postKeyPressed(int keyCode) {
        keyPressed(keyCode);
    }

    public void postKeyReleased(int keyCode) {
        keyReleased(keyCode);
    }

    public void postKeyRepeated(int keyCode) {
        keyRepeated(keyCode);
    }

    public void postPointerPressed(int x, int y) {
        pointerPressed(x, y);
    }

    public void postPointerReleased(int x, int y) {
        pointerReleased(x, y);
    }

    public void postPointerDragged(int x, int y) {
        pointerDragged(x, y);
    }

    protected void showNotify() {}

    protected void hideNotify() {}

    protected void keyPressed(int keyCode) {}

    protected void keyReleased(int keyCode) {}

    protected void keyRepeated(int keyCode) {}

    protected void pointerPressed(int x, int y) {}

    protected void pointerReleased(int x, int y) {}

    protected void pointerDragged(int x, int y) {}

    public interface RepaintListener {
        void onRequestRepaint();
        void onFullScreenModeChanged(boolean fullScreen);
    }
}
