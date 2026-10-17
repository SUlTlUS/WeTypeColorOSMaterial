"""Check hook signatures against actual DEX names, not JADX's restored aliases."""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def dex_symbols(data, field_definitions=None):
    def u4(offset):
        return struct.unpack_from('<I', data, offset)[0]

    def leb(offset):
        value, shift = 0, 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                return value, offset
            shift += 7

    def table(header):
        return u4(header), u4(header + 4)

    count, offset = table(0x38)
    strings = []
    for i in range(count):
        _, start = leb(u4(offset + i * 4))
        end = data.index(b'\0', start)
        strings.append(data[start:end].decode('utf-8', errors='replace'))
    count, offset = table(0x40)
    types = [strings[u4(offset + i * 4)] for i in range(count)]
    count, offset = table(0x48)
    protos = []
    for i in range(count):
        _, return_type, params_offset = struct.unpack_from('<III', data, offset + i * 12)
        params = [] if not params_offset else [
            types[struct.unpack_from('<H', data, params_offset + 4 + j * 2)[0]]
            for j in range(u4(params_offset))]
        protos.append('(' + ''.join(params) + ')' + types[return_type])
    count, offset = table(0x50)
    fields = []
    for i in range(count):
        cls, field_type, name = struct.unpack_from('<HHI', data, offset + i * 8)
        fields.append((types[cls], strings[name], types[field_type]))
    count, offset = table(0x58)
    methods = []
    for i in range(count):
        cls, proto, name = struct.unpack_from('<HHI', data, offset + i * 8)
        methods.append((types[cls], strings[name], protos[proto]))
    definitions, classes = set(), set()
    count, offset = table(0x60)
    for i in range(count):
        class_offset = offset + i * 32
        classes.add(types[u4(class_offset)])
        class_data = u4(class_offset + 24)
        if not class_data:
            continue
        sizes = []
        for _ in range(4):
            size, class_data = leb(class_data)
            sizes.append(size)
        for size in sizes[:2]:
            index = 0
            for _ in range(size):
                delta, class_data = leb(class_data)
                index += delta
                _, class_data = leb(class_data)
                if field_definitions is not None:
                    field_definitions.add(fields[index])
        for size in sizes[2:]:
            index = 0
            for _ in range(size):
                delta, class_data = leb(class_data)
                index += delta
                _, class_data = leb(class_data)
                _, class_data = leb(class_data)
                definitions.add(methods[index])
    return definitions, classes, set(methods)


def symbols(path, field_definitions=None):
    definitions, classes, references = set(), set(), set()
    with zipfile.ZipFile(path) as archive:
        for entry in archive.namelist():
            if entry.startswith('classes') and entry.endswith('.dex'):
                ds, cs, rs = dex_symbols(archive.read(entry), field_definitions)
                definitions.update(ds)
                classes.update(cs)
                references.update(rs)
    return definitions, classes, references


