# Changelog

## v0.1.0 — 2026-09-10

First experimental release for monome norns / norns shield.

- Capture up to 30 seconds, or load the first 30 seconds of an audio file.
- Mix variable-speed looping with a bounded, stereo granular layer.
- Independent grain scan, position, duration, density, spray and freeze.
- Digital sample-rate/bit-depth degradation and probabilistic grain stutters.
- Tape-inspired saturation, bandwidth loss, noise, wow/flutter and dropouts.
- Stereo FreeVerb2 reverb and internal resampling for cumulative generation loss.
- Nine pages for the three-encoder interface, parameter presets and file browser.
- MIT license, algorithm documentation, Lua control tests and offline DSP tests.

Known limitations: mono sample storage, no audio undo, no automatic sample
persistence, no grid/MIDI sync, and no physical norns validation yet. See README
and `docs/VALIDATION.md` before treating this as a stable performance instrument.
