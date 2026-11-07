import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

EXECUTABLE = {"linux64": "chrome", "win64": "chrome.exe"}
REQUIRED_TOOLS = ("apt-cache", "apt-get", "dpkg", "ldd")
FONTCONF = ('<fontconfig><dir prefix="relative">fonts</dir>'
            '<cachedir prefix="xdg">fontconfig</cachedir></fontconfig>')


def download(source, platform_name):
    cache = Path.home() / ".cache" / "vaadmin-browser" / "source"
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / f"{platform_name}.zip"
    if archive.exists() and hashlib.sha256(archive.read_bytes()).hexdigest() == source["sourceSha256"]:
        return archive
    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as handle:
        temporary = Path(handle.name)
    try:
        urllib.request.urlretrieve(source["sourceUrl"], temporary)
        if hashlib.sha256(temporary.read_bytes()).hexdigest() != source["sourceSha256"]:
            raise ValueError("浏览器源包校验失败")
        temporary.replace(archive)
    finally:
        temporary.unlink(missing_ok=True)
    return archive


def archive_root(archive):
    with zipfile.ZipFile(archive) as source:
        roots = {name.split("/")[0] for name in source.namelist() if "/" in name}
    roots = {root for root in roots if root}
    if len(roots) != 1:
        raise ValueError(f"浏览器压缩包根目录不唯一: {sorted(roots)}")
    return roots.pop()


def require_tools():
    missing = [tool for tool in REQUIRED_TOOLS if shutil.which(tool) is None]
    if missing:
        raise SystemExit(f"linux64 运行时必须在 Debian/Ubuntu 上产出，缺少工具: {', '.join(missing)}。")


def package_closure(packages):
    resolved = subprocess.run(
        ["apt-cache", "depends", "--recurse", "--no-recommends", "--no-suggests",
         "--no-conflicts", "--no-breaks", "--no-replaces", "--no-enhances", *packages],
        check=True, capture_output=True, text=True).stdout
    names = {line.strip() for line in resolved.splitlines()
             if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+:_-]*", line.strip())}
    return sorted(names)


def extract_packages(names, work):
    downloads = work / "debs"
    downloads.mkdir()
    for name in names:
        subprocess.run(["apt-get", "download", name], cwd=downloads,
                       check=False, capture_output=True)
    root = work / "root"
    root.mkdir()
    for package in sorted(downloads.glob("*.deb")):
        subprocess.run(["dpkg", "-x", str(package), str(root)], check=True, capture_output=True)
    return root


def linked_libraries(binary, pool):
    environment = {"LD_LIBRARY_PATH": str(pool), "PATH": "/usr/bin:/bin"}
    seen, queue = set(), [binary]
    while queue:
        current = queue.pop()
        output = subprocess.run(["ldd", str(current)], check=False, capture_output=True,
                                text=True, env=environment).stdout
        for line in output.splitlines():
            if "=>" not in line:
                continue
            candidate = line.split("=>", 1)[1].strip().split(" ", 1)[0]
            path = Path(candidate)
            if not path.is_absolute() or path in seen or path.parent != pool:
                continue
            seen.add(path)
            queue.append(path)
    return sorted(seen)


def assemble_linux(browser, build, work):
    require_tools()
    packages = extract_packages(package_closure(build["debianPackages"]), work)
    pool = packages / "usr/lib/x86_64-linux-gnu"
    closure = linked_libraries(browser / EXECUTABLE["linux64"], pool)
    if not closure:
        raise SystemExit("未能解析浏览器依赖库")
    library = browser / "lib"
    library.mkdir()
    for path in closure:
        shutil.copy2(path, library / path.name)
    fonts = browser / "fonts"
    for name in build["keepFonts"]:
        matches = list(packages.glob(f"usr/share/fonts/**/{name}"))
        if not matches:
            raise SystemExit(f"字体缺失: {name}")
        target = fonts / "opentype/noto" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(matches[0], target)
    (browser / "fonts.conf").write_text(FONTCONF, encoding="utf8")
    executable = browser / EXECUTABLE["linux64"]
    executable.chmod(0o755)
    environment = {"LD_LIBRARY_PATH": str(library), "PATH": "/usr/bin:/bin"}
    result = subprocess.run([str(executable), "--version"], check=False, capture_output=True,
                            text=True, env=environment)
    if result.returncode != 0:
        raise SystemExit(f"浏览器无法启动: {result.stderr.strip() or result.stdout.strip()}")
    print(result.stdout.strip())


def trim_locales(browser, keep):
    locales = browser / "locales"
    if not locales.is_dir():
        return
    for entry in list(locales.iterdir()):
        if entry.name.split(".")[0].split("_")[0] not in keep:
            entry.unlink()


parser = argparse.ArgumentParser()
parser.add_argument("--platform", required=True, choices=["linux64", "win64"])
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()

root = Path(__file__).resolve().parent
lock = json.loads((root / "browser-lock.json").read_text())
source = lock["platforms"][args.platform]
build = lock["build"]
archive = download(source, args.platform)
args.output.mkdir(parents=True, exist_ok=True)

with tempfile.TemporaryDirectory(dir=args.output) as staging:
    stage = Path(staging)
    with zipfile.ZipFile(archive) as zip_source:
        zip_source.extractall(stage)
    browser = stage / archive_root(archive)
    if args.platform == "linux64":
        assemble_linux(browser, build, stage)
    trim_locales(browser, set(build["keepLocales"]))
    target = args.output / f"runtime-{args.platform}.zip"
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as result:
        for path in sorted(browser.rglob("*")):
            if path.is_file():
                result.write(path, path.relative_to(browser).as_posix())

digest = hashlib.sha256(target.read_bytes()).hexdigest()
size = round(target.stat().st_size / 1024 / 1024, 1)
print(f"{args.platform} runtime={target.name} size={size}MB sha256={digest}")
