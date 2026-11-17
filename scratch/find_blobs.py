import subprocess

output = subprocess.check_output(['git', 'fsck', '--lost-found'], text=True, errors='ignore')
blobs = []
for line in output.splitlines():
    if 'dangling blob' in line:
        blobs.append(line.split()[2])

print(f'Checking {len(blobs)} dangling blobs...')

for b in blobs:
    try:
        content = subprocess.check_output(['git', 'cat-file', '-p', b], text=True, errors='ignore')
        if 'applySmartCorrection' in content or 'isVoucherImportReady' in content or 'teller_corrected_voucher_import' in content:
            print(f'FOUND MATCH: blob {b}')
            with open(f'scratch/recovered_{b[:8]}.kt', 'w', encoding='utf-8') as out:
                out.write(content)
            print(f'Saved scratch/recovered_{b[:8]}.kt (length {len(content)})')
    except Exception as e:
        pass
