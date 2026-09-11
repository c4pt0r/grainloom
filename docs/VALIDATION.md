# Validation

## Automated checks

`tests/test_controls.py` executes the actual Lua script in a mocked norns host.
It verifies defaults, namespaced parameters, K2 freeze, K3 sample on/off,
recording-time debounce, encoder dispatch, polling, and cleanup.

```sh
python3 tests/test_controls.py
```

`tests/test_dsp.py` compiles the actual engine with a small CroneEngine test
double and renders the recorder/replay path offline. It requires local `sclang`
and `scsynth` paths.

```sh
python3 tests/test_dsp.py /path/to/sclang /path/to/scsynth
```

## Hardware checks

1. Confirm Grainloom loads without SuperCollider or Lua errors.
2. With system monitor off, verify input remains audible at MIX 10:0.
3. At MIX 0:10, verify the previous 2.5-second pass is audible.
4. Confirm feedback 0 replaces each pass and feedback 0.72 creates decaying repeats.
5. Freeze writes with K2 and verify the captured loop remains unchanged.
6. Turn sample replay off with K3 and verify the input side of MIX remains audible.
7. Change recording time repeatedly and confirm only one final Buffer rebuild.
8. Run for at least 15 minutes and inspect JACK for xruns.

The release remains experimental until the long-running hardware check passes.
