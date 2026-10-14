package com.amaxonia.erp.ui.pos.components

import androidx.compose.ui.graphics.Color
import com.amaxonia.erp.data.remote.dto.DepartmentDto

/**
 * Tokens de color, forma y tipografía extraídos directamente de Flow ERP:
 * - flow-tokens.css (:root)
 * - flow-app.css (body.flow-ui)
 * - app/module/factura/POS/main.css (.rest-catalogo, .rest-cat, .rest-prod)
 */
object FlowCatalogTokens {
    // Marca Flow ERP (flow-tokens.css)
    val Brand = Color(0xFF2488FA)            // --fl-brand
    val BrandStrong = Color(0xFF201B82)      // --fl-brand-strong
    val BrandHover = Color(0xFF1A74E0)       // --fl-brand-hover
    val BrandLight = Color(0xFF5AA8FF)       // --fl-brand-light
    val Indigo = Color(0xFF4F46E5)           // --fl-indigo

    // Tintas y neutros
    val Ink = Color(0xFF0F0C3D)              // --fl-ink (texto oscuro principal)
    val Text = Color(0xFF121212)             // --fl-text
    val Text2 = Color(0xFF475569)            // --fl-text-2
    val Text3 = Color(0xFF94A3B8)            // --fl-text-3
    val Line = Color(0xFFE4E9F0)             // --fl-line (bordes de inputs y cards)
    val LineStrong = Color(0xFFD5DDE8)       // --fl-line-strong
    val Canvas = Color(0xFFF4F5F8)           // --fl-canvas (fondos de inputs/caja)
    val Soft = Color(0xFFEEF5FF)             // --fl-soft
    val Soft2 = Color(0xFFDCEBFF)            // --fl-soft-2
    val Surface = Color(0xFFFFFFFF)          // --fl-surface

    // Estados
    val Success = Color(0xFF10B981)          // --fl-success
    val Danger = Color(0xFFF43F5E)           // --fl-danger
    val Warning = Color(0xFFF5A524)          // --fl-warning

    /**
     * Paleta de 6 categorías de app/module/factura/POS/main.css:
     * - Color 1: #F2555A / #FEECEC (Rojo coral)
     * - Color 2: #F08A24 / #FEF3E6 (Naranja)
     * - Color 3: #10B981 / #E7F8F1 (Verde)
     * - Color 4: #0EA5A4 / #E6F6F6 (Turquesa / Teal)
     * - Color 5: #8B5CF6 / #F1ECFE (Morado)
     * - Color 6: #E0468A / #FCEBF3 (Rosa)
     * - "Todos" (Color 0): #2488FA / #EEF5FF (Flow Brand)
     */
    data class CategoryColor(
        val accent: Color,
        val softBg: Color,
    )

    val ColorTodos = CategoryColor(
        accent = Color(0xFF2488FA),
        softBg = Color(0xFFEEF5FF),
    )

    val CategoryPalette = listOf(
        CategoryColor(accent = Color(0xFFF2555A), softBg = Color(0xFFFEECEC)), // 1
        CategoryColor(accent = Color(0xFFF08A24), softBg = Color(0xFFFEF3E6)), // 2
        CategoryColor(accent = Color(0xFF10B981), softBg = Color(0xFFE7F8F1)), // 3
        CategoryColor(accent = Color(0xFF0EA5A4), softBg = Color(0xFFE6F6F6)), // 4
        CategoryColor(accent = Color(0xFF8B5CF6), softBg = Color(0xFFF1ECFE)), // 5
        CategoryColor(accent = Color(0xFFE0468A), softBg = Color(0xFFFCEBF3)), // 6
    )

    fun getColorForIndex(index: Int): CategoryColor {
        if (index < 0) return ColorTodos
        return CategoryPalette[index % CategoryPalette.size]
    }

    fun getColorForDepartment(deptId: Int?, departments: List<DepartmentDto>): CategoryColor {
        if (deptId == null) return ColorTodos
        val index = departments.indexOfFirst { it.id == deptId }
        return if (index >= 0) getColorForIndex(index) else ColorTodos
    }

    fun getColorForProduct(productDepartment: String, departments: List<DepartmentDto>): CategoryColor {
        val dept = departments.firstOrNull {
            it.id.toString() == productDepartment ||
                it.displayName.equals(productDepartment, ignoreCase = true) ||
                it.name.equals(productDepartment, ignoreCase = true)
        }
        return if (dept != null) getColorForDepartment(dept.id, departments) else ColorTodos
    }
}
