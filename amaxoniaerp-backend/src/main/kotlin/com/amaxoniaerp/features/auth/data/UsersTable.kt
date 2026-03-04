package com.amaxoniaerp.features.auth.data

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("usuarios") {
    val codUsuario = integer("cod_usuario")
    val usuario = varchar("usuario", length = 100)
    val clave = varchar("clave", length = 64)
    val status = varchar("status", length = 1)
    val codEmpresas = varchar("cod_empresas", length = 1024).nullable()
    val perfil = varchar("perfil", length = 50).nullable()
    val nivelId = integer("nivel_id").nullable()

    override val primaryKey = PrimaryKey(codUsuario)
}
