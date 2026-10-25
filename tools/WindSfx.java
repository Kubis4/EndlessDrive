import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Soft wind bed for fog / storm ambient loop. */
public class WindSfx {
    static final int SR = 22050;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        writeWav(out, render());
        System.out.println("wrote " + out.getAbsolutePath());
    }

    static float[] render() {
        int n = SR * 4;
        float[] buf = new float[n];
        float lp = 0f;
        float lp2 = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            float nse = hash(i * 13 + 7);
            lp += 0.018f * (nse - lp);
            lp2 += 0.006f * (lp - lp2);
            float whoosh = (float) Math.sin(2 * Math.PI * (0.11 + 0.04 * Math.sin(t * 0.37)) * t);
            float gust = 0.55f + 0.45f * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * 0.23 * t + 1.1));
            buf[i] = (lp2 * 0.82f + whoosh * lp * 0.28f) * gust * 0.55f;
        }
        // Seamless-ish loop: fade ends into each other.
        int fade = (int) (0.18f * SR);
        for (int i = 0; i < fade; i++) {
            float a = i / (float) fade;
            float b = 1f - a;
            float mixed = buf[i] * a + buf[n - fade + i] * b;
            buf[i] = mixed;
            buf[n - fade + i] = mixed;
        }
        normalize(buf, 0.72f);
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
