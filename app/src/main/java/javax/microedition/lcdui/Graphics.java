package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class Graphics {
    public static final int HCENTER = 1;
    public static final int VCENTER = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int TOP = 16;
    public static final int BOTTOM = 32;
    public static final int BASELINE = 64;

    public static final int SOLID = 0;
    public static final int DOTTED = 1;

    private android.graphics.Canvas canvas;
    private final Paint paint;
    private int colorRGB = 0x000000;
    private Font currentFont = Font.getDefaultFont();
    private int translationX = 0;
    private int translationY = 0;
    private int baseSaveCount = -1; // tracks the save count at base state

    private final Rect rectSrc = new Rect();
    private final Rect rectDst = new Rect();
    private final RectF rectF = new RectF();

    public Graphics() {
        this.paint = new Paint();
        this.paint.setAntiAlias(false); 
        this.paint.setStyle(Paint.Style.FILL);
    }

    public void setCanvas(android.graphics.Canvas canvas) {
        // Restore previous save only if we actually made one
        if (this.canvas != null && baseSaveCount >= 0) {
            this.canvas.restoreToCount(baseSaveCount);
        }
        this.canvas = canvas;
        baseSaveCount = -1;
        translationX = 0;
        translationY = 0;
        if (canvas != null) {
            // Save base state — from here we can safely restore to this point
            baseSaveCount = canvas.save();
        }
    }

    public android.graphics.Canvas getCanvas() {
        return canvas;
    }

    public void translate(int x, int y) {
        translationX += x;
        translationY += y;
        if (canvas != null) {
            canvas.translate(x, y);
        }
    }

    public int getTranslateX() {
        return translationX;
    }

    public int getTranslateY() {
        return translationY;
    }

    public void setColor(int RGB) {
        this.colorRGB = RGB;
        paint.setColor(RGB | 0xFF000000);
    }

    public void setColor(int r, int g, int b) {
        int rgb = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        setColor(rgb);
    }

    public int getRGB() {
        return colorRGB;
    }

    public int getRedComponent() {
        return (colorRGB >> 16) & 0xFF;
    }

    public int getGreenComponent() {
        return (colorRGB >> 8) & 0xFF;
    }

    public int getBlueComponent() {
        return colorRGB & 0xFF;
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    public void drawRect(int x, int y, int width, int height) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(x, y, x + width, y + height, paint);
    }

    public void fillRect(int x, int y, int width, int height) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + width, y + height, paint);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.STROKE);
        rectF.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rectF, arcWidth, arcHeight, paint);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.FILL);
        rectF.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rectF, arcWidth, arcHeight, paint);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.STROKE);
        rectF.set(x, y, x + width, y + height);
        canvas.drawArc(rectF, startAngle, arcAngle, false, paint);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        if (canvas == null) return;
        paint.setStyle(Paint.Style.FILL);
        rectF.set(x, y, x + width, y + height);
        canvas.drawArc(rectF, startAngle, arcAngle, true, paint);
    }

    public void setFont(Font font) {
        this.currentFont = (font != null) ? font : Font.getDefaultFont();
        paint.setTypeface(currentFont.getTypeface());
        paint.setTextSize(currentFont.getAndroidSize());
    }

    public Font getFont() {
        return currentFont;
    }

    public void drawString(String str, int x, int y, int anchor) {
        if (canvas == null || str == null) return;
        paint.setStyle(Paint.Style.FILL);
        
        float drawX = x;
        float drawY = y;
        
        if ((anchor & HCENTER) != 0) {
            drawX -= paint.measureText(str) / 2;
        } else if ((anchor & RIGHT) != 0) {
            drawX -= paint.measureText(str);
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float fontHeight = fm.bottom - fm.top;
        if ((anchor & VCENTER) != 0) {
            drawY += fontHeight / 2 - fm.bottom;
        } else if ((anchor & BOTTOM) != 0) {
            drawY -= fm.bottom;
        } else if ((anchor & TOP) != 0) {
            drawY -= fm.top;
        }

        canvas.drawText(str, drawX, drawY, paint);
    }

    public void drawChar(char character, int x, int y, int anchor) {
        drawString(String.valueOf(character), x, y, anchor);
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (canvas == null || img == null) return;
        Bitmap bmp = img.getBitmap();
        if (bmp == null) return;

        float drawX = x;
        float drawY = y;

        if ((anchor & HCENTER) != 0) {
            drawX -= (float) bmp.getWidth() / 2;
        } else if ((anchor & RIGHT) != 0) {
            drawX -= bmp.getWidth();
        }

        if ((anchor & VCENTER) != 0) {
            drawY -= (float) bmp.getHeight() / 2;
        } else if ((anchor & BOTTOM) != 0) {
            drawY -= bmp.getHeight();
        }

        canvas.drawBitmap(bmp, drawX, drawY, paint);
    }

    public void drawRegion(Image src, int x_src, int y_src, int width, int height, int transform, int x_dest, int y_dest, int anchor) {
        if (canvas == null || src == null) return;
        Bitmap bmp = src.getBitmap();
        if (bmp == null) return;

        float drawX = x_dest;
        float drawY = y_dest;

        int destW = width;
        int destH = height;
        
        if (transform >= 4 && transform <= 7) {
            destW = height;
            destH = width;
        }

        if ((anchor & HCENTER) != 0) {
            drawX -= (float) destW / 2;
        } else if ((anchor & RIGHT) != 0) {
            drawX -= destW;
        }

        if ((anchor & VCENTER) != 0) {
            drawY -= (float) destH / 2;
        } else if ((anchor & BOTTOM) != 0) {
            drawY -= destH;
        }

        rectSrc.set(x_src, y_src, x_src + width, y_src + height);
        rectDst.set(0, 0, width, height);

        canvas.save();
        canvas.translate(drawX, drawY);

        // MIDP Sprite transform constants (NOT sequential): each op is applied to
        // the source region so that drawing rectDst=(0,0,width,height) fills the
        // transformed bounding box anchored at (0,0). Sequences below are chosen
        // so no extra offset is introduced.
        //   0 TRANS_NONE | 1 MIRROR_ROT180 | 2 MIRROR | 3 ROT180
        //   4 MIRROR_ROT270 | 5 ROT90 | 6 ROT270 | 7 MIRROR_ROT90
        switch (transform) {
            case 0: // TRANS_NONE
                break;
            case 2: // TRANS_MIRROR (horizontal flip)
                canvas.translate(width, 0);
                canvas.scale(-1, 1);
                break;
            case 1: // TRANS_MIRROR_ROT180 (vertical flip)
                canvas.translate(0, height);
                canvas.scale(1, -1);
                break;
            case 3: // TRANS_ROT180
                canvas.translate(width, height);
                canvas.scale(-1, -1);
                break;
            case 5: // TRANS_ROT90
                canvas.translate(height, 0);
                canvas.rotate(90);
                break;
            case 6: // TRANS_ROT270
                canvas.translate(0, width);
                canvas.rotate(270);
                break;
            case 7: // TRANS_MIRROR_ROT90
                canvas.translate(height, width);
                canvas.rotate(90);
                canvas.scale(-1, 1);
                break;
            case 4: // TRANS_MIRROR_ROT270
                canvas.rotate(270);
                canvas.scale(-1, 1);
                break;
        }

        canvas.drawBitmap(bmp, rectSrc, rectDst, paint);
        canvas.restore();
    }

    public void setClip(int x, int y, int width, int height) {
        if (canvas == null) return;
        // Restore to the base saved state, then re-apply clip
        if (baseSaveCount >= 0) {
            canvas.restoreToCount(baseSaveCount);
            baseSaveCount = canvas.save();
        } else {
            baseSaveCount = canvas.save();
        }
        canvas.translate(translationX, translationY);
        canvas.clipRect(x, y, x + width, y + height);
    }

    public void clipRect(int x, int y, int width, int height) {
        if (canvas == null) return;
        canvas.clipRect(x, y, x + width, y + height);
    }

    public int getClipX() {
        if (canvas == null) return 0;
        Rect bounds = canvas.getClipBounds();
        return bounds.left;
    }

    public int getClipY() {
        if (canvas == null) return 0;
        Rect bounds = canvas.getClipBounds();
        return bounds.top;
    }

    public int getClipWidth() {
        if (canvas == null) return 0;
        Rect bounds = canvas.getClipBounds();
        return bounds.width();
    }

    public int getClipHeight() {
        if (canvas == null) return 0;
        Rect bounds = canvas.getClipBounds();
        return bounds.height();
    }

    /**
     * Renders a series of device-independent RGB+transparency values.
     * This is a critical method used by many J2ME games for direct pixel rendering.
     */
    public void drawRGB(int[] rgbData, int offset, int scanlength, int x, int y, int width, int height, boolean processAlpha) {
        if (canvas == null || rgbData == null) return;
        if (width <= 0 || height <= 0) return;

        // Validate bounds. scanlength may be negative (vertical flip).
        // For each row r the base index is: offset + r * scanlength.
        // The extreme rows are row 0 (base = offset) and row (height-1).
        int firstRowBase = offset;
        int lastRowBase  = offset + (height - 1) * scanlength;
        int minIdx = Math.min(firstRowBase, lastRowBase);
        int maxIdx = Math.max(firstRowBase, lastRowBase) + width - 1;
        if (minIdx < 0 || maxIdx >= rgbData.length) {
            android.util.Log.w("J2ME-Graphics",
                "drawRGB: out-of-bounds. len=" + rgbData.length
                + " minIdx=" + minIdx + " maxIdx=" + maxIdx
                + " offset=" + offset + " scanlength=" + scanlength
                + " w=" + width + " h=" + height);
            return;
        }

        int[] pixels = new int[width * height];
        if (!processAlpha) {
            for (int row = 0; row < height; row++) {
                int srcBase = offset + row * scanlength;
                int dstBase = row * width;
                for (int col = 0; col < width; col++) {
                    pixels[dstBase + col] = rgbData[srcBase + col] | 0xFF000000;
                }
            }
        } else {
            for (int row = 0; row < height; row++) {
                System.arraycopy(rgbData, offset + row * scanlength, pixels, row * width, width);
            }
        }

        Bitmap bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        canvas.drawBitmap(bmp, x, y, paint);
    }

    /**
     * Draws a substring at the given position with anchor.
     */
    public void drawSubstring(String str, int offset, int len, int x, int y, int anchor) {
        if (str == null) return;
        drawString(str.substring(offset, offset + len), x, y, anchor);
    }

    /**
     * Draws characters array.
     */
    public void drawChars(char[] data, int offset, int length, int x, int y, int anchor) {
        if (data == null) return;
        drawString(new String(data, offset, length), x, y, anchor);
    }

    public void setStrokeStyle(int style) {
        // SOLID or DOTTED - simplified impl
    }

    public int getStrokeStyle() {
        return SOLID;
    }

    public int getColor() {
        return colorRGB;
    }
}
