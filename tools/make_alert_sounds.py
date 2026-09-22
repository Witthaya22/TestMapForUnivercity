#!/usr/bin/env python3
"""Generate the two hazard alert tones shipped in `app/src/main/res/raw/`.

The tones are synthesised here rather than downloaded so that the repository owns every
sample in them: no licence to track, nothing fetched at build time, and a tone that turns
out to be wrong outdoors can be reshaped by editing numbers instead of hunting for a
replacement clip.

They are deliberately short. A hazard tone plays immediately before the spoken warning, so
anything longer than about half a second delays the sentence that carries the information.

    python3 tools/make_alert_sounds.py

Writes:
    app/src/main/res/raw/hazard_chime.wav   soft two-note rise  - caution / warning
    app/src/main/res/raw/hazard_alert.wav   three urgent pulses - danger
"""

from __future__ import annotations

import math
import pathlib
import struct
import wave

# 22.05 kHz is plenty for a tone whose highest partial is under 5 kHz, and halves the size
# of a file that ships in every APK.
SAMPLE_RATE = 22050
AMPLITUDE = 0.62  # Leaves headroom so the phone speaker does not clip on the peaks.

OUT_DIR = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def envelope(position: float, attack: float, release: float) -> float:
    """Fade a note in and out so it does not click. `position` runs 0.0 to 1.0."""
    if position < attack:
        return position / attack
    if position > 1.0 - release:
        return (1.0 - position) / release
    return 1.0


def note(frequency: float, seconds: float, harmonic: float, attack: float, release: float):
    """One tone. `harmonic` adds a third partial, which is what makes it carry outdoors."""
    total = int(SAMPLE_RATE * seconds)
    for i in range(total):
        t = i / SAMPLE_RATE
        position = i / total
        value = math.sin(2 * math.pi * frequency * t)
        value += harmonic * math.sin(2 * math.pi * frequency * 3 * t)
        yield value / (1.0 + harmonic) * envelope(position, attack, release)


def silence(seconds: float):
    for _ in range(int(SAMPLE_RATE * seconds)):
        yield 0.0


def write(name: str, samples) -> None:
    frames = bytearray()
    for value in samples:
        clamped = max(-1.0, min(1.0, value * AMPLITUDE))
        frames += struct.pack("<h", int(clamped * 32767))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(bytes(frames))
    print(f"{path.relative_to(OUT_DIR.parents[4])}  {path.stat().st_size / 1024:.1f} KB")


def chime():
    """A two-note rise, A5 then E6.

    Used for `caution` and `warning`: it has to be noticed without being alarming, because
    most marked hazards are things to walk around rather than things to stop for.
    """
    yield from note(880.0, 0.13, harmonic=0.12, attack=0.04, release=0.35)
    yield from note(1318.5, 0.22, harmonic=0.12, attack=0.03, release=0.55)


def alert():
    """Three short pulses just under 1.6 kHz.

    Used for `danger`. Three is the smallest count that reads as urgent rather than as a
    notification, and this band sits above most traffic noise, which is exactly where the
    warning that needs to arrive is.
    """
    for index in range(3):
        yield from note(1568.0, 0.085, harmonic=0.3, attack=0.06, release=0.3)
        if index < 2:
            yield from silence(0.055)


if __name__ == "__main__":
    write("hazard_chime.wav", chime())
    write("hazard_alert.wav", alert())
