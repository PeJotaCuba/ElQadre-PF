import sys

with open('patch.txt', 'r') as f:
    patch_lines = f.read().splitlines()

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    lines = f.read().splitlines()

new_lines = []
i = 0
while i < len(lines):
    match = True
    for j in range(len(patch_lines)):
        if i + j >= len(lines) or lines[i+j] != patch_lines[j]:
            match = False
            break
    if match:
        new_lines.append('}')
        i += len(patch_lines)
    else:
        new_lines.append(lines[i])
        i += 1

with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
    for line in new_lines:
        f.write(line + '\n')
