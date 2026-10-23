package dev.russell.moodsampler.data.claude

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A failed Claude call, with a message fit to show on the capture form. */
class ClaudeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin wrapper over the Anthropic Java SDK: one structured-output request per
 * capture. The SDK client is cached per API key.
 */
class ClaudeClient {

    private var cached: Pair<String, AnthropicClient>? = null

    @Synchronized
    private fun client(apiKey: String): AnthropicClient {
        cached?.let { (key, client) -> if (key == apiKey) return client else client.close() }
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(2)
            .build()
            .also { cached = apiKey to it }
    }

    /**
     * Send [text] (after the optional JPEG [image]) under [system], constrained to
     * [schema], and return the JSON text of the reply.
     * @throws ClaudeException with a user-facing message on any failure.
     */
    suspend fun structured(
        apiKey: String,
        model: ClaudeModel,
        system: String,
        text: String,
        image: ByteArray?,
        schema: Map<String, Any>,
    ): String = withContext(Dispatchers.IO) {
        val params = request(model, system, text, image, schema)

        val message = try {
            client(apiKey).messages().create(params)
        } catch (e: UnauthorizedException) {
            throw ClaudeException("Claude rejected the API key. Check it in Settings.", e)
        } catch (e: PermissionDeniedException) {
            throw ClaudeException("This API key can't use ${model.label}. Try another model in Settings.", e)
        } catch (e: NotFoundException) {
            throw ClaudeException("${model.label} isn't available to this API key. Try another model in Settings.", e)
        } catch (e: RateLimitException) {
            throw ClaudeException("Claude is rate-limiting this key. Try again in a moment.", e)
        } catch (e: BadRequestException) {
            throw ClaudeException("Claude couldn't process this request (${e.message}).", e)
        } catch (e: InternalServerException) {
            throw ClaudeException("Claude is having trouble right now. Try again, or fill it in by hand.", e)
        } catch (e: AnthropicServiceException) {
            throw ClaudeException("Claude returned an error (${e.statusCode()}).", e)
        } catch (e: AnthropicIoException) {
            throw ClaudeException("Couldn't reach Claude. Check your connection.", e)
        }

        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw ClaudeException("Claude declined this one. Fill it in by hand.")
            StopReason.MAX_TOKENS -> throw ClaudeException("Claude's reply was cut off. Try again.")
            else -> Unit
        }
        message.content().firstNotNullOfOrNull { block -> block.text().map { it.text() }.orElse(null) }
            ?: throw ClaudeException("Claude sent an empty reply. Try again.")
    }

    companion object {
        /** The Messages API request for one capture; separate so its JSON can be unit-tested. */
        internal fun request(
            model: ClaudeModel,
            system: String,
            text: String,
            image: ByteArray?,
            schema: Map<String, Any>,
        ): MessageCreateParams {
            val blocks = buildList {
                if (image != null) {
                    add(
                        ContentBlockParam.ofImage(
                            Base64ImageSource.builder()
                                .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                .data(Base64.getEncoder().encodeToString(image))
                                .build()
                        )
                    )
                }
                add(ContentBlockParam.ofText(text))
            }
            return MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(16_000L)
                .system(system)
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(
                    OutputConfig.builder()
                        // Filling in a form from a short narration needs little deliberation.
                        .effort(OutputConfig.Effort.LOW)
                        .format(
                            JsonOutputFormat.builder()
                                .schema(
                                    JsonOutputFormat.Schema.builder()
                                        .putAllAdditionalProperties(schema.mapValues { JsonValue.from(it.value) })
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .apply {
                    // If a safety classifier declines, retry server-side on the model Anthropic picks.
                    if (model.hasServerFallback) {
                        putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    }
                }
                .build()
        }
    }
}
