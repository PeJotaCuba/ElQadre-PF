import re

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "r") as f:
    text = f.read()

text = text.replace(
    'val expectedYieldVal = if (tanda.expectedYield > 0.0) tanda.expectedRevenue else tanda.estimatedYield',
    'val expectedYieldVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield'
)

text = text.replace(
    'val yieldPct = if (tanda.estimatedYield > 0.0) (actualYieldVal / tanda.estimatedYield) * 100.0 else 100.0',
    'val yieldPct = if (expectedYieldVal > 0.0) (actualYieldVal / expectedYieldVal) * 100.0 else 100.0'
)

text = text.replace(
    'Text("${tanda.estimatedYield.toInt()} ${tanda.productionUnit}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Emerald700)',
    'Text("${expectedYieldVal.toInt()} ${tanda.productionUnit}", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Emerald700)'
)

text = text.replace(
    'Producción Esperada: ${tanda.estimatedYield.toInt()}',
    'Producción Esperada: ${tanda.expectedYield.toInt()}'
)

with open("app/src/main/java/com/example/ui/screens/admin/TandasPane.kt", "w") as f:
    f.write(text)
