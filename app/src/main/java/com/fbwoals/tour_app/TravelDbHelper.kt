package com.fbwoals.tour_app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// SQLiteOpenHelper를 직접 상속해 여행 기록 CRUD와 스키마 업그레이드를 담당합니다.
class TravelDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // 앱 최초 실행 시 여행 기록 테이블을 생성합니다.
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

    // 기존 설치본의 DB가 새 컬럼을 안전하게 따라오도록 버전별 마이그레이션을 수행합니다.
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

    // 목록 화면에서 사용할 전체 여행 기록을 날짜/번호 기준으로 정렬해 조회합니다.
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

    // 상세/수정 화면에서 사용할 단일 여행 기록을 기본키로 조회합니다.
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

    // 새 여행 기록을 DB에 추가합니다.
    fun insert(record: TravelRecord): Long {
        return writableDatabase.insert(TABLE_NAME, null, record.toValues(includeId = false))
    }

    // 기존 여행 기록을 DB에 반영합니다.
    fun update(record: TravelRecord): Int {
        return writableDatabase.update(
            TABLE_NAME,
            record.toValues(includeId = false),
            "$COL_NO = ?",
            arrayOf(record.no.toString())
        )
    }

    // 선택한 여행 기록 1건을 삭제합니다.
    fun delete(no: Long): Int {
        return writableDatabase.delete(TABLE_NAME, "$COL_NO = ?", arrayOf(no.toString()))
    }

    // 전체 삭제 옵션에서 모든 여행 기록을 삭제합니다.
    fun deleteAll(): Int = writableDatabase.delete(TABLE_NAME, null, null)

    // TravelRecord를 SQLite에 저장 가능한 ContentValues로 변환합니다.
    private fun TravelRecord.toValues(includeId: Boolean): ContentValues {
        return ContentValues().apply {
            if (includeId) put(COL_NO, no)
            put(COL_PLACE, place)
            put(COL_VISIT_DATE, visitDate)
            put(COL_MEMO, memo)
            // 대표 사진은 별도 컬럼에 저장하고, 전체 사진 목록은 줄바꿈 구분자로 저장합니다.
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

    // Cursor의 현재 행을 TravelRecord 객체로 변환합니다.
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

    // DB에 문자열로 저장된 사진 URI 목록을 List 형태로 복원합니다.
    private fun parsePhotoUris(photoUrisText: String?, legacyPhotoUri: String?): List<String> {
        return photoUrisText
            ?.split(PHOTO_URI_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
            ?: listOfNotNull(legacyPhotoUri)
    }

    // 이미 존재하는 컬럼을 다시 추가하지 않도록 확인 후 ALTER TABLE을 실행합니다.
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
