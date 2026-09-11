# Minimal loop algorithm

The refactored engine has one recorder, one mono circular Buffer, and one replay
voice.

For every Buffer pass, `RecordBuf` applies:

```text
buffer[n] = input[n] * (1 - feedback)
          + old_buffer[n] * feedback
```

At the default feedback of 0.72, every repeat retains 72% of the previous pass.
The two weights sum to one, so sustained input cannot make the Buffer grow on
every pass. Feedback is limited to 0.98.

The recorder and replay voice run concurrently. The voice is placed before the
recorder in the synth graph, so it reads the previous sample before that location
is overwritten. `PlayBuf` loops continuously and supports forward, reverse, and
stopped playback rates.

The replay signal is multiplied by sample level, mixed with stereo hardware input
using an equal-power crossfade, DC-filtered, limited to 0.95, and sent to output.

The optional slice layer uses two asynchronous `GrainBuf` streams. Each trigger
chooses a random Buffer position, duration variation, stereo pan, quantized speed,
and forward/reverse direction. Speeds are selected from the tape-ratio set `0.5`,
`2/3`, `0.75`, `1`, `4/3`, `1.5`, and `2`, capped by the speed-max control. Hann
windows prevent hard slice boundaries. Tape and slice replay are equal-power
mixed before sample level. This layer is output-only: it is not routed back into
`RecordBuf`. No other effects are connected.

K2 is **freeze**: it sets the recorder's `run` control. At zero, the write head
stops and Buffer contents remain unchanged while replay continues. K3 is
**on/off**: it gates sample replay while leaving the input side of the mix
available.

Changing recording time allocates a new Buffer, atomically points recorder and
voice to it, then frees the old Buffer after a short grace period.
