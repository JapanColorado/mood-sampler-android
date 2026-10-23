package dev.russell.moodsampler.data.claude

import com.anthropic.core.jsonMapper
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** The JSON body the SDK would send, checked field by field. */
class ClaudeRequestTest {

    private fun body(model: ClaudeModel, image: ByteArray? = null): JsonObject {
        val params = ClaudeClient.request(model, "sys", "hello", image, AssistPrompt.SCHEMA)
        return Json.parseToJsonElement(jsonMapper().writeValueAsString(params._body())).jsonObject
    }

    @Test
    fun carriesModelSystemEffortAndSchema() {
        val json = body(ClaudeModel.SONNET_5_5)
        assertThat(json["model"]!!.jsonPrimitive.content).isEqualTo("claude-sonnet-5-5")
        assertThat(json["system"]!!.jsonPrimitive.content).isEqualTo("sys")
        val output = json["output_config"]!!.jsonObject
        assertThat(output["effort"]!!.jsonPrimitive.content).isEqualTo("low")
        val format = output["format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        val schema = format["schema"]!!.jsonObject
        assertThat(schema["type"]!!.jsonPrimitive.content).isEqualTo("object")
        assertThat(schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("summary", "place", "activities", "company", "pain_types", "tags")
        assertThat(schema["properties"]!!.jsonObject["place"]!!.jsonObject["anyOf"]!!.jsonArray).hasSize(2)
        assertThat(json).doesNotContainKey("thinking")
    }

    @Test
    fun opusAndSonnetOptIntoServerFallbackButHaikuDoesNot() {
        assertThat(body(ClaudeModel.OPUS_5_5)["fallbacks"]!!.jsonPrimitive.content).isEqualTo("default")
        assertThat(body(ClaudeModel.HAIKU_5_5)).doesNotContainKey("fallbacks")
        val headers = ClaudeClient.request(ClaudeModel.OPUS_5_5, "s", "t", null, AssistPrompt.SCHEMA)._headers()
        assertThat(headers.values("anthropic-beta")).contains("server-side-fallback-2026-07-01")
    }

    @Test
    fun photoGoesBeforeTheTextAsBase64Jpeg() {
        val content = body(ClaudeModel.HAIKU_5_5, image = byteArrayOf(1, 2, 3))["messages"]!!.jsonArray
            .single().jsonObject["content"]!!.jsonArray
        assertThat(content.map { it.jsonObject["type"]!!.jsonPrimitive.content }).containsExactly("image", "text").inOrder()
        val source = content[0].jsonObject["source"]!!.jsonObject
        assertThat(source["media_type"]!!.jsonPrimitive.content).isEqualTo("image/jpeg")
        assertThat(source["data"]!!.jsonPrimitive.content).isEqualTo("AQID")
    }
}
