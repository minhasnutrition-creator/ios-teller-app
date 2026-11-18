package javax.xml.parsers

import org.w3c.dom.Document
import java.io.InputStream

open class DocumentBuilder {
    fun parse(stream: InputStream): Document = object : Document {}
}

open class DocumentBuilderFactory {
    var isNamespaceAware: Boolean = false

    fun newDocumentBuilder(): DocumentBuilder = DocumentBuilder()

    companion object {
        fun newInstance(): DocumentBuilderFactory = DocumentBuilderFactory()
    }
}
