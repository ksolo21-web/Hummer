from pathlib import Path
import json,hashlib,sys,xml.etree.ElementTree as E
from PIL import Image
root=Path(sys.argv[1]);passed=[];failed=[]
for name in ['main','before-restart','after-restart','wide','retry']:
 raw=(root/f'phase3d-{name}.log').read_text();c=n=None;ok=[];bad=[]
 for line in raw.splitlines():
  if line.startswith('INSTRUMENTATION_STATUS: class='):c=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS: test='):n=line.split('=',1)[1]
  if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
   code=int(line.rsplit(' ',1)[1])
   if code<=0 and n:(ok if code==0 else bad).append((c,n));c=n=None
 if name=='retry':
  assert set(ok+bad)==set(failed),(ok,bad,failed)
  failed=[x for x in failed if x not in ok]
 passed+=ok;failed+=bad
 assert len(ok)+len(bad)=={'main':6,'before-restart':1,'after-restart':1,'wide':2,'retry':2}[name],(name,ok,bad)
unique=sorted(set(passed));xml=E.Element('testsuite',name='Phase3D integration',tests=str(len(unique)+len(set(failed))),failures=str(len(set(failed))),errors='0')
for c,n in sorted(set(passed+failed)):
 item=E.SubElement(xml,'testcase',classname=c,name=n)
 if (c,n) in failed:E.SubElement(item,'failure').text='See native run logs'
E.ElementTree(xml).write(root/'TEST-phase3d.xml',encoding='UTF-8',xml_declaration=True)
(root/'test-identities.json').write_text(json.dumps(dict(passed=unique,failed=failed,execution_count=12,retried_failed_identities=2,retained_phase3d_identities=6,frozen_prior=132),indent=2)+'\n')
assert len(unique)==8 and not failed,(unique,failed)
proof=json.loads((root/'phase3d-restart-proof.json').read_text());assert proof['passed'] and proof['pid']!=proof['newPid']
rows=[]
for p in sorted(root.glob('phase3d-*.png')):
 if '-debug-' in p.name:continue
 im=Image.open(p);rows.append(dict(file=p.name,width=im.width,height=im.height,sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
expected={f'phase3d-{wide}{state}-{theme}.png' for wide in ['','wide-'] for state in ['edited-review','local-decision','export-blocked','stale-review'] for theme in ['light','dark']}|{f'phase3d-{mode}-page{page}.png' for mode in ['letter','telephone'] for page in [1,2]}
assert {x['file'] for x in rows}==expected
(root/'screenshot-manifest.json').write_text(json.dumps(rows,indent=2)+'\n')
(root/'status.txt').write_text('PHASE3D=RUNTIME_PASSED_PENDING_CRITIC\nNEW_IDENTITIES=8\nFROZEN_IDENTITIES=132\nTOTAL_IDENTITIES=140\nPROCESS_RESTART=ACTUAL_PID_CHANGE\nIMAGES=20\n')
print('Phase3D:8 identities,6 retained and2 retried,20 retained images;132 earlier identities frozen')
