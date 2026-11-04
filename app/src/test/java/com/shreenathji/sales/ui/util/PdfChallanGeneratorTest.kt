package com.shreenathji.sales.ui.util

import com.shreenathji.sales.data.local.entities.OrderEntity
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.data.local.entities.OrderWithItems
import com.shreenathji.sales.ui.util.challan.ChallanDataProcessor
import com.shreenathji.sales.ui.util.challan.HtmlChallanGenerator
import com.shreenathji.sales.ui.util.challan.PdfChallanGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfChallanGeneratorTest {

    private fun createSampleOrder(
        imageUrisJson: String? = null,
        itemImages: List<String?> = emptyList()
    ): OrderWithItems {
        val order = OrderEntity(
            id = "ord_1",
            orderNumber = "ORD-001",
            partyId = "p_1",
            partyName = "Test Textiles",
            totalAmount = 5000.0,
            imageUrisJson = imageUrisJson
        )
        val items = if (itemImages.isEmpty()) {
            listOf(
                OrderItemEntity(
                    id = "item_1",
                    orderId = "ord_1",
                    productName = "Cotton Fabric",
                    meter = 100.0,
                    price = 50.0,
                    totalPrice = 5000.0
                )
            )
        } else {
            itemImages.mapIndexed { idx, url ->
                OrderItemEntity(
                    id = "item_$idx",
                    orderId = "ord_1",
                    productName = "Cotton Fabric $idx",
                    meter = 50.0,
                    price = 50.0,
                    totalPrice = 2500.0,
                    imageUrl = url
                )
            }
        }
        return OrderWithItems(order = order, items = items)
    }

    @Test
    fun `extractImages returns empty list when no photos are attached`() {
        val orderWithItems = createSampleOrder()
        val images = PdfChallanGenerator.extractImages(orderWithItems)
        assertTrue(images.isEmpty())
    }

    @Test
    fun `extractImages extracts image URLs from order imageUrisJson`() {
        val json = "[\"https://example.com/photo1.jpg\",\"https://example.com/photo2.jpg\"]"
        val orderWithItems = createSampleOrder(imageUrisJson = json)
        val images = PdfChallanGenerator.extractImages(orderWithItems)

        assertEquals(2, images.size)
        assertEquals("https://example.com/photo1.jpg", images[0])
        assertEquals("https://example.com/photo2.jpg", images[1])
    }

    @Test
    fun `extractImages merges and dedupes order and item photos`() {
        val json = "[\"https://example.com/photo1.jpg\"]"
        val orderWithItems = createSampleOrder(
            imageUrisJson = json,
            itemImages = listOf("https://example.com/photo1.jpg", "https://example.com/photo2.jpg")
        )
        val images = PdfChallanGenerator.extractImages(orderWithItems)

        assertEquals(2, images.size)
        assertEquals("https://example.com/photo1.jpg", images[0])
        assertEquals("https://example.com/photo2.jpg", images[1])
    }

    @Test
    fun `HtmlChallanGenerator includes photo pages when images are present`() {
        val json = "[\"https://example.com/photo1.jpg\",\"https://example.com/photo2.jpg\",\"https://example.com/photo3.jpg\"]"
        val orderWithItems = createSampleOrder(imageUrisJson = json)
        val html = HtmlChallanGenerator.generateChallanHtml(orderWithItems)

        assertTrue(html.contains("Color &amp; Design Swatches"))
        assertTrue(html.contains("https://example.com/photo1.jpg"))
        assertTrue(html.contains("https://example.com/photo2.jpg"))
        assertTrue(html.contains("https://example.com/photo3.jpg"))
    }

    @Test
    fun `processOrderItemsForPrint combines multiple color codes when meters are equal`() {
        val items = listOf(
            OrderItemEntity(id = "1", orderId = "ord_1", productName = "Dabbu Cotton", meter = 310.0, price = 88.0, totalPrice = 27280.0, notes = "3"),
            OrderItemEntity(id = "2", orderId = "ord_1", productName = "Dabbu Cotton", meter = 310.0, price = 88.0, totalPrice = 27280.0, notes = "5"),
            OrderItemEntity(id = "3", orderId = "ord_1", productName = "Dabbu Cotton", meter = 310.0, price = 88.0, totalPrice = 27280.0, notes = "22")
        )
        val result = ChallanDataProcessor.processOrderItemsForPrint(items)
        assertEquals(1, result.rows.size)
        assertEquals("3, 5, 22", result.rows[0].notes)
        assertEquals(930.0, result.rows[0].meter, 0.001)

        val order = OrderEntity(id = "ord_1", orderNumber = "ORD-001", partyId = "p_1", partyName = "sai creation", totalAmount = 81840.0)
        val html = HtmlChallanGenerator.generateChallanHtml(OrderWithItems(order = order, items = items))
        assertTrue(html.contains("Color / Notes"))
        assertTrue(html.contains("3, 5, 22"))
    }

    @Test
    fun `HtmlChallanGenerator includes sales rep name when present`() {
        val order = OrderEntity(
            id = "ord_1",
            orderNumber = "ORD-001",
            partyId = "p_1",
            partyName = "Test Textiles",
            totalAmount = 1000.0,
            salesRepName = "Kunal Verma"
        )
        val html = HtmlChallanGenerator.generateChallanHtml(OrderWithItems(order = order, items = emptyList()))
        assertTrue(html.contains("SALES REP:"))
        assertTrue(html.contains("Kunal Verma"))
    }

    @Test
    fun `HtmlChallanGenerator defaults sales rep name to Direct or Staff when null`() {
        val order = OrderEntity(
            id = "ord_1",
            orderNumber = "ORD-001",
            partyId = "p_1",
            partyName = "Test Textiles",
            totalAmount = 1000.0,
            salesRepName = null
        )
        val html = HtmlChallanGenerator.generateChallanHtml(OrderWithItems(order = order, items = emptyList()))
        assertTrue(html.contains("SALES REP:"))
        assertTrue(html.contains("Direct / Staff"))
    }

    @Test
    fun `processOrderItemsForPrint puts order photos in bottomImages for swatch gallery`() {
        val items = listOf(
            OrderItemEntity(id = "1", orderId = "ord_1", productName = "REEVA PLUS 58\" (Top)", meter = 120.0, price = 69.0, totalPrice = 8280.0, cutMeter = 120.0),
            OrderItemEntity(id = "2", orderId = "ord_1", productName = "REEVA PLUS 58\" (Bottom)", meter = 90.0, price = 69.0, totalPrice = 6210.0, cutMeter = 90.0)
        )
        val photos = listOf("https://example.com/p1.jpg", "https://example.com/p2.jpg")
        val result = ChallanDataProcessor.processOrderItemsForPrint(items, photos)

        assertEquals(2, result.rows.size)
        assertEquals(2, result.bottomImages.size)
        assertEquals("https://example.com/p1.jpg", result.bottomImages[0])
        assertEquals("https://example.com/p2.jpg", result.bottomImages[1])

        val order = OrderEntity(
            id = "ord_1",
            orderNumber = "RR-453401",
            partyId = "p_1",
            partyName = "sukima export",
            totalAmount = 14490.0,
            imageUrisJson = "[\"https://example.com/p1.jpg\",\"https://example.com/p2.jpg\"]"
        )
        val html = HtmlChallanGenerator.generateChallanHtml(OrderWithItems(order = order, items = items))
        assertTrue(html.contains("Color &amp; Design Swatches"))
        assertTrue(html.contains("https://example.com/p1.jpg"))
        assertTrue(html.contains("https://example.com/p2.jpg"))
        assertTrue(html.contains("Color / Notes"))
    }
}
