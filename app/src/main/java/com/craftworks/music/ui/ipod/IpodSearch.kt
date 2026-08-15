package com.craftworks.music.ui.ipod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.managers.settings.SearchHistoryManager
import kotlinx.coroutines.launch
import java.util.Locale

internal fun ipodSearchMatches(query: String, vararg values: CharSequence?): Boolean {
    val terms = query
        .trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return true

    val searchableText = values
        .filterNotNull()
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    return terms.all(searchableText::contains)
}

@Composable
internal fun IpodPullDownSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search"
) {
    val context = LocalContext.current
    val history = remember(context) { SearchHistoryManager(context) }
    val recentSearches by history.recentSearchesFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFD1D1D5))) {
      Box(
          modifier = Modifier
              .fillMaxWidth()
              .height(46.dp)
              .padding(horizontal = 8.dp, vertical = 7.dp)
      ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { scope.launch { history.recordSearch(query) } }
            ),
            cursorBrush = SolidColor(IpodColors.Blue),
            textStyle = TextStyle(
                color = IpodColors.Text,
                fontFamily = IpodFontFamily,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp
            ),
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White)
                        .padding(start = 8.dp, end = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = IpodColors.SecondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = IpodColors.SecondaryText,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.sp
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = IpodColors.SecondaryText,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { onQueryChange("") }
                                )
                        )
                    }
                }
            }
        )
      }
      if (focused && query.isBlank() && recentSearches.isNotEmpty()) {
          recentSearches.take(3).forEach { recent ->
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                      .fillMaxWidth()
                      .height(34.dp)
                      .clickable {
                          onQueryChange(recent)
                          scope.launch { history.recordSearch(recent) }
                      }
                      .padding(horizontal = 12.dp)
              ) {
                  Icon(
                      imageVector = Icons.Rounded.Search,
                      contentDescription = null,
                      tint = IpodColors.SecondaryText,
                      modifier = Modifier.size(16.dp)
                  )
                  Text(
                      text = recent,
                      color = IpodColors.Text,
                      fontFamily = IpodFontFamily,
                      fontSize = 13.sp,
                      maxLines = 1,
                      modifier = Modifier.padding(start = 8.dp)
                  )
              }
          }
      }
    }
}
