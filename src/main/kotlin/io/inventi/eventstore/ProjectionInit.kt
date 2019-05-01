package io.inventi.eventstore

import io.inventi.eventstore.util.LoggerDelegate
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpMethod
import org.springframework.http.client.support.BasicAuthenticationInterceptor
import org.springframework.stereotype.Component
import org.springframework.util.Base64Utils
import org.springframework.util.DigestUtils
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.HttpMessageConverterExtractor
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import org.springframework.web.util.UriComponentsBuilder.fromUriString
import java.io.File
import javax.validation.constraints.NotEmpty


@ConfigurationProperties("eventstore")
@Configuration
@Validated
@ComponentScan("io.inventi.eventstore")
class EventStoreInitConfig {

    var projectionsInit: ProjectionsProperties = ProjectionsProperties()
    lateinit var endpoint: String
    lateinit var username: String
    lateinit var password: String
}


class ProjectionsProperties {

    var enabled: Boolean = true
    var folder: String = "/js"
    var updateOnConflict: Boolean = true
    var overwriteWithoutVersion: Boolean = true
    var failOnError: Boolean = true
}

@Component
class ProjectionInit(private val builder: RestTemplateBuilder,
                     private val eventstore: EventStoreInitConfig) {

    val version_prefix = "//version:"

    companion object {
        @JvmStatic
        val logger by LoggerDelegate()
    }

    @EventListener
    fun onInit(evt: ContextRefreshedEvent) {

        if (!eventstore.projectionsInit.enabled) {
            logger.info("Projection init disabled, skipping")
            return
        }

        val template = builder
                .rootUri(eventstore.endpoint)
                .additionalInterceptors(BasicAuthenticationInterceptor(eventstore.username, eventstore.password))
                .build()


        val filesPath = javaClass.getResource(eventstore.projectionsInit.folder).toURI()
        val walk = File(filesPath).walkBottomUp()
        walk.filter { it.extension == "js" }.forEach {
            ensureProjection(template, it.name.dropLast(3), it.readBytes())
        }
    }


    fun ensureProjection(template: RestTemplate, name: String, content: ByteArray) {

        val version = "${version_prefix} ${DigestUtils.md5DigestAsHex(content)}";

        val versionedContent: ByteArray = (version + "\n").toByteArray(Charsets.UTF_8) + content

        logger.info("Initializing projection \"${name}\" to eventstore")
        template.runCatching {
            postForObject<Map<String, String>>(
                    fromUriString("/projections/continuous")
                            .queryParam("name", name)
                            .queryParam("emit", "yes")
                            .queryParam("checkpoints", "yes")
                            .queryParam("enabled", "yes")
                            .queryParam("trackemittedstreams", "no").toUriString(),
                    versionedContent)
        }.onFailure {
            val msg = it.message ?: ""

            if (msg.contains("409 Conflict") && eventstore.projectionsInit.updateOnConflict) {
                maybeUpdateProjection(template, name, versionedContent, version)
            } else if (eventstore.projectionsInit.failOnError) {
                logger.error("Can't initialize projections due to the error, giving up.")
                throw it
            } else {
                logger.error("Can't initialize projections due to following error: ", it)
            }
        }
    }

    fun maybeUpdateProjection(template: RestTemplate, name: String, content: ByteArray, version: String) {

        val oldContent = template.getForObject("/projection/${name}/query", ByteArray::class.java)
        val oldVersion = oldContent?.let { extractVersion(it) }

        if (oldVersion != null) {
            if (logger.isDebugEnabled) {
                logger.debug("Comparing versions: old ${Base64Utils.encodeToString(oldVersion.toByteArray())} " +
                        "with new ${Base64Utils.encodeToString(version.toByteArray())}");
            }
            if (oldVersion != version) {
                updateProjection(template, content, name)
                logger.info("Projection \"${name}\" was updated to newer version")
            } else {
                logger.info("Projection \"${name}\" has same version, no changes were made")
            }
        } else if (eventstore.projectionsInit.overwriteWithoutVersion) {

            updateProjection(template, content, name)
            logger.info("Projection \"${name}\" was overwritten as version not found")
        }
    }

    private fun updateProjection(template: RestTemplate, content: ByteArray, name: String) {
        val requestCallback = template.httpEntityCallback<Any>(content, Map::class.java)
        val responseExtractor = HttpMessageConverterExtractor(Map::class.java, template.getMessageConverters())
        template.execute(
                fromUriString("/projection/${name}/query").queryParam("emit", "yes").toUriString(),
                HttpMethod.PUT, requestCallback, responseExtractor)
    }

    fun extractVersion(content: ByteArray): String? {

        val lines = String(content, Charsets.UTF_8).lines()
        if (lines.isNotEmpty() && lines[0].startsWith(version_prefix)) {
            return lines[0]
        } else {
            return null
        }
    }
}