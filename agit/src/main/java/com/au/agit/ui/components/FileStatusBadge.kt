package com.au.agit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.au.agit.R
import com.au.agit.model.ChangeType
import com.au.agit.model.FileChange
import com.au.agit.model.RepositoryFile
import com.au.agit.ui.theme.LocalThemePalette

@Composable
fun FileStatusBadge(change: FileChange, modifier: Modifier = Modifier) {
    val color = changeColor(change.type)
    val description = change.kind + if (change.staged) ", " + stringResource(R.string.staged) else ""
    Surface(modifier = modifier.semantics { contentDescription = description }, color = changeBackground(change.type), contentColor = color, shape = RoundedCornerShape(6.dp)) {
        Text(change.type.marker, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun changeColor(type: ChangeType): Color {
    return LocalThemePalette.current.statuses.getValue(type).foreground
}

@Composable
fun changeBackground(type: ChangeType): Color = LocalThemePalette.current.statuses.getValue(type).background

@Composable
fun FileTypeIcon(file: RepositoryFile, modifier: Modifier = Modifier) {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    val drawable = if (file.directory) R.drawable.ic_folder else when (extension) {
        "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "sh", "swift", "c", "cpp", "h", "html", "css", "xml", "json", "toml", "yaml", "yml", "gradle" -> R.drawable.ic_filetype_code
        "md", "txt", "log", "properties", "gitignore" -> R.drawable.ic_filetype_txt
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "avif", "svg" -> R.drawable.ic_filetype_img
        "mp4", "webm", "mkv", "mov", "avi" -> R.drawable.ic_filetype_video
        "mp3", "wav", "ogg", "flac", "m4a" -> R.drawable.ic_filetype_audio
        "zip", "gz", "tar", "7z", "rar" -> R.drawable.ic_filetype_zip
        "pdf" -> R.drawable.ic_filetype_pdf
        "doc", "docx", "rtf" -> R.drawable.ic_filetype_doc
        else -> R.drawable.ic_filetype_other
    }
    Image(painterResource(drawable), contentDescription = null, modifier = modifier.size(36.dp))
}

@Preview
@Composable
private fun StatusBadgePreview() { MaterialTheme { FileStatusBadge(FileChange("Main.kt", "Modified", type = ChangeType.Modified)) } }
