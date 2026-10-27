#!/usr/bin/env python3
"""Build a small signed Android APK with the installed SDK and JDK, offline."""
import argparse,hashlib,json,os,re,secrets,shutil,subprocess,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def run(args,**kwargs):subprocess.run([str(a) for a in args],check=True,**kwargs)
def build(debug,require_existing_key=False):
    sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
    java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
    platforms=sorted((sdk/'platforms').glob('android-36*/android.jar'))
    toolsets=sorted((sdk/'build-tools').glob('*'),key=lambda p:tuple(int(n) for n in re.findall(r'\d+',p.name)))
    if not platforms or not toolsets:raise SystemExit('Install Android platform 36 and build-tools, then set ANDROID_HOME')
    tools=toolsets[-1];platform=platforms[-1]
    gradle=(ROOT/'app/build.gradle').read_text()
    version=re.search(r"versionName '([^']+)'",gradle).group(1)
    code=re.search(r'versionCode (\d+)',gradle).group(1)
    def jdk(name):return java/'bin'/(name+('.exe' if os.name=='nt' else ''))
    def native(name):return tools/(name+('.exe' if os.name=='nt' else ''))
    env={**os.environ,'JAVA_HOME':str(java),'PATH':str(java/'bin')+os.pathsep+os.environ['PATH']}
    folder=ROOT/'build'/('debug' if debug else 'release');folder.mkdir(parents=True,exist_ok=True)
    classes=folder/'classes';generated=folder/'generated/org/starpass/router';classes.mkdir(exist_ok=True);generated.mkdir(parents=True,exist_ok=True)
    # Do not retain classes of a previous source set in a production build.
    for file in classes.rglob('*.class'):file.unlink()
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml');node=manifest.getroot();node.set('package','org.starpass.router')
    uses=ET.SubElement(node,'uses-sdk');uses.set(A+'minSdkVersion','26');uses.set(A+'targetSdkVersion','36')
    app=node.find('application');app.set(A+'debuggable','true' if debug else 'false')
    if debug:
        runner=ET.SubElement(node,'instrumentation');runner.set(A+'name','org.starpass.router.TestRunner');runner.set(A+'targetPackage','org.starpass.router')
    manifest.write(folder/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    (generated/'BuildConfig.java').write_text('package org.starpass.router; public final class BuildConfig {public static final boolean DEBUG='+str(debug).lower()+';}\n')
    sources=list((ROOT/'app/src/main/java').rglob('*.java'))+[generated/'BuildConfig.java']
    if debug:sources+=list((ROOT/'tests').rglob('*.java'))
    run([jdk('javac'),'--release','17','-encoding','UTF-8','-classpath',platform,'-d',classes]+sources,env=env)
    compiled=folder/'resources.zip'
    run([native('aapt2'),'compile','--dir',ROOT/'app/src/main/res','-o',compiled],env=env)
    unsigned=folder/'unsigned.apk'
    run([native('aapt2'),'link','-I',platform,'--manifest',folder/'AndroidManifest.xml','--version-code',code,'--version-name',version,'-A',ROOT/'app/src/main/assets','-o',unsigned,compiled],env=env)
    run([jdk('java'),'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--release','--min-api','26','--lib',platform,'--output',folder]+list(classes.rglob('*.class')),env=env)
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in folder.glob('classes*.dex'):archive.write(dex,dex.name)
    aligned=folder/'aligned.apk';run([native('zipalign'),'-f','4',unsigned,aligned],env=env)
    signing=Path(os.environ.get('ROUTER_SIGNING_DIR',str(ROOT/'.signing')))
    if require_existing_key and not all((signing/name).is_file() for name in ('key.json','starpass-router.jks')):raise SystemExit('Release requires the existing signing key; refusing to create a new identity')
    signing.mkdir(exist_ok=True,mode=0o700);os.chmod(signing,0o700)
    metadata=signing/'key.json';keystore=signing/'starpass-router.jks'
    if not metadata.exists():
        metadata.write_text(json.dumps({'password':secrets.token_urlsafe(32)}));os.chmod(metadata,0o600)
    password=json.loads(metadata.read_text())['password'];env['ROUTER_KEYSTORE_PASSWORD']=password
    if not keystore.exists():
        run([jdk('keytool'),'-genkeypair','-noprompt','-keystore',keystore,'-storepass:env','ROUTER_KEYSTORE_PASSWORD','-keypass:env','ROUTER_KEYSTORE_PASSWORD','-alias','starpass-router','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=StarPass Router Local Build'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        os.chmod(keystore,0o600)
    output=folder/(f'StarPass-Router-{version}-test.apk' if debug else f'StarPass-Router-{version}.apk')
    signer=[jdk('java'),'-jar',tools/'lib/apksigner.jar']
    run(signer+['sign','--ks',keystore,'--ks-key-alias','starpass-router','--ks-pass','env:ROUTER_KEYSTORE_PASSWORD','--out',output,aligned],env=env)
    run(signer+['verify','--verbose',output],env=env)
    print(hashlib.sha256(output.read_bytes()).hexdigest(),output)
    return output
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--debug',action='store_true');p.add_argument('--require-existing-key',action='store_true');args=p.parse_args();build(args.debug,args.require_existing_key)
