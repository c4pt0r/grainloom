# grainloom

A minimal continuous feedback loop and slice machine for monome norns.

Grainloom continuously records a mono input into a circular Buffer while playing
that Buffer. On every pass, new input is mixed with a decayed copy of the old
contents. The default 2.5-second loop therefore behaves like a compact tape delay.
Two additional readers pick small random slices from the same live Buffer and
play them at quantized tape-speed ratios, including probabilistic reverse.

There is no capture step: launch Grainloom, play into the input, and the loop
starts evolving immediately.

[中文入门](docs/QUICKSTART.zh-CN.md)

## Quick start

1. Set the norns system monitor level to zero.
2. Launch Grainloom and send it audio. Recording and replay start automatically.
3. Let at least one loop pass, then adjust `feedback` and `input / sample mix`.
4. Raise `tape / slice mix` to hear more random slices.
5. Use K2 to freeze the current Buffer; use K3 to turn sample replay on or off.

## Controls

Turn E1 to select a page. E2 and E3 edit its controls.

| Page | E2 | E3 |
| --- | --- | --- |
| LOOP | recording time (0.1–30 s) | feedback (0–0.98) |
| TAPE | tape speed/direction (-2×–2×) | input:sample mix |
| SLICE | slice size (0.06–0.5 s) | slice density (1–8 Hz) |
| SLICE PLAY | quantized speed ceiling (0.5×–2×) | reverse probability |
| LEVEL | tape:slice mix | sample level (0.25×–4×) |

- K2 **freeze**: freezes/resumes the record head. Playback continues while frozen.
- K3 **on/off**: turns sample replay on or off. The input side of MIX remains audible.

Defaults: 2.5 seconds, 0.72 feedback, normal tape speed, 2:8 input:sample
balance, 50% tape:slice mix, 0.2-second slices at 5 Hz, 1.5× quantized
speed ceiling, 35% reverse probability, 1.5× sample level, and 0.75 output.
Output level is available from PARAMETERS rather than an encoder page.

Changing recording time clears the current loop and allocates a new Buffer after
a 0.4-second encoder debounce. MIX is shown as `input:sample`: `10:0` is input
only, `5:5` is equal balance, and `0:10` is sample only.

## Signal path

```text
input ─> feedback RecordBuf ─> Buffer ─┬─> looping PlayBuf ───────┐
                                      └─> two GrainBuf readers ─┴─> tape/slice mix ─> sample level ─> wet
input ───────────────────────────────────────────────────────────────────────────────────────────────> dry
dry + wet ─> input/sample mix ─> limiter ─> output
```

The record path selects the louder hardware input channel rather than summing
left and right, avoiding mono cancellation from opposite-polarity sources. Input
is DC-filtered, given 2× protected capture makeup, and limited before recording.
Feedback is capped below unity; new input uses `1-feedback`, making every write a
bounded crossfade rather than an accumulating gain stage.

The optional slice layer runs two asynchronous, windowed readers at random Buffer
positions. Every slice chooses a musical tape-speed ratio from `0.5×`, `2/3×`,
`0.75×`, `1×`, `4/3×`, `1.5×`, and `2×`, limited by `quantized speed max`;
`reverse chance` independently reverses slices. Density is capped at 8 Hz per
stream and each reader is capped at eight concurrent grains. Slices mix only into
replay and never feed the record Buffer.

Slices are output-only: they never feed the record Buffer, so increasing slice
mix cannot create an uncontrolled feedback stack. The final limiter protects the
output from overlapping slice peaks.

There is intentionally no generation-loss, dropout, wow/flutter, bit-crush,
glitch, reverb, file loading, or resampling-generation path. Grainloom does not
change the global norns reverb.

For MIX to represent the complete dry/wet balance, set the norns system monitor
level to zero because system monitoring is a separate dry path.

## Install

```sh
cd /home/we/dust/code
git clone https://github.com/c4pt0r/grainloom.git
```

The custom engine is `lib/Engine_Grainloom.sc`. After the first installation,
restart the norns audio services once so SuperCollider discovers the engine, then
select Grainloom. Ordinary Lua-only updates require only an app reload.

## Documentation

- [中文入门](docs/QUICKSTART.zh-CN.md)
- [Current algorithm](docs/ALGORITHMS.md)
- [Validation](docs/VALIDATION.md)
- [Changelog](CHANGELOG.md)

## License

MIT
