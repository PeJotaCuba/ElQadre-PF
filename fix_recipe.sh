#!/bin/bash
sed -i '1410,1420c\
                        onConfirm(\
                            RecetaIngrediente(\
                                productoElaboradoId = productoElaboradoId,\
                                materiaPrimaId = selectedMateriaId,\
                                quantity = qty,\
                                unit = selectedMateria?.unit ?: "g"\
                            )\
                        )\
                    }\
                },\
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),' app/src/main/java/com/example/ui/screens/admin/ProductionWorkspaceDialog.kt
