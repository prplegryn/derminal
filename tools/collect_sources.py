#!/usr/bin/env python3
"""Collect Debian corresponding sources for the frozen installed package set.

Run after prepare_runtime.py. Source downloads do not modify the packaged image.
"""
import json
import sys
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import prepare_runtime
from prepare_runtime import ROOT,CACHE,run_guest

def main():
 prepare_runtime.CHROOT='--chroot' in sys.argv
 root=CACHE/'rootfs';sources=CACHE/'sources';sources.mkdir(exist_ok=True)
 for name in ['proot-source.zip','talloc-source.tar.gz','shmem-source.tar.gz']:
  shutil.copyfile(ROOT/'third_party/runtime'/name,sources/name)
 inventory=run_guest(root,"dpkg-query -W -f='${binary:Package}\\t${Version}\\t${source:Package}\\t${source:Version}\\n'",capture=True).stdout
 (sources/'debian-packages.tsv').write_text(inventory)
 specs=sorted({line.split('\t')[2]+'='+line.split('\t')[3] for line in inventory.strip().split('\n')})
 (sources/'debian-source-packages.txt').write_text('\n'.join(specs)+'\n')
 out=root/'tmp/derminal-corresponding-source';out.mkdir(exist_ok=True)
 shutil.copyfile(sources/'debian-source-packages.txt',out/'packages.txt')
 (root/'etc/apt/derminal-source.list').write_text('deb-src http://deb.debian.org/debian trixie main contrib\ndeb-src http://security.debian.org/debian-security trixie-security main contrib\ndeb-src http://deb.debian.org/debian trixie-updates main contrib\n')
 run_guest(root,"apt-get -o Dir::Etc::sourcelist=/etc/apt/derminal-source.list -o Dir::Etc::sourceparts=- update && cd /tmp/derminal-corresponding-source && xargs apt-get -o Dir::Etc::sourcelist=/etc/apt/derminal-source.list -o Dir::Etc::sourceparts=- source --download-only < packages.txt")
 # Termux build scripts and patches are included alongside the upstream archives.
 tree=json.load(urllib.request.urlopen('https://api.github.com/repos/termux/termux-packages/git/trees/master?recursive=1'))
 commit=tree['sha'];(sources/'termux-packages-revision.txt').write_text(commit+'\n')
 for component in ['proot','libtalloc','libandroid-shmem']:
  prefix='packages/'+component+'/'
  for entry in tree['tree']:
   if entry['type']!='blob' or not entry['path'].startswith(prefix):continue
   dest=sources/'termux-packaging'/entry['path'];dest.parent.mkdir(parents=True,exist_ok=True)
   with urllib.request.urlopen('https://raw.githubusercontent.com/termux/termux-packages/'+commit+'/'+entry['path']) as response:dest.write_bytes(response.read())
 artifact=ROOT/'.delivery/derminal-runtime-sources.tar.gz'
 with tarfile.open(artifact,'w:gz',compresslevel=1) as tar:
  tar.add(sources,arcname='derminal-runtime-sources')
  tar.add(out,arcname='derminal-runtime-sources/debian')
  for f in CACHE.glob('*.deb'):tar.add(f,arcname='derminal-runtime-sources/native-binaries/'+f.name)
  for name in ['tools/prepare_runtime.py','tools/build_native.sh','tools/runtime_bundle.py','THIRD_PARTY.md','runtime-lock.json','app/src/main/assets/runtime.json']:
   tar.add(ROOT/name,arcname='derminal-runtime-sources/project/'+name)
 print('Corresponding sources:',artifact.stat().st_size,flush=True)

if __name__=='__main__':main()
