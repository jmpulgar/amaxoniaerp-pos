package com.amaxoniaerp.features.sales.domain

sealed class ProcessSaleException(message: String) : RuntimeException(message)

class DuplicateInvoiceException(message: String) : ProcessSaleException(message)

class InsufficientStockException(message: String) : ProcessSaleException(message)

class InvalidSaleRequestException(message: String) : ProcessSaleException(message)
