package noryn.launcher.storage

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import noryn.launcher.core.model.WidgetInstance
import noryn.launcher.core.model.WidgetPendingPhase
import noryn.launcher.core.model.WidgetSizePreset

/** Small structured store for host-owned widget instances and in-flight add transactions. */
class WidgetRepository(context: Context) {
    private val database = WidgetDatabase(context.applicationContext)

    fun all(): List<WidgetInstance> = database.readableDatabase.query(
        TABLE_WIDGETS,
        COLUMNS,
        null,
        null,
        null,
        null,
        "$COLUMN_POSITION ASC, $COLUMN_ID ASC",
    ).use { cursor ->
        buildList {
            val idColumn = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val packageColumn = cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)
            val classColumn = cursor.getColumnIndexOrThrow(COLUMN_CLASS)
            val positionColumn = cursor.getColumnIndexOrThrow(COLUMN_POSITION)
            val sizeColumn = cursor.getColumnIndexOrThrow(COLUMN_SIZE)
            val phaseColumn = cursor.getColumnIndexOrThrow(COLUMN_PHASE)
            while (cursor.moveToNext()) {
                add(
                    WidgetInstance(
                        appWidgetId = cursor.getInt(idColumn),
                        providerPackage = cursor.getString(packageColumn),
                        providerClass = cursor.getString(classColumn),
                        position = cursor.getInt(positionColumn),
                        size = cursor.getString(sizeColumn).toWidgetSize(),
                        providerAvailable = false,
                        pendingPhase = cursor.getString(phaseColumn)?.let { phase ->
                            runCatching { WidgetPendingPhase.valueOf(phase) }.getOrNull()
                        },
                    ),
                )
            }
        }
    }

    fun insertPending(appWidgetId: Int, provider: ComponentName) {
        val nextPosition = all().size
        val values = ContentValues().apply {
            put(COLUMN_ID, appWidgetId)
            put(COLUMN_PACKAGE, provider.packageName)
            put(COLUMN_CLASS, provider.className)
            put(COLUMN_POSITION, nextPosition)
            put(COLUMN_SIZE, WidgetSizePreset.Standard.name)
            put(COLUMN_PHASE, WidgetPendingPhase.Binding.name)
        }
        database.writableDatabase.insertOrThrow(TABLE_WIDGETS, null, values)
    }

    fun setPendingPhase(appWidgetId: Int, phase: WidgetPendingPhase) {
        database.writableDatabase.update(
            TABLE_WIDGETS,
            ContentValues().apply { put(COLUMN_PHASE, phase.name) },
            "$COLUMN_ID = ?",
            arrayOf(appWidgetId.toString()),
        )
    }

    fun markActive(appWidgetId: Int) {
        database.writableDatabase.update(
            TABLE_WIDGETS,
            ContentValues().apply { putNull(COLUMN_PHASE) },
            "$COLUMN_ID = ?",
            arrayOf(appWidgetId.toString()),
        )
    }

    fun setSize(appWidgetId: Int, size: WidgetSizePreset) {
        database.writableDatabase.update(
            TABLE_WIDGETS,
            ContentValues().apply { put(COLUMN_SIZE, size.name) },
            "$COLUMN_ID = ?",
            arrayOf(appWidgetId.toString()),
        )
    }

    fun remove(appWidgetId: Int) {
        val db = database.writableDatabase
        db.transaction {
            val removedPosition = db.query(
                TABLE_WIDGETS,
                arrayOf(COLUMN_POSITION),
                "$COLUMN_ID = ?",
                arrayOf(appWidgetId.toString()),
                null,
                null,
                null,
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null } ?: return@transaction
            db.delete(TABLE_WIDGETS, "$COLUMN_ID = ?", arrayOf(appWidgetId.toString()))
            db.execSQL(
                "UPDATE $TABLE_WIDGETS SET $COLUMN_POSITION = $COLUMN_POSITION - 1 WHERE $COLUMN_POSITION > ?",
                arrayOf(removedPosition),
            )
        }
    }

    fun reorder(appWidgetIds: List<Int>) {
        val db = database.writableDatabase
        db.transaction {
            appWidgetIds.distinct().forEachIndexed { index, id ->
                db.update(
                    TABLE_WIDGETS,
                    ContentValues().apply { put(COLUMN_POSITION, index) },
                    "$COLUMN_ID = ?",
                    arrayOf(id.toString()),
                )
            }
        }
    }

    fun close() = database.close()

    private fun String.toWidgetSize(): WidgetSizePreset =
        runCatching { WidgetSizePreset.valueOf(this) }.getOrDefault(WidgetSizePreset.Standard)

    private class WidgetDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $TABLE_WIDGETS (
                    $COLUMN_ID INTEGER PRIMARY KEY,
                    $COLUMN_PACKAGE TEXT NOT NULL,
                    $COLUMN_CLASS TEXT NOT NULL,
                    $COLUMN_POSITION INTEGER NOT NULL,
                    $COLUMN_SIZE TEXT NOT NULL,
                    $COLUMN_PHASE TEXT
                )""".trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Schema changes must be introduced as explicit, additive migrations.
        }

        companion object {
            private const val DATABASE_NAME = "noryn_widgets.db"
            private const val DATABASE_VERSION = 1
        }
    }

    private companion object {
        const val TABLE_WIDGETS = "widget_instances"
        const val COLUMN_ID = "app_widget_id"
        const val COLUMN_PACKAGE = "provider_package"
        const val COLUMN_CLASS = "provider_class"
        const val COLUMN_POSITION = "position"
        const val COLUMN_SIZE = "size_preset"
        const val COLUMN_PHASE = "pending_phase"
        val COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_PACKAGE,
            COLUMN_CLASS,
            COLUMN_POSITION,
            COLUMN_SIZE,
            COLUMN_PHASE,
        )
    }
}
