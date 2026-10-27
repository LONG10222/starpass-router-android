#!/usr/bin/env python3
"""Build a small signed Android APK with the installed SDK and JDK, offline."""
import argparse,hashlib,json,os,secrets,shutil,subprocess,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def run(args,**kwargs):subprocess.run([str(a) for a in args],check=True,**kwargs)
def build(debug):
    sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
    java=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
    tools=sdk/'build-tools/36.1.0';platform=sdk/'platforms/android-36.1/android.jar'
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
    run([java/'bin/javac','--release','17','-encoding','UTF-8','-classpath',platform,'-d',classes]+sources,env=env)
    compiled=folder/'resources.zip'
    run([tools/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',compiled],env=env)
    unsigned=folder/'unsigned.apk'
    run([tools/'aapt2','link','-I',platform,'--manifest',folder/'AndroidManifest.xml','--version-code','1','--version-name','1.0.0','-A',ROOT/'app/src/main/assets','-o',unsigned,compiled],env=env)
    run([tools/'d8','--release','--min-api','26','--lib',platform,'--output',folder]+list(classes.rglob('*.class')),env=env)
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as archive:
        for dex in folder.glob('classes*.dex'):archive.write(dex,dex.name)
    aligned=folder/'aligned.apk';run([tools/'zipalign','-f','4',unsigned,aligned],env=env)
    signing=ROOT/'.signing';signing.mkdir(exist_ok=True,mode=0o700);os.chmod(signing,0o700)
    metadata=signing/'key.json';keystore=signing/'starpass-router.jks'
    if not metadata.exists():
        metadata.write_text(json.dumps({'password':secrets.token_urlsafe(32)}));os.chmod(metadata,0o600)
    password=json.loads(metadata.read_text())['password'];env['ROUTER_KEYSTORE_PASSWORD']=password
    if not keystore.exists():
        run([java/'bin/keytool','-genkeypair','-noprompt','-keystore',keystore,'-storepass:env','ROUTER_KEYSTORE_PASSWORD','-keypass:env','ROUTER_KEYSTORE_PASSWORD','-alias','starpass-router','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=StarPass Router Local Build'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        os.chmod(keystore,0o600)
    output=folder/('StarPass-Router-1.0.0-test.apk' if debug else 'StarPass-Router-1.0.0.apk')
    run([tools/'apksigner','sign','--ks',keystore,'--ks-key-alias','starpass-router','--ks-pass','env:ROUTER_KEYSTORE_PASSWORD','--out',output,aligned],env=env)
    run([tools/'apksigner','verify','--verbose',output],env=env)
    print(hashlib.sha256(output.read_bytes()).hexdigest(),output)
    return output
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--debug',action='store_true');build(p.parse_args().debug)
