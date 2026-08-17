package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.features.items.domain.CreateProductRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

internal fun insertItemVE(
    table: ItemsTableVE,
    request: CreateProductRequest,
): Int =
    table.insert {
        // Campos base
        it[codItem] = request.code
        it[descripcion1] = request.name
        it[descripcion2] = request.description
        it[referencia] = request.reference
        it[codigoBarras] = request.barcode
        it[codigoBarras2] = request.barcode2 ?: ""
        it[codigoBarras3] = request.barcode3 ?: ""
        it[codDepartamento] = request.departmentId
        it[departamentoId] = request.departmentId
        it[seccionId] = request.sectionId
        it[familiaId] = request.familyId
        it[subfamiliaId] = request.subfamilyId
        it[marcaId] = request.brandId
        it[codLinea] = request.lineId
        it[lineaId] = request.lineId
        it[precio1] = request.price1.toBigDecimal()
        it[utilidad1] = request.utility1.toBigDecimal()
        it[coniva1] = request.priceWithTax1.toBigDecimal()
        it[precio2] = request.price2.toBigDecimal()
        it[utilidad2] = request.utility2.toBigDecimal()
        it[coniva2] = request.priceWithTax2.toBigDecimal()
        it[precio3] = request.price3.toBigDecimal()
        it[utilidad3] = request.utility3.toBigDecimal()
        it[coniva3] = request.priceWithTax3.toBigDecimal()
        it[precio4] = request.price4.toBigDecimal()
        it[utilidad4] = request.utility4.toBigDecimal()
        it[coniva4] = request.priceWithTax4.toBigDecimal()
        it[precio5] = request.price5.toBigDecimal()
        it[utilidad5] = request.utility5.toBigDecimal()
        it[coniva5] = request.priceWithTax5.toBigDecimal()
        it[costoActual] = request.currentCost.toBigDecimal()
        it[montoExento] = !request.isTaxExempt
        it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
        it[existenciaTotal] = request.totalStock
        it[estatus] = "A"
        it[codItemForma] = 1
        it[tipoProd] = 2
        it[usuarioCreacion] = "API"
        // Campos específicos VE (solo los que existen)
        it[balanza] = request.isScale ?: false
        it[idMonedaBase] = request.baseCurrencyId
    } get table.idItem

internal fun insertItemPA(
    table: ItemsTablePA,
    request: CreateProductRequest,
): Int =
    table.insert {
        // Campos base
        it[codItem] = request.code
        it[descripcion1] = request.name
        it[descripcion2] = request.description
        it[referencia] = request.reference
        it[codigoBarras] = request.barcode
        it[codigoBarras2] = request.barcode2 ?: ""
        it[codigoBarras3] = request.barcode3 ?: ""
        it[codDepartamento] = request.departmentId
        it[departamentoId] = request.departmentId
        it[seccionId] = request.sectionId
        it[familiaId] = request.familyId
        it[subfamiliaId] = request.subfamilyId
        it[marcaId] = request.brandId
        it[codLinea] = request.lineId
        it[lineaId] = request.lineId
        it[precio1] = request.price1.toBigDecimal()
        it[utilidad1] = request.utility1.toBigDecimal()
        it[coniva1] = request.priceWithTax1.toBigDecimal()
        it[precio2] = request.price2.toBigDecimal()
        it[utilidad2] = request.utility2.toBigDecimal()
        it[coniva2] = request.priceWithTax2.toBigDecimal()
        it[precio3] = request.price3.toBigDecimal()
        it[utilidad3] = request.utility3.toBigDecimal()
        it[coniva3] = request.priceWithTax3.toBigDecimal()
        it[precio4] = request.price4.toBigDecimal()
        it[utilidad4] = request.utility4.toBigDecimal()
        it[coniva4] = request.priceWithTax4.toBigDecimal()
        it[precio5] = request.price5.toBigDecimal()
        it[utilidad5] = request.utility5.toBigDecimal()
        it[coniva5] = request.priceWithTax5.toBigDecimal()
        it[costoActual] = request.currentCost.toBigDecimal()
        it[montoExento] = !request.isTaxExempt
        it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
        it[existenciaTotal] = request.totalStock
        it[estatus] = "A"
        it[codItemForma] = 1
        it[tipoProd] = 2
        it[usuarioCreacion] = "API"
        // Campos específicos PA (solo los que existen)
        it[detallesKit] = request.kitDetails ?: "F"
        it[idSegmentoGob] = request.governmentSegmentId
        it[idFamiliaGob] = request.governmentFamilyId
    } get table.idItem

