# Changelog

## Unreleased — current main

The original experimental instrument has been rebuilt as a minimal,
always-running feedback loop and slice machine.

### Current behavior

- Start continuous record and replay automatically with a 2.5-second default
  Buffer; no capture gesture or input threshold is required.
- Preserve part of the previous pass using bounded feedback: new input uses
  `1-feedback` and old Buffer content uses `feedback`.
- Provide a -2× to 2× looping tape reader and a complete 10:0 to 0:10
  input/sample mix.
- Add two asynchronous random slice readers with adjustable duration and density,
  random Buffer position and stereo pan, and independent reverse probability.
- Quantize random slice speed to `0.5×`, `2/3×`, `0.75×`, `1×`, `4/3×`, `1.5×`,
  or `2×`, subject to the speed-max control.
- Keep slices outside the record feedback path and limit capture and final output
  to prevent recursive gain and overlapping-slice clipping.
- Set current defaults to 0.72 feedback, 2:8 input/sample, 50% tape/slice,
  1.5× sample level, and 0.75 output.
- Define K2 as record-head `freeze` and K3 as sample replay `on/off`.
- Apply recording-time changes after a short debounce and safely replace the old
  Buffer.
- Namespace every app parameter so Grainloom does not collide with or modify
  global norns controls such as reverb.

### Fixes

- Select the record input channel with hysteresis so near-equal left and right
  levels cannot chatter and park the crossfade where both inputs sum.
- Free a replaced Buffer after one second rather than 0.1, so a slice still
  reading it (up to 0.625 s) cannot outlive the Buffer.
- Queue a recording-time change that arrives during an allocation instead of
  dropping it, and add a five-second watchdog so a wedged allocation can no
  longer leave the interface in `WAIT` with K2 and K3 dead.

### Removed from the current engine

- Manual record/stop capture and sample file loading.
- AUTO, DELAY, LOOPER, and other memory modes.
- Generation loss, dropout, wow/flutter, bit-crush, glitch, scan, and internal
  reverb.
- Resampling generations and `print next generation`.

## v0.1.0 — 2026-09-10

First experimental release for monome norns / norns shield.

- Capture up to 30 seconds, or load the first 30 seconds of an audio file.
- Mix variable-speed looping with a bounded, stereo granular layer.
- Independent grain scan, position, duration, density, spray, and freeze.
- Digital sample-rate/bit-depth degradation and probabilistic grain stutters.
- Tape-inspired saturation, bandwidth loss, noise, wow/flutter, and dropouts.
- Stereo FreeVerb2 reverb and internal resampling for cumulative generation loss.
- Nine pages for the three-encoder interface, parameter presets, and file browser.
- MIT license, algorithm documentation, Lua control tests, and offline DSP tests.
