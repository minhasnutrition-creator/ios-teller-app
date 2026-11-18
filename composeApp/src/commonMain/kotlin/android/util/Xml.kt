package android.util

import org.xmlpull.v1.XmlPullParser

object Xml {
    fun newPullParser(): XmlPullParser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
}
