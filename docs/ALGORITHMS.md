# Current algorithm

Grainloom has one mono circular Buffer, one continuous recorder, and one replay
voice. The replay voice contains the tape reader and two random slice readers.
There are no selectable memory modes or additional effect chains.

## Continuous feedback loop

Recording and replay begin automatically when the app starts. For every Buffer
pass, `RecordBuf` applies:

```text
buffer[n] = input[n] * (1 - feedback)
          + old_buffer[n] * feedback
```

At the default feedback of 0.72, each pass retains 72% of the old content and
uses 28% new input. The weights sum to one, preventing sustained input from
adding an unbounded gain stage on every pass. Feedback is capped at 0.98.

The recorder chooses the louder hardware input channel, avoiding cancellation
when left and right carry opposite-polarity versions of the same mono source.
The choice is made with hysteresis: it commits to the right channel only once
that channel reaches 1.25x the left, and returns only below 0.8x. A bare
comparison would chatter whenever the two sit near each other and park the
selector mid-crossfade, where both inputs sum.
The selected input is DC-filtered, given 2× capture makeup, and limited to 0.9
before it reaches the Buffer.

The replay voice runs before the recorder in the server node graph, so each
Buffer location is read before that location is overwritten. A looping `PlayBuf`
provides the main tape voice and accepts continuous forward, reverse, and zero
rates from -2× to 2×.

## Dub

Holding K2 for half a second while the loop is frozen starts dub. The record
head runs again, but the write weights change:

```text
buffer[n] = input[n] * dub_level + old_buffer[n] * 1
```

Unlike the normal pass this is additive rather than a crossfade, which is what
layering means: the loop under the head keeps its level and new input is added
to it. Repeated layers therefore accumulate, exactly as they do on any
overdubbing looper, and the output limiter is what holds the result in range.
`dub level` defaults to 1 and lives in PARAMETERS.

The two weights are interpolated over 20 ms rather than switched, because
`RecordBuf` reads `recLevel` and `preLevel` once per control block and a hard
step would click. Dub reuses the running recorder rather than replacing it, so
the write head keeps the position freeze left it at and the new layer stays
aligned with the loop; a fresh `RecordBuf` would restart at frame 0.

Any press of K2 ends dub, which stops the head where it stands and returns to
the frozen state. The plain freeze toggle also clears dub, so the two gestures
cannot leave the recorder in a mixed mode.

## Tape window

The tape voice reads a window of the Buffer rather than always the whole of it,
so `PlayBuf` is replaced by `Phasor` driving `BufRd`; `PlayBuf` can only loop an
entire Buffer. `window start` and `window size` become the Phasor's bounds, and
`BufRd` loops its index, so a window running past the end of the Buffer wraps
around to the beginning instead of reading out of range. The window floor is two
frames, which at short recording times is an audio-rate buzz rather than a loop.

The record head is unaffected and keeps circling the entire Buffer, so a narrow
window is continually refilled with material written outside it.

## Audio-rate loops

Recording time reaches down to 20 ms. Below roughly 50 ms the Buffer stops being
a phrase and becomes a waveform: the loop's own repetition rate is the pitch,
feedback is its decay, and dub is additive synthesis onto it. Nothing in the
engine special-cases this; it is the same circular Buffer read faster than the
ear resolves as rhythm. The Lua control is exponential so the short end is
actually reachable with an encoder.

## Slice position and age

Slice positions are anchored to the record head rather than drawn uniformly from
the Buffer. The head's position is not exposed by `RecordBuf`, so the voice
rebuilds it: a `Phasor` advancing one frame per sample whenever the recorder
runs, gated by the same `write_run` value the engine gives the recorder's `run`,
and reset by the same trigger. It is deliberately not lagged, so repeated
freezing cannot drift it out of alignment with the real head.

Each slice then draws:

```text
pos = wrap(write_head - age - random(0, spread), 0, 1)
```

`age` is therefore a distance into the past that stays constant as the head
moves, rather than a fixed spot in the Buffer, and `spread` is the width of the
span drawn from. At `age 0, spread 1` the expression is a uniform draw over the
whole Buffer, which is exactly what the readers did before.

## Bloom

