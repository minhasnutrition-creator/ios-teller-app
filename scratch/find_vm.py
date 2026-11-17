import glob, re

for f in sorted(glob.glob('decompiled_6725/smali_classes2/a/*.smali')):
    with open(f, 'r', encoding='utf-8', errors='ignore') as fp:
        c = fp.read()
    if 'ImportCleanupScreen.kt' in c:
        m = re.findall(r'c = "([^"]+)"', c)
        print(f, m)
