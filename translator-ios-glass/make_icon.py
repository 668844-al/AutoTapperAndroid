from PIL import Image, ImageDraw, ImageFont

size = 256
im = Image.new('RGBA', (size, size), (0,0,0,0))
p = im.load()
for y in range(size):
    for x in range(size):
        t = (x+y)/(2*(size-1))
        if t < .55:
            q=t/.55
            c=(int(102+(155-102)*q), int(118+(99-118)*q), int(255+(232-255)*q), 255)
        else:
            q=(t-.55)/.45
            c=(int(155+(239-155)*q), int(99+(114-99)*q), int(232+(170-232)*q), 255)
        p[x,y]=c
mask=Image.new('L',(size,size),0)
d=ImageDraw.Draw(mask)
d.rounded_rectangle((8,8,size-8,size-8), radius=58, fill=255)
im.putalpha(mask)
ov=Image.new('RGBA',(size,size),(0,0,0,0)); od=ImageDraw.Draw(ov)
od.rounded_rectangle((24,20,size-24,102), radius=36, fill=(255,255,255,38))
im=Image.alpha_composite(im,ov)
font=None
for fp in [r'C:\Windows\Fonts\msyhbd.ttc', r'C:\Windows\Fonts\msyh.ttc', r'C:\Windows\Fonts\segoeuib.ttf']:
    try:
        font=ImageFont.truetype(fp, 104)
        break
    except Exception:
        pass
d=ImageDraw.Draw(im)
text='译' if font else 'EN'
if font is None:
    font=ImageFont.load_default()
bbox=d.textbbox((0,0), text, font=font)
tw,th=bbox[2]-bbox[0],bbox[3]-bbox[1]
d.text(((size-tw)/2,(size-th)/2-8), text, font=font, fill='white')
im.save('app.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
