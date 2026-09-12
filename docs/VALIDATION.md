# Validation

The checks below match the current minimal loop-and-slice engine. Tests for the
removed v0.1.0 effects and manual sampling workflow are intentionally absent.

## Automated controls test

`tests/test_controls.py` executes `grainloom.lua` in a mocked norns host. It
checks:

- all current defaults and namespaced parameter IDs;
- the 2:8 input/sample formatter;
- K2 freeze and K3 sample on/off;
- the recording-length debounce and Buffer restart command;
- encoder dispatch across all five pages;
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
8. Sweep tape speed through positive, zero, and negative values.
9. Increase slice mix, then verify random positions, quantized pitch steps, and
   probabilistic reverse are audible.
10. Change recording time several times quickly and confirm only the final value
    rebuilds and clears the Buffer.
11. Run for at least 15 minutes and check that the voice and recorder remain
    present, CPU remains stable, and JACK reports no steady-state xruns.

The current engine has been compiled and loaded successfully on the target norns,
with its expected two nodes and all four audio/app services active. Short
steady-state observations showed no new xruns or engine errors. The longer test
remains the recommended check before performance use.
