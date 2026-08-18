package com.amaxoniaerp.features.sales.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory

sealed class ProcessSaleException(
    category: ErrorCategory,
    message: String,
) : ApiException(category, message)

class DuplicateInvoiceException(
    message: String,
) : ProcessSaleException(ErrorCategory.Conflict, message)

class InsufficientStockException(
    message: String,
) : ProcessSaleException(ErrorCategory.DomainRule, message)

class InvalidSaleRequestException(
    message: String,
) : ProcessSaleException(ErrorCategory.Validation, message)
