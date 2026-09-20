#!/bin/bash
sed -i '162,175c\
                                onAddProductClick = { showAddProductDialog = true },\
                                onEditProductClick = { p ->\
                                    productToEdit = p\
                                    showAddProductDialog = true\
                                },\
                                onManageRecipeClick = { p -> selectedProductForRecipe = p },\
                                onToggleProductActive = { p ->\
                                    viewModel.updateProduct(p.copy(isAvailable = !p.isAvailable))\
                                },\
                                onShowFichaCostoClick = { p -> showFichaCostoDialog = p }\
                            )' app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt
