"""Makes the offer reader's chimes (CLAUDE.md section 9b) as 16-bit mono WAV files.

    python design/sounds/make_sounds.py

writes app/src/main/res/raw/offer_profit.wav (two notes going up) and offer_loss.wav (two lower
notes going down), so a rider can tell them apart without looking. Standard library only.
"""

import math
import struct
import wave
from pathlib import Path

RATE = 22050
RAW = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"


def note(freq, start, length, peak):
    """A soft bell-like note: a sine with a little of its octave, a 5 ms rise and an exponential fade."""
    samples = []
    for i in range(int(length * RATE)):
        t = i / RATE
        attack = min(1.0, t / 0.005)
        decay = math.exp(-t * 9.0)
        wave_ = math.sin(2 * math.pi * freq * t) + 0.25 * math.sin(4 * math.pi * freq * t)
        samples.append(peak * attack * decay * wave_ / 1.25)
    return start, samples


def write(name, notes, total):
    mix = [0.0] * int(total * RATE)
    for start, samples in notes:
        offset = int(start * RATE)
        for i, value in enumerate(samples):
            if offset + i < len(mix):
                mix[offset + i] += value
    # A 10 ms fade at the end, so it never stops with a click.
    tail = int(0.01 * RATE)
    for i in range(tail):
        mix[-tail + i] *= 1 - i / tail
    with wave.open(str(RAW / name), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767)) for v in mix))


RAW.mkdir(parents=True, exist_ok=True)
# Profit: E5 then A5, rising.
write("offer_profit.wav", [note(659.25, 0.0, 0.32, 0.6), note(880.0, 0.11, 0.38, 0.6)], 0.5)
# Loss: E4 then B3, falling and lower.
write("offer_loss.wav", [note(329.63, 0.0, 0.32, 0.7), note(246.94, 0.13, 0.4, 0.7)], 0.55)
