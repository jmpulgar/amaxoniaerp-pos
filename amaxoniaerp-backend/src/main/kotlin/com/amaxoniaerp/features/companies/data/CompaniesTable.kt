package com.amaxoniaerp.features.companies.data

import org.jetbrains.exposed.sql.Table

object CompaniesTable : Table("nomempresa") {
    val codigo = integer("codigo")
    val nombre = varchar("nombre", length = 200)
    val bd = varchar("bd", length = 200).nullable()
    val bdContabilidad = varchar("bd_contabilidad", length = 200).nullable()
    val bdNomina = varchar("bd_nomina", length = 200).nullable()
    val admisActivo = bool("admis_activo")

    override val primaryKey = PrimaryKey(codigo)
}

object DatosEmpresaTable : Table("datos_empresa") {
    val codEmpresa = integer("cod_empresa")
    val rif = varchar("rif", length = 50).nullable()
    val nit = varchar("nit", length = 50).nullable()

    override val primaryKey = PrimaryKey(codEmpresa)
}
