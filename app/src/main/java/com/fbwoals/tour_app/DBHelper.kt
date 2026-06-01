package com.example.project11_1

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Memo(val id: Int, val photoPath: String, val memo: String)

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "MemoDB.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "Memo"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PHOTO_PATH = "photoPath"
        private const val COLUMN_MEMO = "memo"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PHOTO_PATH TEXT,
                $COLUMN_MEMO TEXT
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertMemo(photoPath: String, memo: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_PATH, photoPath)
            put(COLUMN_MEMO, memo)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun updateMemo(id: Int, photoPath: String, memo: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PHOTO_PATH, photoPath)
            put(COLUMN_MEMO, memo)
        }
        return db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteMemo(id: Int): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getAllMemos(): List<Memo> {
        val db = readableDatabase
        val cursor = db.query(TABLE_NAME, null, null, null,
            null, null, "$COLUMN_ID DESC")
        val memoList = mutableListOf<Memo>()
        with(cursor) {
            while (moveToNext()) {
                val id = getInt(getColumnIndexOrThrow(COLUMN_ID))
                val photoPath = getString(getColumnIndexOrThrow(COLUMN_PHOTO_PATH))
                val memo = getString(getColumnIndexOrThrow(COLUMN_MEMO))
                memoList.add(Memo(id, photoPath, memo))
            }
            close()
        }
        return memoList
    }
}
