package de.markusressel.mkdocseditor.data.persistence.entity

import android.content.Context
import android.text.format.Formatter
import android.view.View
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocsrestclient.document.DocumentModel
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.relation.ToOne
import java.util.*

/**
 * Created by Markus on 04.06.2018.
 */
@Entity
data class DocumentEntity(
        @Id var entityId: Long = 0,
        val type: String = "Document",
        @Unique val id: String = "",
        val name: String = "",
        var filesize: Long = -1L,
        var modtime: Date = Date(),
        val url: String = "") : IdentifiableListItem {
    override fun getItemId(): String = id

    lateinit var parentSection: ToOne<SectionEntity>
    lateinit var content: ToOne<DocumentContentEntity?>

    val offlineAvailableVisibility: Int
        get() {
            return if (content.target != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

    /**
     * Human readable representation of file size
     */
    fun formattedDocumentSize(context: Context): String {
        return Formatter.formatFileSize(context, filesize)
    }

}

fun DocumentModel.asEntity(parentSection: SectionEntity, contentEntity: DocumentContentEntity? = null): DocumentEntity {
    val d = DocumentEntity(0, this.type, this.id, this.name, this.filesize, this.modtime, this.url)
    d.parentSection.target = parentSection
    contentEntity?.let {
        d.content.target = it
    }
    return d
}
