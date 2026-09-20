#!/bin/bash
sed -i '160,211c\
                        ProductionTab.PRODUCTOS -> {\
                            ProductosYRecetasPane(\
                                uiState = uiState,\
                                viewModel = viewModel,\
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
                            )\
                        }\
                        ProductionTab.MATERIAS -> {\
                            MateriasPrimasPane(\
                                uiState = uiState,\
                                viewModel = viewModel,\
                                onAddMateriaClick = { showAddMateriaDialog = true },\
                                onEditMateriaClick = { m ->\
                                    materiaToEdit = m\
                                    showAddMateriaDialog = true\
                                },\
                                onToggleMateriaActive = { m ->\
                                    viewModel.updateMateriaPrima(m.copy(isActive = !m.isActive))\
                                }\
                            )\
                        }\
                        ProductionTab.COSTOS -> {\
                            CostosAnalisisPane(\
                                uiState = uiState,\
                                viewModel = viewModel\
                            )\
                        }\
                        ProductionTab.GASTOS_GENERALES -> {\
                            GastosGeneralesPane(\
                                uiState = uiState,\
                                onAddGastoClick = { showAddGastoDialog = true },\
                                onEditGastoClick = { g -> \
                                     gastoToEdit = g\
                                     showAddGastoDialog = true\
                                },\
                                onToggleGastoActive = { g -> \
                                     viewModel.updateGastoGeneral(g.copy(isActive = !g.isActive))\
                                }\
                            )\
                        }' app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt
