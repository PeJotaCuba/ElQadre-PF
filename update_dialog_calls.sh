#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt

    if (showAddGastoDialog) {
        AddEditGastoGeneralDialog(
            gasto = gastoToEdit,
            onDismiss = {
                showAddGastoDialog = false
                gastoToEdit = null
            },
            onConfirm = { g ->
                if (g.id == 0L) {
                    viewModel.insertGastoGeneral(g)
                } else {
                    viewModel.updateGastoGeneral(g)
                }
                showAddGastoDialog = false
                gastoToEdit = null
            }
        )
    }

    showFichaCostoDialog?.let { p ->
        FichaCostoDialog(
            product = p,
            uiState = uiState,
            onDismiss = { showFichaCostoDialog = null }
        )
    }
}
INNER_EOF
sed -i '$ d' app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt
bash update_dialog_calls.sh