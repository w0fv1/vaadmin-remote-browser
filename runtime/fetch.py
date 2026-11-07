import argparse
import hashlib
import json
import tempfile
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--platform", required=True, choices=["linux64", "win64"])
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()

root = Path(__file__).resolve().parent
lock = json.loads((root / "browser-lock.json").read_text())
entry = lock["platforms"][args.platform]
sha256 = entry["runtimeSha256"]
args.output.mkdir(parents=True, exist_ok=True)
target = args.output / "runtime.zip"
if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == sha256:
    print(f"runtime {args.platform} up to date")
    raise SystemExit(0)
with tempfile.NamedTemporaryFile(dir=args.output, delete=False) as handle:
    temporary = Path(handle.name)
try:
    urllib.request.urlretrieve(entry["runtimeUrl"], temporary)
    if hashlib.sha256(temporary.read_bytes()).hexdigest() != sha256:
        raise ValueError("浏览器运行时下载校验失败")
    temporary.replace(target)
finally:
    temporary.unlink(missing_ok=True)
print(f"runtime {args.platform} fetched -> {target}")
