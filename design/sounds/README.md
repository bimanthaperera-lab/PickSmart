# Offer chimes

The offer reader can play a short chime when a new offer shows PROFIT or LOSS (CLAUDE.md section 9b; off by default, "Sound on profit or loss" under Account → While riding). `make_sounds.py` writes both files to `app/src/main/res/raw/`. Change the notes there and run it again rather than editing the WAV files:

```bash
python design/sounds/make_sounds.py
```

- `offer_profit.wav`: two notes going up (E5, A5), about 0.5 s.
- `offer_loss.wav`: two lower notes going down (E4, B3), about 0.55 s.

They are made from sine waves by the script, so there is no licence to track.
