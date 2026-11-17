import subprocess

p_fsck = subprocess.run(['git', 'fsck', '--lost-found'], capture_output=True, text=True, errors='ignore')
blobs = []
for line in p_fsck.stdout.splitlines():
    if 'dangling blob' in line:
        blobs.append(line.split()[2])

with open('scratch/blob_list.txt', 'w') as f:
    for b in blobs:
        f.write(b + '\n')

print(f'Saved {len(blobs)} blobs to scratch/blob_list.txt')

with open('scratch/blob_list.txt', 'rb') as stdin_file:
    proc = subprocess.Popen(['git', 'cat-file', '--batch'], stdin=stdin_file, stdout=subprocess.PIPE)
    matches = 0
    while True:
        header = proc.stdout.readline()
        if not header:
            break
        parts = header.decode('ascii', errors='ignore').split()
        if len(parts) < 3 or parts[1] == 'missing':
            continue
        sha, obj_type, size = parts[0], parts[1], int(parts[2])
        data = proc.stdout.read(size)
        proc.stdout.read(1)
        text = data.decode('utf-8', errors='ignore')
        if any(k in text for k in ['applySmartCorrection', 'isVoucherImportReady', 'unrecognizedItems', 'teller_corrected_voucher_import']):
            print(f'MATCH: {sha} (size {size})')
            matches += 1
            with open(f'scratch/recovered_{sha[:8]}.kt', 'w', encoding='utf-8') as out:
                out.write(text)

print(f'Done! Found {matches} matching files.')
