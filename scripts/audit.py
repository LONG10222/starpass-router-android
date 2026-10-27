#!/usr/bin/env python3
"""Audit the distributable APK, not the private instrumentation build."""
from pathlib import Path
import os,re,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1]
apk=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/release/StarPass-Router-1.0.0.apk'
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
with zipfile.ZipFile(apk) as archive:
    dex=archive.read('classes.dex')
    for forbidden in (b'org/starpass/router/TestRunner',b'test-profile.json',b'example!x'):
        assert forbidden not in dex,'Test or private provisioning data found'
    assert {'assets/login.js','assets/compat.js'}<=set(archive.namelist())
manifest=subprocess.check_output([str(sdk/'build-tools/36.1.0/aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert 'E: instrumentation' not in manifest
for attribute in ('debuggable','allowBackup','fullBackupContent'):
    assert re.search(r':'+attribute+r'\([^)]*\)=\(type 0x12\)0x0\b',manifest),attribute
assert manifest.count('E: uses-permission')==1 and 'android.permission.INTERNET' in manifest
assert manifest.count('E: activity ')==1 and 'E: service' not in manifest and 'E: receiver' not in manifest and 'E: provider' not in manifest
print('APK audit passed: release-only code, no embedded credentials, no backup, INTERNET only')
