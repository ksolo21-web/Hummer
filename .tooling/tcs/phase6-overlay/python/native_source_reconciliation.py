"""Portable v1 reconciliation reader. This is content validation, never PDF approval.

The Python consumer independently decodes the same explicit schema and computes
road/building differences. It does not claim to execute Android/GIS or authenticate
an author. Eligibility also requires the owning application's current ledger.
"""
import datetime
import hashlib
import json
import re

SCHEMA = 'native-source-reconciliation-v1'
MAX_BYTES = 1024 * 1024
ROOT = {'schema','registrationId','predecessorEventSha256','territory','mode','knowledgeBaseRevision','importedSourceSha256',
        'lockedReferenceSha256','sourceClass','author','reviewedAtUtc',
        'assignmentContentSha256','inventorySha256','sourceCoverageComplete',
        'explicitAssignmentConfirmation','crossTerritoryInferenceUsed',
        'styleOnlyGeographyUsed','segments','buildings'}
ROAD = {'segmentId','name','status','role','insideSide','accessOnly','endpointAKind',
        'endpointBKind','evidenceNote','confirmed'}
BUILDING = {'buildingId','sourceMembers','assigned','evidenceNote','confirmed'}
SHA = re.compile(r'[0-9a-f]{64}\Z')

def require(ok, message):
    if not ok:
        raise ValueError(message)

def text(value, limit=256, blank=False):
    require(isinstance(value,str) and (blank or bool(value.strip())) and
            value == value.strip() and len(value)<=limit and
            all(32<=ord(c)<=126 for c in value),'Invalid reconciliation text')

def boolean(value):
    require(type(value) is bool,'Expected boolean')

