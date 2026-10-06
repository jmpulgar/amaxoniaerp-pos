package com.amaxonia.kiosk.ui.customizer

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import com.amaxonia.kiosk.domain.cart.OrderGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductCustomizerViewModelTest {
    private lateinit var orderGraph: OrderGraph

    private val singleMandatoryGroup =
        KioskModifierGroupDto(
            id = 1,
            name = "Elige tu Pan",
            min = 1,
            max = 1,
            isMandatory = true,
            isCombo = false,
            options =
                listOf(
                    KioskModifierOptionDto(id = 10, name = "Pan Brioche", extraPrice = "0.00"),
                    KioskModifierOptionDto(id = 11, name = "Pan Artesanal", extraPrice = "0.50"),
                    KioskModifierOptionDto(id = 12, name = "Pan Sin Gluten", extraPrice = "1.00", soldOut = true),
                ),
        )

    private val multiOptionalGroup =
        KioskModifierGroupDto(
            id = 2,
            name = "Salsas Adicionales",
            min = 0,
            max = 2,
            isMandatory = false,
            isCombo = false,
            options =
                listOf(
                    KioskModifierOptionDto(id = 20, name = "Salsa BBQ", extraPrice = "0.25"),
                    KioskModifierOptionDto(id = 21, name = "Salsa Tártara", extraPrice = "0.25"),
                    KioskModifierOptionDto(id = 22, name = "Mostaza Miel", extraPrice = "0.50"),
                ),
        )

    private val mandatoryMultiGroup =
        KioskModifierGroupDto(
            id = 3,
            name = "Guarniciones (Mín 2)",
            min = 2,
            max = 3,
            isMandatory = true,
            isCombo = false,
            options =
                listOf(
                    KioskModifierOptionDto(id = 30, name = "Papas Fritas", extraPrice = "0.00"),
                    KioskModifierOptionDto(id = 31, name = "Aros de Cebolla", extraPrice = "0.50"),
                    KioskModifierOptionDto(id = 32, name = "Ensalada", extraPrice = "0.00"),
                ),
        )

    private val sampleItem =
        KioskItemDto(
            id = 100,
            categoryId = 1,
            name = "Hamburguesa Doble",
            description = "Doble carne con queso",
            price = "5.00",
            taxRate = "7.00",
            imageUrl = null,
            soldOut = false,
            modifierGroups = listOf(singleMandatoryGroup, multiOptionalGroup),
        )

    @Before
    fun setUp() {
        orderGraph = OrderGraph()
        orderGraph.setCurrencyConfig(
            KioskCurrencyConfig(base = "USD", secondary = "Bs", rate = "40.0"),
        )
    }

    @Test
    fun `initializes with mandatory single choice preselected`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val state = viewModel.uiState.value

        assertEquals(1, state.quantity)
        val selectedBread = state.selectedOptions[singleMandatoryGroup.id]
        assertNotNull(selectedBread)
        assertEquals(1, selectedBread?.size)
        assertEquals("Pan Brioche", selectedBread?.first()?.name)

        // Multi-optional group should be empty initially
        assertNull(state.selectedOptions[multiOptionalGroup.id])
        assertTrue(state.isValid)
    }

    @Test
    fun `toggleOption in single-choice group replaces selection`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val optionArtisanal = singleMandatoryGroup.options[1]

        viewModel.toggleOption(singleMandatoryGroup, optionArtisanal)
        val selectedBread = viewModel.uiState.value.selectedOptions[singleMandatoryGroup.id]

        assertEquals(1, selectedBread?.size)
        assertEquals("Pan Artesanal", selectedBread?.first()?.name)
    }

    @Test
    fun `toggleOption ignores sold out modifier`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val soldOutOption = singleMandatoryGroup.options[2] // Pan Sin Gluten (soldOut = true)

        viewModel.toggleOption(singleMandatoryGroup, soldOutOption)
        val selectedBread = viewModel.uiState.value.selectedOptions[singleMandatoryGroup.id]

        assertEquals("Pan Brioche", selectedBread?.first()?.name)
    }

    @Test
    fun `toggleOption in multi-choice group adds and removes up to max`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val bbq = multiOptionalGroup.options[0]
        val tartara = multiOptionalGroup.options[1]
        val honeyMustard = multiOptionalGroup.options[2]

        // Add BBQ
        viewModel.toggleOption(multiOptionalGroup, bbq)
        var selectedSauces = viewModel.uiState.value.selectedOptions[multiOptionalGroup.id]
        assertEquals(1, selectedSauces?.size)

        // Add Tartara
        viewModel.toggleOption(multiOptionalGroup, tartara)
        selectedSauces = viewModel.uiState.value.selectedOptions[multiOptionalGroup.id]
        assertEquals(2, selectedSauces?.size)

        // Attempt to add 3rd when max is 2 -> should be ignored
        viewModel.toggleOption(multiOptionalGroup, honeyMustard)
        selectedSauces = viewModel.uiState.value.selectedOptions[multiOptionalGroup.id]
        assertEquals(2, selectedSauces?.size)

        // Remove BBQ
        viewModel.toggleOption(multiOptionalGroup, bbq)
        selectedSauces = viewModel.uiState.value.selectedOptions[multiOptionalGroup.id]
        assertEquals(1, selectedSauces?.size)
        assertEquals("Salsa Tártara", selectedSauces?.first()?.name)
    }

    @Test
    fun `calculates unit and total price accurately with extras and quantity`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val optionArtisanal = singleMandatoryGroup.options[1] // +0.50
        val bbq = multiOptionalGroup.options[0] // +0.25

        viewModel.toggleOption(singleMandatoryGroup, optionArtisanal)
        viewModel.toggleOption(multiOptionalGroup, bbq)

        // Base 5.00 + 0.50 + 0.25 = 5.75
        assertEquals(Money.fromString("5.75"), viewModel.uiState.value.unitPrice)
        assertEquals(Money.fromString("5.75"), viewModel.uiState.value.totalPrice)

        // Increment quantity to 3 -> 5.75 * 3 = 17.25
        viewModel.incrementQuantity()
        viewModel.incrementQuantity()
        assertEquals(3, viewModel.uiState.value.quantity)
        assertEquals(Money.fromString("17.25"), viewModel.uiState.value.totalPrice)

        // Decrement quantity to 2 -> 5.75 * 2 = 11.50
        viewModel.decrementQuantity()
        assertEquals(2, viewModel.uiState.value.quantity)
        assertEquals(Money.fromString("11.50"), viewModel.uiState.value.totalPrice)

        // Decrement cannot go below 1
        viewModel.decrementQuantity()
        viewModel.decrementQuantity()
        assertEquals(1, viewModel.uiState.value.quantity)
    }

    @Test
    fun `limits special instructions note to 80 characters`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        val longNote = "A".repeat(100)

        viewModel.onNoteChanged(longNote)
        assertEquals(80, viewModel.uiState.value.note.length)
        assertEquals("A".repeat(80), viewModel.uiState.value.note)
    }

    @Test
    fun `addToCart blocks and shows validation errors if mandatory group is not satisfied`() {
        val complexItem = sampleItem.copy(modifierGroups = listOf(mandatoryMultiGroup))
        val viewModel = ProductCustomizerViewModel(complexItem, orderGraph)

        assertFalse(viewModel.uiState.value.isValid)

        var successCalled = false
        viewModel.addToCart(onSuccess = { successCalled = true })

        assertFalse(successCalled)
        assertEquals(0, orderGraph.lines.value.size)
        assertTrue(viewModel.uiState.value.validationErrors.containsKey(mandatoryMultiGroup.id))

        // Fulfill min requirements
        viewModel.toggleOption(mandatoryMultiGroup, mandatoryMultiGroup.options[0])
        viewModel.toggleOption(mandatoryMultiGroup, mandatoryMultiGroup.options[1])
        assertTrue(viewModel.uiState.value.isValid)

        viewModel.addToCart(onSuccess = { successCalled = true })
        assertTrue(successCalled)
        assertEquals(1, orderGraph.lines.value.size)
        val addedLine = orderGraph.lines.value.first()
        assertEquals("Hamburguesa Doble", addedLine.item.name)
        assertEquals(2, addedLine.selectedModifiers.size)
    }

    @Test
    fun `addToCart succeeds with valid selections and note`() {
        val viewModel = ProductCustomizerViewModel(sampleItem, orderGraph)
        viewModel.onNoteChanged("Sin cebolla por favor")

        var successCalled = false
        viewModel.addToCart(onSuccess = { successCalled = true })

        assertTrue(successCalled)
        assertEquals(1, orderGraph.lines.value.size)
        val addedLine = orderGraph.lines.value.first()
        assertEquals("Sin cebolla por favor", addedLine.note)
        assertEquals(1, addedLine.selectedModifiers.size)
        assertEquals("Pan Brioche", addedLine.selectedModifiers.first().optionName)
    }

    @Test
    fun `options flagged isDefault are preselected without exceeding max`() {
        val combo =
            sampleItem.copy(
                modifierGroups =
                    listOf(
                        singleMandatoryGroup.copy(
                            // The sold-out option is flagged too but must never be preselected.
                            options = singleMandatoryGroup.options.map { it.copy(isDefault = it.id == 11 || it.soldOut) },
                        ),
                        multiOptionalGroup.copy(
                            options = multiOptionalGroup.options.map { it.copy(isDefault = true) },
                        ),
                        mandatoryMultiGroup,
                    ),
            )

        val state = ProductCustomizerViewModel(combo, orderGraph).uiState.value

        assertEquals(listOf(11), state.selectedOptions[singleMandatoryGroup.id]?.map { it.id })
        assertEquals(listOf(20, 21), state.selectedOptions[multiOptionalGroup.id]?.map { it.id })
        assertNull(state.selectedOptions[mandatoryMultiGroup.id])
    }
}
