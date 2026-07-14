package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.Promocion

interface PromotionRepository {
    suspend fun syncPromotions(): Result<Unit>

    suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>>

    suspend fun getPromotionById(promotionId: String): Result<Promocion>
}
