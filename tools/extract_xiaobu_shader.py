"""Recover the NEW_G2 AGSL source exactly as Xiaobu's ShadowEdge builder assembles it."""
import hashlib
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source_path = root / 'evidence/xiaobu/sources/com/coui/appcompat/shadowedge/agsl/ShadowEdge.java'
source = source_path.read_text(encoding='utf-8')
literal = r'"(?:\\.|[^"\\])*"'
constants = {
    name: json.loads(value) for name, value in re.findall(
        rf'private static final String (AGSL_\w+) = ({literal});', source)
}
builder = source[source.index('private final String buildSourceInternal'):source.index('public final String buildSource(')]
initial = re.search(rf'StringBuilder sb = new StringBuilder\(({literal})\)', builder)
shadow_g2 = re.search(rf'else if \(i3 == 3\) \{{\s*sb.append\(({literal})\)', builder)
tail = re.search(rf'sb.append\(({literal})\);\s*int i4', builder)
assert initial and shadow_g2 and tail, 'Reference builder layout changed'
parts = [
    json.loads(initial.group(1)), constants['AGSL_CONER_NEW_G2'],
    constants['AGSL_BLEND_SHADOW_FUNCTION_PART1'], json.loads(shadow_g2.group(1)),
    json.loads(tail.group(1)), constants['AGSL_MAIN_FUNCTION_PART2_CORNER_NEW_G2'],
    constants['AGSL_MAIN_FUNCTION_PART3'],
]
shader = ''.join(parts)
assert 'vec4 main(vec2 fragCoord)' in shader
assert 'smoothSDF_D' in shader
destination = root / 'app/src/main/resources/material/xiaobu_shadowedge.agsl'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(shader, encoding='utf-8', newline='\n')
provenance = {
    'source': str(source_path.relative_to(root)).replace('\\', '/'),
    'source_sha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
    'corner_type': 'NEW_G2 (2)',
    'parts_sha256': [hashlib.sha256(part.encode()).hexdigest() for part in parts],
    'shader_sha256': hashlib.sha256(shader.encode()).hexdigest(),
    'preset': 'AbstractC0876s: EDGE_STYLE_2, SHADOW_STYLE_2, weight=2, radius=8dp',
}
(root / 'evidence/button-shader-provenance.json').write_text(
    json.dumps(provenance, indent=2), encoding='utf-8')
print(f'Extracted unmodified NEW_G2 shader: {len(shader)} characters, {len(parts)} parts')
