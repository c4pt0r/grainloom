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
6. While frozen, hold K2 to dub new layers onto the loop; press K2 again to stop.

## Controls

Turn E1 to select a page. E2 and E3 edit its controls.

| Page | E2 | E3 |
| --- | --- | --- |
| LOOP | recording time (20 ms–30 s) | feedback (0–0.98) |
| WINDOW | window start | window size (1–100%) |
| TAPE | tape speed/direction (-2×–2×) | input:sample mix |
| REGEN | regen | regen tone (200 Hz–8 kHz) |
| SLICE | slice size (0.06–0.5 s) | slice density (1–8 Hz) |
| SLICE POS | slice age | slice spread |
| SLICE PLAY | quantized speed ceiling (0.5×–2×) | reverse probability |
| LEVEL | tape:slice mix | sample level (0.25×–4×) |
| BLOOM | bloom | bloom time (0.5–10 s) |

- K2 **freeze**: a short press freezes/resumes the record head. Playback
  continues while frozen.
- K2 **hold** (0.5 s, while frozen): starts dub. The record head runs again, but
  the existing loop is preserved and new input is layered on top of it instead
  of crossfading with it. Any press of K2 ends dub and freezes again. Holding
  K2 while the head is already running does nothing but the usual freeze.
- K3 **on/off**: turns sample replay on or off. The input side of MIX remains audible.

Because K2 now carries two gestures, its freeze toggle acts on the release
rather than the press.

Defaults: 2.5 seconds, 0.72 feedback, the full Buffer as the tape window,
normal tape speed, 2:8 input:sample balance, 50% tape:slice mix, 0.2-second
slices at 5 Hz, age 0 with full spread, 1.5× quantized speed ceiling, 35%
reverse probability, no bloom, 1.5× sample level, and 0.75 output. Every
control added after the first release defaults to the behaviour it replaced, so
a fresh instance sounds exactly as it did before them.
Output level and dub level are available from PARAMETERS rather than an encoder
page.

Changing recording time clears the current loop and allocates a new Buffer after
a 0.4-second encoder debounce. A change that arrives while an allocation is
already running is queued and applied afterwards, so the Buffer always ends at
the length the parameter shows. If the engine does not answer within five
seconds, the header reads `ERR` and the keys become usable again. MIX is shown as `input:sample`: `10:0` is input
only, `5:5` is equal balance, and `0:10` is sample only.

## Signal path

```text
input ─> feedback RecordBuf ─> Buffer ─┬─> looping PlayBuf ───────┐
                                       └─> two GrainBuf readers ──┴─> tape/slice mix ─> sample level ─> wet
input ───────────────────────────────────────────────────────────────────────────────────────────────> dry
dry + wet ─> input/sample mix ─> limiter ─> output
```

The record path selects the louder hardware input channel rather than summing
left and right, avoiding mono cancellation from opposite-polarity sources. The
choice uses hysteresis, so two channels at similar levels cannot chatter and
leave the selector parked mid-crossfade, summing the very inputs it is there to
keep apart. Input
is DC-filtered, given 2× protected capture makeup, and limited before recording.
Feedback is capped below unity; new input uses `1-feedback`, making every write a
bounded crossfade rather than an accumulating gain stage.

Dub is the one place where a write is not a bounded crossfade. The previous
pass is kept at full level and new input is added on top at `dub level`, so
repeated layers accumulate the way they do on any overdubbing looper; the final
limiter is what keeps the output in range. Ending dub freezes the head where it
stands, so the layer stays aligned with the loop.

Recording time reaches down to 20 ms, which is an audio rate rather than a loop
length. Below roughly 50 ms the Buffer stops being a phrase and becomes a
waveform: the repetition itself is the pitch, feedback becomes its decay, and
dub becomes additive synthesis on top of it. The control is exponential so the
short end is reachable.

The tape reader plays a window of the Buffer rather than always the whole of it.
`window start` places it and `window size` sets its length, down to one percent;
a small window is a stutter, and opening it up returns to the full loop. The
record head keeps circling the entire Buffer underneath, so a narrow window is
continually refilled with material from outside it. A window that runs past the
end of the Buffer wraps.

The optional slice layer runs two asynchronous, windowed readers whose positions
are anchored to the moving record head. `slice age` sets how far into the past
they reach and `slice spread` how wide a span they draw from, so "one second
ago" stays one second ago instead of meaning a fixed spot in the Buffer. At age
0 with full spread the draw is uniform across the whole Buffer, which is what
the readers did before. Every slice chooses a musical tape-speed ratio from `0.5×`, `2/3×`,
`0.75×`, `1×`, `4/3×`, `1.5×`, and `2×`, limited by `quantized speed max`;
`reverse chance` independently reverses slices. Density is capped at 8 Hz per
stream and each reader is capped at eight concurrent grains. Slices mix only into
replay and never feed the record Buffer.

`regen` folds the tape reader back into the record input, which is what turns
varispeed into accumulation: at any speed but 1× each circulation is re-recorded
shifted, and the loop climbs or sinks through itself. Three things keep that
from running away. `regen tone` is a lowpass in the fold-back path and never
opens fully, so every generation loses top end and an upward spiral hits a wall
instead of piling up near Nyquist. The 2× capture makeup applies only to the
hardware input, not the fold-back, which would otherwise double the loop gain.
And dub suppresses regen entirely, because dub already retains the whole
previous pass and adding the Buffer's own output on top of that is above unity
by construction. The amount is also capped below one internally.

Turning E1 wraps from the last page back to the first.

`bloom` makes the machine answer silence. An envelope follower on the input
falls away over `bloom time`; as it does, slices grow longer, thin out, and take
over more of the tape:slice balance, and they retreat the moment anything is
played. At its default of zero it does nothing at all.

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
