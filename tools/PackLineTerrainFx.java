import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Terrain FX ako vodorovná stopa za kolesom: 8 typov × 8 frame-ov.
 * Mriežka ostáva 2×4 dlaždíc, v každej 4×2 frame-y.
 */
public class PackLineTerrainFx {
    private static final int[][] RGB = {
        {191, 174, 142}, // asphalt dust
        {156, 150, 138}, // gravel
        {210, 176, 118}, // sand
        {72, 42, 24},    // mud
        {170, 214, 230}, // water
        {244, 248, 252}, // snow
        {210, 232, 242}, // ice
        {220, 222, 226}  // tyre smoke
    };

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        int frameW = 128;
        int frameH = 72;
        int tileW = frameW * 4;
        int tileH = frameH * 2;
        BufferedImage atlas = new BufferedImage(tileW * 2, tileH * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag = atlas.createGraphics();
        ag.setComposite(java.awt.AlphaComposite.Src);
        ag.setBackground(new Color(0, 0, 0, 0));
        ag.clearRect(0, 0, atlas.getWidth(), atlas.getHeight());
        ag.dispose();

        for (int type = 0; type < 8; type++) {
            int tileCol = type % 2;
            int tileRow = type / 2;
            for (int frame = 0; frame < 8; frame++) {
                int fx = frame % 4;
                int fy = frame / 4;
                int ox = tileCol * tileW + fx * frameW;
                int oy = tileRow * tileH + fy * frameH;
                paintTrail(atlas, ox, oy, frameW, frameH, type, frame);
            }
        }
        ImageIO.write(atlas, "png", out);
    }

    private static void paintTrail(BufferedImage atlas, int ox, int oy, int w, int h, int type, int frame) {
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(ox, oy);
        int[] rgb = RGB[type];
        float midY = h * 0.52f;
        int puffs = 9;
        for (int i = 0; i < puffs; i++) {
            float birth = (frame / 8f + i / (float) puffs) % 1f;
            float x = w * (0.92f - birth * 0.86f);
            float y = midY;
            float rx = w * (0.10f + birth * 0.10f);
            float ry = h * (0.22f + birth * 0.10f);
            float a = (1f - birth) * (type == 7 ? 0.48f : 0.62f);
            if (type == 3 || type == 4 || type == 6) {
                rx = w * (0.07f + birth * 0.08f);
                ry = h * (0.16f + birth * 0.08f);
                a *= 0.90f;
            }
            blob(g, x, y, rx, ry, rgb, a);
            if (type == 1 || type == 3 || type == 6) {
                blob(g, x - rx * 0.25f, y, rx * 0.28f, ry * 0.35f, darken(rgb, 0.68f), a * 0.75f);
            }
        }
        g.dispose();
    }

    private static int[] darken(int[] rgb, float k) {
        return new int[] {
            Math.max(0, (int) (rgb[0] * k)),
            Math.max(0, (int) (rgb[1] * k)),
            Math.max(0, (int) (rgb[2] * k))
        };
    }

    private static void blob(Graphics2D g, float x, float y, float rx, float ry, int[] rgb, float a) {
        float aa = Math.max(0f, Math.min(1f, a));
        if (aa <= 0.01f || rx < 0.5f) return;
        Color core = new Color(rgb[0], rgb[1], rgb[2], Math.round(255 * aa));
        Color edge = new Color(rgb[0], rgb[1], rgb[2], 0);
        float r = Math.max(rx, ry);
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(x, y),
            r,
            new float[] { 0f, 0.55f, 1f },
            new Color[] { core, new Color(rgb[0], rgb[1], rgb[2], Math.round(140 * aa)), edge }
        ));
        g.fill(new Ellipse2D.Float(x - rx, y - ry, rx * 2f, ry * 2f));
    }
}
