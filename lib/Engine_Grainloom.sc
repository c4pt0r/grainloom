// SPDX-License-Identifier: MIT
Engine_Grainloom : CroneEngine {
    var voice, recorder, sample, work, incoming, task, retired, tap;
    var state = 0, recording = false, alive = true, seconds = 0, revision = 0;

    alloc {
        var server = context.server;
        retired = List.new;
        tap = Bus.audio(server, 2);
        SynthDef(\grainloom_record, { |buf, inL, inR|
            RecordBuf.ar((In.ar(inL) + In.ar(inR)) * 0.5, buf,
                loop: 0, doneAction: 0);
        }).add;
        SynthDef(\grainloom_voice, { |out, tapOut, buf, active=0, t_reset=0,
            position=0, scan=1, freeze=0, rate=1, size=0.12, density=12,
            spray=0.02, blend=0.7, crush=0, glitch=0, reverb=0.25,
            room=0.8, tone=10000, gain=0.65, loss=0.2, wow=0.15,
            flutter=0.1, dropout=0|
            var frames = BufFrames.kr(buf).max(2);
            var drift = (LFNoise2.kr(0.6)*wow*0.4
                + LFNoise2.kr(9)*flutter*0.1).midiratio;
            var duration = size.clip(0.02, 0.4);
            // At most 12 overlapping grains; GrainBuf has 32 slots.
            var hz = density.clip(2, 40).min(12 / duration);
            var trigger = Impulse.kr(hz);
            var walk = Phasor.ar(t_reset,
                scan * (1-freeze) * BufRateScale.kr(buf) / frames, 0, 1);
            var center = (A2K.kr(walk) + position).wrap(0, 1);
            var burst = TRand.kr(0, 1, Impulse.kr(8)) < glitch;
            var held = Latch.kr(center, Impulse.kr(8));
            var grainPos = Select.kr(burst, [center, held]);
            var jitter = TRand.kr(spray.neg, spray, trigger);
            var pan = TRand.kr(-0.8, 0.8, trigger);
            var grains = GrainBuf.ar(2, trigger, duration, buf, rate * drift,
                (grainPos+jitter).wrap(0, 1), 2, pan, -1, 32);
            var head = Phasor.ar(t_reset,
                Lag.kr(rate, 0.04) * drift * BufRateScale.kr(buf), 0, frames);
            var phase = head / frames;
            // A short seam fade avoids hard discontinuities at loop wrap.
            var edge = (phase * frames / (SampleRate.ir * 0.005)).clip(0,1)
                * ((1-phase) * frames / (SampleRate.ir * 0.005)).clip(0,1);
            var loop = BufRd.ar(1, buf, head, 1, 4) * edge;
            var norm = (hz * duration).max(1).sqrt.reciprocal;
            var signal = (loop ! 2) * (1-Lag.kr(blend, 0.04))
                + (grains * norm * Lag.kr(blend, 0.04));
            var bits = (16 - (crush * 12)).round(1);
            var steps = 2.pow(bits - 1);
            var degraded = Latch.ar(signal, Impulse.ar(
                SampleRate.ir * (1-(crush*0.97)))) .round(steps.reciprocal);
            var tapeSignal, dropGate;
            signal = XFade2.ar(signal, degraded, Lag.kr(crush,0.03)*2-1);
            // An original tape-loss model, not the Generation Loss pedal DSP.
            tapeSignal = (signal * (1+loss*3)).tanh / (1+loss);
            tapeSignal = LPF.ar(HPF.ar(tapeSignal, 25+loss*180),
                18000 * (0.12.pow(loss)));
            tapeSignal = tapeSignal + (PinkNoise.ar(0.002) ! 2) * loss;
            dropGate = Lag.kr(1 - ((TRand.kr(0,1,Impulse.kr(12)) < (dropout*0.3))
                * TRand.kr(0.35,0.95,Impulse.kr(12))), 0.008);
            signal = (signal * (1-loss) + tapeSignal * loss) * dropGate;
            signal = LPF.ar(signal, Lag.kr(tone,0.05).clip(200,18000));
            // Gate the source before reverb so pause lets the tail decay.
            signal = signal * Lag.kr(active, 0.025);
            signal = FreeVerb2.ar(signal[0], signal[1],
                Lag.kr(reverb,0.05), room.clip(0,0.98), 0.5);
            signal = Limiter.ar(LeakDC.ar(signal), 0.95);
            Out.ar(tapOut, signal); // pre master-gain resampling
            Out.ar(out, signal * Lag.kr(gain,0.05));
        }).add;
        server.sync;
        sample = Buffer.alloc(server, 2, 1);
        server.sync;
        voice = Synth(\grainloom_voice, [\out, context.out_b, \tapOut,tap, \buf, sample], context.xg);
        // One atomic value avoids races between completion and status polls.
        this.addPoll(\grainloom_state, { revision*10 + if(state == -1,9,state) });
        this.addPoll(\grainloom_seconds, { seconds });
        [\position, \scan, \freeze, \rate, \size, \density, \spray,
            \blend, \crush, \glitch, \reverb, \room, \tone, \gain,
            \loss, \wow, \flutter, \dropout].do { |name|
            this.addCommand(name, "f", { |msg| voice.set(name, msg[1]) });
        };
        this.addCommand(\playing, "i", { |msg|
            if(state == 1) { voice.set(\active, msg[1].clip(0,1)) };
        });
        this.addCommand(\capture, "fi", { |msg|
            if((state != 2) and: { state != 3 }) {
                state = 3;
                task = Routine {
                    var started, length, next;
                    var limit = msg[1].clip(0.1,30);
                    var bounce = msg[2] == 1;
                    // Use engine duration, not a possibly stale Lua duration poll.
                    if(bounce) { limit = seconds.clip(0.1,30) };
                    voice.set(\active, if(bounce,1,0));
                    work = Buffer.alloc(server, (server.sampleRate*limit).asInteger, 1);
                    server.sync;
                    recording = true;
                    seconds = 0;
                    recorder = Synth.after(voice, \grainloom_record, [\buf,work,
                        \inL,if(bounce,tap.index,context.in_b[0]),
                        \inR,if(bounce,tap.index+1,context.in_b[1])]);
                    started = Main.elapsedTime;
                    state = 2;
                    while { recording and: { (Main.elapsedTime-started) < limit } } {
                        seconds = (Main.elapsedTime-started).min(limit);
                        0.02.wait;
                    };
                    length = (Main.elapsedTime-started).clip(0.1,limit);
                    recording = false;
                    state = 3;
                    recorder.free; recorder = nil;
                    server.sync;
                    next = Buffer.alloc(server, (length*server.sampleRate).asInteger,1);
                    incoming = next;
                    server.sync;
                    work.copyData(next, 0, 0, next.numFrames);
                    server.sync;
                    work.free; work = nil;
                    this.replaceSample(next, length);
                }.play(SystemClock);
            };
        });
        this.addCommand(\stopCapture, "", { recording = false });
        this.addCommand(\read, "s", { |msg|
            if((state != 2) and: { state != 3 }) {
                state = 3;
                task = Routine {
                    var file = SoundFile.new;
                    var next, count, sr;
                    if(file.openRead(msg[1])) {
                        sr = file.sampleRate;
                        count = file.numFrames.min((sr*30).asInteger);
                        file.close;
                        if(count > 1) {
                            state = 3;
                            voice.set(\active,0);
                            0.04.wait;
                            next = Buffer.readChannel(server,msg[1],0,count,[0]);
                            incoming = next;
                            server.sync;
                            this.replaceSample(next, count/sr);
                        } { state = -1; revision = revision+1; voice.set(\active,0) };
                    } { state = -1; revision = revision+1; voice.set(\active,0) };
                }.play(SystemClock);
            };
        });
    }

    replaceSample { |next, length|
        var old = sample;
        sample = next;
        incoming = nil;
        voice.set(\buf,sample, \t_reset,1, \active,1);
        seconds = length;
        state = 1;
        revision = revision+1;
        retired.add(old);
        0.5.wait; // let grains referencing the old buffer finish
        if(alive) {
            old.free;
            retired.remove(old);
        };
    }

    free {
        alive = false;
        recording = false;
        task !? { task.stop };
        recorder !? { recorder.free };
        voice !? { voice.free };
        sample !? { sample.free };
        work !? { work.free };
        incoming !? { incoming.free };
        retired !? { retired.do(_.free) };
        tap !? { tap.free };
    }
}
