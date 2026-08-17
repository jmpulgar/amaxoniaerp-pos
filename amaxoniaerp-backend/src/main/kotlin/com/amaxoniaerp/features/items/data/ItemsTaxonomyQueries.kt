package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.dbQuery
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager

suspend fun ItemsRepository.listDepartments(database: Database): List<Pair<Int, String>> =
    dbQuery(database) {
        DepartamentoTable
            .selectAll()
            .orderBy(DepartamentoTable.descripcion)
            .map { row ->
                val id = row[DepartamentoTable.id]
                val code = row[DepartamentoTable.codigo]?.trim().orEmpty()
                val desc = row[DepartamentoTable.descripcion]?.trim().orEmpty()
                val name =
                    when {
                        code.isNotBlank() && desc.isNotBlank() -> "$code - $desc"
                        desc.isNotBlank() -> desc
                        code.isNotBlank() -> code
                        else -> "Departamento $id"
                    }
                id to name
            }
    }

suspend fun ItemsRepository.listSections(
    database: Database,
    departmentId: Int,
): List<Pair<Int, String>> =
    dbQuery(database) {
        listCatalogBySql(
            """
            SELECT
                S.id AS id,
                CASE
                    WHEN COALESCE(NULLIF(TRIM(S.codigo), ''), '') <> ''
                         AND COALESCE(NULLIF(TRIM(S.descripcion), ''), '') <> ''
                        THEN CONCAT(S.codigo, ' - ', S.descripcion)
                    WHEN COALESCE(NULLIF(TRIM(S.descripcion), ''), '') <> ''
                        THEN S.descripcion
                    WHEN COALESCE(NULLIF(TRIM(S.codigo), ''), '') <> ''
                        THEN S.codigo
                    ELSE CONCAT('Seccion ', S.id)
                END AS name
            FROM seccion S
            INNER JOIN seccion_departamento SD ON SD.seccion_id = S.id
            WHERE SD.departamento_id = $departmentId
            ORDER BY name ASC
            """.trimIndent(),
        )
    }

suspend fun ItemsRepository.listFamilies(
    database: Database,
    sectionId: Int,
): List<Pair<Int, String>> =
    dbQuery(database) {
        listCatalogBySql(
            """
            SELECT DISTINCT
                F.id AS id,
                CASE
                    WHEN COALESCE(NULLIF(TRIM(F.codigo), ''), '') <> ''
                         AND COALESCE(NULLIF(TRIM(F.descripcion), ''), '') <> ''
                        THEN CONCAT(F.codigo, ' - ', F.descripcion)
                    WHEN COALESCE(NULLIF(TRIM(F.descripcion), ''), '') <> ''
                        THEN F.descripcion
                    WHEN COALESCE(NULLIF(TRIM(F.codigo), ''), '') <> ''
                        THEN F.codigo
                    ELSE CONCAT('Familia ', F.id)
                END AS name
            FROM familia F
            INNER JOIN item I ON I.familia_id = F.id
            WHERE I.seccion_id = $sectionId
            ORDER BY name ASC
            """.trimIndent(),
        )
    }

suspend fun ItemsRepository.listSubFamilies(
    database: Database,
    familyId: Int,
): List<Pair<Int, String>> =
    dbQuery(database) {
        listCatalogBySql(
            """
            SELECT
                SF.id AS id,
                CASE
                    WHEN COALESCE(NULLIF(TRIM(SF.codigo), ''), '') <> ''
                         AND COALESCE(NULLIF(TRIM(SF.descripcion), ''), '') <> ''
                        THEN CONCAT(SF.codigo, ' - ', SF.descripcion)
                    WHEN COALESCE(NULLIF(TRIM(SF.descripcion), ''), '') <> ''
                        THEN SF.descripcion
                    WHEN COALESCE(NULLIF(TRIM(SF.codigo), ''), '') <> ''
                        THEN SF.codigo
                    ELSE CONCAT('Subfamilia ', SF.id)
                END AS name
            FROM subfamilia SF
            INNER JOIN subfamilia_familia SFF ON SFF.subfamilia_id = SF.id
            WHERE SFF.familia_id = $familyId
            ORDER BY name ASC
            """.trimIndent(),
        )
    }

suspend fun ItemsRepository.listBrands(database: Database): List<Pair<Int, String>> =
    dbQuery(database) {
        listCatalogBySql(
            """
            SELECT
                M.id AS id,
                CASE
                    WHEN COALESCE(NULLIF(TRIM(M.codigo), ''), '') <> ''
                         AND COALESCE(NULLIF(TRIM(M.descripcion), ''), '') <> ''
                        THEN CONCAT(M.codigo, ' - ', M.descripcion)
                    WHEN COALESCE(NULLIF(TRIM(M.descripcion), ''), '') <> ''
                        THEN M.descripcion
                    WHEN COALESCE(NULLIF(TRIM(M.codigo), ''), '') <> ''
                        THEN M.codigo
                    ELSE CONCAT('Marca ', M.id)
                END AS name
            FROM marca M
            ORDER BY name ASC
            """.trimIndent(),
        )
    }

suspend fun ItemsRepository.listLines(
    database: Database,
    brandId: Int,
): List<Pair<Int, String>> =
    dbQuery(database) {
        listCatalogBySql(
            """
            SELECT
                L.cod_linea AS id,
                CASE
                    WHEN COALESCE(NULLIF(TRIM(L.descripcion), ''), '') <> ''
                        THEN CONCAT(LPAD(L.cod_linea, 5, '0'), ' - ', L.descripcion)
                    ELSE CONCAT('Linea ', L.cod_linea)
                END AS name
            FROM linea L
            WHERE L.marca = $brandId
            ORDER BY name ASC
            """.trimIndent(),
        )
    }

internal fun listCatalogBySql(sql: String): List<Pair<Int, String>> =
    TransactionManager.current().exec(sql) { result ->
        val list = mutableListOf<Pair<Int, String>>()
        while (result.next()) {
            list.add(result.getInt("id") to result.getString("name"))
        }
        list
    } ?: emptyList()
