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

### Added

- Dub: holding K2 for half a second while frozen runs the record head again
  with the existing pass preserved and new input layered on top, and any press
  of K2 ends it and refreezes in place. Adds a `dub level` parameter, available
  from PARAMETERS.

- Recording time reaches down to 20 ms, turning short loops into audio-rate
  waveforms where repetition is pitch, feedback is decay, and dub is additive
  synthesis. The control is now exponential.
- Tape reads a window of the Buffer (`window start`, `window size`) instead of
  always the whole of it, via `Phasor`/`BufRd` in place of `PlayBuf`.
- Slice positions are anchored to the moving record head, with `slice age` as a
  distance into the past and `slice spread` as the span drawn from.
- `bloom` grows the slice layer into silences and withdraws it when anything is
  played, over `bloom time`.
- `regen` folds the tape reader back into the record input, so varispeed
  accumulates into a spiral, bounded by a lowpass in the send path
  (`regen tone`), by keeping the capture makeup off the fold-back, and by
  suppressing the send entirely during dub.
- Nine encoder pages, adding WINDOW, REGEN, SLICE POS, and BLOOM. E1 wraps.
- Every control added after the first release defaults to the behaviour it
  replaced, so defaults sound unchanged.

- Recording time is a wrap point rather than a Buffer size. It applies on the
  next sample, clears nothing, never goes silent, and can be swept while
  playing; the Buffer is allocated once at load at twice the longest loop, and
  the recorder mirrors every sample one loop ahead so reads past the loop end
  wrap correctly.

- Recall the last PSET when the script loads, before any value is pushed to
  the engine. Reading a PSET already fires each control's action, so no bang is
  needed and unrelated system params stay untouched.
- Replace the one-line file header with a short summary and key reference, which
  is what the SELECT screen shows; the SPDX line moved below it so it no longer
  appears there.

### Fixes

- Select the record input channel with hysteresis so near-equal left and right
  levels cannot chatter and park the crossfade where both inputs sum.
- Free a replaced Buffer after one second rather than 0.1, so a slice still
  reading it (up to 0.625 s) cannot outlive the Buffer.
- Stop reading a parameter from `cleanup`, which norns also runs after a
  failed `init`, where the paramset is empty. The lookup raised and the loop
  was never told to stop; the engine ignores the length argument anyway.
- Add a five-second watchdog so a wedged allocation can no longer leave the
  interface in `WAIT` with K2 and K3 dead. Recording-time changes no longer
  allocate at all, so the queueing this originally paired with is gone too.

### Removed from the current engine

- Manual record/stop capture and sample file loading.
- AUTO, DELAY, LOOPER, and other memory modes.
- Generation loss, dropout, wow/flutter, bit-crush, glitch, scan, and internal
  reverb.
- Resampling generations and `print next generation`. The bounded `regen` send
  added later is a different mechanism: one audio-rate fold-back with a fixed
  stability budget, not a rendered chain of generations.

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
