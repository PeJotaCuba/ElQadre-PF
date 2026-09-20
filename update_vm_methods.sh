#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt

    // Gastos Generales
    fun insertGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = viewModelScope.launch {
        repository.insertGastoGeneral(gasto)
    }

    fun updateGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = viewModelScope.launch {
        repository.updateGastoGeneral(gasto)
    }

    fun deleteGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = viewModelScope.launch {
        repository.deleteGastoGeneral(gasto)
    }
}
INNER_EOF
sed -i '$ d' app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
bash update_vm_methods.sh