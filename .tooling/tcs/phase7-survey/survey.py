#!/usr/bin/env python3
"""Bounded read-only official GIS discovery. Data discovery is not territory approval."""
from __future__ import annotations
import hashlib, json, time, urllib.parse, urllib.request, urllib.error
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path('evidence/survey'); ROOT.mkdir(parents=True, exist_ok=True)
ROADS='https://gisservices.oakgov.com/arcgis/rest/services/Enterprise/EnterpriseTransportationDataMapService/MapServer/0'
SITES='https://gisservices.oakgov.com/arcgis/rest/services/Enterprise/EnterpriseOpenParcelDataMapService/MapServer/0'
BUILDINGS='https://gisservices.oakgov.com/arcgisds/rest/services/Hosted/Building_Outline/FeatureServer/30'
CENSUS='https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Transportation/MapServer'
allowed={'gisservices.oakgov.com','tigerweb.geo.census.gov'}
receipts=[]

def now(): return datetime.now(timezone.utc).isoformat()
def request(endpoint, params, label):
    assert urllib.parse.urlsplit(endpoint).hostname in allowed
    params={'f':'json',**params}
    url=endpoint+'?'+urllib.parse.urlencode(params)
    error=None
    for attempt in range(3):
        started=now()
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'TerritoryCardStudio/Phase7-source-audit','Accept':'application/json'})
            with urllib.request.urlopen(req,timeout=60) as response:
                assert response.status==200
                body=response.read(20_000_001)
                assert len(body)<=20_000_000, 'response size limit'
            value=json.loads(body)
            assert isinstance(value,dict), 'expected JSON object'
            path=ROOT/(label+f'-attempt{attempt+1}.json');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(body)
            row={'url':url,'startedAt':started,'receivedAt':now(),'path':path.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(body).hexdigest(),'bytes':len(body),'attempt':attempt+1}
            receipts.append(row)
            if 'error' in value: raise RuntimeError(str(value['error']))
            return value
        except (urllib.error.URLError, TimeoutError, OSError, ValueError, AssertionError, RuntimeError) as e:
            error=f'{type(e).__name__}: {e}'
            receipts.append({'url':url,'startedAt':started,'failedAt':now(),'attempt':attempt+1,'error':error})
            if attempt<2: time.sleep(2*(attempt+1))
    raise RuntimeError(f'{label}: {error}')

def retrieve(layer, where, label, bounds=None):
    query={'where':where}
    if bounds is not None:
        x0,y0,x1,y1=bounds
        assert -84<x0<x1<-82 and 42<y0<y1<43.5
        assert x1-x0<0.04 and y1-y0<0.04,'scope too large'
        query.update(geometry=','.join(map(str,bounds)),geometryType='esriGeometryEnvelope',inSR=4326,spatialRel='esriSpatialRelIntersects')
    initial=request(layer+'/query',{**query,'returnIdsOnly':'true'},label+'-ids-before')
    assert initial.get('exceededTransferLimit',False) is False
    ids=initial.get('objectIds') or []
    assert len(ids)==len(set(ids)) and len(ids)<=4000,'ID inventory duplicate or oversized'
    key=initial.get('objectIdFieldName','OBJECTID')
    rows=[]
    for start in range(0,len(ids),150):
        chunk=ids[start:start+150]
        data=request(layer+'/query',{'objectIds':','.join(map(str,chunk)),'outFields':'*','returnGeometry':'true','outSR':4326,'returnZ':'false','returnM':'false'},label+f'-page-{start//150:03d}')
        assert data.get('exceededTransferLimit',False) is False,'truncated feature page'
        features=data.get('features');assert isinstance(features,list)
        found=[next(v for k,v in f['attributes'].items() if k.lower()==key.lower()) for f in features]
        assert sorted(found)==sorted(chunk),'page does not cover its requested object IDs'
        rows.extend(features)
    final=request(layer+'/query',{**query,'returnIdsOnly':'true'},label+'-ids-after')
    assert final.get('exceededTransferLimit',False) is False
    assert sorted(ids)==sorted(final.get('objectIds') or []),'dataset changed during collection'
    result={'layer':layer,'where':where,'boundsWgs84':bounds,'objectIdField':key,'objectIds':sorted(ids),'count':len(rows),'complete':True,'stableIds':True,'features':rows,'retrievedAt':now()}
    (ROOT/(label+'-complete.json')).write_text(json.dumps(result,indent=2))
    return result

