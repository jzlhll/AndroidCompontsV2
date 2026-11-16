package com.au.module_androiduiex.picker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.au.module_android.appLocaleLabel
import com.au.module_androiduiex.styles.ComposeTypography
import java.util.Locale

/** 应用语言单选，空语言标签表示跟随系统；选中后由调用方写入 AppCompatDelegate。 */
@Composable
fun AppLanguagePicker(
    locales: List<Locale>,
    selectedLanguageTag: String,
    followSystemLabel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.selectableGroup(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(listOf<Locale?>(null) + locales, key = { it?.toLanguageTag().orEmpty() }) { locale ->
            val tag = locale?.toLanguageTag().orEmpty()
            val selected = selectedLanguageTag == tag
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(tag) })
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(locale?.let(::appLocaleLabel) ?: followSystemLabel,
                    style = ComposeTypography.Font16, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
