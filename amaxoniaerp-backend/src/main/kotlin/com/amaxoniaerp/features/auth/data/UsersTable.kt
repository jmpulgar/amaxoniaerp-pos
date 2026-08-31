package com.amaxoniaerp.features.auth.data

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("usuarios") {
    val codUsuario = integer("cod_usuario")
    val usuario = varchar("usuario", length = 50)
    val clave = varchar("clave", length = 60)
    val status = varchar("status", length = 1).default("1")
    val codEmpresas = varchar("cod_empresas", length = 250).default("1")
    val perfil = integer("perfil").default(1)
    val nivelId = integer("nivel_id").default(1)

    override val primaryKey = PrimaryKey(codUsuario)
}
