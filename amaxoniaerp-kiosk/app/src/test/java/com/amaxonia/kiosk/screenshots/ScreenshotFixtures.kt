package com.amaxonia.kiosk.screenshots

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCategoryDto
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskReceipt
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.cart.SelectedModifier
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.menu.MenuUiState
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo

/** Realistic sample catalog for screenshot tests (no images: the offline placeholder look is part of the review). */
object ScreenshotFixtures {
    const val BURGERS = 1
    const val COMBOS = 2
    const val SIDES = 3
    const val DRINKS = 4
    const val DESSERTS = 5

    val categories =
        listOf(
            KioskCategoryDto(id = BURGERS, name = "Hamburguesas", order = 1),
            KioskCategoryDto(id = COMBOS, name = "Combos", order = 2),
            KioskCategoryDto(id = SIDES, name = "Acompañantes", order = 3),
            KioskCategoryDto(id = DRINKS, name = "Bebidas", order = 4),
            KioskCategoryDto(id = DESSERTS, name = "Postres", order = 5),
        )

    private val sizeGroup =
        KioskModifierGroupDto(
            id = 10,
            name = "Tamaño del combo",
            min = 1,
            max = 1,
            isMandatory = true,
            isCombo = true,
            options =
                listOf(
                    KioskModifierOptionDto(id = 101, name = "Mediano", extraPrice = "0.00"),
                    KioskModifierOptionDto(id = 102, name = "Grande", extraPrice = "1.25"),
                ),
        )

    private val drinkGroup =
        KioskModifierGroupDto(
            id = 11,
            name = "Elige tu bebida",
            min = 1,
            max = 1,
            isMandatory = true,
            isCombo = true,
            options =
                listOf(
                    KioskModifierOptionDto(id = 111, name = "Refresco de cola", extraPrice = "0.00"),
                    KioskModifierOptionDto(id = 112, name = "Limonada natural", extraPrice = "0.75"),
                    KioskModifierOptionDto(id = 113, name = "Té frío de durazno", extraPrice = "0.00"),
                    KioskModifierOptionDto(id = 114, name = "Batido de fresa", extraPrice = "1.50", soldOut = true),
                ),
        )

    private val extrasGroup =
        KioskModifierGroupDto(
            id = 12,
            name = "Extras",
            min = 0,
            max = 3,
            isMandatory = false,
            isCombo = false,
            options =
                listOf(
                    KioskModifierOptionDto(id = 121, name = "Tocino crujiente", extraPrice = "1.00"),
                    KioskModifierOptionDto(id = 122, name = "Queso cheddar extra", extraPrice = "0.75"),
                    KioskModifierOptionDto(id = 123, name = "Jalapeños", extraPrice = "0.50"),
                    KioskModifierOptionDto(id = 124, name = "Sin cebolla", extraPrice = "0.00"),
                ),
        )

    val comboDobleQueso =
        item(
            id = 201,
            category = COMBOS,
            name = "Combo Doble Queso",
            price = "9.95",
            description = "Doble carne a la parrilla, doble queso cheddar, papas medianas y bebida.",
            groups = listOf(sizeGroup, drinkGroup, extrasGroup),
        )

    val items =
        listOf(
            item(1, BURGERS, "Hamburguesa Clásica", "5.50", "Carne 100% res, lechuga, tomate y salsa de la casa."),
            item(2, BURGERS, "Doble Queso Bacon", "7.25", "Doble carne, cheddar fundido y tocino crujiente."),
            item(3, BURGERS, "Pollo Crispy", "6.40", "Pechuga empanizada, pepinillos y mayonesa de chipotle."),
            item(4, BURGERS, "Hamburguesa BBQ Ahumada", "7.90", "Aros de cebolla, salsa barbacoa y queso ahumado."),
            item(5, BURGERS, "Veggie Garden", "6.10", "Medallón de garbanzo, aguacate y brotes.", soldOut = true),
            item(6, BURGERS, "Mini Burger Kids", "3.95", "Carne, queso y salsa de tomate."),
            comboDobleQueso,
            item(202, COMBOS, "Combo Clásico", "8.25", "Hamburguesa clásica, papas medianas y bebida."),
            item(301, SIDES, "Papas Grandes", "2.95", "Doradas y crujientes, con sal de mar."),
            item(302, SIDES, "Aros de Cebolla", "3.25", "Empanizados y crocantes."),
            item(401, DRINKS, "Refresco 22oz", "1.95", "Cola, naranja o limón."),
            item(402, DRINKS, "Limonada Natural", "2.50", "Recién exprimida."),
            item(501, DESSERTS, "Sundae de Chocolate", "2.75", "Helado de vainilla con chocolate caliente."),
        )

    fun menuState(
        cartCount: Int = 3,
        cartSubtotal: String = "18.85",
    ): MenuUiState =
        MenuUiState(
            isLoading = false,
            categories = categories,
            selectedCategoryId = BURGERS,
            items = items,
            cartItemCount = cartCount,
            cartSubtotal = Money.fromString(cartSubtotal),
        )

    /** Order graph with a realistic 4-line cart. */
    fun filledOrderGraph(): OrderGraph =
        OrderGraph().apply {
            setCurrencyConfig(KioskCurrencyConfig())
            addLine(
                item = comboDobleQueso,
                modifiers =
                    listOf(
                        SelectedModifier(102, "Grande", Money.fromString("1.25")),
                        SelectedModifier(112, "Limonada natural", Money.fromString("0.75")),
                        SelectedModifier(121, "Tocino crujiente", Money.fromString("1.00")),
                    ),
                note = "Sin pepinillos, por favor",
            )
            addLine(item = items.first { it.id == 1 }, qty = 2)
            addLine(item = items.first { it.id == 301 })
            addLine(item = items.first { it.id == 401 }, qty = 2)
        }

    fun completedOrder(tableTent: String? = "12"): CompletedOrderInfo =
        CompletedOrderInfo(
            orderNumber = "A-127",
            diningMode = MODE_DINE_IN,
            tableTent = tableTent,
            total = Money.fromString("27.43"),
            paymentResponse =
                KioskPaymentResponse(
                    orderNumber = "A-127",
                    status = "PAID",
                    dispatch = "RETIRO_MOSTRADOR",
                    receipt =
                        KioskReceipt(
                            companyName = "Amaxonia Burger",
                            orderNumber = "A-127",
                            diningMode = MODE_DINE_IN,
                            customerName = "Consumidor Final",
                            customerId = "CF",
                            date = "",
                            lines = emptyList(),
                            subtotal = "25.64",
                            tax = "1.79",
                            total = "27.43",
                            paymentBrand = "VISA",
                            paymentLast4 = "4242",
                            paymentAuthCode = "AUT1",
                            paymentReference = "REF1",
                        ),
                ),
        )

    @Suppress("LongParameterList")
    private fun item(
        id: Int,
        category: Int,
        name: String,
        price: String,
        description: String,
        soldOut: Boolean = false,
        groups: List<KioskModifierGroupDto> = emptyList(),
    ) = KioskItemDto(
        id = id,
        categoryId = category,
        name = name,
        description = description,
        price = price,
        taxRate = "7",
        imageUrl = null,
        soldOut = soldOut,
        modifierGroups = groups,
    )
}
