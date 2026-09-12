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

- K2 (`freeze`) sets the recorder's `run` control to zero or one. Frozen Buffer
  contents keep replaying.
- K3 (`on/off`) gates tape and slice replay. It does not gate the input branch.

Changing recording time waits for a 0.4-second encoder debounce, allocates a new
Buffer, points recorder and replay to it, and frees the previous Buffer after a
one-second grace period. That period outlasts the longest slice that can still
be reading the old Buffer, which is `slice size 0.5 s x 1.25 jitter`. A length
change that arrives while an allocation is in flight is held and applied when
that allocation completes, rather than dropped. Changing length therefore clears
the current loop.

The Lua side arms a five-second watchdog whenever it asks for a new Buffer. If
the engine never reports a settled state, the watchdog releases the interface,
shows `ERR` in the header, and re-enables K2 and K3 instead of leaving them dead
until the app is reloaded.

Grainloom does not create or change global norns reverb controls. Generation
loss, dropout, wow/flutter, bit crushing, glitch, file loading, manual capture,
and generation printing are not present in the current engine.
