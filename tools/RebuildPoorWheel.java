import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Poor wheel from existing project art only. Flatten disk lighting so spin
 * does not carry a directional shadow. No new generated artwork.
 */
public class RebuildPoorWheel {
    static final double HUB = 0.61;

    public static void main(String[] args) throws Exception {
        File srcFile = firstExisting(
                "art/wheels/_orig/poor_git.png",
                "art/car-upgrade-2026-09-06/final/wheel_poor.png",
                "art/car-upgrade-2026-09-05/final/wheel_poor.png"
        );
        System.out.println("Source: " + srcFile.getPath());
        BufferedImage src = ImageIO.read(srcFile);
        Pix pix = Pix.from(src);
        punchBlackPlate(pix);
        flattenHubLighting(pix, HUB);
        // Soft radial dish only (symmetric) – kill leftover angular shade.
        enforceRadialHub(pix, HUB);

        BufferedImage flat = pix.toImage();
        BufferedImage out = scaleToSquare(flat, 512);
        punchBlackPlate(Pix.from(out)); // ensure corners after scale
        // re-get pixels after scale
        Pix outPix = Pix.from(out);
        punchBlackPlate(outPix);
        out = outPix.toImage();
        out = cropToContent(out, 2);

        File outHd = new File("art/wheels/wheel_poor_hd_source.png");
        File outPng = new File("app/src/main/res/drawable-nodpi/wheel_poor.png");
        ImageIO.write(out, "png", outHd);
        ImageIO.write(out, "png", outPng);
        File oldWebp = new File("app/src/main/res/drawable-nodpi/wheel_poor.webp");
        if (oldWebp.isFile()) oldWebp.delete();

        double fill = contentRadius(Pix.from(out)) / (out.getWidth() * 0.5);
        System.out.printf("Wrote %s (%dx%d fill=%.3f)%n", outPng.getPath(), out.getWidth(), out.getHeight(), fill);
    }

    static File firstExisting(String... paths) {
        for (String p : paths) {
            File f = new File(p);
            if (f.isFile()) return f;
        }
        throw new IllegalStateException("No existing poor wheel found");
    }

