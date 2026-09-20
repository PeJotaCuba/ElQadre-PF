import re

def remove_bad_items(filepath):
    with open(filepath, 'r') as f:
        text = f.read()

    # The bad block starts with:
    #         item {
    #             Surface(
    #                 shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    #                 color = androidx.compose.ui.graphics.Color.White,
    #                 shadowElevation = 2.dp,
    #                 modifier = Modifier.fillMaxWidth()
    #             ) {
    #                 Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    #                     Text("Actualizaciones"
    
    # Let's use a regex to match the entire bad block
    pattern = r'        item \{\n            Surface\(\n                shape = androidx\.compose\.foundation\.shape\.RoundedCornerShape\(14\.dp\),\n                color = androidx\.compose\.ui\.graphics\.Color\.White,\n                shadowElevation = 2\.dp,\n                modifier = Modifier\.fillMaxWidth\(\)\n            \) \{\n                Column\(modifier = Modifier\.padding\(16\.dp\), verticalArrangement = Arrangement\.spacedBy\(12\.dp\)\) \{\n                    Text\("Actualizaciones".*?\}\n                \}\n            \}\n        \}\n        \n'
    
    cleaned = re.sub(pattern, '', text, flags=re.DOTALL)
    
    # In case there are some left that used {  }
    pattern_empty = r'        item \{\n            Surface\(\n                shape = androidx\.compose\.foundation\.shape\.RoundedCornerShape\(14\.dp\),\n                color = androidx\.compose\.ui\.graphics\.Color\.White,\n                shadowElevation = 2\.dp,\n                modifier = Modifier\.fillMaxWidth\(\)\n            \) \{\n                Column\(modifier = Modifier\.padding\(16\.dp\), verticalArrangement = Arrangement\.spacedBy\(12\.dp\)\) \{\n                    Text\("Actualizaciones".*?\}\n                \}\n            \}\n        \}\n        \n'
    
    cleaned = re.sub(pattern_empty, '', cleaned, flags=re.DOTALL)

    with open(filepath, 'w') as f:
        f.write(cleaned)

remove_bad_items('app/src/main/java/com/example/ui/screens/salon/SalonScreen.kt')
remove_bad_items('app/src/main/java/com/example/ui/screens/barra/BarraAjustesTab.kt')
remove_bad_items('app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt')

print("Bad items removed.")
