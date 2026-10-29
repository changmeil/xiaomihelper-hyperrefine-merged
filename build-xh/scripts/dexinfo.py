import struct, sys

def uleb(data, off):
    r = 0; sh = 0
    while True:
        b = data[off]; off += 1
        r |= (b & 0x7f) << sh
        if not b & 0x80:
            return r, off
        sh += 7

def read(data, off, fmt):
    return struct.unpack_from(fmt, data, off)

def strings(data):
    n, off = read(data, 0x38, '<II')
    out = []
    for i in range(n):
        so = read(data, off + i * 4, '<I')[0]
        _, p = uleb(data, so)  # utf16 len
        e = data.index(b'\x00', p)
        out.append(data[p:e].decode('utf-8', 'replace'))
    return out

def types(data, ss):
    n, off = read(data, 0x40, '<II')
    return [ss[read(data, off + i * 4, '<I')[0]] for i in range(n)]

def protos(data, ss, ts):
    n, off = read(data, 0x48, '<II')
    out = []
    for i in range(n):
        shorty, ret, poff = read(data, off + i * 12, '<III')
        params = []
        if poff:
            cnt = read(data, poff, '<I')[0]
            for j in range(cnt):
                params.append(ts[read(data, poff + 4 + j * 2, '<H')[0]])
        out.append((ts[ret], params))
    return out

def fieldids(data, ss, ts):
    n, off = read(data, 0x50, '<II')
    out = []
    for i in range(n):
        c, t, nm = read(data, off + i * 8, '<HHI')
        out.append(ts[c] + '.' + ss[nm] + ':' + ts[t])
    return out

def methodids(data, ss, ts, ps):
    n, off = read(data, 0x58, '<II')
    out = []
    for i in range(n):
        c, p, nm = read(data, off + i * 8, '<HHI')
        ret, params = ps[p]
        out.append((ts[c], ss[nm], ret, params))
    return out

def classdefs(data, ss, ts):
    n, off = read(data, 0x60, '<II')
    out = []
    for i in range(n):
        cidx, acc, sup, ifo, src, anno, cdoff, sv = read(data, off + i * 32, '<IIIIIIII')
        out.append(ts[cidx])
    return out, off, n

def methods_of(data, off):
    # off points to class_data_off field location
    cdoff = read(data, off, '<I')[0]
    if cdoff == 0:
        return [], []
    p = cdoff
    sf, p = uleb(data, p)
    inf, p = uleb(data, p)
    dm, p = uleb(data, p)
    vm, p = uleb(data, p)
    res = []
    idx = 0
    for _ in range(sf + inf):
        _, p = uleb(data, p); _, p = uleb(data, p)
    for _ in range(dm + vm):
        diff, p = uleb(data, p)
        acc, p = uleb(data, p)
        code, p = uleb(data, p)
        idx += diff
        res.append(idx)
    return res, (dm, vm)

def main(path):
    data = open(path, 'rb').read()
    ss = strings(data)
    ts = types(data, ss)
    ps = protos(data, ss, ts)
    ms = methodids(data, ss, ts, ps)
    fs = fieldids(data, ss, ts)
    classes, cdoff_base, n = classdefs(data, ss, ts)
    print('== classes in %s (%d) ==' % (path, n))
    for i, c in enumerate(classes):
        print(' ', c)
    print('== external (non-dex) class refs of interest ==')
    for c in classes:
        if 'xposed' in c.lower() or 'xici' in c.lower():
            print('  ', c)
    print('== methods declared in classes ==')
    for i, c in enumerate(classes):
        mo, _ = methods_of(data, cdoff_base + i * 32 + 24)
        for mi in mo:
            try:
                print('  ', c, ms[mi][1], '(', ','.join(ms[mi][3]), ') ->', ms[mi][2])
            except Exception as e:
                pass
    print('== method refs (called) that mention xposed/xici ==')
    for c, nm, ret, params in ms:
        if 'xposed' in c.lower() or 'xposed' in nm.lower():
            print('  ', c + '->' + nm, '(', ','.join(params), ')', ret)
    print('== string consts sample ==')
    for s in ss:
        if any(k in s for k in ('wxcompat', 'onPackageReady', 'hook', 'versionCode')) and len(s) < 90:
            print('  ', s)

main(sys.argv[1])
