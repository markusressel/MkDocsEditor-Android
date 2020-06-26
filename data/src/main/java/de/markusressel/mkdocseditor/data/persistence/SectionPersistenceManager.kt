package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.*
import io.objectbox.kotlin.query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionPersistenceManager @Inject constructor(
        private val documentPersistenceManager: DocumentPersistenceManager,
        private val documentContentPersistenceManager: DocumentContentPersistenceManager,
        private val resourcePersistenceManager: ResourcePersistenceManager
) : PersistenceManagerBase<SectionEntity>(SectionEntity::class) {

    /**
     * Get the root section
     */
    fun getRootSection(): SectionEntity? {
        return findById("root")
    }

    /**
     * Find a section by it's document id (not the database entity id)
     */
    fun findById(sectionId: String): SectionEntity? {
        return standardOperation().query {
            equal(SectionEntity_.id, sectionId)
        }.findUnique()
    }

    /**
     * Inserts sections (and their contents) if missing,
     * updates any already existing entities,
     * and finally removes entities that are not present in the given dataset
     */
    fun insertOrUpdateRoot(newData: SectionEntity) {
        addOrUpdate(newData)

        // remove data that is not on the server anymore
        deleteMissing(newData)
    }


    private fun addOrUpdate(rootSection: SectionEntity) {
        addOrUpdateEntityFields(newData = rootSection)
    }

    private fun addOrUpdateEntityFields(newData: SectionEntity): SectionEntity {
        // use existing section or insert new one
        val section = standardOperation().query {
            equal(SectionEntity_.id, newData.id)
        }.findUnique() ?: standardOperation().get(standardOperation().put(newData))

        newData.documents.forEach { newDocument ->
            val documentEntity = documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, newDocument.id)
            }.findUnique()?.apply {
                filesize = newDocument.filesize
                modtime = newDocument.modtime
            } ?: newDocument
            documentEntity.parentSection.target = section

            val documentContentEntity = documentContentPersistenceManager.standardOperation().query {
                equal(DocumentContentEntity_.documentId, documentEntity.id)
            }.findUnique()
            documentEntity.content.target = documentContentEntity

            documentPersistenceManager.standardOperation().put(documentEntity)
        }

        newData.resources.forEach { newResource ->
            val resourceEntity = resourcePersistenceManager.standardOperation().query {
                equal(ResourceEntity_.id, newResource.id)
            }.findUnique()?.apply {
                filesize = newResource.filesize
                modtime = newResource.modtime
            } ?: newResource
            resourceEntity.parentSection.target = section

            resourcePersistenceManager.standardOperation().put(resourceEntity)
        }

        // clear old sections
        section.subsections.clear()
        newData.subsections.forEach { newSection ->
            section.subsections.add(addOrUpdateEntityFields(newSection))
        }
        // insert updated section
        standardOperation().put(section)

        return section
    }

    private fun deleteMissing(newData: SectionEntity) {
        val sectionIds = mutableSetOf<String>()
        val documentIds = mutableSetOf<String>()
        val resourceIds = mutableSetOf<String>()
        findIdsRecursive(newData, sectionIds, documentIds, resourceIds)

        // remove stale sections
        val existingSectionIds = standardOperation().query {
            `in`(SectionEntity_.id, sectionIds.toTypedArray())
        }.findIds()

        // find others
        val missingSectionIds = standardOperation().query {
            notIn(SectionEntity_.entityId, existingSectionIds)
        }.findIds()

        standardOperation().removeByKeys(missingSectionIds.toList())

        // remove stale documents
        val existingDocumentIds = documentPersistenceManager.standardOperation().query {
            `in`(DocumentEntity_.id, documentIds.toTypedArray())
        }.findIds()

        // find others
        val missingDocumentIds = documentPersistenceManager.standardOperation().query {
            notIn(DocumentEntity_.entityId, existingDocumentIds)
        }.findIds()

        documentPersistenceManager.standardOperation().removeByKeys(missingDocumentIds.toList())

        // remove stale resources
        val existingResourceIds = resourcePersistenceManager.standardOperation().query {
            `in`(ResourceEntity_.id, resourceIds.toTypedArray())
        }.findIds()

        // find others
        val missingResourceIds = resourcePersistenceManager.standardOperation().query {
            notIn(ResourceEntity_.entityId, existingResourceIds)
        }.findIds()

        resourcePersistenceManager.standardOperation().removeByKeys(missingResourceIds.toList())
    }

    companion object {

        /**
         * Recursive method to find all section, document and resource ids
         */
        private fun findIdsRecursive(section: SectionEntity, sectionIds: MutableSet<String>, documentIds: MutableSet<String>, resourceIds: MutableSet<String>) {
            sectionIds.add(section.id)
            documentIds.addAll(section.documents.map { it.id })
            resourceIds.addAll(section.resources.map { it.id })
            section.subsections.forEach {
                findIdsRecursive(it, sectionIds, documentIds, resourceIds)
            }
        }
    }


}