package com.uth.inventarioapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Product(val id: Long, val name: String, val cents: Long, val stock: Int, val barcode: String?, val minimum: Int)
data class Movement(val name: String, val qty: Int, val type: String, val date: Long)

class InventoryDb(context: Context) : SQLiteOpenHelper(context, "inventory.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price INTEGER NOT NULL CHECK(price >= 0), stock INTEGER NOT NULL CHECK(stock >= 0), barcode TEXT UNIQUE, minimum INTEGER NOT NULL DEFAULT 5 CHECK(minimum >= 0))")
        db.execSQL("CREATE TABLE movements(id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE RESTRICT, qty INTEGER NOT NULL CHECK(qty > 0), type TEXT NOT NULL CHECK(type IN ('ENTRADA','SALIDA')), date INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun products(): List<Product> = readableDatabase.rawQuery("SELECT * FROM products ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while(c.moveToNext()) add(Product(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3), c.getString(4), c.getInt(5))) }
    }
    fun save(id: Long?, name: String, cents: Long, stock: Int, barcode: String?, minimum: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply { put("name", name); put("price", cents); put("barcode", barcode); put("minimum", minimum); if(id == null) put("stock", stock) }
            if(id == null) {
                val newId = db.insertOrThrow("products", null, values)
                if(stock > 0) insertMovement(db, newId, stock, "ENTRADA")
            } else check(db.update("products", values, "id=?", arrayOf(id.toString())) == 1)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun insertMovement(db: SQLiteDatabase, id: Long, qty: Int, type: String) {
        db.insertOrThrow("movements", null, ContentValues().apply { put("product_id", id); put("qty", qty); put("type", type); put("date", System.currentTimeMillis()) })
    }
    fun move(id: Long, qty: Int, type: String) {
        require(qty > 0 && type in listOf("ENTRADA", "SALIDA"))
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stock = db.rawQuery("SELECT stock FROM products WHERE id=?", arrayOf(id.toString())).use { check(it.moveToFirst()); it.getLong(0) }
            val next = if(type == "ENTRADA") stock + qty else stock - qty
            require(next in 0..Int.MAX_VALUE.toLong()) { "Stock insuficiente o cantidad demasiado grande." }
            db.execSQL("UPDATE products SET stock=? WHERE id=?", arrayOf(next, id))
            insertMovement(db, id, qty, type)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun delete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("movements", "product_id=?", arrayOf(id.toString()))
            db.delete("products", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun movements(): List<Movement> = readableDatabase.rawQuery("SELECT p.name,m.qty,m.type,m.date FROM movements m JOIN products p ON p.id=m.product_id ORDER BY m.date DESC,m.id DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(Movement(c.getString(0), c.getInt(1), c.getString(2), c.getLong(3))) }
    }
}