def validate(r):
    require(type(r) is dict and set(r)==ROOT and r['schema']==SCHEMA,'Schema drift')
    require(type(r['registrationId']) is str and re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',r['registrationId']),'Invalid registration id')
    require(r['predecessorEventSha256'] is None or (type(r['predecessorEventSha256']) is str and SHA.fullmatch(r['predecessorEventSha256'])),'Invalid predecessor hash')
    text(r['territory'],16)
    digits=re.sub(r'^(?:TA|A|T)?([0-9]+)[a-z]?$',r'\1',r['territory'])
    require(digits.isdigit() and int(digits)<=2147483647,'Territory number overflow')
    # Identity compatibility is checked by the canonical territory_identity consumer.
    require(re.fullmatch(r'(?:TA|A|T)?[1-9][0-9]*[a-z]?',r['territory']) is not None,'Invalid territory')
    require(r['mode'] in {'REGULAR','LETTER_WRITING','TELEPHONE'},'Invalid mode')
    text(r['knowledgeBaseRevision']);text(r['author'],120)
    for name in ('importedSourceSha256','lockedReferenceSha256','assignmentContentSha256'):
        require(type(r[name]) is str and SHA.fullmatch(r[name]),'Invalid hash')
    require(r['inventorySha256'] is None or (type(r['inventorySha256']) is str and SHA.fullmatch(r['inventorySha256'])),'Invalid inventory hash')
    require(r['sourceClass'] in {'current_assignment_map','legacy_reference','style_only'},'Invalid source class')
    require(type(r['reviewedAtUtc']) is str and re.fullmatch(r'[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z',r['reviewedAtUtc']),'Invalid timestamp')
    datetime.datetime.strptime(r['reviewedAtUtc'],'%Y-%m-%dT%H:%M:%SZ')
    for key in ('sourceCoverageComplete','explicitAssignmentConfirmation','crossTerritoryInferenceUsed','styleOnlyGeographyUsed'):
        boolean(r[key])
    require(type(r['segments']) is list and 1<=len(r['segments'])<=512,'Invalid segments')
    require(type(r['buildings']) is list and len(r['buildings'])<=512,'Invalid buildings')
    for row in r['segments']:
        require(type(row) is dict and set(row)==ROAD,'Road schema drift')
        text(row['segmentId'],120);text(row['name']);text(row['evidenceNote'],1000,True)
        require(row['status'] in {'yellow','green','red','context'} and row['role'] in {'perimeter','interior','excluded','context'},'Invalid road meaning')
        require(row['insideSide'] in {'','left','right'} and row['endpointAKind'] in {'junction','termination'} and row['endpointBKind'] in {'junction','termination'},'Invalid road topology')
        boolean(row['confirmed']);boolean(row['accessOnly'])
    for row in r['buildings']:
        require(type(row) is dict and set(row)==BUILDING,'Building schema drift')
        text(row['buildingId'],120);text(row['evidenceNote'],1000,True)
        require(type(row['sourceMembers']) is list and (1 if row['assigned'] else 0)<=len(row['sourceMembers'])<=512,'Invalid members')
        for m in row['sourceMembers']:text(m,120)
        require(len(set(row['sourceMembers']))==len(row['sourceMembers']),'Duplicate members')
        boolean(row['assigned']);boolean(row['confirmed'])
    require(len({v['segmentId'] for v in r['segments']})==len(r['segments']),'Duplicate road')
    require(len({v['buildingId'] for v in r['buildings']})==len(r['buildings']),'Duplicate building')

def encode(r):
    validate(r)
    value=json.dumps(r,ensure_ascii=True,sort_keys=True,separators=(',',':')).encode('ascii')
    require(len(value)<=MAX_BYTES,'Reconciliation too large')
    return value

def decode(raw):
    require(type(raw) is bytes and 1<=len(raw)<=MAX_BYTES,'Invalid byte length')
    value=raw.decode('utf-8',errors='strict')
    depth=0;quote=False;escape=False
    for c in value:
        if quote:
            if escape:escape=False
            elif c=='\\':escape=True
            elif c=='"':quote=False
        elif c=='"':quote=True
        elif c in '[{':
            depth+=1;require(depth<=8,'Excessive nesting')
        elif c in ']}':
            depth-=1;require(depth>=0,'Invalid nesting')
    require(not quote and depth==0,'Invalid JSON')
    def pairs(items):
        out={}
        for key,val in items:
            require(key not in out,'Duplicate key');out[key]=val
        return out
    r=json.loads(value,object_pairs_hook=pairs)
    require(encode(r)==raw,'Noncanonical encoding')
    return r

def differences(r,roads,buildings):
    """Compare explicit user observations to named candidate geometry records."""
    validate(r)
    expected={x['segmentId']:x for x in r['segments']}
    actual={x['segmentId']:x for x in roads}
    require(len(actual)==len(roads),'Duplicate candidate road')
    fields=ROAD-{'segmentId','evidenceNote','confirmed'}
    same=lambda s,a: all(s[k]==a[k] for k in fields)
    observed_buildings={x['buildingId']:x for x in r['buildings']}
    actual_buildings={x['buildingId']:x for x in buildings}
    require(len(actual_buildings)==len(buildings),'Duplicate candidate building')
    unresolved=0
    for k in observed_buildings.keys()|actual_buildings.keys():
        s=observed_buildings.get(k);b=actual_buildings.get(k)
        if s is None or b is None or not s['confirmed'] or s['assigned']!=b['assigned'] or sorted(s['sourceMembers'])!=sorted(b['sourceMembers']):unresolved+=1
    return dict(missingExpectedSegmentCount=len(expected.keys()-actual.keys()),
        unexpectedMeaningChangingSegmentCount=len(actual.keys()-expected.keys()),
        assignmentColorConflictCount=sum(not same(s,actual[k]) for k,s in expected.items() if k in actual),
        unresolvedPerimeterWorkedSideCount=sum(a['role']=='perimeter' and (a['insideSide'] not in {'left','right'} or a['segmentId'] not in expected or not expected[a['segmentId']]['confirmed'] or not same(expected[a['segmentId']],a)) for a in roads),
        unresolvedBuildingSiteCount=unresolved)

def assignment_content_sha256(v):
    """Exact native-assignment-content-v1 binary digest; authoritySha256 alone excluded."""
    import struct
    out=bytearray()
    def text(s):
        raw=s.encode('utf-8');out.extend(struct.pack('>i',len(raw)));out.extend(raw)
    def integer(n):out.extend(struct.pack('>i',n))
    def number(n):
        import math
        n=float(n);require(math.isfinite(n),'Nonfinite coordinate');out.extend(struct.pack('>d',n))
    def boolean(b):require(type(b) is bool,'Not boolean');out.extend(b'\x01' if b else b'\x00')
    def point(p):number(p['x']);number(p['y'])
    def array(a,write):integer(len(a));[write(x) for x in a]
    text('native-assignment-content-v1');text(v['displayId'])
    m=re.fullmatch(r'(TA|A|T)?([1-9][0-9]*)([a-z])?',v['displayId']);require(m is not None,'Invalid identity')
    integer(int(m[2]));text(m[1] or '');text(m[3] or '')
    for k in ('canonicalFilename','knowledgeBaseRevision','authorityRole','sourceMasterLabel','locality','updated'):text(v[k])
    array(v['directionsLines'],text)
    for k in ('layoutMode','housingType','coordinateSpace'):text(v[k])
    def road(r):
        for k in ('segmentId','name','normalizedName','status','role','insideSide'):text(r[k])
        boolean(r['accessOnly']);text(r['endpointAKind']);text(r['endpointBKind']);number(r['widthPt']);array(r['points'],point)
    array(v['roads'],road)
    def label(i):
        text(i['text']);point(i['center']);boolean(i['origin'] is not None)
        if i['origin'] is not None:point(i['origin'])
        number(i['angleDeg']);number(i['fontSizePt'])
    def building(b):
        for k in ('buildingId','label','housingType'):text(b[k])
        boolean(b['assigned']);text(b['attachedGroup']);array(b['sourceMembers'],text);array(b['labelItems'],label);array(b['polygon'],point)
    array(v['buildings'],building)
    return hashlib.sha256(out).hexdigest()

if __name__=='__main__':
    import sys
    raw=open(sys.argv[1],'rb').read();r=decode(raw)
    require(encode(r)==raw,'Byte parity failure')
    if len(sys.argv)>2:
        assignment=json.load(open(sys.argv[2]))
        expected=open(sys.argv[3]).read().strip()
        require(assignment_content_sha256(assignment)==expected,'Assignment content digest parity failed')
    matrix_count=0
    if len(sys.argv)>4:
        for case in json.load(open(sys.argv[4])):
            require(differences(case['reconciliation'],case['assignment']['roads'],case['assignment']['buildings'])==case['expected'],'Structural difference parity failed')
            matrix_count+=1
    print(json.dumps({'structuralMatrixCases':matrix_count,'schema':SCHEMA,'sha256':hashlib.sha256(raw).hexdigest(),'byteParity':True,'differences':differences(r,r['segments'],r['buildings'])},sort_keys=True))
