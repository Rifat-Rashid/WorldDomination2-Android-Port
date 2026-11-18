"""Build hidden Help BMFonts from packaged authored TTFs, never system fonts.

Pillow is a build-time tool only. No reference checkout or Python runtime is
needed by Android. Cmap inventory is retained; the atlas covers printable Latin,
punctuation and currency used by the original English command UI.
"""
from pathlib import Path
import gzip, hashlib, io, json, struct
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/original/ui/help/hidden-art'

def cmap(raw):
    tables={raw[12+i*16:16+i*16].decode():struct.unpack_from('>II',raw,20+i*16)
            for i in range(struct.unpack_from('>H',raw,4)[0])}
    base=tables['cmap'][0]
    offsets=[struct.unpack_from('>I',raw,base+8+i*8)[0]
             for i in range(struct.unpack_from('>H',raw,base+2)[0])]
    off=next(base+x for x in offsets if struct.unpack_from('>H',raw,base+x)[0]==4)
    count=struct.unpack_from('>H',raw,off+6)[0]//2
    ends=struct.unpack_from('>'+str(count)+'H',raw,off+14)
    starts=struct.unpack_from('>'+str(count)+'H',raw,off+16+count*2)
    deltas=struct.unpack_from('>'+str(count)+'h',raw,off+16+count*4)
    ranges=struct.unpack_from('>'+str(count)+'H',raw,off+16+count*6)
    found={}
    for i,(start,end,delta,ran) in enumerate(zip(starts,ends,deltas,ranges)):
        for cp in range(start,min(end,65534)+1):
            gid=(cp+delta)&65535 if ran==0 else struct.unpack_from('>H',raw,off+16+count*6+i*2+ran+(cp-start)*2)[0]
            if ran and gid:gid=(gid+delta)&65535
            if gid:found[cp]=gid
    assert 'kern' not in tables and 'GPOS' not in tables, 'Handle original kerning before exporting'
    return found

def export(name,source,size):
    packed=ROOT/'app/src/main/assets/original/runtime/fonts'/source
    raw=gzip.decompress(packed.read_bytes()); available=cmap(raw)
    font=ImageFont.truetype(io.BytesIO(raw),size,layout_engine=ImageFont.Layout.BASIC)
    chars=[cp for cp in available if 32<=cp<=591 or 8192<=cp<=8303 or 8352<=cp<=8399]
    chars=[cp for cp in chars if chr(cp).isprintable()]
    ascent,descent=font.getmetrics(); width=2048; x=y=2; row=0; glyphs=[]
    for cp in chars:
        box=font.getbbox(chr(cp),anchor='ls'); advance=font.getlength(chr(cp))
        assert advance==int(advance)
        left,top,right,bottom=box; w,h=right-left,bottom-top
        if x+w+2>width:x=2;y+=row+4;row=0
        glyphs.append(dict(id=cp,x=x,y=y,width=w,height=h,xoffset=left,
                           yoffset=ascent+top,xadvance=int(advance),pillowBaselineBounds=list(box)))
        x+=w+4;row=max(row,h)
    height=1
    while height<y+row+2:height*=2
    image=Image.new('RGBA',(width,height))
    draw=ImageDraw.Draw(image)
    for g in glyphs:
        if g['width'] and g['height']:
            draw.text((g['x']-g['xoffset'],g['y']-g['pillowBaselineBounds'][1]),chr(g['id']),font=font,anchor='ls',fill='white')
    # Keep RGB white even outside ink so linear minification cannot bleed black.
    alpha=image.getchannel('A'); image=Image.new('RGBA',image.size,'white');image.putalpha(alpha)
    image.save(OUT/(name+'.png'))
    lines=[f'info face="{font.getname()[0]}" size={size} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0',
           f'common lineHeight={ascent+descent} base={ascent} scaleW={width} scaleH={height} pages=1 packed=0',
           f'page id=0 file="{name}.png"',f'chars count={len(glyphs)}']
    for g in glyphs:lines.append('char '+' '.join(f'{k}={g[k]}' for k in ('id','x','y','width','height','xoffset','yoffset','xadvance'))+' page=0 chnl=15')
    lines.append('kernings count=0')
    (OUT/(name+'.fnt')).write_text('\n'.join(lines)+'\n',encoding='utf8')
    meta=dict(source=source,sourcePackedSHA256=hashlib.sha256(packed.read_bytes()).hexdigest(),
              sourceTTFSHA256=hashlib.sha256(raw).hexdigest(),size=size,ascent=ascent,descent=descent,
              sourceRasterScale=4,sourceAnchor='mt for centered dynamic text; visible glyph top aligned per string',
              cmapAvailableCount=len(available),cmapCodepoints=list(available),atlasCodepoints=chars,
              kerning='No kern or GPOS table in authored font; Pillow BASIC integer advances',glyphs=glyphs,
              probes={s:dict(advance=font.getlength(s),bboxLT=font.getbbox(s,anchor='lt')) for s in ('trump','I am Bush','Chapter 8')})
    (OUT/(name+'.json')).write_text(json.dumps(meta,indent=2)+'\n')
    print(name,len(chars),'of',len(available),'available',image.size,meta['probes'])

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    export('hidden-font-command-120','4439_Arial_30pt_st.ttf.gz',120)
    export('hidden-font-warning-40','3322_Arial_10pt_st.ttf.gz',40)
