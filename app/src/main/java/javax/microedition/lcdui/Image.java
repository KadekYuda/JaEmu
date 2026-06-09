package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import com.emulator.j2me.core.EmulatorEngine;
import javax.microedition.lcdui.game.Sprite;

@SuppressWarnings("unused")
public class Image {
    private final Bitmap bitmap;
    private final boolean mutable;
    private Graphics graphics;

    private Image(Bitmap bitmap, boolean mutable) {
        this.bitmap = bitmap;
        this.mutable = mutable;
    }

    public static Image createImage(int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0); 
        return new Image(bmp, true);
    }

    public static Image createImage(Image source) {
        Bitmap bmp = Bitmap.createBitmap(source.bitmap);
        return new Image(bmp, source.mutable);
    }

    public static Image createImage(String name) throws IOException {
        if (!name.startsWith("/")) {
            name = "/" + name;
        }
        // Try via EmulatorEngine (reads from JAR/ZIP directly)
        InputStream is = EmulatorEngine.getResourceAsStream(name);
        if (is == null) {
            Log.e("J2ME-Image", "Resource not found: " + name);
            throw new IOException("Resource not found: " + name);
        }
        try {
            return createImage(is);
        } finally {
            try { is.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Creates a new mutable Image from a region of the source Image.
     * Used by many J2ME games to split sprite sheets.
     */
    public static Image createImage(Image source, int x, int y, int width, int height, int transform) {
        if (source == null) throw new NullPointerException("source image is null");
        // Extract the sub-region
        Bitmap sub = Bitmap.createBitmap(source.bitmap, x, y, width, height);
        if (transform != 0) {
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            switch (transform) {
                case Sprite.TRANS_ROT90:   matrix.postRotate(90);  break;
                case Sprite.TRANS_ROT180:  matrix.postRotate(180); break;
                case Sprite.TRANS_ROT270:  matrix.postRotate(270); break;
                case Sprite.TRANS_MIRROR:  matrix.preScale(-1, 1); break;
                case Sprite.TRANS_MIRROR_ROT90:  matrix.preScale(-1, 1); matrix.postRotate(90);  break;
                case Sprite.TRANS_MIRROR_ROT180: matrix.preScale(-1, 1); matrix.postRotate(180); break;
                case Sprite.TRANS_MIRROR_ROT270: matrix.preScale(-1, 1); matrix.postRotate(270); break;
            }
            sub = Bitmap.createBitmap(sub, 0, 0, sub.getWidth(), sub.getHeight(), matrix, false);
        }
        return new Image(sub, false);
    }

    public static Image createImage(byte[] imageData, int imageOffset, int imageLength) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeByteArray(imageData, imageOffset, imageLength, opts);
        if (bmp == null) {
            Log.e("J2ME-Image", "Failed to decode image from byte array, length=" + imageLength);
            throw new IllegalArgumentException("Invalid image data");
        }
        return new Image(bmp, false);
    }

    public static Image createImage(InputStream stream) throws IOException {
        if (stream == null) {
            throw new NullPointerException("Stream cannot be null");
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeStream(stream, null, opts);
        if (bmp == null) {
            Log.e("J2ME-Image", "BitmapFactory.decodeStream returned null");
            throw new IOException("Failed to decode image from stream");
        }
        return new Image(bmp, false);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        if (!processAlpha) {
            int[] opaqueRgb = new int[rgb.length];
            for (int i = 0; i < rgb.length; i++) {
                opaqueRgb[i] = rgb[i] | 0xFF000000;
            }
            rgb = opaqueRgb;
        }
        Bitmap bmp = Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888);
        return new Image(bmp, false);
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public Graphics getGraphics() {
        if (!mutable) {
            throw new IllegalStateException("Cannot get Graphics for an immutable Image");
        }
        if (graphics == null) {
            graphics = new Graphics();
            graphics.setCanvas(new android.graphics.Canvas(bitmap));
        }
        return graphics;
    }

    public int getWidth() {
        return bitmap.getWidth();
    }

    public int getHeight() {
        return bitmap.getHeight();
    }

    public boolean isMutable() {
        return mutable;
    }

    public void getRGB(int[] rgbData, int offset, int scanLength, int x, int y, int width, int height) {
        if (bitmap == null || rgbData == null) return;
        if (width <= 0 || height <= 0) return;
        bitmap.getPixels(rgbData, offset, scanLength, x, y, width, height);
    }
}