`bloom` couples the slice layer inversely to the input. An `Amplitude` follower
on the hardware input releases over `bloom time`; the complement of that, scaled
by `bloom`, is a value that rises as playing stops and returns to zero as soon
as anything is played. It lengthens slices up to 2x, thins density to as little
as half, and leans the tape/slice balance towards slices by up to 80% of the
remaining distance. At `bloom 0` every one of those terms cancels.

The size and density moves are deliberately opposed so the concurrent grain
count cannot climb: at the extremes it lands near five per reader, inside the
eight-grain cap.

## Regen

`regen` folds the tape reader's output back into the record input, so the Buffer
feeds itself. At any tape speed but 1x each circulation is re-recorded shifted,
and the loop spirals through itself; at 1x it is a fixed-offset delay between
the read and write heads.

The voice and the recorder are separate Synths, so the send travels over a
private audio Bus written with `ReplaceOut` and read with `In`. The voice
already runs before the recorder in the node graph, so the recorder reads the
value the voice wrote in the same block.

Three things bound it, and all three are structural rather than advisory:

- `regen tone` is a lowpass in the fold-back path, clipped to 8 kHz at the top.
  Every generation therefore loses top end, and an upward varispeed spiral runs
  into a falling ceiling instead of accumulating near Nyquist.
- The 2x capture makeup is applied to the hardware input alone. Folding it into
  the send as well would make the circulation gain `2*regen*(1-fb)`, which
  diverges above `regen 0.5`.
- Dub multiplies the send by `1-dub`. Dub's `preLevel` is 1, so adding the
  Buffer's own output to a fully retained pass is a gain above unity by
  construction.

With those in place the circulation gain is `fb + regen*(1-fb)`, and the amount
is capped at 0.9 internally so it stays below one even at maximum feedback. The
existing 0.9 limiter on the record input bounds the level regardless.

Regen is gated by `active`, so K3 stops the machine feeding itself as well as
stopping replay.

## Random slices

Two asynchronous `GrainBuf` streams read the same live Buffer. Their trigger
rates are deliberately offset (`density` and `density × 0.79`) so they do not
continually fire together. Every trigger chooses:

- a random Buffer position;
- a duration between 75% and 125% of `slice size`;
- a random stereo position;
- an independently selected forward or reverse direction;
- one quantized tape-speed ratio.

The available positive speed ratios are:

```text
0.5, 2/3, 0.75, 1, 4/3, 1.5, 2
```

`quantized speed max` removes ratios above its current value. `reverse chance`
then independently changes the sign of each selected ratio. Built-in Hann
windows soften slice boundaries. Density is capped at 8 Hz per stream and each
stream is limited to eight concurrent grains.

Tape and slice replay are equal-power mixed, then multiplied by sample level.
Slices are output-only and never return to `RecordBuf`, so overlapping grains do
not become part of the feedback loop.

## Output and buttons

The wet replay and stereo hardware input are combined by an equal-power
input/sample crossfade. The result is DC-filtered, limited to 0.95, multiplied by
output level, and sent to the engine output.

- K2 (`freeze`) sets the recorder's `run` control to zero or one on the key
  release. Frozen Buffer contents keep replaying. Holding the key instead starts
  dub, described above; because the key carries two gestures, a press that a
  hold or a dub exit has already consumed does not also toggle freeze.
- K3 (`on/off`) gates tape and slice replay. It does not gate the input branch.

Changing recording time waits for a 0.4-second encoder debounce, allocates a new
Buffer, points recorder and replay to it, and frees the previous Buffer after a
one-second grace period. That period outlasts the longest slice that can still
be reading the old Buffer, which is `slice size 0.5 s x 1.25 jitter`. A length
change that arrives while an allocation is in flight is held and applied when
that allocation completes, rather than dropped. Changing length therefore clears
the current loop.

Unloading the app stops the recorder and replay without consulting the
paramset, because norns also calls `cleanup` when `init` failed before the
parameters were added.

The Lua side arms a five-second watchdog whenever it asks for a new Buffer. If
the engine never reports a settled state, the watchdog releases the interface,
shows `ERR` in the header, and re-enables K2 and K3 instead of leaving them dead
until the app is reloaded.

Grainloom does not create or change global norns reverb controls. Generation
loss, dropout, wow/flutter, bit crushing, glitch, file loading, manual capture,
and generation printing are not present in the current engine.
