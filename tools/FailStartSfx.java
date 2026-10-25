import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Dead-battery starter: dry solenoid clicks, no crank and no stall.
 */
public class FailStartSfx {
    static final int SR = 22050;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        float[] samples = render();
        writeWav(out, samples);
        System.out.println("wrote " + out.getAbsolutePath() + " n=" + samples.length);
    }

    static float[] render() {
        float duration = 0.86f;
        int n = (int) (SR * duration);
        float[] buf = new float[n];
        // Six weakening solenoid clacks — the battery hasn't got the current
        // to spin the starter, only the relay slaps.
        float[] starts = { 0.04f, 0.16f, 0.28f, 0.41f, 0.55f, 0.70f };
        float[] gain = { 1.00f, 0.92f, 0.84f, 0.70f, 0.52f, 0.34f };
        float[] pitch = { 1.00f, 0.98f, 1.03f, 0.96f, 1.01f, 0.93f };
        for (int i = 0; i < starts.length; i++) {
            click(buf, starts[i], gain[i], pitch[i], 11 + i * 17);
        }
        // Tiny residual coil buzz that dies with the last click.
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            float env = (float) Math.exp(-t * 3.2) * 0.018f;
            buf[i] += env * (float) Math.sin(2 * Math.PI * 58 * t);
        }
        normalize(buf, 0.86f);
        return buf;
    }

    static void click(float[] buf, float at, float amp, float pitch, int seed) {
        int start = (int) (at * SR);
        int len = (int) (0.055f * SR);
        for (int i = 0; i < len; i++) {
            int idx = start + i;
            if (idx < 0 || idx >= buf.length) continue;
            float t = i / (float) SR;
            float nse = hash(seed + i);
            // Contact: a 1.5 ms noise spike.
            float contact = nse * (float) Math.exp(-t / 0.0014f);
            // Metallic ring of the solenoid plunger.
            float ring = (float) (
                Math.sin(2 * Math.PI * 1860 * pitch * t) * Math.exp(-t / 0.007f) * 0.55 +
                Math.sin(2 * Math.PI * 920 * pitch * t) * Math.exp(-t / 0.011f) * 0.38 +
                Math.sin(2 * Math.PI * 340 * pitch * t) * Math.exp(-t / 0.018f) * 0.22
            );
            // Body thunk of the relay housing.
            float thunk = (float) Math.sin(2 * Math.PI * 118 * pitch * t) *
                (float) Math.exp(-t / 0.022f);
            // Sharp mechanical edge.
            float edge = (t < 0.0012f ? (1f - t / 0.0012f) : 0f) * (nse * 0.4f + 0.6f);
            buf[idx] += amp * (contact * 0.38f + ring * 0.42f + thunk * 0.48f + edge * 0.22f);
        }
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
        int fileLen = 36 + dataLen;
        header.write(new byte[] { 'R', 'I', 'F', 'F' });
        putInt(header, fileLen);
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
