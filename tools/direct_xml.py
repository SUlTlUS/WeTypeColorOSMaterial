"""Dispatch stock FrameLayouts in the two verified IME root layouts; keep all attributes."""
import struct

FRAME_IDS = {2131296987, 2131297421}  # Verified 4.0.0: keyboard_container_rl, s10_setting_container
FRAME_CLASS = 'dev.coloros.wetype.direct.MaterialFrameLayout'
IME_ROOTS = {
    'com.tencent.wetype.plugin.hld.view.ImeRootView',
    'com.tencent.wetype.plugin.hld.keyboard.S10SettingsKeyboard',
}


def _length(data, offset, utf8):
    if utf8:
        value = data[offset]
        return (((value & 0x7f) << 8) | data[offset + 1], offset + 2) if value & 0x80 else (value, offset + 1)
    value = struct.unpack_from('<H', data, offset)[0]
    return (((value & 0x7fff) << 16) | struct.unpack_from('<H', data, offset + 2)[0], offset + 4) if value & 0x8000 else (value, offset + 2)


def _encoded_length(value, utf8):
    if utf8:
        return bytes([value]) if value < 0x80 else bytes([0x80 | (value >> 8), value & 0xff])
    return struct.pack('<H', value) if value < 0x8000 else struct.pack('<HH', 0x8000 | (value >> 16), value & 0xffff)


def patch_ime_frames(data):
    if data[:2] != b'\x03\x00':
        return data, []
    chunks, strings, stack, edits, resources = [], [], [], [], []
    pool_index = None
    ime_layout, root_seen = False, False
    offset = struct.unpack_from('<H', data, 2)[0]
    while offset < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, offset)
        if size < header or size == 0 or offset + size > len(data):
            raise ValueError('Malformed binary XML chunk')
        chunk = bytearray(data[offset:offset + size])
        if kind == 1:
            pool_index = len(chunks)
            count, styles, flags, start, style_start = struct.unpack_from('<IIIII', chunk, 8)
            utf8 = bool(flags & 0x100)
            for i in range(count):
                at = start + struct.unpack_from('<I', chunk, header + 4 * i)[0]
                length, at = _length(chunk, at, utf8)
                if utf8:
                    length, at = _length(chunk, at, True)
                strings.append(bytes(chunk[at:at + length * (1 if utf8 else 2)]).decode('utf-8' if utf8 else 'utf-16le'))
        elif kind == 0x180:
            resources = list(struct.unpack_from('<' + 'I' * ((size - header) // 4), chunk, header))
        elif kind == 0x102:
            name = struct.unpack_from('<I', chunk, 20)[0]
            if not root_seen:
                ime_layout = strings[name] in IME_ROOTS
                root_seen = True
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', chunk, 24)
            view_id = None
            for i in range(attr_count):
                at = 16 + attr_start + i * attr_size
                attr_name = struct.unpack_from('<I', chunk, at + 4)[0]
                if attr_name < len(resources) and resources[attr_name] == 0x010100d0:
                    view_id = struct.unpack_from('<I', chunk, at + 16)[0]
            replace = ime_layout and strings[name] in ('FrameLayout', 'android.widget.FrameLayout')
            if replace:
                edits.append({'id': view_id, 'original_class': strings[name], 'class': FRAME_CLASS})
                struct.pack_into('<I', chunk, 20, len(strings))
            stack.append(replace)
        elif kind == 0x103:
            if stack.pop():
                struct.pack_into('<I', chunk, 20, len(strings))
        chunks.append(chunk)
        offset += size
    if not edits:
        return data, []
    assert not stack and pool_index is not None
    pool = chunks[pool_index]
    _, header, size = struct.unpack_from('<HHI', pool)
    count, styles, flags, start, style_start = struct.unpack_from('<IIIII', pool, 8)
    utf8 = bool(flags & 0x100)
    encoded = FRAME_CLASS.encode('utf-8' if utf8 else 'utf-16le')
    text = _encoded_length(len(FRAME_CLASS), utf8)
    if utf8:
        text += _encoded_length(len(encoded), True)
    text += encoded + (b'\0' if utf8 else b'\0\0')
    text += b'\0' * (-len(text) % 4)
    old_text_end = style_start or size
    new_pool = bytearray(pool[:header])
    new_pool += pool[header:header + count * 4]
    new_pool += struct.pack('<I', old_text_end - start)
    new_pool += pool[header + count * 4:start]
    new_pool += pool[start:old_text_end] + text + pool[old_text_end:]
    struct.pack_into('<I', new_pool, 4, len(new_pool))
    struct.pack_into('<I', new_pool, 8, count + 1)
    struct.pack_into('<I', new_pool, 16, flags & ~1)  # appended string is not sorted
    struct.pack_into('<I', new_pool, 20, start + 4)
    struct.pack_into('<I', new_pool, 24, style_start + 4 + len(text) if style_start else 0)
    chunks[pool_index] = new_pool
    result = bytearray(data[:struct.unpack_from('<H', data, 2)[0]]) + b''.join(chunks)
    struct.pack_into('<I', result, 4, len(result))
    return bytes(result), edits
