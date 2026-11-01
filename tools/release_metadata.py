import hashlib
import json
import os
from pathlib import Path

p=Path('delivery/derminal-arm64.apk')
Path('delivery/derminal-build.json').write_text(json.dumps({'name':'derminal','version':'1.0.0','build':os.environ['GITHUB_RUN_NUMBER'],'commit':os.environ['GITHUB_SHA'],'workflow':'https://github.com/'+os.environ['GITHUB_REPOSITORY']+'/actions/runs/'+os.environ['GITHUB_RUN_ID'],'apkSha256':hashlib.sha256(p.read_bytes()).hexdigest(),'runtime':json.loads(Path('runtime-lock.json').read_text()),'validation':{'terminalUnitTests':True,'appUnitTests':True,'androidLint':True,'signedRelease':True,'emulatorUiInspection':False,'deviceUiAcceptance':'pending'}},indent=2)+'\n')
