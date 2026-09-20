with open('app/src/main/java/com/example/data/local/AppDatabase.kt', 'r') as f:
    text = f.read()

text = text.replace("version = 24,", "version = 25,")

migration_24_25 = """        val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE config_general ADD COLUMN urlCatalogoJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateStatus TEXT NOT NULL DEFAULT 'PENDIENTE'")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateVersion TEXT NOT NULL DEFAULT ''")
                
                database.execSQL("ALTER TABLE config_general ADD COLUMN urlMercainvJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateStatus TEXT NOT NULL DEFAULT 'PENDIENTE'")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateVersion TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_18_19"""

text = text.replace("val MIGRATION_18_19", migration_24_25)
text = text.replace("MIGRATION_23_24)", "MIGRATION_23_24, MIGRATION_24_25)")

with open('app/src/main/java/com/example/data/local/AppDatabase.kt', 'w') as f:
    f.write(text)
print("Patched AppDatabase.kt")
