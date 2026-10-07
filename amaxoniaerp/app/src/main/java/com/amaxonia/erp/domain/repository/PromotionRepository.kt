package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.Promocion

interface PromotionRepository {
    suspend fun getPromotions(forceRefresh: Boolean = false): Result<List<Promocion>>
    suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>>
}
