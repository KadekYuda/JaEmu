package javax.microedition.lcdui;

import android.graphics.Paint;
import android.graphics.Typeface;

public class Font {
    public static final int STYLE_PLAIN = 0;
    public static final int STYLE_BOLD = 1;
    public static final int STYLE_ITALIC = 2;
    public static final int STYLE_UNDERLINED = 4;

    public static final int SIZE_SMALL = 8;
    public static final int SIZE_MEDIUM = 0;
    public static final int SIZE_LARGE = 16;

    public static final int FACE_SYSTEM = 0;
    public static final int FACE_MONOSPACE = 32;
    public static final int FACE_PROPORTIONAL = 64;

    public static final int FONT_STATIC_TEXT = 0;
    public static final int FONT_INPUT_TEXT = 1;

    private static final Font defaultFont = new Font(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM);

    private final int face;
    private final int style;
    private final int size;

    private final Typeface typeface;
    private final float androidSize;
    private final Paint paint;

    private Font(int face, int style, int size) {
        this.face = face;
        this.style = style;
        this.size = size;

        Typeface baseTypeface;
        switch (face) {
            case FACE_MONOSPACE:
                baseTypeface = Typeface.MONOSPACE;
                break;
            case FACE_PROPORTIONAL:
                baseTypeface = Typeface.SANS_SERIF;
                break;
            case FACE_SYSTEM:
            default:
                baseTypeface = Typeface.DEFAULT;
                break;
        }

        int tfStyle = Typeface.NORMAL;
        if ((style & STYLE_BOLD) != 0 && (style & STYLE_ITALIC) != 0) {
            tfStyle = Typeface.BOLD_ITALIC;
        } else if ((style & STYLE_BOLD) != 0) {
            tfStyle = Typeface.BOLD;
        } else if ((style & STYLE_ITALIC) != 0) {
            tfStyle = Typeface.ITALIC;
        }

        this.typeface = Typeface.create(baseTypeface, tfStyle);

        switch (size) {
            case SIZE_SMALL:
                this.androidSize = 12f;
                break;
            case SIZE_LARGE:
                this.androidSize = 20f;
                break;
            case SIZE_MEDIUM:
            default:
                this.androidSize = 16f;
                break;
        }

        this.paint = new Paint();
        this.paint.setTypeface(this.typeface);
        this.paint.setTextSize(this.androidSize);
    }

    public static Font getFont(int face, int style, int size) {
        return new Font(face, style, size);
    }

    public static Font getDefaultFont() {
        return defaultFont;
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public int getFace() {
        return face;
    }

    public boolean isPlain() {
        return style == STYLE_PLAIN;
    }

    public boolean isBold() {
        return (style & STYLE_BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & STYLE_ITALIC) != 0;
    }

    public boolean isUnderlined() {
        return (style & STYLE_UNDERLINED) != 0;
    }

    public Typeface getTypeface() {
        return typeface;
    }

    public float getAndroidSize() {
        return androidSize;
    }

    public int getHeight() {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return (int) Math.ceil(fm.bottom - fm.top);
    }

    public int charWidth(char ch) {
        return (int) Math.ceil(paint.measureText(String.valueOf(ch)));
    }

    public int charsWidth(char[] ch, int offset, int length) {
        return (int) Math.ceil(paint.measureText(new String(ch, offset, length)));
    }

    public int stringWidth(String str) {
        if (str == null) return 0;
        return (int) Math.ceil(paint.measureText(str));
    }

    public int substringWidth(String str, int offset, int len) {
        if (str == null) return 0;
        return (int) Math.ceil(paint.measureText(str, offset, offset + len));
    }
}
