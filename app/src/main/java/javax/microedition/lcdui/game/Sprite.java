package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class Sprite extends Layer {
    public static final int TRANS_NONE = 0;
    public static final int TRANS_ROT90 = 5;
    public static final int TRANS_ROT180 = 3;
    public static final int TRANS_ROT270 = 6;
    public static final int TRANS_MIRROR = 2;
    public static final int TRANS_MIRROR_ROT90 = 7;
    public static final int TRANS_MIRROR_ROT180 = 1;
    public static final int TRANS_MIRROR_ROT270 = 4;

    private Image image;
    private int frame;
    private int transform = TRANS_NONE;

    public Sprite(Image image) {
        super(image.getWidth(), image.getHeight());
        this.image = image;
    }

    public Sprite(Image image, int frameWidth, int frameHeight) {
        super(frameWidth, frameHeight);
        this.image = image;
    }

    public void setFrame(int frame) { this.frame = frame; }
    public int getFrame() { return frame; }

    public void setTransform(int transform) { this.transform = transform; }

    @Override
    public void paint(Graphics g) {
        if (!visible) return;
        g.drawRegion(image, 0, 0, width, height, transform, x, y, Graphics.TOP | Graphics.LEFT);
    }
}
