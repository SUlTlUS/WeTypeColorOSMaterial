"""Compile/render Xiaobu's unmodified shader off-device; this is not IME device acceptance."""
import json
import struct
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root / 'evidence/python-deps'))
import skia


def uniforms(width, height, dark, toolbar=False):
    scale = 3 * .34
    nw, nh = width / scale, height / scale
    radius = min(width, height) / 2 if toolbar else 24
    common = [width, height, 0, 0, width, height, radius, radius, radius, radius, 2.3 if toolbar else 2, 1]
    shadow = [1, 1, 1, (.4 if toolbar else .6) * (.2 if dark else .4), 0, 0,
              (140 if toolbar else 86) * .5 * scale, 0, (20 if toolbar else 10) * scale,
              1 + (.4 * 160 if toolbar else .1 * 600) / nw,
              1 + (.35 * 160 if toolbar else .07 * 600) / nh, 4, .9 if toolbar else .7,
              (20 if toolbar else 1) * .5 * scale, 4 if toolbar else 1, .7 if toolbar else 0]

    def fade(start, length):
        reference_width, reference_height = (160, 160) if toolbar else (600, 200)
        reference_perimeter = 2 * (reference_width + reference_height)
        perimeter = 2 * (nw + nh)
        end = (start + length) * reference_perimeter
        if end > reference_width:
            return ((end - reference_width + nw) - length * reference_perimeter) / perimeter, length * reference_perimeter / perimeter
        factor = (nw - (reference_width - end)) / perimeter
        return start * factor / (start + length), factor * length / (start + length)

    a, b = fade(.06, .4) if toolbar else fade(.18, .35)
    c, d = fade(.01, .28) if toolbar else fade(.25, .25)
    line_alpha = .2 if dark and not toolbar else 1
    edge = [1, 1, 1, 2, 4, 2, 4, .7 * line_alpha, (1 if toolbar else .6) * line_alpha,
            a, a + b, c, c + d, 0, 4, .8]
    data = common + [1] + shadow + [1] + edge
    assert len(data) == 46
    return skia.Data.MakeWithCopy(struct.pack('<46f', *data))


source = (root / 'app/src/main/resources/material/xiaobu_shadowedge.agsl').read_text(encoding='utf-8')
effect = skia.RuntimeEffect.MakeForShader(source)
assert effect is not None
surface = skia.Surface(1120, 570)
canvas = surface.getCanvas()
canvas.clear(0xfffafafa)
face = skia.Typeface.MakeFromFile('C:/Windows/Fonts/segoeui.ttf')
heading, labels, letter = skia.Font(face, 24), skia.Font(face, 17), skia.Font(face, 42)
width, height = 130, 150
samples = []
for dark, x in ((False, 30), (True, 580)):
    bg = 0xff0d0d0d if dark else 0xffe1e1e1
    fg = 0xfff2f2f2 if dark else 0xff1d1d1f
    canvas.drawRoundRect(skia.Rect.MakeXYWH(x, 30, 510, 500), 24, 24, skia.Paint(Color=bg))
    canvas.drawString('Dark' if dark else 'Light', x + 24, 73, heading, skia.Paint(Color=fg))
    for index, (label, text, color) in enumerate([
        ('Normal', 'A', 0x26ffffff if dark else 0x8affffff),
        ('Pressed', 'A', 0x3dffffff if dark else 0x85bbbec2),
        ('Action', 'Enter', 0x9907c160),
    ]):
        left, top = x + 28 + index * 160, 108
        canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(skia.Rect.MakeWH(width, height))
        canvas.drawRoundRect(skia.Rect.MakeWH(width, height), 24, 24, skia.Paint(Color=color))
        shader = effect.makeShader(uniforms(width, height, dark))
        assert shader is not None
        canvas.drawRect(skia.Rect.MakeWH(width, height), skia.Paint(Shader=shader))
        font = labels if label == 'Action' else letter
        text_color = 0xfff2f2f2 if dark or label == 'Action' else 0xff1d1d1f
        canvas.drawString(text, (width - font.measureText(text)) / 2, 88, font, skia.Paint(Color=text_color))
        canvas.restore()
        canvas.drawString(label, left + 22, 292, labels, skia.Paint(Color=fg))
        samples.append({'dark': dark, 'state': label, 'fill_argb': f'{color:08x}'})
    for index, (label, color) in enumerate([
        ('Toolbar', 0x26ffffff if dark else 0x8affffff),
        ('Pressed', 0x3dffffff if dark else 0x85bbbec2),
    ]):
        diameter = 90
        left, top = x + 28 + index * 160, 350
        canvas.save()
        canvas.translate(left, top)
        canvas.clipRect(skia.Rect.MakeWH(diameter, diameter))
        canvas.drawOval(skia.Rect.MakeWH(diameter, diameter), skia.Paint(Color=color))
        shader = effect.makeShader(uniforms(diameter, diameter, dark, toolbar=True))
        assert shader is not None
        canvas.drawRect(skia.Rect.MakeWH(diameter, diameter), skia.Paint(Shader=shader))
        canvas.drawString('M', 25, 58, letter, skia.Paint(Color=fg))
        canvas.restore()
        canvas.drawString(label, left, 483, labels, skia.Paint(Color=fg))
        samples.append({'dark': dark, 'state': label, 'shape': 'circle', 'fill_argb': f'{color:08x}'})
canvas.drawString('Offline shader preview - solid sample backdrop', 30, 556, labels, skia.Paint(Color=0xff555555))
destination = root / 'evidence/key-material-preview.png'
surface.makeImageSnapshot().save(str(destination), skia.kPNG)
report = {'shader_compile': 'pass', 'render': 'pass', 'renderer': 'Skia CPU RuntimeEffect 144',
          'uniform_bytes': 184, 'samples': samples, 'device_acceptance': 'not verified'}
(root / 'evidence/key-shader-validation.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print('PASS: original AGSL compiled and ten key/toolbar material samples rendered')
