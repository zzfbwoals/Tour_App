package com.fbwoals.tour_app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TravelDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_NO INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PLACE TEXT NOT NULL,
                $COL_VISIT_DATE TEXT NOT NULL,
                $COL_MEMO TEXT,
                $COL_PHOTO_URI TEXT,
                $COL_PHOTO_URIS TEXT,
                $COL_COVER_PHOTO_URI TEXT,
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            addColumnIfMissing(db, COL_LATITUDE, "REAL")
            addColumnIfMissing(db, COL_LONGITUDE, "REAL")
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, COL_PHOTO_URIS, "TEXT")
            addColumnIfMissing(db, COL_COVER_PHOTO_URI, "TEXT")
            db.execSQL(
                """
                UPDATE $TABLE_NAME
                SET $COL_PHOTO_URIS = $COL_PHOTO_URI,
                    $COL_COVER_PHOTO_URI = $COL_PHOTO_URI
                WHERE $COL_PHOTO_URI IS NOT NULL AND $COL_PHOTO_URI != ''
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            addColumnIfMissing(db, COL_LATITUDE, "REAL")
            addColumnIfMissing(db, COL_LONGITUDE, "REAL")
            addColumnIfMissing(db, COL_PHOTO_URIS, "TEXT")
            addColumnIfMissing(db, COL_COVER_PHOTO_URI, "TEXT")
        }
    }

    fun getAll(orderNewestFirst: Boolean): List<TravelRecord> {
        val order = if (orderNewestFirst) "$COL_VISIT_DATE DESC, $COL_NO DESC" else "$COL_VISIT_DATE ASC, $COL_NO ASC"
        val records = mutableListOf<TravelRecord>()
        readableDatabase.query(TABLE_NAME, null, null, null, null, null, order).use { cursor ->
            while (cursor.moveToNext()) {
                records += cursor.toRecord()
            }
        }
        return records
    }

    fun getById(no: Long): TravelRecord? {
        readableDatabase.query(
            TABLE_NAME,
            null,
            "$COL_NO = ?",
            arrayOf(no.toString()),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toRecord() else null
        }
    }

    fun insert(record: TravelRecord): Long {
        return writableDatabase.insert(TABLE_NAME, null, record.toValues(includeId = false))
    }

    fun update(record: TravelRecord): Int {
        return writableDatabase.update(
            TABLE_NAME,
            record.toValues(includeId = false),
            "$COL_NO = ?",
            arrayOf(record.no.toString())
        )
    }

    fun delete(no: Long): Int {
        return writableDatabase.delete(TABLE_NAME, "$COL_NO = ?", arrayOf(no.toString()))
    }

    fun deleteAll(): Int = writableDatabase.delete(TABLE_NAME, null, null)

    private fun TravelRecord.toValues(includeId: Boolean): ContentValues {
        return ContentValues().apply {
            if (includeId) put(COL_NO, no)
            put(COL_PLACE, place)
            put(COL_VISIT_DATE, visitDate)
            put(COL_MEMO, memo)
            val coverUri = coverPhotoUri ?: photoUris.firstOrNull() ?: photoUri
            val allPhotoUris = photoUris.ifEmpty {
                listOfNotNull(photoUri)
            }.distinct()
            put(COL_PHOTO_URI, coverUri)
            put(COL_PHOTO_URIS, allPhotoUris.joinToString(PHOTO_URI_SEPARATOR))
            put(COL_COVER_PHOTO_URI, coverUri)
            if (latitude == null) putNull(COL_LATITUDE) else put(COL_LATITUDE, latitude)
            if (longitude == null) putNull(COL_LONGITUDE) else put(COL_LONGITUDE, longitude)
        }
    }

    private fun android.database.Cursor.toRecord(): TravelRecord {
        fun nullableString(column: String): String? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getString(index)
        }

        fun nullableDouble(column: String): Double? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getDouble(index)
        }

        return TravelRecord(
            no = getLong(getColumnIndexOrThrow(COL_NO)),
            place = getString(getColumnIndexOrThrow(COL_PLACE)),
            visitDate = getString(getColumnIndexOrThrow(COL_VISIT_DATE)),
            memo = nullableString(COL_MEMO).orEmpty(),
            photoUri = nullableString(COL_COVER_PHOTO_URI) ?: nullableString(COL_PHOTO_URI),
            latitude = nullableDouble(COL_LATITUDE),
            longitude = nullableDouble(COL_LONGITUDE),
            photoUris = parsePhotoUris(nullableString(COL_PHOTO_URIS), nullableString(COL_PHOTO_URI)),
            coverPhotoUri = nullableString(COL_COVER_PHOTO_URI) ?: nullableString(COL_PHOTO_URI)
        )
    }

    private fun parsePhotoUris(photoUrisText: String?, legacyPhotoUri: String?): List<String> {
        return photoUrisText
            ?.split(PHOTO_URI_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
            ?: listOfNotNull(legacyPhotoUri)
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, column: String, type: String) {
        val exists = db.rawQuery("PRAGMA table_info($TABLE_NAME)", null).use { cursor ->
            var found = false
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $column $type")
        }
    }

    companion object {
        const val DATABASE_NAME = "sch_travel_footprints.db"
        const val DATABASE_VERSION = 4
        const val TABLE_NAME = "travel_records"
        const val COL_NO = "no"
        const val COL_PLACE = "place"
        const val COL_VISIT_DATE = "visit_date"
        const val COL_MEMO = "memo"
        const val COL_PHOTO_URI = "photo_uri"
        const val COL_PHOTO_URIS = "photo_uris"
        const val COL_COVER_PHOTO_URI = "cover_photo_uri"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        private const val PHOTO_URI_SEPARATOR = "\n"
    }
}
