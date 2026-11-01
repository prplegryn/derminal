#!/usr/bin/env python3
"""GitHub delivery. Credentials never enter git URLs, files in the repo, or logs."""
import base64
import ctypes
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
USER=os.environ.get('GITHUB_USERNAME','prplegryn')
REPO=USER+'/derminal'
PRIVATE_TOKEN=Path.home()/'.config/derminal/github-token'
TOKEN=PRIVATE_TOKEN.read_text().strip() if PRIVATE_TOKEN.exists() else os.environ['GH_TOKEN']
SIGNING=Path.home()/'.local/share/derminal-signing'

class SafeRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  result=super().redirect_request(req,fp,code,msg,headers,newurl)
  if result and urllib.parse.urlparse(newurl).hostname!='api.github.com':result.remove_header('Authorization')
  return result

OPENER=urllib.request.build_opener(SafeRedirect())
def api(path,method='GET',data=None,binary=False,content_type=None,accept=None):
 url=path if path.startswith('https://') else 'https://api.github.com'+path
 body=data if isinstance(data,bytes) else json.dumps(data).encode() if data is not None else None
 headers={'Authorization':'Bearer '+TOKEN,'User-Agent':'derminal-development','Accept':accept or ('application/octet-stream' if binary else 'application/vnd.github+json'),'X-GitHub-Api-Version':'2022-11-28'}
 if body is not None:headers['Content-Type']=content_type or 'application/json'
 try:
  with OPENER.open(urllib.request.Request(url,headers=headers,data=body,method=method),timeout=240) as r:
   raw=r.read();return raw if binary else json.loads(raw) if raw else None
 except urllib.error.HTTPError as e:raise RuntimeError('GitHub '+str(e.code)+': '+e.read().decode(errors='replace')[:600]) from None

def signing():
 SIGNING.mkdir(parents=True,exist_ok=True);SIGNING.chmod(0o700)
 cfg=SIGNING/'signing.json';key=SIGNING/'derminal.p12'
 if cfg.exists():password=json.loads(cfg.read_text())['password']
 else:password=secrets.token_urlsafe(36);cfg.write_text(json.dumps({'password':password,'alias':'derminal'}));cfg.chmod(0o600)
 if not key.exists():
  env=os.environ.copy();env['DERMINAL_SIGNING_PASSWORD']=password
  subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-storetype','PKCS12','-alias','derminal','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=derminal,O=derminal','-storepass:env','DERMINAL_SIGNING_PASSWORD','-keypass:env','DERMINAL_SIGNING_PASSWORD'],env=env,capture_output=True,check=True);key.chmod(0o600)
 public=api('/repos/'+REPO+'/actions/secrets/public-key');pk=base64.b64decode(public['key'])
 sodium=ctypes.CDLL('libsodium.so');sodium.sodium_init()
 sodium.crypto_box_seal.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_ulonglong,ctypes.c_void_p];sodium.crypto_box_seal.restype=ctypes.c_int
 for name,value in {'DERMINAL_KEYSTORE_BASE64':base64.b64encode(key.read_bytes()).decode(),'DERMINAL_SIGNING_PASSWORD':password}.items():
  raw=value.encode();cipher=ctypes.create_string_buffer(len(raw)+48)
  assert sodium.crypto_box_seal(cipher,raw,len(raw),pk)==0
  api('/repos/'+REPO+'/actions/secrets/'+name,'PUT',{'encrypted_value':base64.b64encode(cipher.raw).decode(),'key_id':public['key_id']})
 print('Stable signing key installed as encrypted repository secrets')

