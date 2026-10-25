import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Vertically pins both ENGINE_SMOKE rows to the same pipe origin as MISFIRE.
 */
public class AlignExhaustSmoke {
    static final int ATLAS_COLS = 3;
    static final int ATLAS_ROWS = 3;
    static final int FRAME_COLS = 4;
    static final int FRAME_ROWS = 2;
    static final int FRAMES = 8;
    static final int MISFIRE = 2;
    static final int SMOKE = 5;
    static final int ALPHA_CUT = 24;

    public static void main(String[] args) throws Exception {
        File path = new File(args[0]);
        BufferedImage atlas = ImageIO.read(path);
        int tileW = atlas.getWidth() / ATLAS_COLS;
        int tileH = atlas.getHeight() / ATLAS_ROWS;
        int frameW = tileW / FRAME_COLS;
        int frameH = tileH / FRAME_ROWS;

        double targetY = 0;
        int targetN = 0;
        // First misfire row is the stable jet; later frames rise and fade.
        for (int f = 0; f < 4; f++) {
            double y = originY(atlas, MISFIRE, f, tileW, tileH, frameW, frameH);
            if (!Double.isNaN(y)) {
                targetY += y;
                targetN++;
            }
        }
        targetY /= targetN;

        BufferedImage out = new BufferedImage(atlas.getWidth(), atlas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(atlas, 0, 0, null);

        for (int f = 0; f < FRAMES; f++) {
            int[] pos = framePos(SMOKE, f, tileW, tileH, frameW, frameH);
            double y = originY(atlas, SMOKE, f, tileW, tileH, frameW, frameH);
            int shift = Double.isNaN(y) ? 0 : (int) Math.round(targetY - y);
            int[] px = new int[frameW * frameH];
            atlas.getRGB(pos[0], pos[1], frameW, frameH, px, 0, frameW);
            int[] shifted = new int[px.length];
            for (int row = 0; row < frameH; row++) {
                int dest = row + shift;
                if (dest < 0 || dest >= frameH) continue;
                System.arraycopy(px, row * frameW, shifted, dest * frameW, frameW);
            }
            out.setRGB(pos[0], pos[1], frameW, frameH, shifted, 0, frameW);
        }
        ImageIO.write(out, "PNG", path);
        System.out.println("aligned smoke to misfire originY=" + targetY);
    }

    static int[] framePos(int effect, int frame, int tileW, int tileH, int frameW, int frameH) {
        return new int[] {
            (effect % ATLAS_COLS) * tileW + (frame % FRAME_COLS) * frameW,
            (effect / ATLAS_COLS) * tileH + (frame / FRAME_COLS) * frameH
        };
    }

    static double originY(BufferedImage atlas, int effect, int frame,
                          int tileW, int tileH, int frameW, int frameH) {
        int[] pos = framePos(effect, frame, tileW, tileH, frameW, frameH);
        int[] px = new int[frameW * frameH];
        atlas.getRGB(pos[0], pos[1], frameW, frameH, px, 0, frameW);
        int maxX = -1;
        for (int i = 0; i < px.length; i++) {
            if (((px[i] >>> 24) & 255) <= ALPHA_CUT) continue;
            int x = i % frameW;
            if (x > maxX) maxX = x;
        }
        if (maxX < 0) return Double.NaN;
        int xCut = maxX - Math.max(2, frameW / 5);
        long sumY = 0;
        int n = 0;
        for (int i = 0; i < px.length; i++) {
            int a = (px[i] >>> 24) & 255;
            if (a <= ALPHA_CUT) continue;
            if ((i % frameW) < xCut) continue;
            sumY += (long) a * (i / frameW);
            n += a;
        }
        return n == 0 ? Double.NaN : sumY / (double) n;
    }
}
