"""Use Android's trusted shell controller; an ordinary audit APK has read-only Media3 access."""
import argparse
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('command', choices=['next', 'previous', 'play', 'pause'])
parser.add_argument('--sdk', default='F:/pixel center/android-sdk')
args = parser.parse_args()
adb = str(Path(args.sdk) / 'platform-tools/adb.exe')
process = subprocess.Popen([adb, 'shell', 'cmd', 'media_session', 'monitor', 'androidx.media3.session.id.'],
                           stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           encoding='utf-8', errors='replace')
process.stdin.write(args.command + '\n')
process.stdin.flush()
time.sleep(1)
output, _ = process.communicate('q\n', timeout=10)
if process.returncode:
    raise SystemExit(output)
print(output)
