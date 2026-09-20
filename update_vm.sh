#!/bin/bash
sed -i '/repository.allProducts.collect/i \        viewModelScope.launch {\n            repository.gastosGenerales.collect { list ->\n                _uiState.update { it.copy(gastosGenerales = list) }\n            }\n        }\n' app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
