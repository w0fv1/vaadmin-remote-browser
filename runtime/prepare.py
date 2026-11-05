import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import urllib.request
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--platform", required=True, choices=["win64", "linux64"])
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--engine", default="podman")
args = parser.parse_args()
root = Path(__file__).resolve().parent
lock = json.loads((root / "browser-lock.json").read_text())
platform = lock["platforms"][args.platform]
cache = Path.home() / ".cache" / "vaadmin-browser" / lock["version"]
cache.mkdir(parents=True, exist_ok=True)
archive = cache / (args.platform + ".zip")
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != platform["sha256"]:
    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as temp:
        temporary = Path(temp.name)
    try:
        urllib.request.urlretrieve(platform["url"], temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != platform["sha256"]:
            raise ValueError("Browser archive checksum mismatch")
        temporary.replace(archive)
    finally:
        temporary.unlink(missing_ok=True)
args.output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=args.output) as staging:
    stage = Path(staging)
    with zipfile.ZipFile(archive) as source:
        source.extractall(stage)
    browser = stage / ("chrome-" + args.platform)
    if args.platform == "linux64":
        engine = args.engine.split()
        image = "vaadmin-browser-runtime:" + lock["version"]
        subprocess.run(engine + ["build", "--build-arg", "BASE=" + lock["linuxBase"],
                                 "-t", image, "-f", str(root / "Containerfile"), str(root)], check=True)
        container = subprocess.check_output(engine + ["create", image], text=True).strip()
        try:
            subprocess.run(engine + ["cp", container + ":/runtime/.", str(browser)], check=True)
        finally:
            subprocess.run(engine + ["rm", container], check=True)
    (browser / "fonts.conf").write_text(
        '<fontconfig><dir prefix="relative">fonts</dir><cachedir prefix="xdg">fontconfig</cachedir></fontconfig>',
        encoding="utf8")
    target = args.output / "runtime.zip"
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as result:
        for path in sorted(browser.rglob("*")):
            if path.is_file():
                result.write(path, path.relative_to(browser).as_posix())
digest = hashlib.sha256(target.read_bytes()).hexdigest()
(args.output / "runtime.properties").write_text(
    "platform=" + args.platform + "\nversion=" + lock["version"] + "\nsha256=" + digest + "\n",
    encoding="utf8")
