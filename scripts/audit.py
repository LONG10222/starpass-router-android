#!/usr/bin/env python3
"""Audit the distributable APK, not the private instrumentation build."""
from pathlib import Path
import os,re,subprocess,sys,zipfile
root=Path(__file__).resolve().parents[1]
apk=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/release/StarPass-Router-1.1.0.apk'
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
with zipfile.ZipFile(apk) as archive:
    dex=archive.read('classes.dex')
    for forbidden in (b'org/starpass/router/TestRunner',b'test-profile.json',b'example!x'):
        assert forbidden not in dex,'Test or private provisioning data found'
    assert {'assets/login.js','assets/compat.js'}<=set(archive.namelist())
    assert not any(name.endswith('.apk') for name in archive.namelist()),'Test update fixture in production APK'
toolsets=sorted((sdk/'build-tools').glob('*'),key=lambda p:tuple(int(n) for n in re.findall(r'\d+',p.name)))
manifest=subprocess.check_output([str(toolsets[-1]/('aapt.exe' if os.name=='nt' else 'aapt')),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True,encoding='utf-8')
assert 'E: instrumentation' not in manifest
for attribute in ('debuggable','allowBackup','fullBackupContent'):
    assert re.search(r':'+attribute+r'\([^)]*\)=\(type 0x12\)0x0\b',manifest),attribute
assert manifest.count('E: uses-permission')==2 and 'android.permission.INTERNET' in manifest and 'android.permission.REQUEST_INSTALL_PACKAGES' in manifest
assert manifest.count('E: activity ')==1 and 'E: service' not in manifest and 'E: receiver' not in manifest
assert manifest.count('E: provider')==1 and 'org.starpass.router.updates' in manifest
provider=manifest.split('E: provider',1)[1]
assert re.search(r':exported\([^)]*\)=\(type 0x12\)0x0\b',provider)
assert re.search(r':grantUriPermissions\([^)]*\)=\(type 0x12\)0xffffffff\b',provider)
print('APK audit passed: release-only code, no embedded credentials, no backup, private read-only update provider')
