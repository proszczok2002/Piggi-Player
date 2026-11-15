"""Run the external audit instrumentation without suppressing accessibility services."""
import argparse
import datetime
import json
from pathlib import Path
import shlex
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('op', choices=['dump', 'click', 'focus', 'scroll', 'set', 'key', 'media', 'action'])
parser.add_argument('--text')
parser.add_argument('--value')
parser.add_argument('--direction')
parser.add_argument('--key')
parser.add_argument('--hold')
parser.add_argument('--command')
parser.add_argument('--position')
parser.add_argument('--index')
parser.add_argument('--out')
parser.add_argument('--sdk', default='F:/pixel center/android-sdk')
args = parser.parse_args()
command = ['am', 'instrument', '-w']
for key in ['op', 'text', 'value', 'direction', 'key', 'hold', 'command', 'position', 'index']:
    value = getattr(args, key)
    if value is not None:
        command += ['-e', key, value]
command += ['pl.proszczok.piggiaudit/.AuditInstrumentation']
result = subprocess.run([str(Path(args.sdk) / 'platform-tools/adb.exe'), 'shell', shlex.join(command)],
                        capture_output=True, encoding='utf-8', errors='replace', check=True)
out = Path(__file__).resolve().parents[2] / 'app/build/device-audit/evidence'
out.mkdir(exist_ok=True)
record = {'time': datetime.datetime.now().isoformat(), 'args': vars(args), 'output': result.stdout}
with (out / 'commands.jsonl').open('a', encoding='utf-8') as file:
    file.write(json.dumps(record, ensure_ascii=False) + '\n')
if 'INSTRUMENTATION_CODE: -1' not in result.stdout:
    raise SystemExit(result.stdout + result.stderr)
if args.op == 'dump':
    raw = next(line.partition('nodes=')[2] for line in result.stdout.splitlines()
               if line.startswith('INSTRUMENTATION_RESULT: nodes='))
    nodes = json.loads(raw)
    (out / (args.out or 'latest.json')).write_text(json.dumps(nodes, ensure_ascii=False, indent=2), encoding='utf-8')
    for node in nodes:
        if node['visible'] and (node['text'] != 'null' or node['description'] != 'null'):
            print(json.dumps({key: node[key] for key in ['text', 'description', 'class', 'state', 'bounds', 'clickable', 'focused']}, ensure_ascii=False))
else:
    print(result.stdout)
