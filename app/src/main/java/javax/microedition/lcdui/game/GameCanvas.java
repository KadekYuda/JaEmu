package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED    = 1 << Canvas.UP;
    public static final int DOWN_PRESSED  = 1 << Canvas.DOWN;
    public static final int LEFT_PRESSED  = 1 << Canvas.LEFT;
    public static final int RIGHT_PRESSED = 1 << Canvas.RIGHT;
    public static final int FIRE_PRESSED  = 1 << Canvas.FIRE;
    public static final int GAME_A_PRESSED = 1 << Canvas.GAME_A;
    public static final int GAME_B_PRESSED = 1 << Canvas.GAME_B;
    public static final int GAME_C_PRESSED = 1 << Canvas.GAME_C;
    public static final int GAME_D_PRESSED = 1 << Canvas.GAME_D;

    private Image offscreenImage;
    private Graphics offscreenGraphics;
    // Keys currently held down.
    private int keyStateCurrent;
    // Keys pressed at least once since the last getKeyStates() poll.
    private int keyStateLatch;

    protected GameCanvas(boolean suppressKeyEvents) {
        super();
        // Offscreen buffer is lazy-initialized when dimensions are known
    }

    /** Called by emulator when canvas size is set. Reinit offscreen buffer. */
    @Override
    protected void sizeChanged(int w, int h) {
        offscreenImage = Image.createImage(w, h);
        offscreenGraphics = offscreenImage.getGraphics();
    }

    protected Graphics getGraphics() {
        // Lazy init with default size if sizeChanged was never called
        if (offscreenImage == null) {
            offscreenImage = Image.createImage(width > 0 ? width : 240, height > 0 ? height : 320);
            offscreenGraphics = offscreenImage.getGraphics();
        }
        return offscreenGraphics;
    }

    public int getKeyStates() {
        // Per J2ME spec: a bit is set if the key is currently down OR was pressed
        // at least once since the last call. Currently-held keys persist across
        // polls (so holding a direction keeps moving); the press latch clears.
        int state = keyStateCurrent | keyStateLatch;
        keyStateLatch = 0;
        return state;
    }

    @Override
    public void paint(Graphics g) {
        if (offscreenImage != null) {
            g.drawImage(offscreenImage, 0, 0, Graphics.TOP | Graphics.LEFT);
        }
    }

    public void flushGraphics() {
        repaint();
        serviceRepaints();
    }

    public void flushGraphics(int x, int y, int width, int height) {
        repaint(x, y, width, height);
        serviceRepaints();
    }

    @Override
    public void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        if (action != 0) {
            int bit = 1 << action;
            keyStateCurrent |= bit;
            keyStateLatch |= bit;
        }
    }

    @Override
    public void keyReleased(int keyCode) {
        int action = getGameAction(keyCode);
        if (action != 0) {
            keyStateCurrent &= ~(1 << action);
        }
    }
}