def push():
 subprocess.run(['git','config','user.name',USER],cwd=ROOT,check=True);subprocess.run(['git','config','user.email',USER+'@users.noreply.github.com'],cwd=ROOT,check=True)
 remote='https://github.com/'+REPO+'.git'
 exists=subprocess.run(['git','remote','get-url','origin'],cwd=ROOT,capture_output=True).returncode==0
 subprocess.run(['git','remote','set-url' if exists else 'add','origin',remote],cwd=ROOT,check=True)
 with tempfile.TemporaryDirectory(prefix='derminal-git-') as folder:
  helper=Path(folder)/'askpass';helper.write_text('#!/bin/sh\ncase "$1" in *Username*) printf "%s" x-access-token ;; *) printf "%s" "$GH_TOKEN" ;; esac\n');helper.chmod(0o700)
  env=os.environ.copy();env.update({'GH_TOKEN':TOKEN,'GIT_ASKPASS':str(helper),'GIT_TERMINAL_PROMPT':'0'})
  subprocess.run(['git','push','-u','origin','main'],cwd=ROOT,env=env,check=True)

def upload_runtime():
 sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 release=api('/repos/'+REPO+'/releases','POST',{'tag_name':'runtime-v1','target_commitish':sha,'name':'Offline Debian 13 ARM64 runtime','prerelease':True,'body':'Checksummed offline Debian 13.7 system. Native and Debian corresponding sources, package inventory and build recipes are supplied alongside the binary bundle. Exact hashes are pinned in runtime-lock.json.'})
 for filename in ['derminal-runtime-arm64-v1.zip','derminal-runtime-sources.tar.gz']:
  p=ROOT/'.delivery'/filename
  result=api(release['upload_url'].split('{')[0]+'?name='+urllib.parse.quote(filename),'POST',p.read_bytes(),content_type='application/octet-stream');print('Uploaded',result['name'],result['size'])

def dispatch():
 sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 api('/repos/'+REPO+'/actions/workflows/android.yml/dispatches','POST',{'ref':'main'})
 print('Dispatched Actions for',sha)

def status():
 runs=api('/repos/'+REPO+'/actions/workflows/android.yml/runs?per_page=5')['workflow_runs']
 print(json.dumps([{k:r[k] for k in ['id','head_sha','status','conclusion','html_url','run_number']} for r in runs],indent=2))

def deliver(run_id):
 run=api('/repos/'+REPO+'/actions/runs/'+run_id)
 assert run['status']=='completed' and run['conclusion']=='success', 'Exact run must succeed before delivery'
 release=api('/repos/'+REPO+'/releases/tags/v1.0.0-build.'+str(run['run_number']))
 out=Path.home()/'download/apksync';out.mkdir(parents=True,exist_ok=True)
 assets={a['name']:a for a in release['assets']}
 for name in ['derminal-arm64.apk','derminal-arm64.apk.sha256','derminal-build.json','derminal-signature.txt']:
  a=assets[name];payload=api('/repos/'+REPO+'/releases/assets/'+str(a['id']),binary=True)
  target=out/name;tmp=out/(name+'.part');tmp.write_bytes(payload);tmp.replace(target)
 expected=(out/'derminal-arm64.apk.sha256').read_text().split()[0]
 actual=hashlib.sha256((out/'derminal-arm64.apk').read_bytes()).hexdigest()
 assert actual==expected,'Downloaded APK checksum mismatch'
 metadata=json.loads((out/'derminal-build.json').read_text());assert metadata['commit']==run['head_sha']
 print('Downloaded verified Release:',release['html_url']);print(out/'derminal-arm64.apk');print('SHA256',actual)

if __name__=='__main__':
 cmd=sys.argv[1]
 if cmd=='signing':signing()
 elif cmd=='push':push()
 elif cmd=='runtime':upload_runtime()
 elif cmd=='dispatch':dispatch()
 elif cmd=='status':status()
 elif cmd=='deliver':deliver(sys.argv[2])
 elif cmd=='logs':
  raw=api('/repos/'+REPO+'/actions/runs/'+sys.argv[2]+'/logs',binary=True,accept='application/vnd.github+json');p=ROOT/'.runtime'/('actions-'+sys.argv[2]+'.zip');p.write_bytes(raw);print(p)
 else:raise SystemExit('Unknown command')
