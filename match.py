with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    lines = f.read().splitlines()

depth = 0
start_found = False
for i, line in enumerate(lines):
    if "class MainViewModel" in line:
        start_found = True
    if start_found:
        depth += line.count('{')
        depth -= line.count('}')
        if depth == 0:
            print(f"MainViewModel ends at line {i+1}")
            break
