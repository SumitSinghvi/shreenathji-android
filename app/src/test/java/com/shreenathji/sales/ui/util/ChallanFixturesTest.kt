package com.shreenathji.sales.ui.util

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shreenathji.sales.data.local.entities.OrderItemEntity
import com.shreenathji.sales.ui.util.challan.ChallanDataProcessor
import com.shreenathji.sales.ui.util.challan.ChallanPagination
import com.shreenathji.sales.ui.util.challan.ChallanPrintRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the shared challan reference samples (copied from the dashboard repo by
 * `scripts/sync-fixtures.sh`) against the Android grouping + page-break code.
 */
class ChallanFixturesTest {

    private val dir = File(javaClass.classLoader!!.getResource("challan-fixtures")!!.toURI())
    private val fixtures = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString

    private fun JsonObject.dbl(key: String): Double? =
        get(key)?.takeUnless { it.isJsonNull }?.asDouble

    private fun item(j: JsonObject) = OrderItemEntity(
        id = j.str("id") ?: "",
        orderId = "fixture",
        productName = j.str("product_name") ?: "",
        meter = j.dbl("meter") ?: 0.0,
        price = j.dbl("price") ?: 0.0,
        totalPrice = 0.0,
        notes = j.str("notes"),
        designId = j.str("design_id"),
        imageUrl = j.str("image_url"),
        isMatching = j.get("is_matching")?.asBoolean ?: false,
        multiplier = j.get("multiplier")?.asInt ?: 1,
        cutMeter = j.dbl("cut_meter"),
        setGroupId = j.str("set_group_id")
    )

    private fun row(j: JsonObject) = ChallanPrintRow(
        sNo = j.get("sNo").asInt,
        productName = j.str("productName")!!,
        notes = j.str("notes"),
        imageUrl = j.str("imageUrl"),
        meter = j.dbl("meter")!!,
        multiplier = j.get("multiplier").asInt,
        isMatching = j.get("isMatching").asBoolean,
        cutMeter = j.dbl("cutMeter"),
        rate = j.dbl("rate")!!,
        colorCount = j.get("colorCount").asInt
    )

    private fun List<JsonElement>.objs() = map { it.asJsonObject }

    @Test
    fun `finds the samples`() {
        assertTrue("run scripts/sync-fixtures.sh", fixtures.size >= 10)
    }

    @Test
    fun `every sample matches rows, swatches and pages`() {
        for (file in fixtures) {
            val fx = JsonParser.parseString(file.readText()).asJsonObject
            val order = fx.getAsJsonObject("order")
            val expected = fx.getAsJsonObject("expected")
            val orderPhotos = order.getAsJsonArray("image_urls").map { it.asString }
            val hasNotes = !order.str("notes").isNullOrBlank()

            val result = ChallanDataProcessor.processOrderItemsForPrint(
                fx.getAsJsonArray("items").toList().objs().map(::item),
                orderPhotos
            )
            assertEquals("${file.name}: rows", expected.getAsJsonArray("rows").toList().objs().map(::row), result.rows)
            assertEquals(
                "${file.name}: swatches",
                expected.getAsJsonArray("swatches").map { it.asString },
                result.swatches
            )

            val pages = ChallanPagination.paginate(result.rows, result.swatches, hasNotes).map {
                Triple(it.kind.name.lowercase(), it.rows.map { r -> r.sNo }, it.swatches.size)
            }
            val expectedPages = expected.getAsJsonArray("pages").toList().objs().map {
                Triple(it.str("kind")!!, it.getAsJsonArray("rows").map { r -> r.asInt }, it.get("swatches").asInt)
            }
            assertEquals("${file.name}: pages", expectedPages, pages)
        }
    }
}
