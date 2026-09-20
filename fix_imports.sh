#!/bin/bash
sed -i '/import androidx.compose.material3.\*/a \import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.items' app/src/main/java/com/example/ui/screens/admin/GastosGeneralesDialogs.kt
