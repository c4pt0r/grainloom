"""Compile actual SC engine + offline audio render, without opening audio devices.

Usage: python3 tests/test_dsp.py /path/to/sclang /path/to/scsynth
Uses a temporary CroneEngine test double, not the norns runtime.
"""
import array
import json
import math
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import wave

root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 3:
    raise SystemExit(__doc__)
sclang, scsynth = map(lambda p: str(Path(p).resolve()), sys.argv[1:])
with tempfile.TemporaryDirectory(prefix="grainloom-dsp-") as tmp:
    tmp = Path(tmp)
    classes = tmp / "classes"
    classes.mkdir()
    shutil.copy(root / "tests/CroneEngine.sc.in", classes / "CroneEngine.sc")
    shutil.copy(root / "lib/Engine_Grainloom.sc", classes / "Engine_Grainloom.sc")
    config = tmp / "sclang.yaml"
    config.write_text(f"includePaths:\n  - {classes}\nexcludePaths: []\n")
    source, output, printed = [tmp / n for n in ("source.wav", "out.wav", "print.wav")]
    with wave.open(str(source), "wb") as w:
        w.setparams((1, 2, 48000, 0, "NONE", "not compressed"))
        w.writeframes(b"".join(struct.pack("<h", int(12000 * math.sin(
            2 * math.pi * 220 * i / 48000))) for i in range(48000)))
    script = r'''
(
var e = Engine_Grainloom((server: Server.default));
Routine { e.alloc }.play;
Routine {
    var voice, rec, score, options;
    0.5.wait;
    voice = SynthDescLib.global.at(\grainloom_voice);
    rec = SynthDescLib.global.at(\grainloom_record);
    if(voice.isNil or: {rec.isNil}) { "MISSING_SYNTHDEF".postln; 1.exit };
    "GRAINLOOM_SYNTHDEF_OK".postln;
    options = ServerOptions.new.numOutputBusChannels_(2).numInputBusChannels_(0);
    Score.program = @SCSYNTH@;
    score = Score([
        [0, [\d_recv, voice.def.asBytes]],
        [0, [\d_recv, rec.def.asBytes]],
        [0, [\b_allocRead,0,@SOURCE@]],
        [0, [\b_alloc,1,48000,1]],
        [0.1, [\s_new,\grainloom_voice,1000,0,0,
            \buf,0,\out,0,\tapOut,2,\active,1]],
        [1, [\n_set,1000,\loss,0.8,\wow,1,\flutter,1,\dropout,0.5]],
        [1, [\s_new,\grainloom_record,1001,3,1000,\buf,1,\inL,2,\inR,3]],
        [2.05, [\n_free,1001]],
        [2.1, [\b_write,1,@PRINT@,"WAV","int16",48000,0,0]],
        [2.2, [\n_set,1000,\buf,1,\t_reset,1,\size,0.4,
            \density,40,\crush,1,\glitch,1,\rate,-2]],
        [3, [\n_set,1000,\freeze,1,\reverb,0.8]],
        [4, [\n_set,1000,\active,0]],
        [5, [\n_free,1000]],
        [5.1, [\c_set,0,0]]
    ]);
    score.recordNRT(outputFilePath:@OUTPUT@,sampleRate:48000,
        headerFormat:"WAV",sampleFormat:"int16",options:options,
        action:{ |code| ("NRT_EXIT="++code).postln; code.exit });
}.play;
)
'''
    for key, value in {"SCSYNTH": scsynth, "SOURCE": source,
                       "OUTPUT": output, "PRINT": printed}.items():
        script = script.replace("@" + key + "@", json.dumps(str(value)))
    script_path = tmp / "test.scd"
    script_path.write_text(script)
    result = subprocess.run([sclang, "-D", "-l", str(config), str(script_path)],
                            capture_output=True, text=True, timeout=30)
    log = result.stdout + result.stderr
    assert result.returncode == 0 and "NRT_EXIT=0" in log, log
    assert "ERROR" not in log and "FAILURE" not in log, log
    for path, channels in [(output, 2), (printed, 1)]:
        with wave.open(str(path)) as w:
            assert w.getnchannels() == channels
            assert w.getframerate() == 48000
            samples = array.array("h", w.readframes(w.getnframes()))
        peak = max(map(abs, samples)) / 32768
        rms = math.sqrt(sum(v*v for v in samples) / len(samples)) / 32768
        assert 0.0001 < rms < 0.5, (path.name, rms)
        assert peak < 0.99, (path.name, peak)
        print(f"PASS: {path.name}, peak={peak:.4f}, RMS={rms:.4f}")
    print("PASS: engine class/SynthDef compilation, NRT DSP, internal print, reverse/extreme controls")
