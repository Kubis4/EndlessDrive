"""Cut the car's sounds in assets/audio from CC0 BigSoundBank recordings.

Usage: prepare_car_audio.py <folder with the downloads> <assets/audio folder>
Downloads (https://bigsoundbank.com/UPLOAD/flac/<id>.flac, 0500 only as ogg):
0967, 0872, 0500, 2368, 2369, 2370, 2371. Needs numpy, scipy and soundfile.
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.ndimage import uniform_filter1d
from scipy.signal import butter, sosfiltfilt

RATE = 44100


def load(folder: Path, name: str) -> np.ndarray:
    path = folder / (name + (".ogg" if name == "0500" else ".flac"))
    audio, rate = sf.read(path, dtype="float64", always_2d=True)
    mono = audio.mean(axis=1)
    if rate == RATE:
        return mono
    return np.interp(np.arange(0.0, len(mono) * RATE / rate) * rate / RATE, np.arange(len(mono)), mono)


def part(audio: np.ndarray, start: float, end: float) -> np.ndarray:
    return audio[int(start * RATE):int(end * RATE)].copy()


def fade(audio: np.ndarray, into: float = 0.01, out: float = 0.02) -> np.ndarray:
    result = audio.copy()
    a, b = int(into * RATE), int(out * RATE)
    if a: result[:a] *= np.sin(np.linspace(0.0, np.pi / 2.0, a)) ** 2
    if b: result[-b:] *= np.cos(np.linspace(0.0, np.pi / 2.0, b)) ** 2
    return result


def loop(audio: np.ndarray, seam: float = 0.4) -> np.ndarray:
    """Seamless: the end overlaps the start, moved to where their waves agree best."""
    count = int(seam * RATE)
    head = audio[:count]
    search = int(0.12 * RATE)
    best, best_score = 0, -np.inf
    for shift in range(0, search, 8):
        tail = audio[len(audio) - count - shift:len(audio) - shift]
        score = float(np.dot(head, tail))
        if score > best_score:
            best, best_score = shift, score
    body = audio[:len(audio) - best]
    phase = np.linspace(0.0, np.pi / 2.0, count)
    result = body[:-count].copy()
    # Waves that agree add up, noise only in power: the fade's shape goes by how much they do.
    tail = body[-count:]
    agree = np.clip(np.dot(head, tail) / max(np.linalg.norm(head) * np.linalg.norm(tail), 1e-9), 0.0, 1.0)
    result[:count] = head * np.sin(phase) ** (1.0 + agree) + tail * np.cos(phase) ** (1.0 + agree)
    return result


def even_loudness(audio: np.ndarray, window: float = 0.35) -> np.ndarray:
    """Takes out slow swells: a loop must not pulse with what the recording did."""
    power = uniform_filter1d(audio * audio, int(window * RATE))
    return audio * np.sqrt(np.mean(power) / np.maximum(power, 1e-9))


def write(out: Path, name: str, audio: np.ndarray, like_old: bool = True, gain: float = 1.0) -> None:
    target = out / (name + ".ogg")
    level = 0.1
    if like_old and target.exists():
        old, _ = sf.read(target, dtype="float64", always_2d=True)
        level = float(np.sqrt(np.mean(old.mean(axis=1) ** 2)))
    now = float(np.sqrt(np.mean(audio ** 2)))
    audio = audio * level / max(now, 1e-9) * gain
    # Never over the top.
    audio = audio * min(1.0, 0.95 / max(float(np.abs(audio).max()), 1e-9))
    sf.write(target, audio, RATE, format="OGG", subtype="VORBIS")
    print(f"{name:14s} {len(audio) / RATE:5.2f} s  rms {np.sqrt(np.mean(audio ** 2)):.3f}  peak {np.abs(audio).max():.3f}  was rms {level:.3f}  {target.stat().st_size // 1024} KB")


def main() -> None:
    raw, out = Path(sys.argv[1]), Path(sys.argv[2])
    kia = load(raw, "0967")
    peugeot = load(raw, "0872")
    squeal = load(raw, "0500")
    screech = [load(raw, name) for name in ("2368", "2369", "2370", "2371")]

    # The diesel: starter and catching, its long steady idle, the stop.
    write(out, "engine_start", fade(part(kia, 0.0, 3.2), 0.005, 0.9), gain=1.1)
    write(out, "engine_idle", loop(part(kia, 12.0, 21.0), 0.5))
    write(out, "engine_stop", fade(part(kia, 47.15, 49.6), 0.12, 0.3), gain=1.1)
    # The higher loop: the same long steady stretch an octave up (read at twice the speed). The
    # van's recording has the driver's blips in it, heard in a loop as the gearbox hunting.
    write(out, "engine_rev", loop(part(kia, 21.0, 41.0)[::2], 0.5))
    # A starter that does not catch.
    write(out, "stall", fade(part(peugeot, 0.15, 2.9), 0.01, 0.25))

    # Sliding: three squeals of all four tyres one after the other, not one short loop.
    pieces = [part(screech[0], 0.2, 1.6), part(screech[1], 0.15, 1.65), part(screech[2], 0.32, 1.65)]
    joined = pieces[0]
    overlap = int(0.15 * RATE)
    for piece in pieces[1:]:
        phase = np.linspace(0.0, np.pi / 2.0, overlap)
        joined = np.concatenate([joined[:-overlap], joined[-overlap:] * np.cos(phase) + piece[:overlap] * np.sin(phase), piece[overlap:]])
    write(out, "skid", loop(even_loudness(joined), 0.2))
    # Hard braking: the same tyres with the squeal taken off, a low scrub on the road.
    scrub = sosfiltfilt(butter(4, 650.0, "low", fs=RATE, output="sos"), joined)
    write(out, "brake", loop(even_loudness(scrub), 0.3))
    # Short chirps when a tyre lets go: the three loudest slips of the parking squeals.
    power = uniform_filter1d(squeal ** 2, int(0.05 * RATE))
    loud = power > 0.1 * power.max()
    edges = np.flatnonzero(np.diff(loud.astype(int)))
    bursts = [(edges[i], edges[i + 1]) for i in range(0, len(edges) - 1, 2) if edges[i + 1] - edges[i] > 0.25 * RATE]
    bursts.sort(key=lambda b: -float(np.sum(power[b[0]:b[1]])))
    for n, (start, end) in enumerate(bursts[:3]):
        write(out, f"chirp_{n + 1}", fade(squeal[max(0, start - 800):end + 3000], 0.015, 0.1), like_old=False, gain=1.4)


if __name__ == "__main__":
    main()
