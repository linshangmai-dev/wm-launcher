#!/usr/bin/env python3
"""Import pinned library artwork as native Android vectors, preserving geometry."""
from pathlib import Path
import json, subprocess, urllib.request, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'WM-Launcher-development'})
    with urllib.request.urlopen(request, timeout=25) as response:
        return response.read()

metadata = root/'third_party/tabler-icons/SOURCE.json'
commit = json.loads(metadata.read_text())['commit'] if metadata.exists() else subprocess.check_output(
    ['git', 'ls-remote', 'https://github.com/tabler/tabler-icons.git', 'refs/heads/main'],
    text=True, timeout=30).strip().split()[0]
assert len(commit) == 40
base = f'https://raw.githubusercontent.com/tabler/tabler-icons/{commit}/'
source = root/'third_party/tabler-icons'
source.mkdir(parents=True, exist_ok=True)
license_text = fetch(base+'LICENSE')
(source/'LICENSE').write_bytes(license_text)
asset = root/'app/src/main/assets/licenses/tabler-icons.txt'
asset.parent.mkdir(parents=True, exist_ok=True)
asset.write_bytes(b'Tabler Icons: https://github.com/tabler/tabler-icons\nRevision: '+commit.encode()+b'\n\n'+license_text)
imported = {}
for name, target in [('layout-grid', 'wm_ic_factory'),
                     ('chevron-right', 'wm_ic_chevron')]:
    data = fetch(base+f'icons/outline/{name}.svg')
    (source/(name+'.svg')).write_bytes(data)
    svg = ET.fromstring(data)
    paths = []
    for element in svg:
        tag = element.tag.split('}')[-1]
        if element.get('stroke') == 'none':
            continue
        if tag == 'path':
            d = element.attrib['d']
        elif tag == 'circle':
            x,y,r = [float(element.attrib[k]) for k in ('cx','cy','r')]
            d = f'M{x-r},{y}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0'
        elif tag == 'rect':
            x,y,w,h = [float(element.get(k, '0')) for k in ('x','y','width','height')]
            radius = float(element.get('rx', '0'))
            assert radius == 0
            d = f'M{x},{y}h{w}v{h}h{-w}z'
        else:
            raise ValueError(f'Unsupported original SVG element: {tag}')
        paths.append(f'    <path android:fillColor="@android:color/transparent" android:strokeColor="@color/icon_tint"\n'
            f'        android:strokeWidth="{svg.get("stroke-width", "2")}" android:strokeLineCap="round" android:strokeLineJoin="round"\n'
            f'        android:pathData="{d}" />')
    vector = '<?xml version="1.0" encoding="utf-8"?>\n'+f'<!-- Tabler Icons {name}, MIT. Original geometry; revision {commit}. See assets/licenses/tabler-icons.txt. -->\n'+\
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp"\n'+\
        '    android:viewportWidth="24" android:viewportHeight="24">\n'+'\n'.join(paths)+'\n</vector>\n'
    (root/f'app/src/main/res/drawable/{target}.xml').write_text(vector, encoding='utf-8')
    imported[name] = target
(source/'SOURCE.json').write_text(json.dumps({'repository':'https://github.com/tabler/tabler-icons',
    'commit':commit,'icons':imported,'license':'MIT'}, indent=2), encoding='utf-8')
print(json.dumps({'revision':commit, 'icons':imported}))
