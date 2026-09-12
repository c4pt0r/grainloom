# Validation

The checks below match the current minimal loop-and-slice engine. Tests for the
removed v0.1.0 effects and manual sampling workflow are intentionally absent.

## Automated controls test

`tests/test_controls.py` executes `grainloom.lua` in a mocked norns host. It
checks:

- all current defaults and namespaced parameter IDs;
- the last PSET being read exactly once, and before any value reaches the
  engine, so saved values are what it starts from;
- the 2:8 input/sample formatter;
- K2 freeze and K3 sample on/off, including the freeze toggle moving to the
  key release, the long press starting dub only from a frozen loop, and a press
  during dub ending it without also toggling freeze;
- recording-length edits reaching the engine immediately rather than being
  debounced to a final value, not reallocating, and debouncing only the mirror
  catch-up;
- encoder dispatch across all nine pages, starting from the first, and E1
  wrapping rather than clamping at either end;
- the window, slice-age, and bloom defaults matching the behaviour they
  replaced, and the sub-second recording-time formatter;
- a recording-length change that arrives mid-allocation still reaching the
  engine, and the watchdog re-enabling the keys when no answer comes back;
- poll, metro, clock, and cleanup behavior, including `cleanup` running after
  a failed `init` with an empty paramset.

It requires Python and `lupa`:

```sh
python3 tests/test_controls.py
```

## Offline DSP test

`tests/test_dsp.py` compiles the actual SuperCollider engine against a minimal
`CroneEngine` test double. It renders the record/replay path non-realtime and
checks output channel count, sample rate, non-silence, bounded peak, reverse tape
playback, and clean server exit.

```sh
python3 tests/test_dsp.py /path/to/sclang /path/to/scsynth
```

## norns hardware test

After installing or changing `lib/Engine_Grainloom.sc`:

1. Load Grainloom and confirm there are no SuperCollider or Lua errors.
2. Confirm the server contains one `grainloom_loop_voice` and one
   `grainloom_loop_record` node.
3. With system monitor at zero, check `10:0`, `5:5`, and `0:10` input/sample
   balances.
4. Let at least one full loop pass and confirm sample replay contains the earlier
   input rather than only the live source.
5. Confirm feedback 0 replaces each pass and feedback 0.72 creates decaying
   repeats without growing louder each pass.
6. Press K2 and confirm the Buffer stops changing while tape and slices continue.
7. Press K3 and confirm tape/slice replay turns off; when input is included in the
   mix, the input branch remains audible.
8. Freeze with K2, then hold K2 for half a second and confirm the header reads
   `DUB`, the existing loop keeps its level, and new input layers on top of it
   rather than replacing it. Press K2 and confirm recording stops, the header
   returns to `FREEZE`, and the dubbed layer stays in time with the loop.
9. Sweep tape speed through positive, zero, and negative values.
10. Close `window size` and confirm the tape voice becomes a stutter inside the
    loop while the record head keeps refilling the rest of the Buffer; sweep
    `window start` across the wrap point and confirm no dropout or out-of-range
    read.
11. Set recording time to 20 ms and confirm the loop is heard as a pitch that
    tracks tape speed, that feedback acts as its decay, and that dub layers onto
    it without runaway level.
12. Raise `slice age` and confirm slices move into older material while staying
    a constant distance behind the live input; narrow `slice spread` and confirm
    they converge on one moment rather than one fixed Buffer position.
13. Raise `bloom`, stop playing, and confirm the slice layer grows over
    `bloom time` and retreats as soon as input returns.
14. Raise `regen` to maximum at feedback 0.98 with tape speed 2x, play a short
    loud burst, and confirm the loop climbs and then dies away rather than
    accumulating into a rising screech; repeat at -2x. Close `regen tone` and
    confirm the spiral dulls faster. Confirm no output-limiter pumping at rest.
15. With `regen` at maximum, enter dub and confirm the level does not run away,
    then leave dub and confirm the spiral resumes.
16. Increase slice mix, then verify random positions, quantized pitch steps, and
   probabilistic reverse are audible.
17. Sweep recording time across its range while playing and confirm the loop
    never goes silent, is never cleared, and follows the encoder without a
    debounce. Shortening should reframe onto the loop's opening at once;
    lengthening should extend it with a repeat rather than with silence.
18. Jump recording time from 0.05 s straight to 20 s and confirm the tail fills
    with tiled material within a fraction of a second rather than staying
    silent, and that slices near the loop end do not drop out.
19. Freeze, then sweep recording time, and confirm the Buffer contents are still
    not being written.
20. Save a PSET from PARAMETERS, change several controls, reload the script, and
    confirm the saved values come back and the engine is audibly using them.
21. Highlight Grainloom in SELECT and confirm the summary reads correctly, that
    no line is clipped at the right edge, and that E2 scrolls to the end.
22. Run for at least 15 minutes and check that the voice and recorder remain
    present, CPU remains stable, and JACK reports no steady-state xruns.

The current engine has been compiled and loaded successfully on the target norns,
with its expected two nodes and all four audio/app services active. Short
steady-state observations showed no new xruns or engine errors. The longer test
remains the recommended check before performance use.
