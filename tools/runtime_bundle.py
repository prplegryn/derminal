#!/usr/bin/env python3
"""Bundle or restore exact offline assets. Never silently replaces lock hashes."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
FILES=['app/src/main/assets/debian.zip','app/src/main/assets/runtime.json']+[str(p.relative_to(ROOT)) for p in sorted((ROOT/'app/src/main/jniLibs/arm64-v8a').glob('*.so'))]

def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while chunk:=f.read(1024*1024):h.update(chunk)
 return h.hexdigest()

def main():
 action=sys.argv[1];path=Path(sys.argv[2])
 if action=='create':
  path.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(path,'w',zipfile.ZIP_STORED) as archive:
   for name in FILES:archive.write(ROOT/name,name)
  (ROOT/'runtime-lock.json').write_text(json.dumps({'tag':'runtime-v1','asset':path.name,'sha256':sha(path),'files':{name:sha(ROOT/name) for name in FILES}},indent=2)+'\n')
  print('Runtime bundle created',path.stat().st_size)
 elif action=='restore':
  lock=json.loads((ROOT/'runtime-lock.json').read_text())
  if sha(path)!=lock['sha256']:raise ValueError('Runtime bundle checksum mismatch')
  with zipfile.ZipFile(path) as archive:
   if set(archive.namelist())!=set(lock['files']):raise ValueError('Unexpected bundle files')
   for name,digest in lock['files'].items():
    data=archive.read(name)
    if hashlib.sha256(data).hexdigest()!=digest:raise ValueError('Checksum mismatch: '+name)
    target=ROOT/name
    if not target.resolve().is_relative_to(ROOT):raise ValueError('Invalid path')
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
  print('Verified and restored',len(lock['files']),'runtime files')
 else:raise ValueError('Use create or restore')

if __name__=='__main__':main()
