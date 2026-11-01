"""Compile the approved Book hop art to native Android vectors, without runtime work.

The generated AVD uses linear keyframes sampled from the approved motion study.
Its gentle fit keeps the airborne character within Android's 192 dp icon mask.
Run --check to detect stale resources and validate the full interpolated motion.
"""
from __future__ import annotations

import argparse
import bisect
import copy
import math
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
ART = ROOT / 'docs/design/system/project/components/AppLaunchSplash/art'
RES = ROOT / 'app/src/main/res'
ANDROID = 'http://schemas.android.com/apk/res/android'
AAPT = 'http://schemas.android.com/aapt'
ET.register_namespace('android', ANDROID)
ET.register_namespace('aapt', AAPT)
DURATION = 820
K = 130 / 270  # CSS study pixels per source-art unit.
INK, PAPER = '?attr/launchMarkColor', '?attr/launchPaperColor'


def attrs(**values):
    return {f'{{{ANDROID}}}{key}': str(value) for key, value in values.items()}


def number(value):
    return f'{value:.6f}'.rstrip('0').rstrip('.') if abs(value) > .0000005 else '0'


def clamp(value, low=0, high=1):
    return max(low, min(high, value))


def ease(value):
    u = clamp(value)
    return u * u * (3 - 2 * u)


def mix(a, b, u):
    return a + (b - a) * u


def book(t):
    y, angle, sx, sy, fan = 0., 0., 1., 1., 0.
    if t < 110:
        u = ease(t / 110)
        y, angle, sx, sy = 2*u, -1.6*u, 1+.016*u, 1-.028*u
    elif t < 160:
        u = ease((t-110)/50)
        y, angle, sx, sy, fan = mix(2,-2,u), mix(-1.6,2.8,u), mix(1.016,.992,u), mix(.972,1.018,u), .75*u
    elif t < 560:
        d, u = t-160, (t-160)/400
        w = math.exp(-4*u)*math.cos(3*math.pi*u)
        y, angle, sx, sy = -2*w, 2.8*w, 1-.008*w, 1+.018*w
        fan = .75*math.exp(-d/150)*(.65+.35*math.cos(d/24))
    elif t < 620:
        hit = math.sin(math.pi*clamp((t-560)/60))
        y, angle, sx, sy, fan = 3.4*hit, -3.1*hit, 1+.03*hit, 1-.055*hit, .95*hit
    else:
        d = min(200, t-620)
        u = d/200
        w = math.exp(-4*u)*math.sin(3*math.pi*u)
        y, angle, sx, sy = -1.8*w, 2.4*w, 1+.012*w, 1-.018*w
        fan = .65*math.exp(-d/85)*math.sin(d/20)**2*(1-u)**2
    return y, angle, sx, sy, fan


def pose(t):
    y, angle, sx, sy, tuck, blink = 0., 0., 1., 1., 0., 0.
    if t < 110:
        u = ease(t/110)
        y, sx, sy = 7*u, 1+.08*u, 1-.14*u
    elif t < 160:
        u = clamp((t-110)/50)
        y, sx, sy = 7*(1-u*u), mix(1.08,.94,u), mix(.86,1.1,u)
    elif t < 560:
        u = clamp((t-160)/400)
        y = -58*4*u*(1-u)
        angle = -4.5*math.sin(math.pi*u)+2*math.sin(2*math.pi*u)
        sx, sy = .94+.06*ease(u/.28), 1.1-.1*ease(u/.28)
        tuck = math.sin(math.pi*u)**.7
    elif t < 620:
        impact = math.sin(math.pi*clamp((t-560)/60))
        y, sx, sy = 5*impact, 1+.1*impact, 1-.16*impact
    else:
        u = clamp((t-620)/120)
        wave = math.sin(2*math.pi*u)
        y, sx, sy = -2.1*wave*(1-u)**2, 1-.018*wave*(1-u), 1+.025*wave*(1-u)
    if 66 <= t < 132:
        blink = math.sin(math.pi*(t-66)/66)
    if 561 <= t < 612:
        blink = max(blink, .9*math.sin(math.pi*(t-561)/51))
    return y, angle, sx, sy, tuck, blink


