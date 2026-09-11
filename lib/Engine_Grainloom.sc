// SPDX-License-Identifier: MIT
// Minimal continuous feedback loop engine for norns.
Engine_Grainloom : CroneEngine {
    var voice, recorder, sample, incoming, task, retired;
    var state = 0, seconds = 0, revision = 0, alive = true;
    var feedback = 0.72;

    alloc {
        var server = context.server;
        retired = List.new;

        SynthDef(\grainloom_loop_record, { |buf, inL, inR,
            feedback=0.72, run=1|
            var left = In.ar(inL);
            var right = In.ar(inR);
            var side = Lag.kr(
                Amplitude.kr(right,0.01,0.05)
                > Amplitude.kr(left,0.01,0.05), 0.05);
            var input = SelectX.ar(side, [left,right]);
            var fb = feedback.clip(0,0.98);
            input = Limiter.ar(LeakDC.ar(input) * 2, 0.9, 0.01);
            RecordBuf.ar(input, buf,
                recLevel: 1-fb,
                preLevel: fb,
                run: run,
                loop: 1,
                trigger: 1,
                doneAction: 0);
        }).add;

        SynthDef(\grainloom_loop_voice, { |out, inL, inR, buf,
            active=0, t_reset=0, rate=1, mix=0.8,
            sample_gain=1.5, gain=0.75, slice_size=0.2,
            slice_density=5, slice_speed=1.5, slice_reverse=0.35,
            slice_mix=0.5|
            var dry = [In.ar(inL),In.ar(inR)];
            var loop = PlayBuf.ar(1,buf,
                Lag.kr(rate,0.04) * BufRateScale.kr(buf),
                t_reset, 0, 1);
            var density = slice_density.clip(1,8);
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
            var sliceA = GrainBuf.ar(2,trigA,
                slice_size.clip(0.06,0.5)*TRand.kr(0.75,1.25,trigA),
                buf,speedA,TRand.kr(0,1,trigA),2,
                TRand.kr(-0.85,0.85,trigA),-1,8);
            var sliceB = GrainBuf.ar(2,trigB,
                slice_size.clip(0.06,0.5)*TRand.kr(0.75,1.25,trigB),
                buf,speedB,TRand.kr(0,1,trigB),2,
                TRand.kr(-0.85,0.85,trigB),-1,8);
            var slices = LeakDC.ar((sliceA+sliceB)*0.6);
            var replay = XFade2.ar(loop!2,slices,
                Lag.kr(slice_mix,0.04)*2-1);
            var wet = replay * Lag.kr(sample_gain,0.04)
                * Lag.kr(active,0.02);
            var signal = XFade2.ar(dry,wet,Lag.kr(mix,0.04)*2-1);
            signal = Limiter.ar(LeakDC.ar(signal),0.95);
            Out.ar(out,signal * Lag.kr(gain,0.05));
        }).add;

        server.sync;
        sample = Buffer.alloc(server,2,1);
        server.sync;
        voice = Synth(\grainloom_loop_voice,
            [\out,context.out_b,
             \inL,context.in_b[0].index,
             \inR,context.in_b[1].index,
             \buf,sample], context.xg);

        this.addPoll(\grainloom_state, { revision*10+state });
        this.addPoll(\grainloom_seconds, { seconds });

        [\rate,\mix,\sample_gain,\gain,\slice_size,\slice_density,
            \slice_speed,\slice_reverse,\slice_mix].do { |name|
            this.addCommand(name,"f",{|msg| voice.set(name,msg[1]) });
        };
        this.addCommand(\feedback,"f",{|msg|
            feedback = msg[1].clip(0,0.98);
            recorder !? { recorder.set(\feedback,feedback) };
        });
        this.addCommand(\playing,"i",{|msg|
            voice.set(\active,msg[1].clip(0,1));
        });
        this.addCommand(\writing,"i",{|msg|
            recorder !? {
                recorder.set(\run,msg[1].clip(0,1));
                state = if(msg[1] == 0,1,4);
                revision = revision+1;
            };
        });
        this.addCommand(\liveLoop,"if",{|msg|
            if(msg[1] == 0) {
                this.stopLoop;
                state = 0;
                revision = revision+1;
                voice.set(\active,0);
            } {
                this.startLoop(msg[2]);
            };
        });
    }

    startLoop { |requestedLength|
        var server = context.server;
        var limit = requestedLength.clip(0.1,30);
        if(state != 3) {
            this.stopLoop;
            state = 3;
            revision = revision+1;
            task = Routine {
                var next, old;
                next = Buffer.alloc(server,
                    (server.sampleRate*limit).asInteger,1);
                incoming = next;
                server.sync;
                if(alive) {
                    old = sample;
                    sample = next;
                    incoming = nil;
                    voice.set(\buf,sample,\t_reset,1,\active,1);
                    recorder = Synth.after(voice,\grainloom_loop_record,
                        [\buf,sample,
                         \inL,context.in_b[0].index,
                         \inR,context.in_b[1].index,
                         \feedback,feedback]);
                    seconds = limit;
                    state = 4;
                    revision = revision+1;
                    retired.add(old);
                    0.1.wait;
                    if(alive and: { retired.includes(old) }) {
                        old.free;
                        retired.remove(old);
                    };
                } {
                    next.free;
                    incoming = nil;
                };
            }.play(SystemClock);
        };
    }

    stopLoop {
        recorder !? { recorder.free; recorder = nil };
    }

    free {
        alive = false;
        task !? { task.stop };
        recorder !? { recorder.free };
        voice !? { voice.free };
        sample !? { sample.free };
        incoming !? { incoming.free };
        retired !? { retired.do(_.free) };
    }
}
