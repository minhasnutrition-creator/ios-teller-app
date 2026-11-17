package java.awt;

/**
 * Minimal Android stub for {@code java.awt.Color}.
 *
 * <p>The PDF engine (OpenPDF 2.0.3, a desktop-JVM iText fork) hard-references
 * {@code java.awt.Color}: {@code com.lowagie.text.pdf.ExtendedColor} extends it, and
 * {@code Font}, {@code FontFactory}, {@code PdfPCell}, {@code PdfShading} and
 * {@code LineSeparator} all take it as a parameter. Android's runtime has no
 * {@code java.awt} package, so every PDF export died with:
 *
 * <pre>java.lang.NoClassDefFoundError: Failed resolution of: Ljava/awt/Color;</pre>
 *
 * <p>Rewriting ~3,900 lines of PDF generation onto a native Android engine is the
 * clean long-term fix; this stub unblocks it in one file instead. Only the members
 * OpenPDF actually invokes on the app's code paths are implemented — colour
 * components are stored as plain 0-255 ints and read back via the getters.
 *
 * <p>Deliberately NOT implemented: {@code Graphics2D}, {@code BufferedImage},
 * {@code java.awt.Font} and the rest of AWT. Those live only in OpenPDF methods the
 * app never calls ({@code createGraphics*}, the {@code Image.getInstance(java.awt.Image)}
 * overloads), so stubbing them would add surface area for no gain.
 */
public class Color {

    public static final Color white = new Color(255, 255, 255);
    public static final Color WHITE = white;
    public static final Color black = new Color(0, 0, 0);
    public static final Color BLACK = black;
    public static final Color red = new Color(255, 0, 0);
    public static final Color RED = red;
    public static final Color green = new Color(0, 255, 0);
    public static final Color GREEN = green;
    public static final Color blue = new Color(0, 0, 255);
    public static final Color BLUE = blue;
    public static final Color gray = new Color(128, 128, 128);
    public static final Color GRAY = gray;
    public static final Color lightGray = new Color(192, 192, 192);
    public static final Color LIGHT_GRAY = lightGray;
    public static final Color darkGray = new Color(64, 64, 64);
    public static final Color DARK_GRAY = darkGray;
    public static final Color yellow = new Color(255, 255, 0);
    public static final Color YELLOW = yellow;
    public static final Color cyan = new Color(0, 255, 255);
    public static final Color CYAN = cyan;
    public static final Color magenta = new Color(255, 0, 255);
    public static final Color MAGENTA = magenta;
    public static final Color orange = new Color(255, 200, 0);
    public static final Color ORANGE = orange;
    public static final Color pink = new Color(255, 175, 175);
    public static final Color PINK = pink;

    private final int r;
    private final int g;
    private final int b;
    private final int a;

    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public Color(int r, int g, int b, int a) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
        this.a = clamp(a);
    }

    /** Packed 0xRRGGBB (alpha forced opaque), matching AWT's int constructor. */
    public Color(int rgb) {
        this((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }

    public Color(int rgba, boolean hasAlpha) {
        this((rgba >> 16) & 0xFF, (rgba >> 8) & 0xFF, rgba & 0xFF,
                hasAlpha ? ((rgba >>> 24) & 0xFF) : 255);
    }

    public Color(float r, float g, float b) {
        this(Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f), 255);
    }

    public Color(float r, float g, float b, float a) {
        this(Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f), Math.round(a * 255f));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public int getRed() {
        return r;
    }

    public int getGreen() {
        return g;
    }

    public int getBlue() {
        return b;
    }

    public int getAlpha() {
        return a;
    }

    /** Packed 0xAARRGGBB, as AWT returns. */
    public int getRGB() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Color)) {
            return false;
        }
        Color o = (Color) obj;
        return r == o.r && g == o.g && b == o.b && a == o.a;
    }

    @Override
    public int hashCode() {
        return getRGB();
    }

    @Override
    public String toString() {
        return "java.awt.Color[r=" + r + ",g=" + g + ",b=" + b + ",a=" + a + "]";
    }
}