def motion(t):
    by, ba, bx, bs, fan = book(t)
    y, angle, sx, sy, tuck, blink = pose(t)
    grip = 1 if t < 110 else 1-ease((t-110)/65) if t < 175 else 0 if t < 520 else ease((t-520)/60)
    r, dx, dy = math.radians(ba), .52, -98.1
    fx = (math.cos(r)*dx*bx-math.sin(r)*dy*bs-dx)*grip
    fy = (math.sin(r)*dx*bx+math.cos(r)*dy*bs-dy+by)*grip
    book_values = dict(translateY=by/K, rotation=ba, scaleX=bx, scaleY=bs)
    result = {name: dict(book_values) for name in ('book_back','book_front','paws')}
    result['character'] = dict(translateX=fx/K, translateY=(y+fy)/K, rotation=angle+ba*grip, scaleX=sx*mix(1,bx,grip), scaleY=sy*mix(1,bs,grip))
    result['arm_left'] = dict(rotation=-8*tuck)
    result['arm_right'] = dict(rotation=8*tuck)
    for side, direction in (('left',1), ('right',-1)):
        rotation = direction*4*tuck
        shift, a = -.8*tuck/K, math.radians(rotation)
        result['foot_'+side] = dict(rotation=rotation, translateX=-math.sin(a)*shift, translateY=math.cos(a)*shift)
    back_fan = 0 if t >= DURATION else fan
    front_fan = 0 if t >= DURATION else book(max(0,t-22))[4]
    result['page_back'] = dict(translateY=-1.1*back_fan/K, rotation=3.8*back_fan)
    result['page_front'] = dict(translateY=-.65*front_fan/K, rotation=2.6*front_fan)
    closed = blink > .78
    result['eyes'] = dict(scaleY=1 if closed else max(.24,1-blink*.72))
    result['eyes_open_0'] = dict(fillAlpha=0 if closed else 1)
    result['pupils_0'] = dict(fillAlpha=0 if closed else 1)
    result['eyes_closed_0'] = dict(strokeAlpha=1 if closed else 0)
    hands = 1-ease((t-140)/55)*(1-ease((t-510)/55))
    result['paw_left_0'] = result['paw_right_0'] = dict(fillAlpha=hands)
    return result


def group(parent, name, pivot=None, **values):
    if pivot:
        values.update(pivotX=80+270*pivot[0], pivotY=-15+405*pivot[1])
    return ET.SubElement(parent, 'group', attrs(name=name, **values))


def layer(parent, asset, color=INK, alpha=1):
    index = 0
    for element in ET.parse(ART / (asset+'.svg')).getroot().iter():
        tag = element.tag.split('}')[-1]
        if tag not in ('path','rect'):
            continue
        d = element.get('d')
        if tag == 'rect':
            x,y,w,h = (float(element.get(key,'0')) for key in ('x','y','width','height'))
            d = f'M{x},{y}h{w}v{h}h{-w}Z'
        # Book rectangles inherit their stroke from the SVG's one containing group.
        stroke = element.get('stroke') or ('black' if tag == 'rect' else None)
        fill = element.get('fill', 'none' if tag == 'rect' else 'black')
        values = dict(name=asset.replace('-','_')+'_'+str(index), pathData=d, fillColor=color if fill != 'none' else '#00000000', fillAlpha=alpha if fill != 'none' else 0)
        if stroke:
            values.update(strokeColor=color, strokeWidth=element.get('stroke-width','4'), strokeAlpha=alpha,
                          strokeLineCap=element.get('stroke-linecap','butt'), strokeLineJoin=element.get('stroke-linejoin','miter'))
        ET.SubElement(parent, 'path', attrs(**values))
        index += 1