def xml_id_tree(data):
    """Read Android binary XML element nesting and android:id without decompiler aliases."""
    resource_map, roots, stack = [], [], []
    offset = struct.unpack_from('<H', data, 2)[0]
    while offset < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, offset)
        assert size >= header >= 8, 'Malformed binary XML chunk'
        if kind == 0x180:
            resource_map = list(struct.unpack_from('<' + 'I' * ((size - header) // 4), data, offset + header))
        elif kind == 0x102:
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', data, offset + 24)
            node = {'id': None, 'children': []}
            for i in range(attr_count):
                attr = offset + 16 + attr_start + i * attr_size
                name = struct.unpack_from('<I', data, attr + 4)[0]
                if name < len(resource_map) and resource_map[name] == 0x010100d0:
                    node['id'] = struct.unpack_from('<I', data, attr + 16)[0]
            (stack[-1]['children'] if stack else roots).append(node)
            stack.append(node)
        elif kind == 0x103:
            stack.pop()
        offset += size
    assert not stack, 'Unbalanced binary XML'
    return roots


def verify_clipboard_stack(path):
    def nodes(tree):
        for node in tree:
            yield node
            yield from nodes(node['children'])

    ids = [2131296987, 2131296948, 2131296949, 2131296931, 2131296487, 2131296952]
    markers = [struct.pack('<I', value) for value in ids]
    with zipfile.ZipFile(path) as archive:
        for entry in archive.namelist():
            if not entry.endswith('.xml'):
                continue
            data = archive.read(entry)
            if not all(marker in data for marker in markers):
                continue
            stack = next(n for n in nodes(xml_id_tree(data)) if n['id'] == ids[0])
            children = stack['children']
            base = next(i for i, n in enumerate(children)
                        if {ids[3], ids[4]} <= {v['id'] for v in nodes([n])})
            overlays = [next(i for i, n in enumerate(children) if n['id'] == slot)
                        for slot in [ids[5], *ids[1:3]]]
            assert all(base < index for index in overlays), 'Clipboard does not cover the base keyboard branch'
            return {'resource': entry, 'stack_id': ids[0], 'base_branch_index': base,
                    'overlay_branch_indices': overlays, 'policy': 'omit earlier sibling drawing only'}
    raise AssertionError('Clipboard overlay hierarchy not found in target APK')


def verify_auxiliary_layouts(path):
    """Find page layouts by actual root class, not version-specific obfuscated filenames."""
    expected = {
        'S10SettingKeyboardTypeView': {2131296424, 2131296615, 2131297515, 2131297516},
        'S30KeyboardChooseKeyboard': {2131296986},
        'S11EmojiKeyboard': set(),
    }
    found = {}

    def ids(nodes):
        for node in nodes:
            yield node['id']
            yield from ids(node['children'])

    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith('.xml'):
                continue
            data = archive.read(name)
            for cls, required in expected.items():
                if cls.encode() not in data and cls.encode('utf-16le') not in data:
                    continue
                present = set(ids(xml_id_tree(data)))
                assert required <= present, f'{cls} layout IDs missing: {required - present}'
                found[cls] = {'resource': name, 'verified_ids': sorted(required)}
    assert set(found) == set(expected), 'Auxiliary IME page layout missing'
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--wetype', type=Path, required=True)
    parser.add_argument('--xiaobu', type=Path,
                        help='Optional reference APK for rechecking unchanged OEM API signatures')
    parser.add_argument('--module', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    field_definitions = set()
    definitions, target_classes, _ = symbols(args.wetype, field_definitions)
    prefix = 'Lcom/tencent/wetype/plugin/hld/'
    profile = '4.0.0' if (prefix + 'keyboard/selfdraw/drawmethod/a;', 'c',
                          '()' + prefix + 'keyboard/selfdraw/o;') in definitions else '3.5.5'
    theme_type = 'm1' if profile == '4.0.0' else 'n1'
    keyboard_type = 'o' if profile == '4.0.0' else 'n'
    t9_method = 'b2' if profile == '4.0.0' else 'a2'
    feature_method = 'x2' if profile == '4.0.0' else 'u2'
    checks = [
        ('frosted/f;', 'n', '(Landroid/view/View;Z)V'),
        ('frosted/f;', 'c', '(Landroid/view/Window;)V'),
        ('frosted/f;', 'd', '(Landroid/view/Window;ZZ)V'),
        ('frosted/f;', 'h', '()Z'),
        ('frosted/f;', 'k', '(Z)V'),
        ('frosted/f;', 'i', '(Landroid/view/Window;)V'),
        ('feature/a;', feature_method, '()Z'),
        ('frosted/i;', 'e', '(Z)I'),
        ('frosted/i;', 'f', '(Z)I'),
        (f'utils/{theme_type};', 'E2', '(ZLjava/lang/String;)V'),
        (f'utils/{theme_type};', 'F2', f'({prefix}utils/{theme_type};ZLjava/lang/String;ILjava/lang/Object;)V'),
        ('keyboard/selfdraw/drawmethod/a;', 'c', f'(){prefix}keyboard/selfdraw/{keyboard_type};'),
        ('keyboard/selfdraw/drawmethod/c;', 'a', '(Landroid/graphics/Canvas;Lcom/tencent/wetype/plugin/hld/keyboard/selfdraw/j;)V'),
        (f'keyboard/selfdraw/{keyboard_type};', t9_method, '()Z'),
        ('keyboard/selfdraw/j;', 't', '()Landroid/graphics/Rect;'),
        ('keyboard/selfdraw/j;', 'q0', '()Z'),
        ('keyboard/selfdraw/j;', 'i', '()I'),
        ('keyboard/selfdraw/j;', 'Z', '()F'),
        ('keyboard/selfdraw/j;', 'c0', '()Ljava/lang/String;'),
        ('keyboard/selfdraw/j;', 'Y', '()I'),
        ('keyboard/selfdraw/j;', 'k', '()I'),
        ('keyboard/selfdraw/j;', 'X', '()I'),
        ('keyboard/selfdraw/j;', 'j', '()I'),
        ('keyboard/selfdraw/j;', 'q', '()I'),
        ('keyboard/selfdraw/j;', 'G', '()I'),
        ('keyboard/selfdraw/j;', 'I', '()I'),
        ('keyboard/selfdraw/j;', 'w', '()I'),
        ('keyboard/u;', 'b', '(I)Z'),
        ('keyboard/t;', 'c', '()I'),
        ('keyboard/S15CustomPhraseAndClipboardKeyboard;', 'getKeyboardType', f'(){prefix}keyboard/t;'),
        ('keyboard/S15CustomPhraseAndClipboardKeyboard;', 'f0', '()V'),
        ('keyboard/S15CustomPhraseAndClipboardKeyboard;', 'g0', '()V'),
        ('keyboard/S29ClipboardCleanRecordKeyboard;', 'getKeyboardType', f'(){prefix}keyboard/t;'),
        ('keyboard/S29ClipboardCleanRecordKeyboard;', 'f0', '()V'),
        ('keyboard/S29ClipboardCleanRecordKeyboard;', 'g0', '()V'),
        ('keyboard/S30KeyboardChooseKeyboard;', 'getKeyboardType', f'(){prefix}keyboard/t;'),
        ('keyboard/S30KeyboardChooseKeyboard;', 'f0', '()V'),
        ('keyboard/S30KeyboardChooseKeyboard;', 'g0', '()V'),
        ('keyboard/selfdraw/S11EmojiKeyboard;', 'getKeyboardType', f'(){prefix}keyboard/t;'),
        ('keyboard/selfdraw/S11EmojiKeyboard;', 'f0', '()V'),
        ('keyboard/selfdraw/S11EmojiKeyboard;', 'g0', '()V'),
        ('view/settingkeyboard/S10SettingCustomToolbarView;', 'setVisibility', '(I)V'),
        ('keyboard/S10SettingsKeyboard;', 'getKeyboardTypeView',
         f'(){prefix}view/settingkeyboard/S10SettingKeyboardTypeView;'),
        ('view/toolbar/S10CustomToolbarHorizontalScrollView;', 'a', '(IIII)V'),
        ('candidate/TabContainerView;', 'onDraw', '(Landroid/graphics/Canvas;)V'),
        ('float/e;', 'onAttachedToWindow', '()V'),
        ('float/e;', 'onDetachedFromWindow', '()V'),
        ('float/e;', 'getRootView', '()Lcom/tencent/wetype/plugin/hld/view/ImeRadiusConstraintLayout;'),
        ('float/e;', 'getContent', '()Lcom/tencent/wetype/plugin/hld/view/ImeRadiusLinearLayout;'),
        ('view/ImeRadiusConstraintLayout;', 'getRadius', '()I'),
        ('WxHldService;', 'onCreateInputView', '()Landroid/view/View;'),
        ('WxHldService;', 'onStartInputView', '(Landroid/view/inputmethod/EditorInfo;Z)V'),
        ('WxHldService;', 'onWindowShown', '()V'),
        ('WxHldService;', 'onWindowHidden', '()V'),
        ('WxHldService;', 'onConfigurationChanged', '(Landroid/content/res/Configuration;)V'),
        ('WxHldService;', 'onDestroy', '()V'),
    ]
    result = {'profile': profile, 'hook_signatures': []}
    if profile == '4.0.0':
        checks.extend([
            ('float/f$g;', 'g', '()Landroid/graphics/Bitmap;'),
            ('float/f$g;', 'invoke', '()Ljava/lang/Object;'),
            ('model/N;', 'C0', '()Landroid/view/View;'),
        ])
        assert (prefix + 'float/e;', 'U', 'Landroid/widget/ImageView;') in field_definitions
    for cls, method, proto in checks:
        symbol = (prefix + cls, method, proto)
        found = symbol in definitions
        result['hook_signatures'].append({'symbol': list(symbol), 'defined': found})
        assert found, f'Hook not defined: {symbol}'
    theme_checks = [
        ('Le7/u;', 'j', '()Landroid/content/res/Resources;'),
        ('Lcom/tencent/wetype/plugin/hld/skin/e;', 'c', '()LMb/o;'),
        ('LMb/o;', 's', '()I'),
        ('LMb/o;', 'j', '(I)V'),
    ]
    for symbol in theme_checks:
        found = symbol in definitions
        result['hook_signatures'].append({'symbol': list(symbol), 'defined': found})
        assert found, f'Theme method not defined: {symbol}'
    tab_owner = prefix + 'candidate/TabContainerView;'
    tab_fields = {(name, kind) for owner, name, kind in field_definitions if owner == tab_owner}
    bindings = [('f', 'Landroid/graphics/RectF;'), ('g', 'F'),
                ('h', 'Landroid/graphics/Paint;'), ('k', 'Z')]
    for name, kind in bindings:
        assert (name, kind) in tab_fields, f'Tab field missing: {name}:{kind}'
        if kind != 'Z':
            assert sum(t == kind for _, t in tab_fields) == 1, f'Ambiguous Tab field type: {kind}'
    result['clipboard_tab_fields'] = [{'name': name, 'type': kind, 'defined': True}
                                      for name, kind in bindings]
    result['clipboard_overlay_hierarchy'] = verify_clipboard_stack(args.wetype)
    for name in ('S10SettingKeyboardTypeView', 'S10SettingItemView', 'c'):
        assert prefix + f'view/settingkeyboard/{name};' in target_classes, f'{name} missing from target'
    if profile == '4.0.0':
        result['auxiliary_page_layouts'] = verify_auxiliary_layouts(args.wetype)
    refs = symbols(args.xiaobu)[2] if args.xiaobu is not None else set()
    native = [
        ('Lcom/oplus/view/ViewRootManager;', '<init>', '(Landroid/view/View;)V'),
        ('Lcom/oplus/view/ViewRootManager;', 'getBackgroundBlurDrawable', '()Landroid/graphics/drawable/Drawable;'),
        ('Lcom/oplus/view/ViewRootManager;', 'setBlurParams', '(Lcom/oplus/graphics/OplusBlurParam;)V'),
        ('Lcom/oplus/view/ViewRootManager;', 'setBlurRadius', '(I)V'),
        ('Lcom/oplus/view/ViewRootManager;', 'setCornerRadius', '(FFFF)V'),
        ('Lcom/oplus/graphics/OplusBlurParam;', 'setBlurType', '(I)V'),
        ('Lcom/oplus/graphics/OplusBlurParam;', 'setMaterialParams', '(I[F[F)V'),
        ('Lcom/oplus/graphics/OplusBlurParam;', 'setSmoothCornerType', '(I)V'),
        ('Lcom/oplus/graphics/OplusBlurParam;', 'setSmoothCornerWeight', '(F)V'),
    ]
    result['native_signatures'] = []
    for symbol in native if args.xiaobu is not None else []:
        found = symbol in refs
        result['native_signatures'].append({'symbol': list(symbol), 'referenced_by_xiaobu': found})
        assert found, f'Native API not referenced: {symbol}'
    result['native_reference_check'] = ('passed against supplied APK' if args.xiaobu is not None
                                        else 'not rerun; reference APK not provided')
    module_methods, module_classes, _ = symbols(args.module)
    assert ('Ldev/coloros/wetype/material/WeTypeMaterialModule;', '<init>', '()V') in module_methods
    for name in ('ToolbarMaterial', 'XiaobuThemeOverlay', 'WeTypeTheme', 'PanelEdgeMaterial', 'WeTypeCompatibility', 'ClipboardMaterial', 'FloatingMaterial', 'FloatingPreviewMaterial', 'AuxiliaryPagesMaterial'):
        assert f'Ldev/coloros/wetype/material/{name};' in module_classes, f'{name} missing from APK'
    assert not any(c.startswith('Lio/github/libxposed/') or c.startswith('Lde/robv/android/xposed/')
                   for c in module_classes), 'Framework API must be compileOnly'
    with zipfile.ZipFile(args.module) as archive:
        result['module_metadata'] = {
            name: archive.read('META-INF/xposed/' + name).decode('utf-8').strip()
            for name in ('java_init.list', 'module.prop', 'scope.list')}
        if 'material/xiaobu_shadowedge.agsl' in archive.namelist():
            provenance = json.loads(Path('evidence/button-shader-provenance.json').read_text(encoding='utf-8'))
            shader_hash = hashlib.sha256(archive.read('material/xiaobu_shadowedge.agsl')).hexdigest()
            assert shader_hash == provenance['shader_sha256'], 'Bundled shader differs from Xiaobu source'
            result['button_shader_sha256'] = shader_hash
        else:
            raise AssertionError('Key material shader missing from module APK')
    assert result['module_metadata']['scope.list'] == 'com.tencent.wetype'
    assert 'minApiVersion=102' in result['module_metadata']['module.prop']
    result['hashes'] = {
        str(p): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (args.wetype, args.xiaobu, args.module) if p is not None}
    result['runtime_visual_acceptance'] = 'not assessed by static verifier'
    result['panel_edge'] = {'source': 'Xiaobu C0879v / C0880w', 'shader': 'NEW_G2',
                            'edge_style': 2, 'shadow': False, 'blend': 'PLUS', 'weight': 3,
                            'line_width_dp': 4, 'light_alpha': 1, 'dark_alpha': 0.4,
                            'docked_bottom_clip_dp': 4}
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf-8')
    print(f'PASS: {len(checks) + len(theme_checks)} WeType signatures, {len(bindings)} clipboard tab fields, overlay hierarchy, {len(result["native_signatures"])} Xiaobu native signatures, API 102 metadata and compileOnly packaging')
    print('Native reference check:', result['native_reference_check'])


if __name__ == '__main__':
    main()
