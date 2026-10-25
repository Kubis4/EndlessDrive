"""Download and master compact public-domain weather recordings."""

from io import BytesIO
from pathlib import Path
from urllib.request import Request, urlopen

import numpy as np
import soundfile as sf


RAW = Path("app/src/main/res/raw")
SOURCES = {
    "sfx_rain.ogg": (
        "https://upload.wikimedia.org/wikipedia/commons/3/3d/Rain.ogg",
        None,
    ),
    "sfx_thunder.ogg": (
        "https://upload.wikimedia.org/wikipedia/commons/4/42/Rain_and_thunder.ogg",
        (2.70, 10.80),
    ),
    "sfx_thunder_roll.ogg": (
        "https://upload.wikimedia.org/wikipedia/commons/c/c8/Tonitrus.ogg",
        (12.25, 22.25),
    ),
}


def rms(audio: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(audio), dtype=np.float64)))


def fade_edges(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    count = min(int(sample_rate * 0.025), len(audio) // 10)
    ramp = np.sin(np.linspace(0.0, np.pi / 2.0, count, dtype=np.float32)) ** 2
    result = audio.copy()
    result[:count] *= ramp[:, None]
    result[-count:] *= ramp[::-1, None]
    return result


def smooth_loop(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    count = min(int(sample_rate * 0.15), len(audio) // 10)
    phase = np.linspace(0.0, np.pi / 2.0, count, dtype=np.float32)
    fade_in = np.sin(phase) ** 2
    fade_out = np.cos(phase) ** 2
    seam = audio[-count:] * fade_out[:, None] + audio[:count] * fade_in[:, None]
    result = audio.copy()
    result[:count] = seam
    result[-count:] = seam
    return result


def prepare(filename: str, url: str, bounds: tuple[float, float] | None) -> None:
    request = Request(url, headers={"User-Agent": "EndlessDrive/1.0 audio build tool"})
    with urlopen(request) as response:
        source = BytesIO(response.read())
    audio, sample_rate = sf.read(source, dtype="float32", always_2d=True)

    if bounds is not None:
        start, end = (int(seconds * sample_rate) for seconds in bounds)
        audio = audio[start:min(len(audio), end)].copy()
    audio -= np.mean(audio, axis=0, keepdims=True, dtype=np.float64)

    if filename == "sfx_rain.ogg":
        audio = np.tanh(audio * 2.0) / np.tanh(2.0)
        audio *= 0.92 / max(float(np.max(np.abs(audio))), 1e-9)
        audio = smooth_loop(audio, sample_rate)
    elif filename == "sfx_thunder.ogg":
        audio = np.tanh(audio * 2.0) / np.tanh(2.0)
        audio *= 0.92 / max(float(np.max(np.abs(audio))), 1e-9)
        audio = fade_edges(audio, sample_rate)
    else:
        target_rms = 10.0 ** (-16.0 / 20.0)
        audio *= target_rms / max(rms(audio), 1e-9)
        peak = float(np.max(np.abs(audio)))
        if peak > 0.94:
            audio *= 0.94 / peak
        audio = fade_edges(audio, sample_rate)

    destination = RAW / filename
    sf.write(
        destination,
        audio,
        sample_rate,
        format="OGG",
        subtype="VORBIS",
        compression_level=0.55,
    )
    decoded, decoded_rate = sf.read(destination, dtype="float32", always_2d=True)
    if decoded_rate != sample_rate or len(decoded) != len(audio) or not np.isfinite(decoded).all():
        raise RuntimeError(f"Verification failed for {filename}")
    level = 20.0 * np.log10(max(rms(decoded), 1e-9))
    print(f"{filename}: {len(decoded) / sample_rate:.2f}s, {destination.stat().st_size} bytes, {level:.1f} dB RMS")


for output, source in SOURCES.items():
    prepare(output, *source)
