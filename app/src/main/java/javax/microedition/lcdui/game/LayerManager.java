package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import java.util.ArrayList;

public class LayerManager {
    private final ArrayList<Layer> layers = new ArrayList<>();
    private int viewX, viewY, viewWidth, viewHeight;

    public void append(Layer l) { layers.add(l); }
    public void insert(Layer l, int index) { layers.add(index, l); }
    public void remove(Layer l) { layers.remove(l); }
    public Layer getLayerAt(int index) { return layers.get(index); }
    public int getSize() { return layers.size(); }

    public void setViewWindow(int x, int y, int width, int height) {
        this.viewX = x;
        this.viewY = y;
        this.viewWidth = width;
        this.viewHeight = height;
    }

    public void paint(Graphics g, int x, int y) {
        g.translate(x - viewX, y - viewY);
        for (int i = layers.size() - 1; i >= 0; i--) {
            layers.get(i).paint(g);
        }
        g.translate(-(x - viewX), -(y - viewY));
    }
}
