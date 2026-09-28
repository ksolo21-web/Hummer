from pathlib import Path
import json,hashlib,sys,xml.etree.ElementTree as E
from PIL import Image
root=Path(sys.argv[1]);passed=[];failed=[];rows=[]
for name,count in [('main',17),('before-restart',1),('after-restart',1),('wide',2),('affected',2),('audit-retry',1)]:
 raw=(root/f'phase4-{name}.log').read_text();c=n=None;ok=[];bad=[]
 for line in raw.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):c=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):n=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   code=int(line.rsplit(' ',1)[1])
   if code<=0 and n:(ok if code==0 else bad).append((c,n));c=n=None
 passed+=ok;failed+=bad;rows.append(dict(log=name,expected=count,passed=ok,failed=bad))
(root/'execution-results.json').write_text(json.dumps(rows,indent=2)+'\n')
unique=sorted(set(passed));xml=E.Element('testsuite',name='Phase4 lifecycle',tests=str(len(set(passed+failed))),failures=str(len(set(failed))),errors='0')
for c,n in sorted(set(passed+failed)):
 item=E.SubElement(xml,'testcase',classname=c,name=n)
 if (c,n) in failed:E.SubElement(item,'failure').text='See native run logs'
E.ElementTree(xml).write(root/'TEST-phase4.xml',encoding='UTF-8',xml_declaration=True)
(root/'test-identities.json').write_text(json.dumps(dict(passed=unique,failed=failed,execution_count=len(passed)+len(failed),frozen_prior=140),indent=2)+'\n')
for row in rows:assert len(row['passed'])+len(row['failed'])==row['expected'],row
assert len(unique)==21 and not failed,(unique,failed)
proof=json.loads((root/'phase4-restart-proof.json').read_text());assert proof['passed'] and proof['producerPid']!=proof['consumerPid'] and proof['modes']==3
images=[]
for p in sorted(root.glob('phase4-*.png')):
 if '-debug-' in p.name:continue
 im=Image.open(p);images.append(dict(file=p.name,width=im.width,height=im.height,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
expected={f'phase4-{wide}{state}-{theme}.png' for wide in ['','wide-'] for state in ['unapproved','confirmation','approved','rejected','invalidated','suspended'] for theme in ['light','dark']}
assert {x['file'] for x in images}==expected
(root/'screenshot-manifest.json').write_text(json.dumps(images,indent=2)+'\n')
(root/'status.txt').write_text('PHASE4=RUNTIME_PASSED_PENDING_CRITIC\nNEW_IDENTITIES=19\nAFFECTED_PRIOR_RECHECKED=2\nFROZEN_PRIOR_IDENTITIES=140\nTOTAL_IDENTITIES=159\nIMAGES=24\n')
print('Phase4:19 new identities;2 affected old identities;24 images;159 project identities')
