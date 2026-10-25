import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Distant thunder crack + rumble one-shot. */
public class ThunderSfx {
    static final int SR = 22050;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        writeWav(out, render());
        System.out.println("wrote " + out.getAbsolutePath());
    }

    static float[] render() {
        float duration = 2.4f;
        int n = (int) (SR * duration);
        float[] buf = new float[n];
        // Initial crack.
        for (int i = 0; i < (int) (0.09f * SR); i++) {
            float t = i / (float) SR;
            float nse = hash(i * 17 + 3);
            float env = (float) Math.exp(-t / 0.018);
            buf[i] += nse * env * 0.95f;
            buf[i] += (float) Math.sin(2 * Math.PI * 95 * t) * (float) Math.exp(-t / 0.04) * 0.35f;
        }
        // Secondary slap.
        int slap = (int) (0.14f * SR);
        for (int i = 0; i < (int) (0.07f * SR); i++) {
            float t = i / (float) SR;
            float nse = hash(i * 29 + 11);
            buf[slap + i] += nse * (float) Math.exp(-t / 0.022) * 0.55f;
        }
        // Low rumble tail.
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            float nse = hash(i * 9 + 101);
            lp += 0.012f * (nse - lp);
            float env = (float) Math.exp(-t / 0.85) * (0.35f + 0.65f * (float) Math.exp(-t / 0.25));
            buf[i] += lp * env * 0.9f;
            buf[i] += (float) Math.sin(2 * Math.PI * 38 * t) * env * 0.22f;
            buf[i] += (float) Math.sin(2 * Math.PI * 62 * t) * env * 0.12f;
        }
        normalize(buf, 0.88f);
        return buf;
    }

    static float hash(int s) {
        s = (s ^ 61) ^ (s >>> 16);
        s = s + (s << 3);
        s = s ^ (s >>> 4);
        s = s * 0x27d4eb2d;
        s = s ^ (s >>> 15);
        return (s & 0xffff) / 32768f - 1f;
    }

    static void normalize(float[] buf, float peak) {
        float max = 1e-6f;
        for (float v : buf) max = Math.max(max, Math.abs(v));
        float g = peak / max;
        for (int i = 0; i < buf.length; i++) buf[i] *= g;
    }

    static void writeWav(File file, float[] samples) throws Exception {
        byte[] pcm = new byte[samples.length * 2];
        ByteBuffer le = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples.length; i++) {
            float v = Math.max(-1f, Math.min(1f, samples[i]));
            le.putShort(i * 2, (short) Math.round(v * 32767f));
        }
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int dataLen = pcm.length;
        header.write(new byte[] { 'R', 'I', 'F', 'F' });
        putInt(header, 36 + dataLen);
        header.write(new byte[] { 'W', 'A', 'V', 'E', 'f', 'm', 't', ' ' });
        putInt(header, 16);
        putShort(header, 1);
        putShort(header, 1);
        putInt(header, SR);
        putInt(header, SR * 2);
        putShort(header, 2);
        putShort(header, 16);
        header.write(new byte[] { 'd', 'a', 't', 'a' });
        putInt(header, dataLen);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(header.toByteArray());
            fos.write(pcm);
        }
    }

    static void putInt(ByteArrayOutputStream out, int v) {
        out.write(v & 255);
        out.write((v >> 8) & 255);
        out.write((v >> 16) & 255);
        out.write((v >> 24) & 255);
    }

    static void putShort(ByteArrayOutputStream out, int v) {
        out.write(v & 255);
        out.write((v >> 8) & 255);
    }
}