def vector():
    root = ET.Element('vector', attrs(width='288dp', height='288dp', viewportWidth=288, viewportHeight=288))
    fit = group(root, 'mask_fit', pivotX=144, pivotY=220)
    art = group(fit, 'art', scaleX=.4834, scaleY=.4834, translateX=38.85, translateY=42.96)
    back = group(art, 'book_back', (.5,.91))
    layer(back,'book-paper',PAPER)
    layer(back,'book-linework')
    for side,pivot in (('back',(.85,.37)),('front',(.815,.395))):
        page = group(back,'page_'+side,pivot)
        layer(page,'page-'+side+'-paper',PAPER)
        layer(page,'page-'+side+'-line')
    character = group(art,'character',(.504,.407))
    layer(character,'body')
    for side,pivot in (('left',(.411,.395)),('right',(.604,.395))):
        arm = group(character,'arm_'+side,pivot)
        layer(arm,'arm-'+side)
        layer(arm,'arm-'+side+'-crease',PAPER)
    for side,pivot in (('left',(.45,.5)),('right',(.57,.5))):
        foot = group(character,'foot_'+side,pivot)
        layer(foot,'foot-'+side)
    layer(character,'belly',PAPER,.6)
    layer(character,'head-solid')
    layer(character,'face-details',PAPER)
    eyes = group(character,'eyes',(.5,.309))
    layer(eyes,'eyes-open',PAPER)
    layer(eyes,'pupils')
    layer(character,'eyes-closed',PAPER,0)
    front = group(art,'book_front',(.5,.91))
    layer(front,'book-front-paper',PAPER)
    ET.SubElement(front,'clip-path',attrs(pathData='M80,148.62H350V390H80Z'))
    layer(front,'book-linework')
    paws = group(art,'paws',(.5,.91))
    layer(paws,'paw-left')
    layer(paws,'paw-right')
    # Repeated book paths need distinct target names even though they are not animated.
    seen = set()
    for node in root.iter():
        key = f'{{{ANDROID}}}name'
        name = node.get(key)
        if name:
            if name in seen:
                node.set(key,name+'_front')
            seen.add(node.get(key))
    return root


def control_points(data):
    """Bezier control hulls conservatively include every rendered curve pixel."""
    tokens = re.findall(r'[A-Za-z]|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?', data)
    points, i, command, x, y, start = [], 0, '', 0., 0., (0.,0.)
    counts = dict(M=2,L=2,C=6,Q=4,H=1,V=1,Z=0)
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i]; i += 1
            if command.upper() == 'Z':
                x,y = start
                continue
        size = counts[command.upper()]
        values = list(map(float,tokens[i:i+size])); i += size
        relative, c = command.islower(), command.upper()
        if c in ('H','V'):
            if c == 'H': x = values[0]+(x if relative else 0)
            else: y = values[0]+(y if relative else 0)
            points.append((x,y))
        else:
            for j in range(0,size,2):
                points.append((values[j]+(x if relative else 0),values[j+1]+(y if relative else 0)))
            x,y = points[-1]
            if c == 'M': start=(x,y); command='l' if relative else 'L'
    return points


def geometry(root):
    result = []
    def visit(node, ancestors):
        if node.tag == 'group': ancestors = ancestors+[node]
        if node.tag == 'path':
            values = {key.split('}')[-1]: value for key,value in node.attrib.items()}
            # Include stroke width with a conservative halo in the final check.
            result.append((control_points(values['pathData']), ancestors, float(values.get('strokeWidth',0))/2))
        for child in node: visit(child,ancestors)
    visit(root,[])
    return result


def transformed(points, ancestors, frame):
    for x,y in points:
        for node in reversed(ancestors):
            values = {key.split('}')[-1]:value for key,value in node.attrib.items()}
            values.update(frame.get(values.get('name'),{}))
            px,py = float(values.get('pivotX',0)),float(values.get('pivotY',0))
            sx,sy = float(values.get('scaleX',1)),float(values.get('scaleY',1))
            r = math.radians(float(values.get('rotation',0)))
            dx,dy = (x-px)*sx,(y-py)*sy
            x = px+math.cos(r)*dx-math.sin(r)*dy+float(values.get('translateX',0))
            y = py+math.sin(r)*dx+math.cos(r)*dy+float(values.get('translateY',0))
        yield x,y


