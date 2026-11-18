package org.xmlpull.v1

import java.io.InputStream
import java.io.Reader

interface XmlPullParser {
    fun setInput(stream: InputStream, inputEncoding: String?) {}
    fun setInput(reader: Reader) {}
    fun next(): Int = END_DOCUMENT
    fun nextText(): String = ""
    fun getName(): String = ""
    fun getAttributeValue(namespace: String?, name: String): String? = null
    fun getEventType(): Int = START_DOCUMENT

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }
}

class XmlPullParserFactory {
    companion object {
        fun newInstance(): XmlPullParserFactory = XmlPullParserFactory()
    }

    fun newPullParser(): XmlPullParser = object : XmlPullParser {}
}
