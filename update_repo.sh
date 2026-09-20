#!/bin/bash
sed -i 's/val mercaderias = mercaderiaDao.getAll()/val mercaderias = mercaderiaDao.getAll()\n    val gastosGenerales = db.gastoGeneralDao().getAll()/' app/src/main/java/com/example/data/repository/AppRepository.kt
