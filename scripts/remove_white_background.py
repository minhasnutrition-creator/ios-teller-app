from PIL import Image
import os
from datetime import datetime

res_dir = os.path.join(os.getcwd(), 'app', 'src', 'main', 'res', 'drawable')
if not os.path.isdir(res_dir):
    print('drawable directory not found:', res_dir)
    exit(1)

pattern_prefixes = ['ic_rpt_']  # Process only report icons; adjust if you want more

backup_dir = os.path.join(res_dir, 'backup_' + datetime.now().strftime('%Y%m%d_%H%M%S'))
os.makedirs(backup_dir, exist_ok=True)

processed = []
for fname in sorted(os.listdir(res_dir)):
    if not fname.lower().endswith('.png'):
        continue
    if not any(fname.startswith(pref) for pref in pattern_prefixes):
        continue
    fpath = os.path.join(res_dir, fname)
    print('Processing', fname)
    im = Image.open(fpath).convert('RGBA')
    px = im.load()
    w,h = im.size
    changed = False
    # Heuristic: make nearly-white pixels transparent
    for y in range(h):
        for x in range(w):
            r,g,b,a = px[x,y]
            if a == 0:
                continue
            # If pixel is nearly white (allow some tolerance) and opaque, make transparent
            if r >= 250 and g >= 250 and b >= 250:
                px[x,y] = (255,255,255,0)
                changed = True
    if changed:
        # Backup
        os.rename(fpath, os.path.join(backup_dir, fname))
        # Save new image to original path
        im.save(fpath)
        processed.append(fname)
        print('Updated (backup created) ->', fname)
    else:
        print('No white background detected ->', fname)

print('\nDone. Processed files:', processed)
print('Backups are in', backup_dir)

