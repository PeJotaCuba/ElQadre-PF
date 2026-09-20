with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'r') as f:
    text = f.read()

# I need to find the exact string of updateUsers and remove it
import re

start_marker = "    fun updateUsers(url: String? = null) {"
end_marker = "        }\n    }"

# find start and end
start_idx = text.find(start_marker)
end_idx = text.find(end_marker, start_idx) + len(end_marker)

# extract it
functions_code = text[start_idx:end_idx]

# remove it
text = text[:start_idx] + text[end_idx:]
text = text.replace('\n\n\n', '\n\n')

# now find the end of MainViewModel
# find "    }\n}" which is the end of restoreBarraBackup
target = "        }\n    }\n}\n\ndata class SalonCartItem"
replace_with = "        }\n    }\n\n" + functions_code + "\n}\n\ndata class SalonCartItem"

if target in text:
    text = text.replace(target, replace_with)
    with open('app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt', 'w') as f:
        f.write(text)
    print("Fixed!")
else:
    print("Target not found!")
