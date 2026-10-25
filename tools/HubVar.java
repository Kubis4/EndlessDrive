import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class HubVar {
    public static void main(String[] a) throws Exception {
        for (String path : a) {
            BufferedImage im = ImageIO.read(new File(path));
            int w = im.getWidth(), h = im.getHeight();
            double cx = (w - 1) * 0.5, cy = (h - 1) * 0.5;
            double maxR = 1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = im.getRGB(x, y);
                    if (((c >>> 24) & 255) < 8) continue;
                    int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
                    if (r <= 6 && g <= 6 && b <= 6) continue;
                    maxR = Math.max(maxR, Math.hypot(x - cx, y - cy));
                }
            }
            double hubR = maxR * 0.61;
            double ring = hubR * 0.55;
            double[] sum = new double[36], cnt = new double[36];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double d = Math.hypot(x - cx, y - cy);
                    if (Math.abs(d - ring) > 2) continue;
                    int c = im.getRGB(x, y);
                    if (((c >>> 24) & 255) < 8) continue;
                    int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
                    double L = (r * 35 + g * 50 + b * 15) / 100.0;
                    double ang = Math.atan2(y - cy, x - cx);
                    if (ang < 0) ang += Math.PI * 2;
                    int bi = (int) (ang / (Math.PI * 2) * 36) % 36;
                    sum[bi] += L;
                    cnt[bi]++;
                }
            }
            double mean = 0, n = 0;
            for (int i = 0; i < 36; i++) {
                if (cnt[i] > 0) {
                    mean += sum[i] / cnt[i];
                    n++;
                }
            }
            mean /= Math.max(1, n);
            double var = 0;
            for (int i = 0; i < 36; i++) {
                if (cnt[i] > 0) {
                    double d = sum[i] / cnt[i] - mean;
                    var += d * d;
                }
            }
            var /= Math.max(1, n);
            System.out.printf("%s: size=%d hubR=%.1f ringLumaStd=%.2f mean=%.1f%n",
                    path, w, hubR, Math.sqrt(var), mean);
        }
    }
}