def fit_frame(shapes, frame):
    scale = 1.
    for points,ancestors,_ in shapes:
        for x,y in transformed(points,ancestors,frame):
            dx,dy = x-144,y-220
            a,b,c = dx*dx+dy*dy,152*dy,76*76-93*93
            if a > .0001: scale=min(scale,(-b+math.sqrt(b*b-4*a*c))/(2*a))
    frame['mask_fit'] = dict(scaleX=scale,scaleY=scale)


def xml_text(root, comment):
    ET.indent(root,space='    ')
    return '<?xml version="1.0" encoding="utf-8"?>\n<!-- '+comment+' -->\n'+ET.tostring(root,encoding='unicode')+'\n'


def generate(check=False):
    art = vector()
    shapes = geometry(art)
    times = sorted(set(range(0,DURATION+1,10))|{66,110,132,140,160,175,195,300,360,420,510,520,560,561,565,580,590,612,620,740,820})
    frames = [motion(t) for t in times]
    for frame in frames: fit_frame(shapes,frame)
    for node in art.iter():
        for key,value in frames[0].get(node.get(f'{{{ANDROID}}}name'),{}).items():
            node.set(f'{{{ANDROID}}}{key}',number(value))
    avd = ET.Element('animated-vector')
    ET.SubElement(avd,f'{{{AAPT}}}attr',{'name':'android:drawable'}).append(copy.deepcopy(art))
    for name,values in frames[0].items():
        changing = [key for key in values if max(frame[name][key] for frame in frames)-min(frame[name][key] for frame in frames)>.000001]
        if not changing: continue
        target = ET.SubElement(avd,'target',attrs(name=name))
        animation = ET.SubElement(target,f'{{{AAPT}}}attr',{'name':'android:animation'})
        animator = ET.SubElement(animation,'objectAnimator',attrs(duration=DURATION,interpolator='@android:interpolator/linear'))
        for key in changing:
            holder = ET.SubElement(animator,'propertyValuesHolder',attrs(propertyName=key,valueType='floatType'))
            for t,frame in zip(times,frames):
                ET.SubElement(holder,'keyframe',attrs(fraction=number(t/DURATION),value=number(frame[name][key])))
    outputs = {
        RES/'drawable/launch_mark.xml':xml_text(art,'Generated by docs/design/system/tools/gen_launch_splash.py. Static fallback and reduced-motion pose.'),
        RES/'drawable-v31/launch_mark.xml':xml_text(avd,'Generated Book hop: one 820 ms native animation; no added splash hold. Do not edit by hand.'),
    }
    for path,text in outputs.items():
        if check:
            if not path.exists() or path.read_text()!=text: raise SystemExit(f'Stale generated resource: {path}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
    # Validate interpolation between keyframes too, not just the sampled poses.
    maximum = 0.
    for t in range(0,DURATION+1,5):
        i = min(bisect.bisect_right(times,t),len(times)-1)
        a,b = max(0,i-1),i
        u = (t-times[a])/(times[b]-times[a]) if a!=b else 0
        frame = {name:{key:mix(values[key],frames[b][name][key],u) for key in values} for name,values in frames[a].items()}
        for points,ancestors,halo in shapes:
            for x,y in transformed(points,ancestors,frame):
                # .4834 is the largest source-to-dp scale; limb scales may stretch 10%.
                radius=math.hypot(x-144,y-144)+halo*.54
                maximum=max(maximum,radius)
    if maximum>96: raise SystemExit(f'Animation escapes the 192 dp mask: radius {maximum:.2f}')
    print(f'{"Verified" if check else "Generated"} native splash: {DURATION} ms, {len(times)} keyframes, maximum masked radius {maximum:.2f}/96 dp')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    generate(parser.parse_args().check)
