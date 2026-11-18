package org.w3c.dom

interface NodeList {
    val length: Int get() = 0
    fun item(index: Int): Node = object : Node {}
}

interface Node {
    val parentNode: Node? get() = null
    var textContent: String?
        get() = null
        set(_) {}
}

interface Element : Node {
    fun getAttribute(name: String): String = ""
    fun getElementsByTagName(name: String): NodeList = object : NodeList {}
}

interface Document : Node {
    fun getElementsByTagName(name: String): NodeList = object : NodeList {}
}
