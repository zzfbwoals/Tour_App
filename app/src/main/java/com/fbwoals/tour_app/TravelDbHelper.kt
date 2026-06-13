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
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_LATITUDE REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_LONGITUDE REAL")
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
            put(COL_PHOTO_URI, photoUri)
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
            photoUri = nullableString(COL_PHOTO_URI),
            latitude = nullableDouble(COL_LATITUDE),
            longitude = nullableDouble(COL_LONGITUDE)
        )
    }

    companion object {
        const val DATABASE_NAME = "sch_travel_footprints.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "travel_records"
        const val COL_NO = "no"
        const val COL_PLACE = "place"
        const val COL_VISIT_DATE = "visit_date"
        const val COL_MEMO = "memo"
        const val COL_PHOTO_URI = "photo_uri"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
    }
}
