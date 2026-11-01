#!/usr/bin/env python3
"""Create a checksummed offline Debian 13 ARM64 runtime. Requires native ARM64 proot.

CI uses Docker + QEMU for the Debian package preparation only. Android does not use QEMU.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.runtime'
ASSETS = ROOT / 'app/src/main/assets'
NATIVE = ROOT / 'app/src/main/jniLibs/arm64-v8a'
PACKAGES = {
 'proot': ('pool/main/p/proot/proot_5.1.107.98_aarch64.deb', '29de40efea4daa2a6168b617915206511c63e8d46bf5f4b07d48723441ef7f3b'),
 'libtalloc': ('pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb', '556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7'),
 'libandroid-shmem': ('pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb', '0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6'),
}
BASE = 'https://github.com/termux/proot-distro/releases/download/v4.26.0/debian-trixie-aarch64-pd-v4.26.0.tar.xz'
BASE_SHA = 'cda75346f2c9e09e8a802665745b5a7e2bd6d8584dbf1c86c8c57ef54c4e2d3c'
TOOLS = ['bash', 'bash-completion', 'ca-certificates', 'curl', 'git', 'nano', 'vim-tiny', 'less', 'openssh-client', 'procps', 'iproute2', 'file', 'unzip', 'tar', 'xz-utils', 'ncurses-term', 'locales']
CHROOT = False

def download(url, dest, sha):
 if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest() == sha: return
 print('Downloading', dest.name, flush=True)
 with urllib.request.urlopen(url, timeout=120) as r: data = r.read()
 if hashlib.sha256(data).hexdigest() != sha: raise ValueError('SHA256 mismatch: '+dest.name)
 dest.write_bytes(data)

def deb_tar(data):
 assert data[:8] == b'!<arch>\n'
 i = 8
 while i < len(data):
  name = data[i:i+16].decode().strip().rstrip('/')
  size = int(data[i+48:i+58].decode().strip())
  if name.startswith('data.tar'): return tarfile.open(fileobj=io.BytesIO(data[i+60:i+60+size]), mode='r:*')
  i += 60 + size + size % 2
 raise ValueError('Missing data archive')

def native():
 NATIVE.mkdir(parents=True, exist_ok=True)
 for _, (name, sha) in PACKAGES.items():
  path = CACHE / Path(name).name
  vendored = ROOT / 'third_party/runtime' / path.name
  if not path.exists() and vendored.exists(): shutil.copyfile(vendored, path)
  download('https://packages.termux.dev/apt/termux-main/'+name, path, sha)
  with deb_tar(path.read_bytes()) as archive:
   for m in archive:
    if not m.isfile(): continue
    dest = None
    if m.name.endswith('/bin/proot'): dest = 'libproot.so'
    elif m.name.endswith('/libexec/proot/loader'): dest = 'libproot-loader.so'
    elif '/lib/libtalloc.so.' in m.name: dest = 'libtalloc.so'
    elif m.name.endswith('/lib/libandroid-shmem.so'): dest = 'libandroid-shmem.so'
    if dest:
     data = archive.extractfile(m).read().replace(b'libtalloc.so.2\x00', b'libtalloc.so\x00\x00\x00')
     (NATIVE/dest).write_bytes(data)
     (NATIVE/dest).chmod(0o755)

def run_guest(root, command, capture=False):
 if CHROOT:
  mounted=[]
  try:
   for name in ['dev','proc']:
    (root/name).mkdir(exist_ok=True)
    subprocess.run(['mount','--bind','/'+name,str(root/name)],check=True)
    mounted.append(root/name)
   return subprocess.run(['chroot',str(root),'/usr/bin/env','-i','HOME=/root','PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin','LANG=C.UTF-8','DEBIAN_FRONTEND=noninteractive','/bin/bash','-ec',command],check=True,timeout=2400,capture_output=capture,text=capture)
  finally:
   for path in reversed(mounted): subprocess.run(['umount',str(path)],check=True)
 env = os.environ.copy()
 env.pop('LD_PRELOAD', None)
 env['PROOT_LOADER'] = str(NATIVE/'libproot-loader.so')
 env['PROOT_NO_SECCOMP'] = '1'
 return subprocess.run(['proot', '--link2symlink', '-0', '-r', str(root), '-b', '/dev', '-b', '/proc', '-w', '/', '/usr/bin/env', '-i', 'HOME=/root', 'PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin', 'LANG=C.UTF-8', 'DEBIAN_FRONTEND=noninteractive', '/bin/bash', '-ec', command], env=env, check=True, timeout=1200,capture_output=capture,text=capture)

def package(root):
 ASSETS.mkdir(parents=True, exist_ok=True)
 links, modes, size = {}, {}, 0
 image = ASSETS/'debian.zip'
 with zipfile.ZipFile(image, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
  for p in sorted(root.rglob('*')):
   name = p.relative_to(root).as_posix()
   if name.startswith(('dev/', 'proc/', 'sys/')): continue
   if p.is_symlink(): links[name] = os.readlink(p)
   elif p.is_file():
    z.write(p, name); modes[name] = p.stat().st_mode & 0o777; size += p.stat().st_size
   elif p.is_dir(): z.writestr(name+'/', b'')
  meta = {'links': links, 'modes': modes, 'bytes': size, 'files': len(modes)}
  z.writestr('_derminal.json', json.dumps(meta))
 (ASSETS/'runtime.json').write_text(json.dumps({'version':1, 'distribution':'Debian 13 Trixie', 'architecture':'arm64-v8a', 'sha256':hashlib.sha256(image.read_bytes()).hexdigest(), 'unpackedBytes':size, 'files':len(modes), 'base':{'url':BASE, 'sha256':BASE_SHA}, 'native':PACKAGES, 'packages':TOOLS}, indent=2)+'\n')
 print('Offline image:', image.stat().st_size, 'bytes; extracted:', size, flush=True)

def main():
 global CHROOT
 parser = argparse.ArgumentParser(); parser.add_argument('--native-only', action='store_true'); parser.add_argument('--package-root'); parser.add_argument('--chroot',action='store_true')
 args = parser.parse_args(); CHROOT=args.chroot; CACHE.mkdir(exist_ok=True); native()
 if args.native_only: return
 if args.package_root: package(Path(args.package_root)); return
 root = CACHE/'rootfs'
 if not (root/'.derminal-prepared').exists():
  base = CACHE/'debian-base.tar.xz'; download(BASE, base, BASE_SHA)
  if root.exists(): shutil.rmtree(root)
  root.mkdir()
  with tarfile.open(base) as archive:
   members = []
   for m in archive:
    name = m.name.partition('/')[2]
    if not name or m.ischr() or m.isblk() or m.isfifo(): continue
    m.name = name
    if m.islnk(): m.linkname = m.linkname.partition('/')[2]
    members.append(m)
   archive.extractall(root, members=members, filter='fully_trusted')
  (root/'etc/resolv.conf').write_text('nameserver 1.1.1.1\nnameserver 8.8.8.8\n')
  (root/'usr/sbin/policy-rc.d').write_text('#!/bin/sh\nexit 101\n');(root/'usr/sbin/policy-rc.d').chmod(0o755)
  run_guest(root, 'apt-get update && apt-get -y upgrade && apt-get install -y --no-install-recommends '+' '.join(TOOLS)+' && apt-get clean && rm -rf /var/lib/apt/lists/*')
  for name in ['dev','proc','sys','tmp','shared','root']: (root/name).mkdir(exist_ok=True)
  (root/'etc/hostname').write_text('derminal\n')
  (root/'etc/hosts').write_text('127.0.0.1 localhost derminal\n::1 localhost\n')
  (root/'etc/profile.d/derminal.sh').write_text('export LANG=C.UTF-8\nexport TERM=xterm-256color\nexport COLORTERM=truecolor\n')
  (root/'root/.bashrc').write_text('''# derminal shell defaults; this file is yours to edit.
export LANG=C.UTF-8
export TERM=xterm-256color
export COLORTERM=truecolor
HISTSIZE=10000
HISTFILESIZE=50000
HISTCONTROL=ignoreboth
shopt -s histappend checkwinsize
PROMPT_COMMAND='history -a; history -n'
PS1='\\[\\e[1;32m\\]\\u@derminal\\[\\e[0m\\]:\\[\\e[1;34m\\]\\w\\[\\e[0m\\]\\$ '
alias ls='ls --color=auto'
alias grep='grep --color=auto'
[ ! -r /usr/share/bash-completion/bash_completion ] || . /usr/share/bash-completion/bash_completion
''')
  (root/'root/.bash_profile').write_text('[ ! -r ~/.bashrc ] || . ~/.bashrc\n')
  run_guest(root, "bash --version | head -1; git --version; nano --version | head -1; test -e /usr/share/terminfo/x/xterm-256color -o -e /lib/terminfo/x/xterm-256color; cat /etc/debian_version")
  (root/'.derminal-prepared').write_text('1')
 package(root)

if __name__ == '__main__': main()
