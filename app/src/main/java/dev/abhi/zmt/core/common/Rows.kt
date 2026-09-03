package dev.abhi.zmt.core.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abhi.zmt.R
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiBg
import dev.abhi.zmt.ui.theme.TuiDim
import dev.abhi.zmt.ui.theme.TuiFaint
import dev.abhi.zmt.ui.theme.TuiFg
import dev.abhi.zmt.ui.theme.TuiLine

@Composable
fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TuiDim,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
fun SearchRow(
    query: String,
    hint: String,
    shown: Int,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit = {},
    sort: String? = null,
    onSort: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Text(
            text = "/ ",
            style = MaterialTheme.typography.bodyLarge,
            color = TuiAccent,
        )
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TuiFg),
            cursorBrush = SolidColor(TuiAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TuiDim,
                    )
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Text(
                text = "$shown",
                style = MaterialTheme.typography.labelSmall,
                color = TuiDim,
            )
            Text(
                text = "⌕ ",
                style = MaterialTheme.typography.labelLarge,
                color = TuiAccent,
                modifier = Modifier
                    .tuiClickable { onSearch() }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Text(
                text = stringResource(R.string.clear),
                style = MaterialTheme.typography.labelLarge,
                color = TuiFg,
                modifier = Modifier
                    .tuiClickable { onQuery("") }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        if (sort != null && onSort != null) {
            Text(
                text = "[$sort]",
                style = MaterialTheme.typography.labelLarge,
                color = TuiDim,
                modifier = Modifier
                    .tuiClickable(onSort)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    index: Int,
    line1: String,
    line2: String,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (current) TuiFg else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (current) ">" else " ",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (current) TuiBg else Color.Transparent,
            )
            Text(
                text = (index + 1).toString().padStart(3, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = if (current) TuiBg.copy(alpha = 0.55f) else TuiFaint,
                modifier = Modifier.padding(start = 4.dp, end = 10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (current) "${line1}_" else line1,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (current) TuiBg else TuiFg,
                    maxLines = 1,
                    modifier = if (current) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    },
                )
                if (line2.isNotEmpty()) {
                    Text(
                        text = line2,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (current) TuiBg.copy(alpha = 0.7f) else TuiDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
        HorizontalDivider(color = if (current) Color.Transparent else TuiLine)
    }
}

@Composable
fun SubdirHeader(
    title: String,
    meta: String,
    onBack: () -> Unit,
    counts: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "[${stringResource(R.string.back)}]",
                style = MaterialTheme.typography.labelLarge,
                color = TuiFg,
                modifier = Modifier
                    .tuiClickable(onBack)
                    .padding(vertical = 8.dp)
                    .padding(end = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TuiFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val sub = listOf(meta, counts).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = " · $sub",
                    style = MaterialTheme.typography.labelSmall,
                    color = TuiDim,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = TuiLine, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(
        text = "[$label]",
        style = MaterialTheme.typography.labelLarge,
        color = TuiDim,
        modifier = Modifier
            .tuiClickable(onClick)
            .padding(vertical = 8.dp)
            .padding(start = 8.dp),
    )
}

@Composable
fun NewEntryRow(label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tuiClickable(onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(text = " ", style = MaterialTheme.typography.labelSmall, color = TuiBg)
            Text(
                text = "[+]",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TuiAccent,
                modifier = Modifier.padding(start = 4.dp, end = 10.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TuiFg,
            )
        }
        HorizontalDivider(color = TuiLine)
    }
}