internal fun updateItemVE(
    table: ItemsTableVE,
    id: Int,
    request: CreateProductRequest,
): Int =
    table.update({ table.idItem eq id }) {
        it[codItem] = request.code
        it[descripcion1] = request.name
        it[referencia] = request.reference
        it[codigoBarras] = request.barcode
        it[codigoBarras2] = request.barcode2 ?: ""
        it[codigoBarras3] = request.barcode3 ?: ""
        it[codDepartamento] = request.departmentId
        it[departamentoId] = request.departmentId
        it[seccionId] = request.sectionId
        it[familiaId] = request.familyId
        it[subfamiliaId] = request.subfamilyId
        it[marcaId] = request.brandId
        it[codLinea] = request.lineId
        it[lineaId] = request.lineId
        it[precio1] = request.price1.toBigDecimal()
        it[utilidad1] = request.utility1.toBigDecimal()
        it[coniva1] = request.priceWithTax1.toBigDecimal()
        it[precio2] = request.price2.toBigDecimal()
        it[utilidad2] = request.utility2.toBigDecimal()
        it[coniva2] = request.priceWithTax2.toBigDecimal()
        it[precio3] = request.price3.toBigDecimal()
        it[utilidad3] = request.utility3.toBigDecimal()
        it[coniva3] = request.priceWithTax3.toBigDecimal()
        it[precio4] = request.price4.toBigDecimal()
        it[utilidad4] = request.utility4.toBigDecimal()
        it[coniva4] = request.priceWithTax4.toBigDecimal()
        it[precio5] = request.price5.toBigDecimal()
        it[utilidad5] = request.utility5.toBigDecimal()
        it[coniva5] = request.priceWithTax5.toBigDecimal()
        it[costoActual] = request.currentCost.toBigDecimal()
        it[montoExento] = !request.isTaxExempt
        it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
        // Solo VE
        it[balanza] = request.isScale ?: false
        it[idMonedaBase] = request.baseCurrencyId
    }

internal fun updateItemPA(
    table: ItemsTablePA,
    id: Int,
    request: CreateProductRequest,
): Int =
    table.update({ table.idItem eq id }) {
        it[codItem] = request.code
        it[descripcion1] = request.name
        it[referencia] = request.reference
        it[codigoBarras] = request.barcode
        it[codigoBarras2] = request.barcode2 ?: ""
        it[codigoBarras3] = request.barcode3 ?: ""
        it[codDepartamento] = request.departmentId
        it[departamentoId] = request.departmentId
        it[seccionId] = request.sectionId
        it[familiaId] = request.familyId
        it[subfamiliaId] = request.subfamilyId
        it[marcaId] = request.brandId
        it[codLinea] = request.lineId
        it[lineaId] = request.lineId
        it[precio1] = request.price1.toBigDecimal()
        it[utilidad1] = request.utility1.toBigDecimal()
        it[coniva1] = request.priceWithTax1.toBigDecimal()
        it[precio2] = request.price2.toBigDecimal()
        it[utilidad2] = request.utility2.toBigDecimal()
        it[coniva2] = request.priceWithTax2.toBigDecimal()
        it[precio3] = request.price3.toBigDecimal()
        it[utilidad3] = request.utility3.toBigDecimal()
        it[coniva3] = request.priceWithTax3.toBigDecimal()
        it[precio4] = request.price4.toBigDecimal()
        it[utilidad4] = request.utility4.toBigDecimal()
        it[coniva4] = request.priceWithTax4.toBigDecimal()
        it[precio5] = request.price5.toBigDecimal()
        it[utilidad5] = request.utility5.toBigDecimal()
        it[coniva5] = request.priceWithTax5.toBigDecimal()
        it[costoActual] = request.currentCost.toBigDecimal()
        it[montoExento] = !request.isTaxExempt
        it[iva] = if (request.isTaxExempt) BigDecimal.ZERO else request.taxRate.toBigDecimal()
        // Solo PA
        it[detallesKit] = request.kitDetails ?: "F"
        it[idSegmentoGob] = request.governmentSegmentId
        it[idFamiliaGob] = request.governmentFamilyId
    }
