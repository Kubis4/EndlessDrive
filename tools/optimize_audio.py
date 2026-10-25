"""Conservatively master Endless Drive WAV effects and encode them as Ogg Vorbis.

The script keeps source sample rates and mono layout, removes DC, applies a very
small category-aware EQ, smooths starts/ends (or loop seams), and limits gain
changes to 3 dB so the in-game mix does not need to be rebuilt from scratch.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import soundfile as sf


LOOPS = {
    "sfx_brake",
    "sfx_engine_idle",
    "sfx_engine_rev",
    "sfx_leak_loop",
    "sfx_skid",
    "sfx_surface_gravel",
    "sfx_surface_ice",
    "sfx_surface_mud",
    "sfx_surface_water",
    "sfx_wind",
}

TARGET_RMS_DB = {
    "sfx_coolant_hiss": -22.0,
    "sfx_leak": -22.0,
    "sfx_leak_loop": -20.0,
    "sfx_radio": -21.0,
    "sfx_find": -18.0,
}


def rms_db(audio: np.ndarray) -> float:
    rms = float(np.sqrt(np.mean(np.square(audio), dtype=np.float64)))
    return 20.0 * np.log10(max(rms, 1e-9))


def gentle_eq(audio: np.ndarray, sample_rate: int, name: str) -> np.ndarray:
    spectrum = np.fft.rfft(audio)
    freqs = np.fft.rfftfreq(len(audio), 1.0 / sample_rate)

    def bell(center: float, width_octaves: float, db: float) -> np.ndarray:
        safe = np.maximum(freqs, 1.0)
        distance = np.log2(safe / center) / width_octaves
        return db * np.exp(-0.5 * distance * distance)

    gain_db = np.zeros_like(freqs)
    if "engine" in name or name in {"sfx_stall", "sfx_fail_start"}:
        gain_db += bell(170.0, 1.0, 1.1)
        gain_db += bell(1500.0, 1.2, 0.6)
    elif "surface" in name or name.startswith("sfx_mud") or "splash" in name:
        gain_db += bell(2400.0, 1.2, 0.8)
    elif name in {"sfx_blowout", "sfx_debris", "sfx_misfire"}:
        gain_db += bell(190.0, 1.1, 0.8)
        gain_db += bell(2800.0, 1.0, 0.7)

    # Roll off only the extreme top octave; 22.05 kHz sources otherwise expose
    # quantisation fizz after compression.
    nyquist = sample_rate / 2.0
    gain_db -= 1.2 * np.clip((freqs - nyquist * 0.78) / (nyquist * 0.22), 0.0, 1.0)
    spectrum *= np.power(10.0, gain_db / 20.0)
    return np.fft.irfft(spectrum, n=len(audio)).astype(np.float32)


def smooth_loop_seam(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    count = min(int(sample_rate * 0.045), max(1, len(audio) // 12))
    phase = np.linspace(0.0, np.pi / 2.0, count, dtype=np.float32)
    fade_in = np.sin(phase) ** 2
    fade_out = np.cos(phase) ** 2
    seam = audio[-count:] * fade_out + audio[:count] * fade_in
    result = audio.copy()
    result[:count] = seam
    result[-count:] = seam
    return result


def smooth_one_shot(audio: np.ndarray, sample_rate: int) -> np.ndarray:
    count = min(int(sample_rate * 0.004), max(1, len(audio) // 20))
    ramp = np.linspace(0.0, 1.0, count, dtype=np.float32)
    result = audio.copy()
    result[:count] *= ramp
    result[-count:] *= ramp[::-1]
    return result


def target_for(name: str) -> float:
    if name in TARGET_RMS_DB:
        return TARGET_RMS_DB[name]
    if name in LOOPS:
        return -15.5
    if name.startswith("sfx_mud"):
        return -19.0
    return -17.5


def master(audio: np.ndarray, sample_rate: int, name: str) -> np.ndarray:
    if audio.ndim == 2:
        audio = np.mean(audio, axis=1)
    audio = np.asarray(audio, dtype=np.float32)
    audio -= float(np.mean(audio, dtype=np.float64))
    audio = gentle_eq(audio, sample_rate, name)

    gain_db = float(np.clip(target_for(name) - rms_db(audio), -3.0, 3.0))
    audio *= 10.0 ** (gain_db / 20.0)
    peak = float(np.max(np.abs(audio)))
    if peak > 0.94:
        audio *= 0.94 / peak

    audio = smooth_loop_seam(audio, sample_rate) if name in LOOPS else smooth_one_shot(audio, sample_rate)
    return np.clip(audio, -0.98, 0.98)


def convert(source: Path) -> tuple[Path, int, int, float]:
    audio, sample_rate = sf.read(source, dtype="float32", always_2d=False)
    mastered = master(audio, sample_rate, source.stem)
    destination = source.with_suffix(".ogg")
    sf.write(
        destination,
        mastered,
        sample_rate,
        format="OGG",
        subtype="VORBIS",
        compression_level=0.55,
    )
    check, check_rate = sf.read(destination, dtype="float32", always_2d=False)
    if check_rate != sample_rate or len(check) != len(mastered) or not np.isfinite(check).all():
        raise RuntimeError(f"Encoded verification failed: {destination}")
    return destination, source.stat().st_size, destination.stat().st_size, rms_db(check)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    for source in sorted(args.directory.glob("*.wav")):
        destination, old_size, new_size, level = convert(source)
        saving = 100.0 * (1.0 - new_size / old_size)
        print(
            f"{source.name:26} -> {destination.name:26} "
            f"{new_size:7d} B  saved {saving:5.1f}%  RMS {level:5.1f} dB"
        )


if __name__ == "__main__":
    main()
