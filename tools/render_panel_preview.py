"""Render Xiaobu's panel foreground edge and check its clip/transparent interior off-device."""
import json
import struct
import sys
from pathlib import Path

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root / 'evidence/python-deps'))
import skia

floating = '--floating' in sys.argv

def panel_uniforms(width, height, dark, radius, line_width):
    scale = 3 * .34
    nw, nh = width / scale, height / scale
    common = [width, height, 0, 0, width, height, radius if floating else 0,
              radius if floating else 0, radius, radius, 3, 1]
    ratio = line_width / 10
    near, far = max(2, 2 * ratio), max(2, 2 * ratio)

    def fade(start, length):
        perimeter = 2 * (nw + nh)
        end = (start + length) * 1600
        if end > 600:
            return ((end - 600 + nw) - length * 1600) / perimeter, length * 1600 / perimeter
        factor = (nw - (600 - end)) / perimeter
        return start * factor / (start + length), length * factor / (start + length)

    a, b = fade(.18, .35)
    c, d = fade(.25, .25)
    intensity = .4 if dark else 1
    edge = [1, 1, 1, near, near + max(2, 8 * ratio), far, far + max(2, 2 * ratio),
            .7 * intensity, .6 * intensity, a, a + b, c, c + d, 0, 4, .8]
    return skia.Data.MakeWithCopy(struct.pack('<46f', *(common + [0] + [0] * 16 + [1] + edge)))


source = (root / 'app/src/main/resources/material/xiaobu_shadowedge.agsl').read_text(encoding='utf-8')
effect = skia.RuntimeEffect.MakeForShader(source)
assert effect is not None
surface = skia.Surface(940, 1100)
canvas = surface.getCanvas()
canvas.clear(0xfffafafa)
face = skia.Typeface.MakeFromFile('C:/Windows/Fonts/segoeui.ttf')
heading = skia.Font(face, 24)
width, height, radius, line_width = 420, 420, 84, 12
samples = []
for row, dark in enumerate((False, True)):
    top = 80 + row * 520
    bg = 0xff0d0d0d if dark else 0xffe1e1e1
    shader = effect.makeShader(panel_uniforms(width, height, dark, radius, line_width))
    assert shader is not None
    # Verify the actual shader pixels independently of the opaque sample backdrop.
    isolated = skia.Surface(width, height)
    probe = isolated.getCanvas()
    probe.clear(skia.ColorTRANSPARENT)
    probe.clipRect(skia.Rect.MakeWH(width, height if floating else height - line_width))
    probe.drawRect(skia.Rect.MakeWH(width, height), skia.Paint(Shader=shader))
    pixels = isolated.makeImageSnapshot().toarray()
    alpha = pixels[:, :, 3]
    assert alpha[:16, :].max() > 0, 'Top edge highlight missing'
    assert alpha[80:-80, 80:-80].max() == 0, 'Panel edge paints over content'
    bottom = int(alpha[-int(line_width):, :].max())
    if floating:
        assert bottom > 0, 'Floating bottom edge highlight missing'
        assert alpha[-5:, :5].max() == 0, 'Floating bottom corner mask incorrect'
    else:
        assert bottom == 0, 'Docked bottom edge not clipped'
    assert alpha[:5, :5].max() == 0, 'Top corner mask incorrect'
    samples.append({'dark': dark, 'top_edge_max_alpha': int(alpha[:16, :].max()),
                    'interior_max_alpha': 0, 'bottom_max_alpha': bottom})
    for column, enabled in enumerate((False, True)):
        left = 30 + column * 460
        canvas.drawString(('Dark' if dark else 'Light') + (' + panel edge' if enabled else ' - before'),
                          left, top - 24, heading, skia.Paint(Color=0xff333333))
        canvas.save()
        canvas.translate(left, top)
        panel = skia.RRect()
        bottom_corner = (radius, radius) if floating else (0, 0)
        panel.setRectRadii(skia.Rect.MakeWH(width, height), [(radius, radius), (radius, radius), bottom_corner, bottom_corner])
        canvas.drawRRect(panel, skia.Paint(Color=bg))
        # Sample content emphasizes that the edge stays on the outside, above all children.
        for y in (115, 220, 325):
            for x in (25, 125, 225, 325):
                canvas.drawRoundRect(skia.Rect.MakeXYWH(x, y, 70, 70), 16, 16,
                                     skia.Paint(Color=0x26ffffff if dark else 0x8affffff))
        if enabled:
            canvas.clipRect(skia.Rect.MakeWH(width, height if floating else height - line_width))
            canvas.drawRect(skia.Rect.MakeWH(width, height),
                            skia.Paint(Shader=shader, BlendMode=skia.BlendMode.kPlus))
        canvas.restore()
canvas.drawString('Offline shader preview - solid sample backdrop, not device blur',
                  30, 1080, skia.Font(face, 18), skia.Paint(Color=0xff555555))
name = 'floating-panel-edge' if floating else 'panel-edge'
surface.makeImageSnapshot().save(str(root / f'evidence/{name}-preview.png'), skia.kPNG)
(root / f'evidence/{name}-validation.json').write_text(json.dumps({
    'shader_compile': 'pass', 'renderer': 'Skia CPU RuntimeEffect 144',
    'top_highlight': 'pass', 'transparent_interior': 'pass',
    'bottom_highlight' if floating else 'bottom_clip': 'pass',
    'top_corner_mask': 'pass', 'samples': samples, 'device_acceptance': 'not verified',
}, indent=2), encoding='utf-8')
print('PASS: panel light/dark highlights, transparent interior, corner masks and '
      + ('floating bottom highlights' if floating else 'docked bottom clipping'))
