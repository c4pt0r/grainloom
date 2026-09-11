# Changelog

## Unreleased

- Quantize random slice playback to musical tape-speed ratios while retaining the
  existing maximum-speed and independent reverse controls.
- Add a bounded dual-stream slice replay layer: random Buffer positions, varied
  speed, probabilistic reverse, stereo pan, Hann windows, and no feedback routing.
- Fix clipped sample playback by reducing capture makeup to 2×, changing the
  feedback write to a bounded `(1-feedback) + feedback` crossfade, and reducing
  sample level to 1.5× by default with a 4× maximum.
- Refactor Grainloom into a minimal continuous feedback loop machine. Disconnect
  all grain, slice, generation-loss, modulation, degradation, reverb, file-load,
  and print-generation paths; retain only loop time, feedback, tape speed,
  input/sample mix, levels, write freeze, and replay mute.
- Remove LOOPER mode and rename AUTO to TAPE. Memory mode now offers only the
  zero-feedback TAPE memory and feedback DELAY.
- Apply recording-time changes while memory is running, with a short debounce to
  avoid reallocating the Buffer on every encoder tick.
- Reassign K3 as a momentary DUB control while memory is active: hold to overdub
  input, release to preserve the current loop without decay.
- Add a replay-buffer slice layer with independent size, density, scatter,
  speed/direction, and output mix (50% by default); slices do not feed back into
  the Buffer.
- Set the default input:sample balance to 2:8 and raise sample level to 8× with
  a 10× maximum.
- Restore the stable tape-delay workflow as the default: 2.5-second memory,
  0.72 feedback, 65% wet mix, 3× sample trim, and 0.21 generation loss.
- Make AUTO a MOOD-like always-listening rolling memory with no input gate;
  K2 optionally freezes/resumes writing.
- Add an equal-power live-input/sample mix control.
- Add adjustable sample makeup gain (default 1.6×) before the dry/sample mix.
- Add continuous tape mode: a loop-sized rolling input buffer plays the previous
  pass while recording the current pass, and retains the last loop when stopped.
- Fix continuous recording stopping after its first buffer pass on norns by
  using a dedicated recorder SynthDef with DSP-level looping.
- Turn continuous tape into a true feedback delay: each pass combines new input
  with a controllable, decayed copy of the previous pass.
- Raise delay feedback to 0.85 by default; captured looper memory sustains by
  pausing its write head rather than relying on unity feedback.
- Add a MOOD-inspired always-listening workflow: LOOPER silently remembers the
  recent window for instant K2 capture, while DELAY remains audible and feeds back.
- Namespace every script parameter to avoid collisions with norns system controls
  (notably `reverb`), which previously aborted initialization before K2 was active.
- Use norns-validated `RecordBuf` for always-listening memory; K2 now pauses its
  native run control instead of relying on a custom read/write-head graph.
- Make DELAY the plug-and-play default with a 2.5-second memory, 0.78 feedback
  and 60% wet balance; K2 is optional and only freezes the evolving memory.
- Raise the plug-and-play balance to 70% sample and the default sample trim to
  2.5× so the processed memory is not masked by the live input.
- Tame harsh generation-loss noise: band-limit and reduce the synthetic hiss,
  insert it before tape filtering, and smooth loss/dropout parameter changes.
- Bypass the granular layer by default (`grain blend = 0`) while isolating the
  source of harsh artifacts; the tape loop remains the only default sample path.
- Remove dry input from the startup preset (`mix = 1`) so preamp noise is not
  exposed while the first automatic memory window is being filled.
- Remove capture-side saturation and normalize record gain against feedback,
  preventing the first recorded transient from distorting before it decays.
- Add a clean AUTO mode as the default: audible rolling memory with zero feedback,
  plus zero default loss/wow/flutter, preventing delayed noise accumulation and
  read/write-head drift; LOOPER and feedback DELAY remain selectable.

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
