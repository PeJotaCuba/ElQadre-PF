import sys

def fix_file(filename):
    with open(filename, 'r') as f:
        content = f.read()
    
    out = []
    stack = []
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    
    i = 0
    while i < len(content):
        c = content[i]
        
        if in_line_comment:
            if c == '\n':
                in_line_comment = False
            out.append(c)
            i += 1
            continue
            
        if in_block_comment:
            if c == '*' and i+1 < len(content) and content[i+1] == '/':
                in_block_comment = False
                out.append(c)
                out.append('/')
                i += 2
                continue
            out.append(c)
            i += 1
            continue
            
        if in_string:
            if c == '\\':
                out.append(c)
                if i+1 < len(content):
                    out.append(content[i+1])
                    i += 2
                else:
                    i += 1
                continue
            if c == '"':
                in_string = False
            out.append(c)
            i += 1
            continue
            
        if in_char:
            if c == '\\':
                out.append(c)
                if i+1 < len(content):
                    out.append(content[i+1])
                    i += 2
                else:
                    i += 1
                continue
            if c == "'":
                in_char = False
            out.append(c)
            i += 1
            continue
            
        if c == '/' and i+1 < len(content):
            if content[i+1] == '/':
                in_line_comment = True
                out.append('/')
                out.append('/')
                i += 2
                continue
            if content[i+1] == '*':
                in_block_comment = True
                out.append('/')
                out.append('*')
                i += 2
                continue
                
        if c == '"':
            in_string = True
            out.append(c)
            i += 1
            continue
            
        if c == "'":
            in_char = True
            out.append(c)
            i += 1
            continue
            
        if c in ['(', '{']:
            stack.append((c, len(out)))
            out.append(c)
            i += 1
            continue
            
        if c in [')', '}']:
            if stack:
                top_c, pos = stack.pop()
                if top_c == '(':
                    out.append(')')
                elif top_c == '{':
                    out.append('}')
            else:
                out.append(c)
            i += 1
            continue
            
        out.append(c)
        i += 1
        
    with open(filename, 'w') as f:
        f.write("".join(out))

fix_file('app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt')
