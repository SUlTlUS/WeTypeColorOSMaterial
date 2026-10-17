"""Record the API source revision and prove the local copies are unchanged."""
import hashlib
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent.parent
upstream = root / 'evidence/libxposed-api-102'
revision = subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip()
assert revision == '39cac0845771547c9c67a3e3ce255af110a54a0e'
source = upstream / 'api/src/main/java'
local = root / 'api-102/src/main/java'
hashes = {}
for path in source.rglob('*.java'):
    relative = path.relative_to(source)
    assert path.read_bytes() == (local / relative).read_bytes(), f'API source changed: {relative}'
    hashes[str(relative).replace('\\', '/')] = hashlib.sha256(path.read_bytes()).hexdigest()
jar = root / 'api-102/libs/annotation-1.0.0.jar'
result = {
    'api_repository': 'https://github.com/JingMatrix/libxposed-api',
    'api_revision': revision,
    'api_version': '102.0.0',
    'api_source_files_sha256': hashes,
    'annotation_url': 'https://repo.maven.apache.org/maven2/io/github/libxposed/annotation/1.0.0/annotation-1.0.0.jar',
    'annotation_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
    'module_dependency': 'compileOnly(project(":api-102"))',
    'api_sources_modified': False,
}
(root / 'evidence/source-provenance.json').write_text(
    json.dumps(result, indent=2), encoding='utf-8')
print(f'PASS: {len(hashes)} unmodified API source files from {revision}')
