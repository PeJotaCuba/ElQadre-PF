def check_brackets(filename):
    with open(filename, 'r') as f:
        content = f.read()
    
    stack = []
    lines = content.split('\n')
    
    for line_idx, line in enumerate(lines):
        # Very simple check, just looking for { } ( )
        for char_idx, c in enumerate(line):
            if c in ['{', '(']:
                stack.append((c, line_idx+1))
            elif c in ['}', ')']:
                if not stack:
                    print(f"Unmatched {c} at line {line_idx+1}")
                else:
                    top_c, top_line = stack.pop()
                    if (c == '}' and top_c != '{') or (c == ')' and top_c != '('):
                        print(f"Mismatched {c} at line {line_idx+1} (opened {top_c} at {top_line})")
    
    for c, line in stack:
        print(f"Unclosed {c} at line {line}")

check_brackets('app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt')
