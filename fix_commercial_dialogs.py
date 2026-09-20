with open('app/src/main/java/com/example/ui/screens/inicio/CommercialDialogs.kt', 'r') as f:
    content = f.read()

content = content.replace("movilAlt = movilAlt,", "movilAlt = movilAlt,\n                                    dvc = formattedDvc,")

with open('app/src/main/java/com/example/ui/screens/inicio/CommercialDialogs.kt', 'w') as f:
    f.write(content)
