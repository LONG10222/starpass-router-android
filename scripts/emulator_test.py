#!/usr/bin/env python3
"""Run real WebView/UI regression checks against the loopback LuCI fixture."""
import json,os,subprocess,sys,time,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/emulator';OUT.mkdir(parents=True,exist_ok=True)
ADB=os.environ.get('ADB','adb')
def adb(*args):return subprocess.check_output([ADB,*args],text=True,encoding='utf-8')
def status():return json.load(urllib.request.urlopen('http://127.0.0.1:18086/testing/status'))
server=subprocess.Popen([sys.executable,str(ROOT/'tests/router_fixture.py')])
try:
    for _ in range(50):
        try:status();break
        except OSError:time.sleep(.1)
    apk=next((ROOT/'build/debug').glob('*-test.apk'))
    adb('install','-r',str(apk));adb('shell','pm','clear','org.starpass.router')
    # Match the Redmi K70 Pro's tall display proportions; the emulator is not a HyperOS device.
    adb('shell','wm','size','1440x3200');adb('shell','wm','density','560')
    for phase in ('initial','reopen','expired'):
        if phase=='expired':urllib.request.urlopen('http://127.0.0.1:18086/testing/expire').close()
        adb('shell','am','force-stop','org.starpass.router')
        output=adb('shell','am','instrument','-w','-e','phase',phase,'org.starpass.router/.TestRunner')
        (OUT/f'{phase}.txt').write_text(output,encoding='utf-8');print(output,flush=True)
        assert f'PASS {phase}' in output,output
        current=status();expected=3 if phase=='expired' else 2
        assert current['attempts']==expected and current['logouts']==0,current
    names=adb('shell','run-as','org.starpass.router','ls','files').splitlines()
    for name in names:
        if name.endswith('.png'):
            (OUT/name).write_bytes(subprocess.check_output([ADB,'exec-out','run-as','org.starpass.router','cat','files/'+name]))
    (OUT/'fixture-state.json').write_text(json.dumps(status(),indent=2))
    print('PASS all emulator phases; password attempts bounded, zero logout requests')
finally:
    server.terminate();server.wait(timeout=10)
