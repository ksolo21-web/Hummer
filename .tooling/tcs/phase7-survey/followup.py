#!/usr/bin/env python3
"""Read-only navigation/private-access crosscheck. Does not approve territory assignments."""
import hashlib,json,time,urllib.request,urllib.parse,xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime,timezone
root=Path('evidence/followup');root.mkdir(parents=True,exist_ok=True)
receipts=[]
def fetch(url,name):
    assert urllib.parse.urlsplit(url).hostname in {'api.openstreetmap.org','gisservices.oakgov.com','tigerweb.geo.census.gov'}
    for attempt in range(3):
        started=datetime.now(timezone.utc).isoformat()
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'TerritoryCardStudio/Phase7-public-source-crosscheck'}),timeout=75) as response:
                assert response.status==200
                data=response.read(25000001);assert len(data)<=25000000
            path=root/f'{name}-attempt-{attempt+1}.raw';path.write_bytes(data)
            receipts.append({'url':url,'startedAt':started,'receivedAt':datetime.now(timezone.utc).isoformat(),'path':path.name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
            return data
        except Exception as e:
            receipts.append({'url':url,'startedAt':started,'attempt':attempt+1,'error':str(e)})
            if attempt==2:raise
            time.sleep(2*(attempt+1))
def arc(layer,params,name):
    j=json.loads(fetch(layer+'?'+urllib.parse.urlencode({'f':'json',**params}),name));assert 'error' not in j,j
    return j
roads='https://gisservices.oakgov.com/arcgis/rest/services/Enterprise/EnterpriseTransportationDataMapService/MapServer/0'
summary={'phase7Complete':False,'cardApproved':False,'purpose':'Navigation and independent private-access source evidence only','sources':{}}
try:
    for name,bbox in [('a265',(-83.1572,42.6730,-83.1533,42.6764)),('a295',(-83.1333,42.6834,-83.1290,42.6867))]:
        try:
            data=fetch('https://api.openstreetmap.org/api/0.6/map?bbox='+','.join(map(str,bbox)),name+'-osm-map')
            tree=ET.fromstring(data);assert tree.tag=='osm';assert tree.find('error') is None
            nodes={n.attrib['id'] for n in tree.findall('node')};ways=tree.findall('way')
            missing={n.attrib['ref'] for w in ways for n in w.findall('nd') if n.attrib['ref'] not in nodes}
            assert not missing, 'OSM ways have missing referenced nodes'
            summary['sources'][name+'-osm']={'bounds':bbox,'nodes':len(nodes),'ways':len(ways),'completeWayNodes':True,'timestampsPreserved':True}
        except Exception as e: summary['sources'][name+'-osm']={'error':str(e)}
    query={'where':"StreetName IN ('Walton','Livernois')",'geometry':'-83.159,42.673,-83.150,42.690','geometryType':'esriGeometryEnvelope','inSR':4326,'spatialRel':'esriSpatialRelIntersects'}
    before=arc(roads+'/query',{**query,'returnIdsOnly':'true'},'a265-navigation-ids-before');ids=before['objectIds'];assert len(ids)==len(set(ids)) and len(ids)<500
    result=arc(roads+'/query',{'objectIds':','.join(map(str,ids)),'outFields':'*','returnGeometry':'true','outSR':4326},'a265-navigation-roads')
    assert not result.get('exceededTransferLimit',False)
    assert sorted(f['attributes']['OBJECTID'] for f in result['features'])==sorted(ids)
    after=arc(roads+'/query',{**query,'returnIdsOnly':'true'},'a265-navigation-ids-after');assert sorted(after['objectIds'])==sorted(ids)
    summary['sources']['a265-navigation']={'count':len(ids),'complete':True,'stableIds':True}
    # Fresh uniquely named metadata supersedes the two overwritten metadata receipts in survey-v1.
    # Original feature responses and ID/pagination records are retained unchanged.
    census='https://tigerweb.geo.census.gov/arcgis/rest/services/TIGERweb/Transportation/MapServer'
    for layer in [2,6,8]:
        meta=arc(census+'/'+str(layer),{},'fresh-census-'+str(layer)+'-metadata')
        summary['sources']['census-'+str(layer)]={'name':meta['name'],'description':meta.get('description'),'freshMetadata':True}
finally:
    (root/'summary.json').write_text(json.dumps(summary,indent=2))
    (root/'receipts.json').write_text(json.dumps(receipts,indent=2))
    (root/'manifest.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in root.iterdir() if p.is_file() and p.name!='manifest.json'},indent=2))
print(json.dumps(summary,indent=2))
assert all('error' not in v for v in summary['sources'].values()),'One or more independent source calls failed'
