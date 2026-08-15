package com.amaxoniaerp.features.auth.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.security.md5Hash
import com.amaxoniaerp.features.auth.domain.UserRecord
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

class AuthRepository(
    private val database: Database,
) {
    suspend fun authenticate(
        username: String,
        passwordPlain: String,
    ): UserRecord? =
        dbQuery(database) {
            val passwordMd5 = md5Hash(passwordPlain)
            UsersTable
                .selectAll()
                .where { (UsersTable.usuario eq username) and (UsersTable.clave eq passwordMd5) and (UsersTable.status eq "A") }
                .map { row ->
                    UserRecord(
                        id = row[UsersTable.codUsuario],
                        username = row[UsersTable.usuario],
                        companyCodesRaw = row[UsersTable.codEmpresas],
                        role = row[UsersTable.perfil],
                        levelId = row[UsersTable.nivelId],
                    )
                }.singleOrNull()
        }

    suspend fun loadUserById(userId: Int): UserRecord? =
        dbQuery(database) {
            UsersTable
                .selectAll()
                .where { (UsersTable.codUsuario eq userId) and (UsersTable.status eq "A") }
                .map { row ->
                    UserRecord(
                        id = row[UsersTable.codUsuario],
                        username = row[UsersTable.usuario],
                        companyCodesRaw = row[UsersTable.codEmpresas],
                        role = row[UsersTable.perfil],
                        levelId = row[UsersTable.nivelId],
                    )
                }.singleOrNull()
        }
}
