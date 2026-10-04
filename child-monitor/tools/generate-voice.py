"""从文案目录生成固定女声提示；全部解码核验通过后才替换应用资源。"""

import argparse
import array
import asyncio
import hashlib
import importlib.metadata
import json
import math
from pathlib import Path
import shutil
import subprocess
import tempfile

import edge_tts

ROOT = Path(__file__).resolve().parents[1]
VOICE_DIR = ROOT / "androidApp/src/main/assets/voice"
VOICE = "zh-CN-XiaoxiaoNeural"
RATE = "-8%"
PITCH = "+0Hz"
SAMPLE_RATE = 24000
AUDIO_FILTER = "loudnorm=I=-20:TP=-3:LRA=7"


def inspect_audio(ffmpeg, path):
    decoded = subprocess.run(
        [ffmpeg, "-v", "error", "-xerror", "-i", str(path), "-map", "0:a:0",
         "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "-"],
        check=True, capture_output=True,
    ).stdout
    samples = array.array("h")
    samples.frombytes(decoded)
    duration = len(samples) / SAMPLE_RATE
    if not 0.5 <= duration <= 20:
        raise ValueError(f"Invalid audio duration: {path.name}: {duration}")
    rms = math.sqrt(sum(value * value for value in samples) / len(samples)) / 32768
    if rms <= 0 or 20 * math.log10(rms) < -50:
        raise ValueError(f"Empty or silent audio: {path.name}")
    peak = max(abs(value) for value in samples) / 32768
    return {
        "durationSeconds": round(duration, 3),
        "rmsDbfs": round(20 * math.log10(rms), 2),
        "peakDbfs": round(20 * math.log10(peak), 2),
        "bytes": path.stat().st_size,
    }


async def generate(ffmpeg):
    manifest = json.loads((VOICE_DIR / "manifest.json").read_text())
    catalog = json.loads((ROOT / "copy/catalog.json").read_text())
    texts = {entry["platforms"]["android"]: entry["values"]["zh-Hans"] for entry in catalog["entries"]}
    clips = {audio_id: texts[audio_id] for audio_id in manifest["clips"]}
    details = {}
    hashes = {}
    # 中间文件留在临时目录；生成或转码失败不覆盖已有片段。
    with tempfile.TemporaryDirectory(prefix="child-monitor-voice-") as temporary:
        directory = Path(temporary)
        for audio_id, text in clips.items():
            mp3 = directory / f"{audio_id}.mp3"
            output = directory / f"{audio_id}.m4a"
            await edge_tts.Communicate(text, VOICE, rate=RATE, pitch=PITCH).save(str(mp3))
            subprocess.run(
                [ffmpeg, "-v", "error", "-xerror", "-y", "-i", str(mp3),
                 "-map", "0:a:0", "-af", AUDIO_FILTER, "-ar", str(SAMPLE_RATE), "-ac", "1",
                 "-c:a", "aac", "-b:a", "64k", "-movflags", "+faststart", str(output)],
                check=True,
            )
            details[output.name] = inspect_audio(ffmpeg, output)
            hashes[output.name] = hashlib.sha256(output.read_bytes()).hexdigest()
            print(f"Generated {output.name}: {details[output.name]['durationSeconds']} s", flush=True)
        if len(set(hashes.values())) != len(clips):
            raise ValueError("Duplicate audio content in different reminder clips")
        manifest.update({
            "clips": clips,
            "source": "Microsoft Edge online TTS / zh-CN-XiaoxiaoNeural，预先生成普通话女声；运行时离线播放，不使用 TTS",
            "encoding": "AAC-LC / M4A / 64 kbps / 24 kHz / mono",
            "generation": {
                "provider": "edge-tts",
                "providerVersion": importlib.metadata.version("edge-tts"),
                "voice": VOICE,
                "rate": RATE,
                "pitch": PITCH,
                "ffmpegFilter": AUDIO_FILTER,
                "ffmpegVersion": subprocess.check_output([ffmpeg, "-version"], text=True).splitlines()[0],
                "script": "tools/generate-voice.py",
            },
            "sha256": hashes,
            "audioInfo": details,
        })
        for name in hashes:
            shutil.copyfile(directory / name, VOICE_DIR / name)
        (VOICE_DIR / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Updated {len(clips)} verified reminder clips", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    asyncio.run(generate(args.ffmpeg))
