package io.github.meiyongai.toki.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.ui.LocalAppLanguage
import io.github.meiyongai.toki.ui.component.getGroupedShape
import io.github.meiyongai.toki.util.AppLanguage
import java.util.Locale

/**
 * 显示完整语言目录，通过单选行即时更新界面，搜索状态不随语言切换重置。
 * @return Unit；无入参。
 * Callers: MainAppScreen 的语言次级页。
 */
@Composable
internal fun LanguageScreen() {
    val settings = LocalAppLanguage.current
    val strings = LocalContext.current.resources
    val locale = strings.configuration.locales[0]
    var query by rememberSaveable { mutableStateOf("") }
    val systemName = strings.getString(R.string.language_system)
    var searchLocaleTag by rememberSaveable { mutableStateOf(locale.toLanguageTag()) }
    var searchSystemName by rememberSaveable { mutableStateOf(systemName) }
    val languages = remember(query, searchLocaleTag, searchSystemName) {
        filterAppLanguages(query, Locale.forLanguageTag(searchLocaleTag), searchSystemName)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                if (query.isBlank()) {
                    searchLocaleTag = locale.toLanguageTag()
                    searchSystemName = systemName
                }
                query = it
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(strings.getString(R.string.language_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Outlined.Close, contentDescription = strings.getString(R.string.language_clear_search))
                }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).selectableGroup(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (languages.isEmpty()) item(key = "empty") {
                Text(strings.getString(R.string.language_no_results), Modifier.padding(16.dp))
            }
            itemsIndexed(languages, key = { _, item -> item.code }) { index, language ->
                LanguageRow(language, settings.language == language, locale, index, languages.size) {
                    settings.select(language)
                }
            }
        }
    }
}

/**
 * 按本次搜索开始时的语言匹配候选，避免切换界面语言后相同关键词突然失效。
 * @param query 用户输入的名称或 BCP-47 标签片段。
 * @param searchLocale 本次搜索开始时的界面语言，搜索期间保持不变。
 * @param systemName 本次搜索开始时的“跟随系统”文案。
 * @return 保持语言目录顺序的候选列表。
 * Callers: LanguageScreen、LanguageSearchTest。
 */
internal fun filterAppLanguages(query: String, searchLocale: Locale, systemName: String): List<AppLanguage> {
    val term = query.trim()
    return AppLanguage.entries.filter { language ->
        val localName = if (language == AppLanguage.SYSTEM) systemName
            else Locale.forLanguageTag(language.languageTag).getDisplayName(searchLocale)
        term.isEmpty() || language.displayName.contains(term, ignoreCase = true) ||
            localName.contains(term, ignoreCase = true) ||
            language.languageTag.contains(term, ignoreCase = true)
    }
}

/**
 * 使用旗帜、原生语言名称和单一无障碍单选动作呈现语言项。
 * @param language 语言元数据。
 * @param selected 当前是否选中。
 * @param locale 显示语言名称时使用的当前界面语言。
 * @param index 条目位置，用于分组圆角。
 * @param count 可见条目数。
 * @param onClick 用户选择回调。
 * @return Unit。
 * Callers: LanguageScreen。
 */
@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    locale: Locale,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(220), label = "LanguageSelectionColor"
    )
    val system = language == AppLanguage.SYSTEM
    val name = if (system) strings.getString(R.string.language_system) else language.displayName
    val localName = if (system) LocalAppLanguage.current.systemLocale.getDisplayName(locale)
        else Locale.forLanguageTag(language.languageTag).getDisplayName(locale)
    Surface(shape = getGroupedShape(index, count), color = color) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (system) Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(32.dp))
            else Text(
                language.flag,
                fontSize = (28f / LocalDensity.current.fontScale).sp,
                modifier = Modifier.size(36.dp).clearAndSetSemantics { }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (localName != name) Text(
                    localName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