def coordinate_pairs(features):
    for f in features:
        g=f['geometry']
        if 'x' in g: yield [g['x'],g['y']]
        for path in g.get('paths',g.get('rings',[])):
            yield from path

def envelope(features, margin):
    xy=list(coordinate_pairs(features));assert xy,'no anchor geometry'
    bounds=[min(p[0] for p in xy)-margin,min(p[1] for p in xy)-margin,max(p[0] for p in xy)+margin,max(p[1] for p in xy)+margin]
    assert bounds[2]-bounds[0]<0.035 and bounds[3]-bounds[1]<0.035,'anchor is not a unique local neighborhood'
    return bounds

def rochester(features):
    matches=[f for f in features if any('ROCHESTER' in str(v).upper() for k,v in f['attributes'].items() if k in ['CVTTaxNameLeft','CVTTaxNameRight'])]
    assert matches,'Rochester jurisdiction was not verified in returned attributes'
    return matches

summary={'schema':'phase7-official-discovery-v1','startedAt':now(),'purpose':'Source discovery only; boundaries, assignments, geography decisions, PDFs and approvals are not established by this survey','phase7Complete':False,'approvedCards':0,'cases':{},'metadata':{}}
try:
    for name,layer in [('oakland-roads',ROADS),('oakland-sites',SITES),('oakland-buildings',BUILDINGS)]:
        try: summary['metadata'][name]=request(layer,{},'metadata-'+name)
        except Exception as e: summary['metadata'][name]={'error':str(e)}
    cases=[('a265',ROADS,"UPPER(StreetName)='TIMBERLEA'",0.003),('spring-hill-identity-unresolved',ROADS,"UPPER(StreetName)='RHINEBERRY'",0.005),('willow-grove-identity-unresolved',ROADS,"UPPER(StreetName)='WILLOW GROVE'",0.0018),('a295-historical296',SITES,"UPPER(SITESTREETNAME)='MAIN' AND SITESTREETNUMBER >= 660 AND SITESTREETNUMBER <= 678 AND UPPER(SITECITY)='ROCHESTER'",0.0018)]
    for case,layer,where,margin in cases:
        record={'status':'RUNNING','identityApproved':False,'assignmentApproved':False};summary['cases'][case]=record
        try:
            anchor=retrieve(layer,where,case+'-anchor')
            selected=rochester(anchor['features']) if layer==ROADS else anchor['features']
            bounds=envelope(selected,margin);record['discoveryBoundsWgs84']=bounds
            record['anchorObjectIds']=[f['attributes'].get('OBJECTID',f['attributes'].get('objectid')) for f in selected]
            for name,resource in [('roads',ROADS),('site-addresses',SITES),('buildings',BUILDINGS)]:
                try:
                    result=retrieve(resource,'1=1',case+'-'+name,bounds)
                    record[name]={'count':result['count'],'complete':True,'file':case+'-'+name+'-complete.json'}
                except Exception as e: record[name]={'error':str(e),'complete':False}
            for number in [2,6,8]:
                try:
                    meta=request(CENSUS+'/'+str(number),{},f'metadata-census-{number}')
                    result=retrieve(CENSUS+'/'+str(number),'1=1',f'{case}-census-{number}',bounds)
                    record[f'census-{number}']={'count':result['count'],'complete':True,'layerName':meta.get('name'),'description':meta.get('description')}
                except Exception as e: record[f'census-{number}']={'error':str(e),'complete':False}
            record['status']='COLLECTED_NOT_RECONCILED'
        except Exception as e: record.update(status='BLOCKED',error=str(e))
        (ROOT/'summary.json').write_text(json.dumps(summary,indent=2))
finally:
    summary['endedAt']=now();(ROOT/'summary.json').write_text(json.dumps(summary,indent=2))
    (ROOT/'request-receipts.json').write_text(json.dumps(receipts,indent=2))
    (ROOT/'manifest.json').write_text(json.dumps({str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(ROOT.rglob('*')) if p.is_file() and p.name!='manifest.json'},indent=2))
print(json.dumps({k:{a:b for a,b in v.items() if a not in ['description']} for k,v in summary['cases'].items()},indent=2))
assert all(v['status']=='COLLECTED_NOT_RECONCILED' and all(v.get(name,{}).get('complete') is True for name in ['roads','site-addresses','buildings','census-2','census-6','census-8']) for v in summary['cases'].values()), 'One or more discovery sources is incomplete; inspect retained receipts'
