# Algorithms

This document describes our implementation, not an inferred commercial firmware.

## Signal path

```
mono sample ── variable-rate loop ─┐
            └─ grain voices ──────┴─ blend ─ digital degradation ─ tape loss
                                 ─ tone ─ source gate ─ stereo reverb ─ limiter
                                                                   ├─ master → output
                                                                   └─ mono print → new sample
```

## Sample / loop

A mono SuperCollider Buffer stores the sample. A Phasor advances by
`rate * BufRateScale` frames per server sample, wrapping at the actual buffer
length. BufRd uses cubic interpolation. The loop boundary has a linear 5 ms
fade on either side; this is not a crossfaded dual-head looper.

## Grains

GrainBuf creates stereo panned voices with the built-in Hann envelope (`envbufnum
= -1`) and linear sample interpolation. Grain durations range from 20–400 ms.
The scheduler uses a control-rate Impulse at 2–40 Hz, capped so
`duration * density <= 12`. GrainBuf is allocated 32 slots.

An independent normalized Phasor scans the sample. Grain position is its current
phase plus a user offset and per-grain uniformly distributed spray, wrapped into
0–1. Grains wrap the complete sample, not a separate splice. Pitch/direction
comes from `rate`, independent of scan speed. Freeze sets the scan increment to
zero; it doesn't stop grains from reading. Approximate overlap normalization is
`1 / sqrt(max(1, duration*density))`; correlated material can still grow louder,
so the final limiter remains necessary.

## Glitch

At an 8 Hz control tick, compare a uniform random value with `glitch`. When true,
hold the scan position for that tick. Grains repeatedly start at that held
location (plus spray), giving short stutters. This is deliberately a modest,
bounded process rather than arbitrary buffer corruption or a complex probability
sequencer. It affects the grain layer only.

## Digital degradation

`crush` reduces the sample-and-hold clock from the server sample rate to 3% of it,
and quantizes amplitude from 16 toward 4 bits. A crossfade blends the degraded
signal with the clean signal; zero bypasses it. This deliberate aliasing is a
separate treatment from tape loss.

## Tape / generation loss

- Playback-rate drift: smooth LFNoise2 at 0.6 Hz and 9 Hz, with maximum depths of
  0.4 and 0.1 semitones respectively, converted to a pitch ratio. GrainBuf samples
  these rate values on grain creation; the ordinary loop responds continuously.
  This means flutter behaves differently at low grain densities.
- Saturation: `tanh(x * (1 + 3*loss)) / (1 + loss)`.
- Bandwidth: high-pass cutoff `25 + 180*loss` Hz, low-pass cutoff
  `18000 * 0.12^loss` Hz. These are simple filters, not measured tape responses.
- Noise: low-level PinkNoise, scaled by loss inside the loss branch.
- Loss branch is linearly blended with the original according to loss.
- Dropout: at 12 Hz, an event occurs with probability `0.3*dropout` and attenuates
  the signal by a random 35–95%, with 8 ms smoothing. Applies to both channels.

An internal print records the post-reverb, post-limiter signal before master
volume. This is how processing becomes cumulative: the new sample contains the
previous generation's damage. It is not a knob that merely counts generations.
The processing chain remains enabled after printing, so the newly captured
generation is heard through the same chain again. Printing includes only one
sample-length window of the current performance; it does not append a reverb tail.

## Reverb / lifecycle

FreeVerb2 supplies stereo algorithmic reverb. This is a stock UGen, not a custom
MOOD-style multi-tap network. The source is gated before it so pause releases
the tail. The print recorder is ordered after the voice in the synth graph.

Buffer operations are serialized while allocating, recording or loading.
Server sync barriers precede buffer swaps. Old buffers survive for 500 ms after
replacement to cover the maximum 400 ms grain duration. Free releases the active
voice, recorder, in-flight routine, work buffer, retired buffers and print bus.
Completion revision and operation state share one integer poll to avoid a Lua
race between separately delivered success and status messages.

## References

- [norns engine interface](https://monome.org/docs/norns/reference/engine)
- [norns engine studies](https://monome.org/docs/norns/engine-study-1/)
- [norns polls](https://monome.org/docs/norns/reference/poll)
- [SuperCollider GrainBuf](https://doc.sccode.org/Classes/GrainBuf.html)
- [SuperCollider FreeVerb2](https://doc.sccode.org/Classes/FreeVerb2.html)
- [Morphagene manual](https://www.makenoisemusic.com/wp-content/uploads/2024/03/morphagene-manual.pdf)

No source code or assets from the commercial instruments are included.
