package com.uth.inventarioapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var db: InventoryDb
    private var page = "Resumen"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = InventoryDb(this)
        page = savedInstanceState?.getString("page") ?: "Resumen"
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page); super.onSaveInstanceState(outState) }
    private fun money(cents: Long) = "L " + BigDecimal.valueOf(cents, 2).toPlainString()
    private fun View.showText(id: Int, value: String) { findViewById<TextView>(id).text = value }
    private fun render() {
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.main)
        val padding = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<TextView>(R.id.page_title).text = page
        listOf(R.id.nav_summary to "Resumen", R.id.nav_products to "Productos", R.id.nav_movements to "Movimientos").forEach { (id, title) ->
            findViewById<Button>(id).apply { isEnabled = page != title; setOnClickListener { page = title; render() } }
        }
        val container = findViewById<FrameLayout>(R.id.screen_container)
        val layout = when(page) { "Productos" -> R.layout.screen_products; "Movimientos" -> R.layout.screen_movements; else -> R.layout.screen_summary }
        val screen = layoutInflater.inflate(layout, container, false)
        container.addView(screen)
        val products = db.products()
        when(page) {
            "Productos" -> {
                screen.findViewById<Button>(R.id.add_product).setOnClickListener { edit(null) }
                screen.findViewById<View>(R.id.empty_products).visibility = if(products.isEmpty()) View.VISIBLE else View.GONE
                val list = screen.findViewById<LinearLayout>(R.id.product_list)
                products.forEach { p ->
                    val row = layoutInflater.inflate(R.layout.item_product, list, false)
                    row.showText(R.id.product_name, p.name)
                    row.showText(R.id.product_details, "${money(p.cents)} · ${p.stock} unidades\nCódigo: ${p.barcode ?: "Sin código"}")
                    row.showText(R.id.product_warning, "⚠ Stock mínimo: ${p.stock} disponibles (mínimo ${p.minimum})")
                    row.findViewById<View>(R.id.product_warning).visibility = if(p.stock <= p.minimum) View.VISIBLE else View.GONE
                    row.findViewById<Button>(R.id.edit_product).setOnClickListener { edit(p) }
                    row.findViewById<Button>(R.id.delete_product).setOnClickListener { delete(p) }
                    list.addView(row)
                }
            }
            "Movimientos" -> {
                screen.findViewById<Button>(R.id.add_movement).setOnClickListener { movement(products) }
                val history = db.movements()
                screen.findViewById<View>(R.id.empty_movements).visibility = if(history.isEmpty()) View.VISIBLE else View.GONE
                val list = screen.findViewById<LinearLayout>(R.id.movement_list)
                history.forEach { m ->
                    val row = layoutInflater.inflate(R.layout.item_inventory_value, list, false)
                    row.showText(R.id.item_title, m.name)
                    row.showText(R.id.item_details, "${m.type} · ${m.qty} unidades\n${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(m.date)}")
                    list.addView(row)
                }
            }
            else -> {
                val total = products.fold(BigDecimal.ZERO) { sum, p -> sum + BigDecimal.valueOf(p.cents, 2).multiply(BigDecimal.valueOf(p.stock.toLong())) }
                screen.showText(R.id.total_value, "L ${total.toPlainString()}")
                screen.showText(R.id.product_count, "${products.size} productos registrados")
                val low = products.filter { it.stock <= it.minimum }
                screen.showText(R.id.low_stock, if(low.isEmpty()) "Sin alertas de stock mínimo" else "⚠ ${low.size} productos con stock mínimo\n" + low.joinToString("\n") { "${it.name}: ${it.stock} unidades · mínimo ${it.minimum}" })
                screen.findViewById<View>(R.id.empty_summary).visibility = if(products.isEmpty()) View.VISIBLE else View.GONE
                val list = screen.findViewById<LinearLayout>(R.id.top_list)
                products.sortedByDescending { BigDecimal.valueOf(it.cents).multiply(BigDecimal.valueOf(it.stock.toLong())) }.take(5).forEachIndexed { index, p ->
                    val row = layoutInflater.inflate(R.layout.item_inventory_value, list, false)
                    row.showText(R.id.item_title, "${index + 1}. ${p.name}")
                    row.showText(R.id.item_details, "L ${BigDecimal.valueOf(p.cents, 2).multiply(BigDecimal.valueOf(p.stock.toLong())).toPlainString()} · ${p.stock} unidades")
                    list.addView(row)
                }
            }
        }
    }
    private fun edit(p: Product?) {
        val form = layoutInflater.inflate(R.layout.dialog_product, null)
        val name = form.findViewById<EditText>(R.id.input_name).apply { setText(p?.name ?: "") }
        val price = form.findViewById<EditText>(R.id.input_price).apply { setText(p?.let { BigDecimal.valueOf(it.cents, 2).toPlainString() } ?: "") }
        val stockInput = form.findViewById<EditText>(R.id.input_stock)
        val stock = if(p == null) stockInput else null
        stockInput.visibility = if(p == null) View.VISIBLE else View.GONE
        form.findViewById<View>(R.id.input_stock_label).visibility = stockInput.visibility
        val barcode = form.findViewById<EditText>(R.id.input_barcode).apply { setText(p?.barcode ?: "") }
        val minimum = form.findViewById<EditText>(R.id.input_minimum).apply { setText(p?.minimum?.toString() ?: "5") }
        form.showText(R.id.current_stock, p?.let { "Stock actual: ${it.stock}. Usa Movimientos para modificarlo." } ?: "El stock inicial se registra como una entrada.")
        val dialog = AlertDialog.Builder(this).setTitle(if(p == null) "Nuevo producto" else "Editar producto").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                require(name.text.toString().trim().isNotEmpty()) { "Ingresa el nombre del producto." }
                val cents = price.text.toString().replace(',', '.').toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() ?: error("Ingresa un precio válido con hasta dos decimales.")
                val qty = if(stock == null) p!!.stock else stock.text.toString().toIntOrNull() ?: error("Ingresa un stock válido.")
                val min = minimum.text.toString().toIntOrNull() ?: error("Ingresa un mínimo válido.")
                require(cents >= 0 && qty >= 0 && min >= 0) { "Los valores no pueden ser negativos." }
                db.save(p?.id, name.text.toString().trim(), cents, qty, barcode.text.toString().trim().ifEmpty { null }, min)
                dialog.dismiss(); render(); if(qty <= min) alert("Stock mínimo", "${name.text}: quedan $qty unidades.")
            } catch(e: android.database.sqlite.SQLiteConstraintException) { alert("No se pudo guardar", "Ese código de barras ya está registrado.") }
            catch(e: Exception) { alert("Revisa los datos", e.message ?: "Datos inválidos.") }
        } }; dialog.show()
    }
    private fun movement(products: List<Product>) {
        if(products.isEmpty()) { alert("Sin productos", "Registra un producto primero."); return }
        val form = layoutInflater.inflate(R.layout.dialog_movement, null)
        val picker = form.findViewById<Spinner>(R.id.product_picker).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, products.map { "${it.name} (${it.stock} disponibles)" }) }
        val type = form.findViewById<Spinner>(R.id.type_picker).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("ENTRADA", "SALIDA")) }
        val qty = form.findViewById<EditText>(R.id.input_quantity)
        val dialog = AlertDialog.Builder(this).setTitle("Registrar movimiento").setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Registrar", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val amount = qty.text.toString().toIntOrNull() ?: error("Ingresa una cantidad válida.")
                require(amount > 0) { "La cantidad debe ser mayor que cero." }
                val p = products[picker.selectedItemPosition]
                db.move(p.id, amount, type.selectedItem.toString())
                dialog.dismiss(); render()
                db.products().firstOrNull { it.id == p.id && it.stock <= it.minimum }?.let { alert("Stock mínimo", "${it.name}: quedan ${it.stock} unidades (mínimo ${it.minimum}).") }
            } catch(e: Exception) { alert("No se pudo registrar", e.message ?: "Revisa la cantidad.") }
        } }; dialog.show()
    }
    private fun delete(p: Product) {
        AlertDialog.Builder(this).setTitle("Eliminar ${p.name}").setMessage("Se eliminarán el producto y todos sus movimientos. Esta acción no se puede deshacer. ¿Deseas continuar?").setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ ->
            try { db.delete(p.id); render() } catch(e: android.database.sqlite.SQLiteConstraintException) { alert("Producto con historial", "No se puede eliminar un producto con movimientos registrados.") }
        }.show()
    }
    private fun alert(title: String, message: String) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar", null).show() }
    override fun onDestroy() { db.close(); super.onDestroy() }
}

