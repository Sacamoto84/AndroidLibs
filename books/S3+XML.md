```kotlin
    val imgUrlList = mutableListOf<String>()
    val baseUrl = "http://77.91.87.34:10000/sex/"

    val uri = URI(baseUrl)
    val xmlDoc: Document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(uri.toString())
    xmlDoc.documentElement.normalize()
    val bookList: NodeList = xmlDoc.getElementsByTagName("Contents")
    for (i in 0..<bookList.length) {
        val bookNode: Node = bookList.item(i)
        if (bookNode.getNodeType() === Node.ELEMENT_NODE) {
            val elem = bookNode as Element
            val key = elem.getElementsByTagName("Key").item(0).firstChild
            imgUrlList.add(baseUrl + key.nodeValue)
        }
    }
```
