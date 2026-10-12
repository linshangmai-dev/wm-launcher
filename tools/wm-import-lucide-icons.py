#!/usr/bin/env python3
"""Import source-library SVG geometry as native Android vectors, without redrawing."""
from pathlib import Path
import json, re, subprocess, urllib.request, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=root/'third_party/lucide-icons'
source.mkdir(parents=True,exist_ok=True)
metadata=source/'SOURCE.json'
commit=json.loads(metadata.read_text())['commit'] if metadata.exists() else subprocess.check_output(
    ['git','ls-remote','https://github.com/lucide-icons/lucide.git','refs/heads/main'],
    text=True,timeout=30).strip().split()[0]
assert len(commit)==40
base=f'https://raw.githubusercontent.com/lucide-icons/lucide/{commit}/'
def fetch(path):
    with urllib.request.urlopen(urllib.request.Request(base+path,headers={'User-Agent':'WM-Launcher-development'}),timeout=30) as r:
        return r.read()
license_text=fetch('LICENSE')
(source/'LICENSE').write_bytes(license_text)
(root/'app/src/main/assets/licenses/lucide-icons.txt').write_bytes(
    b'Lucide Icons: https://github.com/lucide-icons/lucide\nRevision: '+commit.encode()+b'\n\n'+license_text)
imported={}
for name,target in [('car-front','wm_ic_vehicle'),('battery-charging','wm_ic_energy'),('radar','wm_ic_surround')]:
    data=fetch(f'icons/{name}.svg')
    (source/(name+'.svg')).write_bytes(data)
    svg=ET.fromstring(data)
    paths=[]
    for item in svg:
        tag=item.tag.split('}')[-1]
        if tag=='path': d=item.attrib['d']
        elif tag=='line':
            d=f'M{item.get("x1")},{item.get("y1")}L{item.get("x2")},{item.get("y2")}'
        elif tag in ('polyline','polygon'):
            points=re.findall(r'[-+]?(?:\d*\.\d+|\d+)',item.attrib['points'])
            assert len(points)%2==0
            d='M'+','.join(points[:2])+''.join('L'+','.join(points[i:i+2]) for i in range(2,len(points),2))
            if tag=='polygon': d+='z'
        elif tag=='circle':
            x,y,r=[float(item.get(k)) for k in ('cx','cy','r')]
            d=f'M{x-r},{y}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0'
        elif tag=='rect':
            x,y,w,h=[float(item.get(k,'0')) for k in ('x','y','width','height')]
            r=float(item.get('rx','0'))
            assert r<=min(w,h)/2
            d=f'M{x+r},{y}h{w-2*r}a{r},{r} 0 0,1 {r},{r}v{h-2*r}a{r},{r} 0 0,1 {-r},{r}h{-w+2*r}a{r},{r} 0 0,1 {-r},{-r}v{-h+2*r}a{r},{r} 0 0,1 {r},{-r}z' if r else f'M{x},{y}h{w}v{h}h{-w}z'
        else: raise ValueError(tag)
        paths.append(f'    <path android:fillColor="@android:color/transparent" android:strokeColor="@color/icon_tint"\n'
            f'        android:strokeWidth="{svg.get("stroke-width","2")}" android:strokeLineCap="round" android:strokeLineJoin="round"\n'
            f'        android:pathData="{d}" />')
    vector='<?xml version="1.0" encoding="utf-8"?>\n'+f'<!-- Lucide {name}, revision {commit}. Complete license: assets/licenses/lucide-icons.txt. -->\n'+\
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp"\n'+\
        '    android:viewportWidth="24" android:viewportHeight="24">\n'+'\n'.join(paths)+'\n</vector>\n'
    (root/f'app/src/main/res/drawable/{target}.xml').write_text(vector,encoding='utf-8')
    imported[name]=target
metadata.write_text(json.dumps({'repository':'https://github.com/lucide-icons/lucide','commit':commit,
    'icons':imported,'license':'Complete upstream ISC / Feather MIT notice'},indent=2),encoding='utf-8')
print(json.dumps({'revision':commit,'icons':imported}))
