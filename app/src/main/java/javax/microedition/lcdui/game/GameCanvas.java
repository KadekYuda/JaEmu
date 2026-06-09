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
    private int keyState;

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
        // Per J2ME spec: returns bitmask of keys pressed since last call, then clears
        int state = keyState;
        keyState = 0;
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
            keyState |= (1 << action);
        }
    }

    @Override
    public void keyReleased(int keyCode) {
        // bits stay set until getKeyStates() clears them
    }
}
