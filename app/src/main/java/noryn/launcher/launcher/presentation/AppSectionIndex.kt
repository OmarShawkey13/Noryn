package noryn.launcher.launcher.presentation

import android.icu.text.AlphabeticIndex
import noryn.launcher.core.model.LauncherProfile
import noryn.launcher.core.model.LauncherProfileType
import noryn.launcher.core.model.LauncherApp
import java.util.Locale

data class AppSection(
    val label: String,
    val apps: List<LauncherApp>,
)

data class AppSectionGroup(
    val key: String,
    val title: String,
    val profileType: LauncherProfileType?,
    val profile: LauncherProfile?,
    val sections: List<AppSection>,
)

internal fun buildAppSections(
    apps: List<LauncherApp>,
    customNames: Map<String, String>,
    locale: Locale,
): List<AppSection> {
    val index = AlphabeticIndex<LauncherApp>(locale)
        .addLabels(Locale.ENGLISH)
        .addLabels(Locale.forLanguageTag("ar"))
    val symbols = ArrayList<LauncherApp>()
    apps.forEach { app ->
        val label = customNames[app.id]?.takeIf(String::isNotBlank) ?: app.label
        if (startsWithNonLetter(label)) symbols += app else index.addRecord(label, app)
    }

    val sections = ArrayList<AppSection>()
    index.forEach { bucket ->
        if (bucket.size() > 0) sections += AppSection(bucket.label, bucket.map { it.data })
    }
    if (symbols.isNotEmpty()) {
        val collator = android.icu.text.Collator.getInstance(locale)
        sections += AppSection("#", symbols.sortedWith { left, right ->
            collator.compare(
                customNames[left.id]?.takeIf(String::isNotBlank) ?: left.label,
                customNames[right.id]?.takeIf(String::isNotBlank) ?: right.label,
            )
        })
    }
    return sections
}

private fun startsWithNonLetter(value: String): Boolean {
    var offset = 0
    while (offset < value.length) {
        val codePoint = value.codePointAt(offset)
        offset += Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt()) continue
        return !Character.isLetter(codePoint)
    }
    return true
}
