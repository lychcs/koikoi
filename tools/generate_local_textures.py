#!/usr/bin/env python3
"""Generate the six seamless 64x64 world textures with local Retro Diffusion.

The script uses the *same* runtime as the Aseprite extension:

* Python: ``.retrodiffusion_ext/stable-diffusion-aseprite/venv/Scripts/python.exe``
* Inference server: ``.retrodiffusion_ext/stable-diffusion-aseprite/scripts/image_server.py``
* Pixel model: ``models/base/model.pxlm``
* Tiling LoRA: ``models/lora/tiling.pxlm``

It sends ``txt2img`` websocket requests to the local server on port 8765 with
``tile_x``/``tile_y`` enabled and writes the returned PNGs into
``assets/textures/``. No cloud API is used.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import io
import json
import os
import subprocess
import time
from pathlib import Path

try:
    import websockets
except ImportError as exc:  # pragma: no cover - friendly runtime message
    raise SystemExit(
        "The Retro Diffusion venv is missing 'websockets'. Run the extension "
        "setup first, then invoke this script with that venv's python.exe."
    ) from exc

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
EXT_ROOT = ROOT / ".retrodiffusion_ext"
RD_ROOT = EXT_ROOT / "stable-diffusion-aseprite"
VENV_PYTHON = RD_ROOT / "venv" / "Scripts" / "python.exe"
SERVER = RD_ROOT / "scripts" / "image_server.py"
MODEL = RD_ROOT / "models" / "base" / "model.pxlm"
TILING_LORA = RD_ROOT / "models" / "lora" / "tiling.pxlm"
OUTPUT_DIR = ROOT / "assets" / "textures"
SERVER_URL = "ws://127.0.0.1:8765"
MAX_MESSAGE = 100 * 1024 * 1024

TEXTURES = [
    (
        "grass.png",
        "top-down seamless pixel art mossy forest grass with tiny fallen pink "
        "sakura petals, 16-bit ukiyo-e palette, dark emerald and sage",
        12001,
    ),
    (
        "cliff_stone.png",
        "seamless pixel art dark slate cliff rock with subtle moss cracks, "
        "16-bit ukiyo-e style, deep indigo and charcoal",
        12002,
    ),
    (
        "torii_wood.png",
        "seamless pixel art painted vermillion red cedar wood grain, subtle "
        "dark ink lines, 16-bit japanese shrine style",
        12003,
    ),
    (
        "roof_tiles.png",
        "seamless pixel art traditional japanese kawara roof tiles, dark slate "
        "blue-grey with subtle pixel dithering",
        12004,
    ),
    (
        "stone_path.png",
        "seamless pixel art mossy cobblestone shrine pathway, warm grey stone "
        "and dark crevices",
        12005,
    ),
    (
        "sakura_leaves.png",
        "seamless pixel art dense cherry blossom foliage cluster, soft sakura "
        "pink, coral and deep plum shadows",
        12006,
    ),
]

NEGATIVE = (
    "blurry, smooth gradient, photo, photorealism, 3d render, text, watermark, "
    "signature, border, frame, letterbox, visible seam, tiling grid, low contrast, "
    "flat single color"
)


def fail(message: str) -> None:
    raise SystemExit(message)


def selected_textures(only: str | None) -> list[tuple[str, str, int]]:
    if not only:
        return list(TEXTURES)
    wanted = only.strip().lower()
    if not wanted.endswith(".png"):
        wanted += ".png"
    for texture in TEXTURES:
        if texture[0] == wanted:
            return [texture]
    names = ", ".join(name[:-4] for name, _, _ in TEXTURES)
    fail(f"Unknown texture '{only}'. Choose one of: {names}")
    return []


def check_runtime() -> None:
    missing = [p for p in (VENV_PYTHON, SERVER, MODEL, TILING_LORA) if not p.exists()]
    if missing:
        details = "\n".join(f"  - {p}" for p in missing)
        fail(
            "Retro Diffusion local runtime is incomplete:\n"
            f"{details}\n"
            "Install the extension environment first, e.g. from the repository "
            "root run:\n"
            "  .retrodiffusion_ext\\python\\bootstrap.ps1 "
            "-v .retrodiffusion_ext\\stable-diffusion-aseprite\\venv "
            "-m .retrodiffusion_ext\\stable-diffusion-aseprite\\models\\base "
            "-l .retrodiffusion_ext\\stable-diffusion-aseprite\\models\\lora "
            "-sd false -pix true -pg false -f false"
        )


async def wait_for_server(timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            async with websockets.connect(SERVER_URL, max_size=MAX_MESSAGE) as socket:
                # The server has no "check" case; its handshake is "connected"
                # with the client version matching expectedVersion.
                await socket.send(json.dumps({
                    "action": "connected",
                    "value": {
                        "background": True,
                        "play_sound": False,
                        "version": "lite-1.6.0",
                    },
                }))
                response = json.loads(await asyncio.wait_for(socket.recv(), timeout=5))
                if response.get("action") == "connected":
                    return
        except Exception as exc:  # server may still be importing torch
            last_error = exc
            await asyncio.sleep(1.0)
    fail(f"Local image server did not become ready within {timeout:.0f}s: {last_error}")


def start_server(timeout: float) -> tuple[subprocess.Popen[bytes], io.TextIOWrapper]:
    log_path = EXT_ROOT / "image_server.out.log"
    log = log_path.open("w", encoding="utf-8", errors="replace")
    creationflags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    environment = os.environ.copy()
    # image_server.py prints a Unicode logo through Rich. Redirected Windows
    # stdout defaults to cp1252 and would crash on the block characters.
    environment["PYTHONIOENCODING"] = "utf-8"
    environment["PYTHONUTF8"] = "1"
    environment["NO_COLOR"] = "1"
    environment["TERM"] = "dumb"
    process = subprocess.Popen(
        [str(VENV_PYTHON), str(SERVER)],
        cwd=str(RD_ROOT),
        env=environment,
        stdin=subprocess.DEVNULL,
        stdout=log,
        stderr=subprocess.STDOUT,
        creationflags=creationflags,
    )
    print(f"Started local Retro Diffusion server (PID {process.pid}); log: {log_path}",
        flush=True)
    try:
        asyncio.run(wait_for_server(timeout))
    except BaseException:
        process.terminate()
        log.close()
        raise
    return process, log


def request_for(prompt: str, seed: int) -> dict:
    return {
        "action": "txt2img",
        "value": {
            "prompt": prompt,
            "negative": NEGATIVE,
            "use_ella": False,
            "translate": False,
            "prompt_tuning": True,
            "width": 512,
            "height": 512,
            "pixel_size": 8,
            "quality": 5.0,
            "scale": 6.5,
            "lighting": {
                "apply": False,
                "x": 25,
                "y": 0,
                "z": 0,
                "3d": {"x": 0, "y": 0, "z": 0},
            },
            "composition": {
                "hue": 0,
                "tint": 50,
                "saturation": 50,
                "brightness": 70,
                "contrast": 50,
                "outline": 50,
            },
            "seed": seed,
            "generations": 1,
            "max_batch_size": 1,
            "model": {
                "file": str(MODEL).replace("\\", "/"),
                "device": "cuda",
                "precision": "fp16",
                "optimized": False,
            },
            "loras": [
                {
                    "file": str(TILING_LORA).replace("\\", "/"),
                    "weight": 85,
                }
            ],
            "tile_x": True,
            "tile_y": True,
            "use_pixelvae": True,
            "send_progress": False,
            "post_process": False,
            "background": False,
            "play_sound": False,
        },
    }


async def generate_one(prompt: str, seed: int, timeout: float) -> Image.Image:
    async with websockets.connect(
        SERVER_URL,
        max_size=MAX_MESSAGE,
        ping_interval=20,
        ping_timeout=180,
    ) as socket:
        await socket.send(json.dumps({
            "action": "connected",
            "value": {
                "background": True,
                "play_sound": False,
                "version": "lite-1.6.0",
            },
        }))
        await asyncio.wait_for(socket.recv(), timeout=10)
        await socket.send(json.dumps(request_for(prompt, seed)))
        while True:
            raw = await asyncio.wait_for(socket.recv(), timeout=timeout)
            message = json.loads(raw)
            action = message.get("action")
            if action == "error":
                fail(f"Local generator returned an error for seed {seed}.")
            if action == "display_title":
                print(f"  {message.get('value', {}).get('text', '')}", flush=True)
            if action == "returning" and message.get("type") == "txt2img":
                images = message.get("value", {}).get("images", [])
                if not images:
                    fail(f"Local generator returned no images for seed {seed}.")
                payload = base64.b64decode(images[0]["image"])
                return Image.open(io.BytesIO(payload)).convert("RGB")


def edge_mismatch(image: Image.Image) -> float:
    """Mean absolute RGB difference across the wrapped edges (lower is better)."""
    pixels = image.load()
    width, height = image.size
    total = 0
    count = 0
    for y in range(height):
        left = pixels[0, y]
        right = pixels[width - 1, y]
        total += sum(abs(a - b) for a, b in zip(left, right))
        count += 3
    for x in range(width):
        top = pixels[x, 0]
        bottom = pixels[x, height - 1]
        total += sum(abs(a - b) for a, b in zip(top, bottom))
        count += 3
    return total / max(1, count)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="overwrite existing PNGs")
    parser.add_argument(
        "--only",
        metavar="NAME",
        help="generate only one texture, e.g. grass, cliff_stone or torii_wood",
    )
    parser.add_argument(
        "--keep-server",
        action="store_true",
        help="leave the started image server running after this invocation",
    )
    parser.add_argument(
        "--server-timeout",
        type=float,
        default=240.0,
        help="seconds to wait for image_server.py to import torch and listen",
    )
    parser.add_argument(
        "--generation-timeout",
        type=float,
        default=600.0,
        help="seconds allowed per texture generation",
    )
    args = parser.parse_args()

    check_runtime()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    textures = selected_textures(args.only)

    server = None
    log = None
    generated = 0
    try:
        if not os.environ.get("RD_SERVER_ALREADY_RUNNING"):
            server, log = start_server(args.server_timeout)
        else:
            asyncio.run(wait_for_server(30.0))

        for filename, prompt, seed in textures:
            destination = OUTPUT_DIR / filename
            if destination.exists() and not args.force:
                print(f"Skipping existing {destination} (use --force to regenerate)",
                    flush=True)
                continue
            print(f"Generating {filename} (64x64, seamless)...", flush=True)
            image = asyncio.run(generate_one(prompt, seed, args.generation_timeout))
            if image.size != (64, 64):
                fail(
                    f"{filename}: generator returned {image.width}x{image.height}, "
                    "expected 64x64. Check the model/pixel-size settings."
                )
            image.save(destination, format="PNG")
            generated += 1
            print(
                f"  wrote {destination} "
                f"(edge mismatch {edge_mismatch(image):.1f}/255)",
                flush=True,
            )
    finally:
        if server is not None and not args.keep_server:
            server.terminate()
            try:
                server.wait(timeout=15)
            except subprocess.TimeoutExpired:
                server.kill()
        if log is not None:
            log.close()

    if args.only is None:
        missing = [name for name, _, _ in TEXTURES
            if not (OUTPUT_DIR / name).exists()]
        if missing:
            fail("Missing generated textures: " + ", ".join(missing))
        print(f"All six seamless 64x64 textures are in {OUTPUT_DIR}", flush=True)
    else:
        if generated:
            print(f"Generated {textures[0][0]} (64x64, seamless)", flush=True)
        else:
            print(f"{textures[0][0]} already exists (use --force to regenerate)",
                flush=True)


if __name__ == "__main__":
    main()
