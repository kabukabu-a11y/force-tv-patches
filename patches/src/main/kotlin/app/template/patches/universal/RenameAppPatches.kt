package app.template.patches.universal

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

private fun parseXml(file: File): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    return factory.newDocumentBuilder().parse(file)
}

private fun writeXml(file: File, document: Document) {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.setOutputProperty(OutputKeys.INDENT, "no")
    file.outputStream().use { output ->
        transformer.transform(DOMSource(document), StreamResult(output))
    }
}

/**
 * Applies [transform] to the app label shown by launchers.
 *
 * The label usually lives in a string resource referenced from the
 * manifest (android:label="@string/app_name"), in which case the
 * string in res/values/strings.xml is edited. If the manifest carries
 * a literal label instead, the manifest attribute itself is edited.
 * Only the default (values) string is changed; per-locale labels, if
 * the app defines any, keep the original name.
 */
private fun ResourcePatchContext.transformAppLabel(transform: (String) -> String) {
    val manifestFile = get("AndroidManifest.xml")
    val manifest = parseXml(manifestFile)
    val application = manifest.getElementsByTagName("application").item(0) as? Element
        ?: throw PatchException("No <application> element found in AndroidManifest.xml")

    var label = application.getAttributeNS(ANDROID_NS, "label")
    if (label.isEmpty()) label = application.getAttribute("android:label")

    when {
        label.startsWith("@string/") -> {
            val stringName = label.removePrefix("@string/")
            val stringsFile = get("res/values/strings.xml")
            val strings = parseXml(stringsFile)
            val nodes = strings.getElementsByTagName("string")
            var updated = false
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                if (element.getAttribute("name") == stringName) {
                    element.textContent = transform(element.textContent)
                    updated = true
                    break
                }
            }
            if (!updated) {
                throw PatchException(
                    "String resource '$stringName' (the app label) was not found in res/values/strings.xml"
                )
            }
            writeXml(stringsFile, strings)
        }

        label.isNotEmpty() -> {
            val renamed = transform(label)
            if (application.getAttributeNS(ANDROID_NS, "label").isNotEmpty()) {
                application.setAttributeNS(ANDROID_NS, "android:label", renamed)
            } else {
                application.setAttribute("android:label", renamed)
            }
            writeXml(manifestFile, manifest)
        }

        else -> throw PatchException("This app declares no android:label that could be renamed")
    }
}

/**
 * Renames the app by appending " TV" to its name, so a TV-patched copy
 * installed next to the original (for example with Morphe's Clone app
 * patch) can be told apart on the launcher: "Zangetsu" becomes
 * "Zangetsu TV". Off by default.
 */
@Suppress("unused")
val renameAppTvSuffixPatch = resourcePatch(
    name = "Rename app (add TV suffix)",
    description = "Appends \" TV\" to the app name, e.g. \"Zangetsu\" becomes \"Zangetsu TV\". Useful to tell a TV-patched copy apart from the original.",
    default = false
) {
    execute {
        transformAppLabel { name -> if (name.endsWith("TV")) name else "$name TV" }
    }
}

/**
 * Renames the app by appending " (TV)" to its name: "Zangetsu" becomes
 * "Zangetsu (TV)". A parenthesized variant of the plain suffix patch,
 * for launchers where the two copies should not sort adjacently or
 * where the plain " TV" suffix looks like part of the real name.
 * Off by default.
 */
@Suppress("unused")
val renameAppTvParenSuffixPatch = resourcePatch(
    name = "Rename app (add (TV) suffix)",
    description = "Appends \" (TV)\" to the app name, e.g. \"Zangetsu\" becomes \"Zangetsu (TV)\". Useful to tell a TV-patched copy apart from the original.",
    default = false
) {
    execute {
        transformAppLabel { name -> if (name.endsWith("(TV)")) name else "$name (TV)" }
    }
}
