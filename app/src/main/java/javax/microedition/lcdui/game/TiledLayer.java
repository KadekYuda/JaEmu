package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class TiledLayer extends Layer {
    public TiledLayer(int columns, int rows, Image image, int tileWidth, int tileHeight) {
        super(columns * tileWidth, rows * tileHeight);
    }

    public void setCell(int col, int row, int tileIndex) {}

    @Override
    public void paint(Graphics g) {
        // Stub
    }
}
