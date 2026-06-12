package com.fbwoals.tour_app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TravelRecord(
    val id: Int = 0,
    val place: String,
    val visitDate: String,
    val memo: String,
    val photoUri: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TravelDB.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "travel_records"
        
        private const val COLUMN_ID = "no"
        private const val COLUMN_PLACE = "place"
        private const val COLUMN_VISIT_DATE = "visit_date"
        private const val COLUMN_MEMO = "memo"
        private const val COLUMN_PHOTO_URI = "photo_uri"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PLACE TEXT NOT NULL,
                $COLUMN_VISIT_DATE TEXT NOT NULL,
                $COLUMN_MEMO TEXT,
                $COLUMN_PHOTO_URI TEXT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    // 1. 데이터 추가 (C)
    fun insertRecord(record: TravelRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PLACE, record.place)
            put(COLUMN_VISIT_DATE, record.visitDate)
            put(COLUMN_MEMO, record.memo)
            put(COLUMN_PHOTO_URI, record.photoUri)
            put(COLUMN_LATITUDE, record.latitude)
            put(COLUMN_LONGITUDE, record.longitude)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    // 2. 데이터 수정 (U)
    fun updateRecord(record: TravelRecord): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PLACE, record.place)
            put(COLUMN_VISIT_DATE, record.visitDate)
            put(COLUMN_MEMO, record.memo)
            put(COLUMN_PHOTO_URI, record.photoUri)
            put(COLUMN_LATITUDE, record.latitude)
            put(COLUMN_LONGITUDE, record.longitude)
        }
        return db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(record.id.toString()))
    }

    // 3. 단일 데이터 삭제 (D)
    fun deleteRecord(id: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    // 4. 전체 데이터 삭제 (옵션 메뉴용)
    fun deleteAllRecords(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null)
    }

    // 5. 전체 데이터 정렬 조회 (R)
    // sortByDateDesc: true 이면 최근 방문순 정렬, false 이면 과거 방문순 정렬
    fun getAllRecords(sortByDateDesc: Boolean = true): List<TravelRecord> {
        val db = readableDatabase
        val sortOrder = if (sortByDateDesc) "$COLUMN_VISIT_DATE DESC, $COLUMN_ID DESC" else "$COLUMN_VISIT_DATE ASC, $COLUMN_ID ASC"
        val cursor = db.query(TABLE_NAME, null, null, null,
            null, null, sortOrder)
        val recordList = mutableListOf<TravelRecord>()
        with(cursor) {
            while (moveToNext()) {
                val id = getInt(getColumnIndexOrThrow(COLUMN_ID))
                val place = getString(getColumnIndexOrThrow(COLUMN_PLACE))
                val visitDate = getString(getColumnIndexOrThrow(COLUMN_VISIT_DATE))
                val memo = getString(getColumnIndexOrThrow(COLUMN_MEMO))
                val photoUri = getString(getColumnIndexOrThrow(COLUMN_PHOTO_URI))
                val latitude = if (isNull(getColumnIndexOrThrow(COLUMN_LATITUDE))) null else getDouble(getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = if (isNull(getColumnIndexOrThrow(COLUMN_LONGITUDE))) null else getDouble(getColumnIndexOrThrow(COLUMN_LONGITUDE))
                recordList.add(TravelRecord(id, place, visitDate, memo, photoUri, latitude, longitude))
            }
            close()
        }
        return recordList
    }

    // 6. 단일 데이터 조회 (상세 보기용)
    fun getRecordById(id: Int): TravelRecord? {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, "$COLUMN_ID = ?", arrayOf(id.toString()),
            null, null, null)
        var record: TravelRecord? = null
        with(cursor) {
            if (moveToFirst()) {
                val place = getString(getColumnIndexOrThrow(COLUMN_PLACE))
                val visitDate = getString(getColumnIndexOrThrow(COLUMN_VISIT_DATE))
                val memo = getString(getColumnIndexOrThrow(COLUMN_MEMO))
                val photoUri = getString(getColumnIndexOrThrow(COLUMN_PHOTO_URI))
                val latitude = if (isNull(getColumnIndexOrThrow(COLUMN_LATITUDE))) null else getDouble(getColumnIndexOrThrow(COLUMN_LATITUDE))
                val longitude = if (isNull(getColumnIndexOrThrow(COLUMN_LONGITUDE))) null else getDouble(getColumnIndexOrThrow(COLUMN_LONGITUDE))
                record = TravelRecord(id, place, visitDate, memo, photoUri, latitude, longitude)
            }
            close()
        }
        return record
    }
}
