// SPDX-License-Identifier: MIT
// Minimal continuous feedback loop engine for norns.
Engine_Grainloom : CroneEngine {
    var voice, recorder, sample, regenBus, task;
    var state = 0, seconds = 2.5, revision = 0, alive = true;
    var feedback = 0.72, dubLevel = 1;
    var loopFrames = 0, maxFrames = 0, validFrames = 0;

    alloc {
        var server = context.server;
        var rate = server.sampleRate;

        // The Buffer is twice the longest loop, and the recorder writes every
        // sample twice: once at the head and once one loop ahead of it. Any
        // read that starts inside the loop and runs forward by up to one loop
        // length therefore lands on correctly wrapped audio without the reader
        // needing to know where the loop ends. That is what lets the loop
        // length be a plain number both heads wrap on, instead of a Buffer
        // that has to be reallocated and refilled to change size.
        maxFrames = (rate * 30).asInteger;
        loopFrames = (rate * 2.5).asInteger;
        // How far from frame 0 the Buffer is known to hold loop content.
        validFrames = loopFrames * 2;

        SynthDef(\grainloom_loop_record, { |buf, inL, inR,
            feedback=0.72, run=1, dub=0, dub_level=1, regen_b=0,
            loop_frames=120000, t_reset=0|
            var left = In.ar(inL);
            var right = In.ar(inR);
            var ampL = Amplitude.kr(left,0.01,0.05);
            var ampR = Amplitude.kr(right,0.01,0.05);
            // Commit to right only when it is clearly louder, and back to left
            // only when it clearly is not. A bare comparison chatters whenever
            // the two channels sit near each other, which parks the crossfade
            // mid-way and sums them -- precisely the opposite-polarity
            // cancellation this channel selection exists to avoid.
            var side = Lag.kr(
                Schmidt.kr(ampR / (ampL + 1e-5), 0.8, 1.25), 0.02);
            var input = SelectX.ar(side, [left,right]);
            var fb = feedback.clip(0,0.98);
            // Dub keeps the existing pass intact (preLevel 1) and adds new
            // input on top, so layers stack instead of crossfading.
            var d = Lag.kr(dub.clip(0,1), 0.02);
            var recAmt = ((1-fb) * (1-d)) + (dub_level.clip(0,1) * d);
            var preAmt = (fb * (1-d)) + d;
            var len = loop_frames.clip(2, BufFrames.kr(buf)/2);
            // Freeze has to stop the write, not just the head: with the phase
            // held, a running BufWr would overwrite one frame forever. At run 0
            // the write puts back exactly what it read, a true no-op.
            var go = run.clip(0,1);
            // Rate is exactly 0 or 1 and never lagged, so the phase stays on
            // whole frames and the read below needs no interpolation.
            var phase = Phasor.ar(t_reset, go, 0, len, 0);
            var prev = BufRd.ar(1, buf, phase, 0, 1);
            var written;
            // The 2x makeup belongs to the quiet hardware input only. Applying
            // it to the fold-back too would make the loop gain 2*regen*(1-fb)
            // and send anything above regen 0.5 divergent.
            //
            // Dub gets no fold-back at all: its preLevel is 1, so adding the
            // Buffer's own output on top of a fully retained pass is a loop
            // gain above unity by construction.
            input = LeakDC.ar(input) * 2;
            input = Limiter.ar(input + (In.ar(regen_b) * (1-d)), 0.9, 0.01);
            written = (input * recAmt * go) + (prev * ((preAmt * go) + (1-go)));
            BufWr.ar(written, buf, phase, 0);
            BufWr.ar(written, buf, phase + len, 0);
        }).add;

        SynthDef(\grainloom_loop_voice, { |out, inL, inR, buf,
            active=0, t_reset=0, rate=1, mix=0.8,
            sample_gain=1.5, gain=0.75, slice_size=0.2,
            slice_density=5, slice_speed=1.5, slice_reverse=0.35,
            slice_mix=0.5, window_start=0, window_size=1,
            slice_age=0, slice_spread=1, bloom=0, bloom_time=4,
            write_run=1, regen=0, regen_tone=4000, regen_b=0,
            loop_frames=120000|
            var dry = [In.ar(inL),In.ar(inR)];
            var bufLen = BufFrames.kr(buf).max(2);
            var len = loop_frames.clip(2, bufLen/2);
            var scale = BufRateScale.kr(buf);
            // Bloom grows into the gaps: `quiet` rises as the input falls away
            // over bloom_time and sits at zero while anything is being played.
            var inAmp = Amplitude.kr((dry[0]+dry[1])*0.5,
                0.05, Lag.kr(bloom_time,0.1));
            var bl = Lag.kr(bloom,0.05).clip(0,1)
                * (1 - (inAmp*6).clip(0,1));
            // Tape reads a window of the loop rather than always all of it.
            // A window running past the loop end reads the mirror the recorder
            // keeps one loop ahead, so it wraps correctly with no special case.
            var wStart = Lag.kr(window_start,0.05).clip(0,1) * len;
            var wLen = (Lag.kr(window_size,0.05).clip(0.001,1) * len).max(2);
            var phase = Phasor.ar(t_reset, Lag.kr(rate,0.04) * scale,
                wStart, wStart+wLen, wStart);
            var loop = BufRd.ar(1, buf, phase, 0, 4);
            // The record head's position, rebuilt from the same loop length,
            // the same run gate and the same reset the recorder uses, so the
            // two stay locked. Deliberately unlagged: a ramped rate would put
            // it on fractional frames and drift on every freeze.
            var wPhase = A2K.kr(Phasor.ar(t_reset, write_run, 0, len, 0)) / len;
            var loopNorm = len / bufLen;
            var density = (slice_density.clip(1,8) * (1 - (0.5*bl))).max(0.25);
            var trigA = Impulse.kr(density);
            var trigB = Impulse.kr(density*0.79,0.5);
            var maxSpeed = slice_speed.clip(0.5,2);
            var speedRatios = [0.5,2/3,0.75,1,4/3,1.5,2];
            var maxSpeedIndex = (maxSpeed >= speedRatios).sum-1;
            var speedA = Select.kr(
                TIRand.kr(0,maxSpeedIndex,trigA),speedRatios)
                * Select.kr(TRand.kr(0,1,trigA)<slice_reverse,[1,-1]);
            var speedB = Select.kr(
                TIRand.kr(0,maxSpeedIndex,trigB),speedRatios)
                * Select.kr(TRand.kr(0,1,trigB)<slice_reverse,[1,-1]);
            // Slice positions are anchored to the moving record head, so `age`
            // means a fixed distance into the past rather than a fixed spot.
            // age 0 with spread 1 is a uniform draw over the loop.
            var age = Lag.kr(slice_age,0.05).clip(0,1);
            var spread = Lag.kr(slice_spread,0.05).clip(0,1);
            // A grain may read at up to 2x, and the mirror only guarantees one
            // loop length past the start, so no grain may read further.
            var maxSize = len / (2 * SampleRate.ir);
            var size = (slice_size.clip(0.06,0.5) * (1 + bl)).min(maxSize);
            var posA = (wPhase - age
                - (TRand.kr(0,1,trigA)*spread)).wrap(0,1) * loopNorm;
            var posB = (wPhase - age
                - (TRand.kr(0,1,trigB)*spread)).wrap(0,1) * loopNorm;
            var sliceA = GrainBuf.ar(2,trigA,
                size*TRand.kr(0.75,1.25,trigA),
                buf,speedA,posA,2,
                TRand.kr(-0.85,0.85,trigA),-1,8);
            var sliceB = GrainBuf.ar(2,trigB,
                size*TRand.kr(0.75,1.25,trigB),
                buf,speedB,posB,2,
                TRand.kr(-0.85,0.85,trigB),-1,8);
            var slices = LeakDC.ar((sliceA+sliceB)*0.6);
            var setMix = Lag.kr(slice_mix,0.04);
            // Bloom also leans the balance towards the slice layer.
            var mixEff = (setMix + ((1-setMix) * bl * 0.8)).clip(0,1);
            var replay = XFade2.ar(loop!2,slices,mixEff*2-1);
            var gate = Lag.kr(active,0.02);
            // Regen folds the tape reader back into the record input, which is
            // what makes varispeed accumulate into a pitch spiral. The lowpass
            // is the stability mechanism rather than a colour: every generation
            // loses top end, so an upward spiral runs into a wall instead of
            // piling up at Nyquist. The amount is capped below unity so the
            // circulation gain fb + regen*(1-fb) stays under one.
            var fold = LPF.ar(loop, Lag.kr(regen_tone,0.05).clip(200,8000))
                * (Lag.kr(regen,0.05).clip(0,1) * 0.9) * gate;
            var wet = replay * Lag.kr(sample_gain,0.04) * gate;
            var signal = XFade2.ar(dry,wet,Lag.kr(mix,0.04)*2-1);
            signal = Limiter.ar(LeakDC.ar(signal),0.95);
            ReplaceOut.ar(regen_b, fold);
            Out.ar(out,signal * Lag.kr(gain,0.05));
        }).add;

        server.sync;
        sample = Buffer.alloc(server, maxFrames*2, 1);
        regenBus = Bus.audio(server,1);
        server.sync;

        voice = Synth(\grainloom_loop_voice,
            [\out,context.out_b,
             \inL,context.in_b[0].index,
             \inR,context.in_b[1].index,
             \buf,sample,
             \regen_b,regenBus.index,
             \loop_frames,loopFrames], context.xg);
        recorder = Synth.after(voice,\grainloom_loop_record,
            [\buf,sample,
             \inL,context.in_b[0].index,
             \inR,context.in_b[1].index,
             \feedback,feedback,
             \dub_level,dubLevel,
             \regen_b,regenBus.index,
             \loop_frames,loopFrames]);
        state = 4;
        revision = revision+1;

        this.addPoll(\grainloom_state, { revision*10+state });
        this.addPoll(\grainloom_seconds, { seconds });

        [\rate,\mix,\sample_gain,\gain,\slice_size,\slice_density,
            \slice_speed,\slice_reverse,\slice_mix,
            \window_start,\window_size,\slice_age,\slice_spread,
            \bloom,\bloom_time,\regen,\regen_tone].do { |name|
            this.addCommand(name,"f",{|msg| voice.set(name,msg[1]) });
        };
        this.addCommand(\feedback,"f",{|msg|
            feedback = msg[1].clip(0,0.98);
            recorder !? { recorder.set(\feedback,feedback) };
        });
        this.addCommand(\dub_level,"f",{|msg|
            dubLevel = msg[1].clip(0,1);
            recorder !? { recorder.set(\dub_level,dubLevel) };
        });
        this.addCommand(\dubbing,"i",{|msg|
            recorder !? {
                var on = msg[1].clip(0,1);
                // Dub runs the record head; ending it re-freezes in place.
                recorder.set(\dub,on,\run,on);
                voice.set(\write_run,on);
                state = if(on == 0,1,2);
                revision = revision+1;
            };
        });
        this.addCommand(\playing,"i",{|msg|
            voice.set(\active,msg[1].clip(0,1));
        });
        this.addCommand(\writing,"i",{|msg|
            recorder !? {
                recorder.set(\run,msg[1].clip(0,1),\dub,0);
                voice.set(\write_run,msg[1].clip(0,1));
                state = if(msg[1] == 0,1,4);
                revision = revision+1;
            };
        });
        // Length is now a number both heads wrap on, so it takes effect on the
        // next sample: no reallocation, no reset, no silence, and it can be
        // swept while playing.
        this.addCommand(\loopLength,"f",{|msg| this.setLength(msg[1]) });
        this.addCommand(\primeMirror,"",{|msg| this.primeMirror });
        this.addCommand(\liveLoop,"if",{|msg|
            if(msg[1] == 0) {
                voice.set(\active,0);
                recorder !? { recorder.set(\run,0) };
                state = 0;
                revision = revision+1;
            } {
                this.setLength(msg[2]);
                recorder !? { recorder.set(\run,1,\dub,0) };
                voice.set(\active,1,\write_run,1);
                state = 4;
                revision = revision+1;
            };
        });
    }

    setLength { |requested|
        var limit = requested.clip(0.02,30);
        var frames = (context.server.sampleRate * limit).asInteger
            .clip(2, maxFrames);
        seconds = limit;
        // Move the readers at once, but never past what is known to hold loop
        // content: beyond that they would be reading whatever an earlier and
        // longer loop left behind. The rest follows a few milliseconds later.
        this.applyLength(frames.min(validFrames).max(2));
        if(frames > validFrames) { this.extendTo(frames) };
    }

    applyLength { |frames|
        loopFrames = frames;
        // One bundle. If the two Synths disagree on the length for even a
        // block, the recorder's mirror write lands at phase plus the shorter
        // length, which is inside the reader's loop.
        context.server.makeBundle(nil, {
            voice !? { voice.set(\loop_frames,frames) };
            recorder !? { recorder.set(\loop_frames,frames) };
        });
    }

    extendTo { |frames|
        var server = context.server;
        // Tile what is already there over the part of the Buffer the loop is
        // about to reach, by repeated doubling, and only then hand the readers
        // the longer loop. Copying first is the point: the previous order
        // memcpyd the whole grown region underneath a live read head.
        task !? { task.stop };
        task = Routine {
            var filled = validFrames.max(2), n;
            while { alive and: { filled < frames } } {
                n = filled.min(frames - filled);
                sample.copyData(sample, filled, 0, n);
                server.sync;
                filled = filled + n;
            };
            if(alive) {
                validFrames = frames;
                this.applyLength(frames);
            };
        }.play(SystemClock);
    }

    primeMirror {
        // The mirror is only refreshed as the record head passes it, so right
        // after a length change the region one loop ahead still belongs to the
        // previous length, and a grain or a window overrunning the loop end
        // hears it. One copy fixes that. The mirror is by definition outside
        // the live loop, so unlike the growth tiling this is safe to run while
        // the readers and the recorder are running.
        if(alive and: { loopFrames > 1 }) {
            sample.copyData(sample, loopFrames, 0, loopFrames);
            validFrames = loopFrames * 2;
        };
    }

    free {
        alive = false;
        task !? { task.stop };
        recorder !? { recorder.free };
        voice !? { voice.free };
        sample !? { sample.free };
        regenBus !? { regenBus.free };
    }
}
