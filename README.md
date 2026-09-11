# grainloom

A small, open-source norns instrument for **sampling, loops, grains, tape loss,
glitch and reverb**. Original norns / shield; no grid or external UGen required.

**v0.1.0 experimental release. Not yet validated on norns hardware.** This is an original
instrument inspired by tape and microsound workflows, not a clone of MOOD,
Morphagene or the Generation Loss pedal.

## Install

In a shell on norns, run:

```sh
git clone https://github.com/c4pt0r/grainloom.git /home/we/dust/code/grainloom
```

Alternatively, download the ZIP from [Releases](https://github.com/c4pt0r/grainloom/releases)
and copy its `grainloom` directory using SFTP. The engine must be at
`/home/we/dust/code/grainloom/lib/Engine_Grainloom.sc`. Restart norns to compile
the new engine, then select `grainloom/grainloom` from SELECT.

Keep only one copy of the engine installed. To update a Git installation, run
`git -C /home/we/dust/code/grainloom pull --ff-only`, then restart norns.

Source: [c4pt0r/grainloom](https://github.com/c4pt0r/grainloom) ·
[Changelog](CHANGELOG.md) · [中文入门](docs/QUICKSTART.zh-CN.md)

## Play

1. Connect an input and set input gain in the norns mixer, or load an audio file.
2. Press **K2** to start a new recording. Press again to end it early; otherwise
   recording ends at the selected maximum length (default 8 seconds, maximum 30).
3. The recording starts looping automatically. Turn **E1** to select a page;
   **E2/E3** change its two parameters.
4. **K3** freezes/unfreezes the grain scan. It does not stop the ordinary tape loop.
5. Hold **K1 + K2** to load an audio file; **K1 + K3** pauses/resumes the source.
   Pausing lets the reverb tail decay; playback heads keep moving while muted.

The built-in file browser also exists under PARAMETERS → load sample. norns
reserves long K1 for the system menu, so use a brief modifier chord, or the menu
action if preferred.

| Page | E2 | E3 |
| --- | --- | --- |
| GRAIN | grain duration | density |
| SCAN | position offset | scan speed, including reverse |
| TAPE | playback speed / direction | loop ↔ grain blend |
| SCATTER | random position spread | short stutter probability |
| PATINA | sample-rate / bit-depth degradation | low-pass filter |
| SPACE | reverb mix | room size |
| TAPE LOSS | generation loss | dropout probability |
| INSTABILITY | slow wow | faster flutter |
| LEVEL | output level | maximum recording length |

All sound controls also appear in PARAMETERS and can use norns parameter
preset saving. Presets store settings, **not audio**.

## Generation loss

TAPE LOSS is separate from the digital bitcrusher. It combines soft saturation,
reduced bandwidth, low-level noise and intermittent attenuation. INSTABILITY
modulates playback pitch with slow and faster smooth noise. It is an approximate
tape model with explicitly documented formulas, not a physical tape simulation.

Use PARAMETERS → **print next generation** to record the current processed
output into a replacement sample. It captures one current sample duration,
including the grain mix, tape loss, glitch and reverb, then starts playing the
new sample. The screen's `g` count increments after completion. Repeating this
operation genuinely accumulates degradation. Master output level is excluded
from the internal print path. Print from a paused state resumes the source.

Printing is destructive to the in-memory sample; there is no undo in v0.1.
Load a source file again to return to its original. Files on disk are never
overwritten. Use norns TAPE recording to save a performance externally.

Starting points:

- **Worn loop:** blend 0.2, loss 0.4, wow 0.3, flutter 0.15, reverb 0.2.
- **Frozen dust:** blend 1, grain size 0.15 s, density 20 Hz, spray 0.04;
  press K3, then turn position to explore the sample.
- **Broken cassette:** loss 0.6, dropout 0.3, glitch 0.25, crush 0.15.
- **Generational fog:** blend 0.8, reverb 0.5, loss 0.3; print a few generations.

## Scope and limitations

- Mono sample storage, stereo generated grains and reverb. Live recording mixes
  L/R equally. File loading uses the **first channel only**, up to the first
  30 seconds, and preserves file sample rate for correct pitch.
- Internal printing folds stereo back to mono; stereo processing is generated
  again on playback. Phase cancellation and stereo-width changes can accumulate.
- New input recording replaces the current sample; no simultaneous external
  overdub, splice editor, waveform display, MIDI sync or grid mode yet.
- A 5 ms edge fade on the tape loop softens clicks but can produce a small dip.
  Recording lengths are timed in the language layer, not sample-accurate.
- Grain scan and grain pitch are independent; the ordinary loop follows the
  rate control. Freeze stops scan advancement; spray and pitch can still change.
- Maximum 12 nominal overlapping grains with a 32-slot GrainBuf allocation.
  CPU limits still need to be measured on both original norns and shield.
- Local tests do not prove hardware audio quality, latency or performance.

See [algorithm notes](docs/ALGORITHMS.md) and [validation](docs/VALIDATION.md).

## Development

Lua is the norns interface; `lib/Engine_Grainloom.sc` owns the DSP and buffers.
Only standard SuperCollider UGens are used. The UI does not alter system mixer,
monitoring, compressor or reverb settings. Existing norns monitoring/effects can
therefore be audible in addition to this instrument.

Run the desktop Lua control tests in an isolated Python environment:

```sh
python3 -m venv /tmp/grainloom-tests
/tmp/grainloom-tests/bin/pip install lupa
/tmp/grainloom-tests/bin/python tests/test_controls.py
```

MIT licensed; see [LICENSE](LICENSE).
