package com.amaxonia.erp.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUrlHelperTest {

    @Test
    fun `productImageUrl builds correct url for relative path`() {
        val result = ImageUrlHelper.productImageUrl(
            baseUrl = "http://10.0.2.2:8080/",
            countryCode = "PA",
            companyDb = "empresa_test",
            photoPath = "fotos/1_foto.jpeg",
        )
        assertEquals("http://10.0.2.2:8080/api/data/PA/empresa_test/item/fotos/1_foto.jpeg", result)
    }

    @Test
    fun `productImageUrl builds correct url for simple filename`() {
        val result = ImageUrlHelper.productImageUrl(
            baseUrl = "https://api.listoerp.app",
            countryCode = "VE",
            companyDb = "empresa_ve",
            photoPath = "101_foto.jpg",
        )
        assertEquals("https://api.listoerp.app/api/data/VE/empresa_ve/item/101_foto.jpg", result)
    }

    @Test
    fun `productImageUrl encodes special characters and spaces preserving slashes`() {
        val result = ImageUrlHelper.productImageUrl(
            baseUrl = "http://localhost:8080",
            countryCode = "PA",
            companyDb = "db",
            photoPath = "fotos con espacio/producto café.png",
        )
        assertEquals("http://localhost:8080/api/data/PA/db/item/fotos%20con%20espacio/producto%20caf%C3%A9.png", result)
    }

    @Test
    fun `productImageUrl preserves complete http and https urls`() {
        val httpUrl = "http://example.com/images/prod1.jpg"
        val httpsUrl = "https://cdn.example.com/images/prod2.png"

        assertEquals(httpUrl, ImageUrlHelper.productImageUrl("http://base", "PA", "db", httpUrl))
        assertEquals(httpsUrl, ImageUrlHelper.productImageUrl("http://base", "PA", "db", httpsUrl))
    }

    @Test
    fun `productImageUrl returns empty string when inputs are blank`() {
        assertEquals("", ImageUrlHelper.productImageUrl("http://base", "PA", "db", ""))
        assertEquals("", ImageUrlHelper.productImageUrl("http://base", "PA", "db", "   "))
        assertEquals("", ImageUrlHelper.productImageUrl("", "PA", "db", "foto.jpg"))
        assertEquals("", ImageUrlHelper.productImageUrl("http://base", "", "db", "foto.jpg"))
        assertEquals("", ImageUrlHelper.productImageUrl("http://base", "PA", "", "foto.jpg"))
    }

    @Test
    fun `clientPhotoUrl builds correct url`() {
        val result = ImageUrlHelper.clientPhotoUrl(
            baseUrl = "http://10.0.2.2:8080",
            countryCode = "PA",
            companyDb = "db",
            idCliente = "cli-123",
            photoFilename = "cli-123_foto.jpeg",
        )
        assertEquals("http://10.0.2.2:8080/api/data/PA/db/cliente_foto/cli-123/cli-123_foto.jpeg", result)
    }

    @Test
    fun `clientPhotoUrl preserves full urls and handles empty inputs`() {
        val fullUrl = "https://cdn.example.com/client.jpg"
        assertEquals(fullUrl, ImageUrlHelper.clientPhotoUrl("http://base", "PA", "db", "1", fullUrl))
        assertEquals("", ImageUrlHelper.clientPhotoUrl("http://base", "PA", "db", "1", ""))
        assertEquals("", ImageUrlHelper.clientPhotoUrl("http://base", "PA", "db", "", "foto.jpg"))
    }

    @Test
    fun `paymentMethodImageUrl returns direct image for data URI, full url or raw base64`() {
        val dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val fullUrl = "https://example.com/visa.png"
        val rawBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        assertEquals(dataUri, ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, dataUri))
        assertEquals(fullUrl, ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, fullUrl))
        assertEquals(rawBase64, ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, rawBase64))
    }

    @Test
    fun `paymentMethodImageUrl builds canonical backend url when imageValue is a filename or path`() {
        val expected = "http://10.0.2.2:8080/api/data/PA/testdb/forma_pago/2"
        val resultWithFilename = ImageUrlHelper.paymentMethodImageUrl(
            baseUrl = "http://10.0.2.2:8080/",
            countryCode = "PA",
            companyDb = "testdb",
            idFormaPago = 2,
            imageValue = "forma_2.png",
        )
        assertEquals(expected, resultWithFilename)
    }

    @Test
    fun `paymentMethodImageUrl returns empty when imageValue is null or blank`() {
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, null))
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, ""))
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 1, "   "))
    }

    @Test
    fun `paymentMethodImageUrl returns empty when required params are missing and image is not direct`() {
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("", "PA", "db", 1, "forma.png"))
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "", "db", 1, "forma.png"))
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "", 1, "forma.png"))
        assertEquals("", ImageUrlHelper.paymentMethodImageUrl("http://base", "PA", "db", 0, "forma.png"))
    }
}