    static BufferedImage scaleToSquare(BufferedImage src, int side) {
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, side, side, null);
        g.dispose();
        return out;
    }

    static BufferedImage cropToContent(BufferedImage src, int pad) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (((src.getRGB(x, y) >>> 24) & 0xFF) <= 8) continue;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX) return src;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad);
        maxY = Math.min(h - 1, maxY + pad);
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        int side = Math.max(cw, ch);
        if ((side & 1) == 1) side++;
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        int ox = (side - cw) / 2, oy = (side - ch) / 2;
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getSubimage(minX, minY, cw, ch), ox, oy, null);
        g.dispose();
        return out;
    }

    static void flattenHubLighting(Pix pix, double hubFrac) {
        double cx = (pix.w - 1) * 0.5, cy = (pix.h - 1) * 0.5;
        double maxR = contentRadius(pix);
        double hubR = maxR * hubFrac;
        int rings = Math.max(32, (int) Math.ceil(hubR) + 2);
        double[] sumR = new double[rings];
        double[] sumG = new double[rings];
        double[] sumB = new double[rings];
        double[] wt = new double[rings];
        for (int y = 0; y < pix.h; y++) {
            for (int x = 0; x < pix.w; x++) {
                int c = pix.get(x, y);
                if (isBg(c)) continue;
                double d = Math.hypot(x - cx, y - cy);
                if (d > hubR) continue;
                int ri = Math.min(rings - 1, (int) d);
                double a = alpha(c) / 255.0;
                sumR[ri] += red(c) * a;
                sumG[ri] += green(c) * a;
                sumB[ri] += blue(c) * a;
                wt[ri] += a;
            }
        }
        for (int y = 0; y < pix.h; y++) {
            for (int x = 0; x < pix.w; x++) {
                int c = pix.get(x, y);
                if (isBg(c)) continue;
                double d = Math.hypot(x - cx, y - cy);
                if (d > hubR) continue;
                int ri = Math.min(rings - 1, (int) d);
                if (wt[ri] < 1e-3) continue;
                int mr = (int) Math.round(sumR[ri] / wt[ri]);
                int mg = (int) Math.round(sumG[ri] / wt[ri]);
                int mb = (int) Math.round(sumB[ri] / wt[ri]);
                int r = red(c), g = green(c), b = blue(c);
                boolean rust = r > g + 12 && r > b + 12 && r > 55;
                boolean hole = r < 28 && g < 28 && b < 28 && (r + g + b) < 70;
                int nr, ng, nb;
                if (hole) {
                    nr = Math.min(r, mr);
                    ng = Math.min(g, mg);
                    nb = Math.min(b, mb);
                } else if (rust) {
                    nr = clamp((mr + r) / 2);
                    ng = clamp((mg * 3 + g) / 4);
                    nb = clamp((mb * 3 + b) / 4);
                } else {
                    nr = mr; ng = mg; nb = mb;
                }
                pix.set(x, y, argb(alpha(c), nr, ng, nb));
            }
        }
    }

    /** Second pass: replace hub with pure ring colors except holes/rust. */
    static void enforceRadialHub(Pix pix, double hubFrac) {
        flattenHubLighting(pix, hubFrac);
    }

    static void punchBlackPlate(Pix pix) {
        int w = pix.w, h = pix.h;
        if (!isBg(pix.get(0, 0)) && !isBg(pix.get(w - 1, 0))
                && !isBg(pix.get(0, h - 1)) && !isBg(pix.get(w - 1, h - 1))) {
            return;
        }
        boolean[] seen = new boolean[w * h];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.function.BiConsumer<Integer, Integer> add = (x, y) -> {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            int i = y * w + x;
            if (seen[i] || !isBg(pix.get(x, y))) return;
            seen[i] = true;
            q.add(new int[]{x, y});
        };
        for (int x = 0; x < w; x++) { add.accept(x, 0); add.accept(x, h - 1); }
        for (int y = 0; y < h; y++) { add.accept(0, y); add.accept(w - 1, y); }
        while (!q.isEmpty()) {
            int[] p = q.removeFirst();
            int x = p[0], y = p[1];
            pix.set(x, y, 0);
            add.accept(x - 1, y);
            add.accept(x + 1, y);
            add.accept(x, y - 1);
            add.accept(x, y + 1);
        }
    }

    static double contentRadius(Pix pix) {
        double cx = (pix.w - 1) * 0.5, cy = (pix.h - 1) * 0.5;
        double maxR = 1;
        for (int y = 0; y < pix.h; y++) {
            for (int x = 0; x < pix.w; x++) {
                if (isBg(pix.get(x, y))) continue;
                maxR = Math.max(maxR, Math.hypot(x - cx, y - cy));
            }
        }
        return maxR;
    }

    static boolean isBg(int c) {
        if (alpha(c) < 8) return true;
        return red(c) <= 6 && green(c) <= 6 && blue(c) <= 6;
    }

    static int alpha(int c) { return (c >>> 24) & 0xFF; }
    static int red(int c) { return (c >>> 16) & 0xFF; }
    static int green(int c) { return (c >>> 8) & 0xFF; }
    static int blue(int c) { return c & 0xFF; }
    static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
    static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }

    static final class Pix {
        final int w, h;
        final int[] px;
        Pix(int w, int h, int[] px) { this.w = w; this.h = h; this.px = px; }
        static Pix from(BufferedImage img) {
            int w = img.getWidth(), h = img.getHeight();
            BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            return new Pix(w, h, argb.getRGB(0, 0, w, h, null, 0, w));
        }
        int get(int x, int y) { return px[y * w + x]; }
        void set(int x, int y, int c) { px[y * w + x] = c; }
        BufferedImage toImage() {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, w, h, px, 0, w);
            return out;
        }
    }
}
