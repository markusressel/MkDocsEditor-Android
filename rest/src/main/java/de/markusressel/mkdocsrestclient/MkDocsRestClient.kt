package de.markusressel.mkdocsrestclient

import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Response
import de.markusressel.mkdocsrestclient.document.DocumentApi
import de.markusressel.mkdocsrestclient.document.DocumentApiImpl
import de.markusressel.mkdocsrestclient.resource.ResourceApi
import de.markusressel.mkdocsrestclient.resource.ResourceApiImpl
import de.markusressel.mkdocsrestclient.section.SectionApi
import de.markusressel.mkdocsrestclient.section.SectionApiImpl
import de.markusressel.mkdocsrestclient.section.SectionModel
import io.reactivex.Single

/**
 * Convenience delegation class for easy access to all api methods
 *
 * Created by Markus on 03.06.2018.
 */
class MkDocsRestClient constructor(
        private val requestManager: RequestManager = RequestManager(),
        sectionApi: SectionApi = SectionApiImpl(requestManager),
        documentApi: DocumentApi = DocumentApiImpl(requestManager),
        resourceApi: ResourceApi = ResourceApiImpl(requestManager))
    : SectionApi by sectionApi,
        DocumentApi by documentApi,
        ResourceApi by resourceApi {

    /**
     * Set the url for this client
     *
     * @param hostname the new url
     */
    fun setHostname(hostname: String) {
        requestManager.hostname = hostname
    }

    /**
     * @return the url for this client
     */
    fun getHostname(): String {
        return requestManager.hostname
    }

    /**
     * Specify whether to use SSL
     *
     * @param enabled true enables ssl, false disables it
     */
    fun setUseSSL(enabled: Boolean) {
        requestManager.ssl = enabled
    }

    /**
     * Specify the port to use
     *
     * @param port the port number
     */
    fun setPort(port: Int) {
        requestManager.port = port
    }

    /**
     * Set the BasicAuthConfig for this client
     */
    fun getBasicAuthConfig(): BasicAuthConfig? {
        return requestManager.basicAuthConfig
    }

    /**
     * Set the BasicAuthConfig for this client
     */
    fun setBasicAuthConfig(basicAuthConfig: BasicAuthConfig) {
        requestManager.basicAuthConfig = basicAuthConfig
    }

    /**
     * Check if the server is alive and reachable
     */
    fun isHostAlive(): Single<Pair<Response, ByteArray>> {
        return requestManager.doRequest("/alive/", Method.GET)
    }

    /**
     * Get the complete item tree
     */
    fun getItemTree(): Single<SectionModel> {
        return requestManager.doRequest("/tree/", Method.GET, SectionModel.SingleDeserializer())
    }

}