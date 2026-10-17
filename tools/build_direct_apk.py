"""Build a standalone ColorOS material edition of the supplied WeType 4.0.0 APK.

The original APK, manifest, resources, native libraries and module sources are preserved.
Only selected DEX methods are instrumented; the material runtime is added as a new DEX.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SUPPORTED_SOURCE_SHA256 = 'f7f21c78d293c0d144f52f461b9c75b2d631d23f2a37ee1107eb53b35d86df4c'
DIRECT_REVISION = 'r2'
ARTIFACTS = [
    ('org.smali', 'dexlib2', '2.5.2'), ('org.smali', 'smali', '2.5.2'),
    ('org.smali', 'util', '2.5.2'), ('org.antlr', 'antlr-runtime', '3.5.2'),
    ('org.antlr', 'antlr', '3.5.2'), ('org.antlr', 'stringtemplate', '3.2.1'),
    ('com.beust', 'jcommander', '1.64'), ('com.google.guava', 'guava', '27.1-android'),
    ('com.google.code.findbugs', 'jsr305', '3.0.2'),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, cwd=ROOT)


def module_version():
    config = (ROOT / 'app/build.gradle.kts').read_text('utf-8')
    match = re.search(r'\bversionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', config)
    if match is None:
        raise ValueError('Cannot determine the material module version')
    version = match.group(1)
    entry = (ROOT / 'app/src/main/java/dev/coloros/wetype/material/WeTypeMaterialModule.java').read_text('utf-8')
    if f'; module={version}; process=' not in entry:
        raise ValueError('Module entry log version differs from Gradle versionName')
    return version


def material_source_hashes():
    return {
        path.relative_to(ROOT).as_posix(): sha(path)
        for path in sorted((ROOT / 'app/src/main/java/dev/coloros/wetype/material').glob('*.java'))
        if path.name not in ('MainActivity.java', 'InfoActivity.java')
    }


def dependencies():
    directory = ROOT / 'direct/tooling'
    directory.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / 'direct/tooling-lock.json').read_text('utf-8'))
    paths = []
    for group, artifact, version in ARTIFACTS:
        name = f'{artifact}-{version}.jar'
        path = directory / name
        if not path.exists():
            url = f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{name}'
            urllib.request.urlretrieve(url, path)
        if sha(path) != lock[name]:
            raise ValueError(f'Tool dependency hash mismatch: {path}')
        paths.append(str(path))
    return os.pathsep.join(paths)


def generate_runtime(work):
    destination = work / 'src/dev/coloros/wetype/material'
    destination.mkdir(parents=True, exist_ok=True)
    generated = []
    for path in (ROOT / 'app/src/main/java/dev/coloros/wetype/material').glob('*.java'):
        if path.name in ('MainActivity.java', 'InfoActivity.java'):
            continue
        text = path.read_text('utf-8')
        text = text.replace('import io.github.libxposed.api.XposedModule;',
                            'import dev.coloros.wetype.direct.DirectModule;')
        text = text.replace('import io.github.libxposed.api.error.XposedFrameworkError;',
                            'import dev.coloros.wetype.direct.DirectModule.FrameworkError;')
        text = text.replace('import io.github.libxposed.api.XposedInterface;',
                            'import dev.coloros.wetype.direct.DirectModule;')
        text = text.replace('extends XposedModule', 'extends DirectModule')
        text = text.replace('XposedFrameworkError', 'FrameworkError')
        text = text.replace('XposedInterface.Invoker', 'DirectModule.Invoker')
        text = text.replace('API 102 hooks installed for WeType ', 'Direct APK callbacks installed for WeType ')
        if path.name == 'WeTypeMaterialModule.java':
            anchor = '        refreshAfterFirstDraw();\n    }\n\n    private void requestRefresh()'
            assert text.count(anchor) == 1
            text = text.replace(anchor, '        refreshAfterFirstDraw();\n'
                                '        dev.coloros.wetype.direct.DrawGuard.watch(view);\n'
                                '    }\n\n    private void requestRefresh()')
        if path.name == 'FloatingMaterial.java':
            anchor = '        module.info("floating popup material bound");'
            assert text.count(anchor) == 1, 'Floating popup binding anchor changed'
            text = text.replace(anchor,
                                '        dev.coloros.wetype.direct.DrawGuard.watch(view);\n'
                                '        module.info("floating popup material bound");')
        if 'io.github.libxposed' in text:
            raise ValueError(f'Unconverted framework API in {path.name}')
        output = destination / path.name
        output.write_text(text, encoding='utf-8')
        generated.append(output)
    return generated + list((ROOT / 'direct/src').rglob('*.java'))


def main():
    version = module_version()
    source_hashes = material_source_hashes()
    shader_hash = sha(ROOT / 'app/src/main/resources/material/xiaobu_shadowedge.agsl')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / f'dist/WeType-4.0.0-ColorOSMaterial-{version}-direct-{DIRECT_REVISION}.apk')
    parser.add_argument('--sdk', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk')
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-21.0.10')))
    args = parser.parse_args()
    source, output = args.input.resolve(), args.output.resolve()
    if source == output:
        raise ValueError('Input and output must differ')
    source_sha256 = sha(source)
    if source_sha256 != SUPPORTED_SOURCE_SHA256:
        raise ValueError('Input SHA-256 differs from the inspected WeType 4.0.0 APK; a new mapping review is required')
    # Reusable work area owned by this builder. No recursive removal of other artifacts.
    work = ROOT / 'direct/build' / f'{version}-{DIRECT_REVISION}'
    work.mkdir(parents=True, exist_ok=True)
    if (work / 'input-sha256.txt').exists() and (work / 'input-sha256.txt').read_text() != source_sha256:
        raise ValueError('Different source APK: use a separate workspace or remove only direct/build')
    (work / 'input-sha256.txt').write_text(source_sha256)
    from verify_apks import symbols
    definitions, classes, _ = symbols(source)
    if ('Lcom/tencent/wetype/plugin/hld/feature/a;', 'x2', '()Z') not in definitions:
        raise ValueError('This patch profile requires the supplied WeType 4.0.0 APK')
    if any(cls.startswith('Ldev/coloros/wetype/') for cls in classes):
        raise ValueError('Source already contains material runtime')
    java, javac = args.java_home / 'bin/java.exe', args.java_home / 'bin/javac.exe'
    build_tools = args.sdk / 'build-tools/37.0.0'
    android = args.sdk / 'platforms/android-37.0/android.jar'
    if not android.is_file():
        raise FileNotFoundError(f'Android 37 SDK missing: {android}')
    classpath = dependencies()
    patcher_classes = work / 'patcher-classes'
    patcher_classes.mkdir(exist_ok=True)
    run(javac, '-encoding', 'UTF-8', '-cp', classpath, '-d', patcher_classes, ROOT / 'direct/ApkInstrumenter.java')
    run(java, '-Xmx3g', '-cp', str(patcher_classes) + os.pathsep + classpath,
        'ApkInstrumenter', source, work / 'patched')
    sources = generate_runtime(work)
    runtime_jar = work / 'runtime.jar'
    with tempfile.TemporaryDirectory(prefix='runtime-classes-', dir=work) as folder:
        runtime_classes = Path(folder)
        run(javac, '-encoding', 'UTF-8', '--release', '17', '-cp', android, '-d', runtime_classes, *sources)
        with zipfile.ZipFile(runtime_jar, 'w') as archive:
            for path in sorted(runtime_classes.rglob('*.class')):
                archive.write(path, path.relative_to(runtime_classes).as_posix())
    dex_dir = work / 'runtime-dex'
    dex_dir.mkdir(exist_ok=True)
    run(java, '-cp', build_tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release',
        '--min-api', '33', '--lib', android, '--output', dex_dir, runtime_jar)
    unsigned, aligned = work / 'unsigned.apk', work / 'aligned.apk'
    replaced, preserved, layouts = [], [], {}
    from direct_xml import patch_ime_frames, FRAME_IDS
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(unsigned, 'w') as result:
        names = original.namelist()
        dex_numbers = [int(re.fullmatch(r'classes(\d*)\.dex', name).group(1) or '1')
                       for name in names if re.fullmatch(r'classes\d*\.dex', name)]
        next_dex = max(dex_numbers) + 1
        for entry in original.infolist():
            if re.match(r'META-INF/(?:[^/]+\.(?:RSA|DSA|EC|SF)|MANIFEST\.MF)$', entry.filename, re.I):
                continue
            patched = work / 'patched' / entry.filename
            if re.fullmatch(r'classes\d*\.dex', entry.filename) and patched.exists():
                data = patched.read_bytes()
                replaced.append(entry.filename)
            else:
                data = original.read(entry)
                if entry.filename.endswith('.xml'):
                    data, edits = patch_ime_frames(data)
                    if edits:
                        layouts[entry.filename] = edits
                if entry.filename not in layouts:
                    preserved.append(entry.filename)
            result.writestr(entry, data)
        result.writestr(f'classes{next_dex}.dex', (dex_dir / 'classes.dex').read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.write(ROOT / 'app/src/main/resources/material/xiaobu_shadowedge.agsl', 'material/xiaobu_shadowedge.agsl')
    if not FRAME_IDS <= {edit['id'] for edits in layouts.values() for edit in edits}:
        raise ValueError('Verified IME drawing containers missing from source APK')
    run(build_tools / 'zipalign.exe', '-f', '-P', '16', '4', unsigned, aligned)
    output.parent.mkdir(parents=True, exist_ok=True)
    signer = build_tools / 'lib/apksigner.jar'
    run(java, '-jar', signer, 'sign', '--ks', Path.home() / '.android/debug.keystore',
        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', output, aligned)
    run(java, '-jar', signer, 'verify', '--verbose', '--print-certs', output)
    run(build_tools / 'zipalign.exe', '-c', '-P', '16', '4', output)
    final_definitions, final_classes, _ = symbols(output)
    assert not any('xposed' in cls.lower() for cls in final_classes - classes)
    for owner, name, proto in definitions:
        assert (owner, name, proto) in final_definitions, f'Original API missing: {owner} {name} {proto}'
    methods = (work / 'patched/patched-methods.txt').read_text().splitlines()
    for key in methods:
        if key.startswith('framework:'):
            continue
        owner, method = key.split('->')
        name, proto = method.split('(', 1)
        assert (owner, '$wetype$original$' + name, '(' + proto) in final_definitions
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output) as final:
        for name in preserved:
            assert original.read(name) == final.read(name), f'Unexpected entry change: {name}'
        from verify_apks import xml_id_tree
        for name in layouts:
            expected, _ = patch_ime_frames(original.read(name))
            assert final.read(name) == expected, f'Unexpected layout patch: {name}'
            assert xml_id_tree(original.read(name)) == xml_id_tree(final.read(name)), f'Layout IDs or hierarchy changed: {name}'
    report = {
        'source': str(source), 'source_sha256': source_sha256, 'output': str(output), 'output_sha256': sha(output),
        'material_version': version, 'direct_revision': DIRECT_REVISION, 'profile': '4.0.0', 'patched_dex': replaced,
        'runtime_dex': f'classes{next_dex}.dex', 'patched_methods': methods,
        'preserved_entries': len(preserved), 'manifest_resource_table_and_native_libraries_unchanged': True,
        'ime_layout_tag_changes': layouts, 'layout_ids_attributes_and_hierarchy_preserved': True,
        'drawing_backend': 'FrameLayout.drawChild callbacks; no clipBounds simulation',
        'original_method_signatures_preserved': True, 'new_xposed_classes': 0,
        'original_bytecode_preserved': True, 'wrapper_routes_verified': True,
        'apk_signature_verified': True, 'alignment_16kb_verified': True,
        'device_validation': 'not run', 'signing': 'Android local debug certificate; differs from official signature',
    }
    if material_source_hashes() != source_hashes or module_version() != version:
        raise ValueError('Module sources changed while building; rebuild against a stable snapshot')
    if sha(ROOT / 'app/src/main/resources/material/xiaobu_shadowedge.agsl') != shader_hash:
        raise ValueError('Shader changed while building; rebuild against a stable snapshot')
    report['material_sources_sha256'] = source_hashes
    report['shader_sha256'] = shader_hash
    report_path = ROOT / f'evidence/direct-apk-verification-{version}-{DIRECT_REVISION}.json'
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f'PASS: standalone APK {output}\nReport: {report_path}', flush=True)


if __name__ == '__main__':
    main()
