# Validation

## Desktop checks completed

- Executed the actual Lua script using a mocked norns host in Lua (via Python
  `lupa`): defaults, recording start/stop, duplicate actions while busy, stale
  status replies, internal printing, freeze, pause, file cancellation, invalid
  file metadata, engine failure recovery, encoder dispatch and poll cleanup.
- Compiled the actual engine class and both SynthDefs under SuperCollider
  3.14.1 on macOS arm64. CroneEngine was replaced by a minimal desktop test
  double; this is not a complete simulation of norns.
- Rendered 5.1 seconds of 48 kHz stereo audio offline using `scsynth`, including
  high tape loss, wow/flutter, dropouts, maximum grain duration/density, reverse
  playback, full crush/glitch, freeze and pause/reverb tail.
- Recorded the internal print bus into a mono buffer, wrote it to WAV and used
  that buffer as the next playback source. Both print and final render contained
  nonzero, bounded audio; no SuperCollider ERROR/FAILURE was reported.

These are compilation/control/audio smoke tests, not a subjective listening
review or a CPU benchmark. NRT rendering does not verify physical input,
real-time deadlines, or the asynchronous engine command lifecycle on norns.

Re-run:

```sh
python3 tests/test_dsp.py /path/to/sclang /path/to/scsynth
```

See README for the Lua test setup. The test double has a `.sc.in` extension so
it will not become a conflicting installed SuperCollider class.

## Required before a stable hardware release

1. Install a single copy and restart norns. Confirm Grainloom appears without
   duplicate-class, missing-UGen or engine-load errors in maiden.
2. Record 8 seconds from L/R input, then record with an early stop. Verify the
   visible duration, playback pitch and absence of stale audio after the end.
3. Load mono/stereo WAV at 44.1 and 48 kHz. Confirm expected pitch; first-channel
   selection for stereo and truncation at 30 seconds should match the README.
4. Rapidly press record/load around buffer operations. Change scripts during a
   recording and after repeated prints. Check memory/buffer release and errors.
5. Compare loss/wow/flutter/dropout at zero and high values, using sustained
   notes and transients. Listen for clicks, unwanted pumping and excessive hiss.
6. Print several generations. Check cumulative damage, gain, mono folding,
   freeze behavior and the absence of unintended self-feedback.
7. Check E1/E2/E3, short K1 chords, long K1 menu entry and file-browser return on
   the actual 128×64 screen. Save and recall parameter presets.
8. Measure average/peak CPU on original norns and shield with 400 ms grains,
   density 40 Hz, full reverb and simultaneous internal recording. Listen for
   xruns over at least several minutes. Set a release limit from those results.

Until these checks pass, distribute as an experimental prototype.
