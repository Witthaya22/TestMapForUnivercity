package th.ac.kmutnb.prachin.map.ui.khaoyai.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.NewsItem
import th.ac.kmutnb.prachin.map.ui.khaoyai.EmojiBadge
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.NotFound
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.SampleNotice
import th.ac.kmutnb.prachin.map.ui.khaoyai.thaiDate

@Composable
fun NewsTab(
    news: List<NewsItem>,
    contentPadding: PaddingValues,
    onOpenNews: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.news_title))
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SampleNotice() }
            if (news.isEmpty()) {
                item { Text(stringResource(R.string.news_empty), modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(news, key = { it.id }) { item ->
                NewsCard(item, onClick = { onOpenNews(item.id) })
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            EmojiBadge(item.emoji, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NewsMeta(item)
                Spacer(Modifier.height(4.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NewsMeta(item: NewsItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.category.isNotBlank()) Pill(item.category, MaterialTheme.colorScheme.primary)
        Text(
            thaiDate(item.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NewsDetailScreen(item: NewsItem?, onBack: () -> Unit) {
    if (item == null) {
        NotFound(stringResource(R.string.news_not_found), onBack)
        return
    }
    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.news_title), onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .navigationBarsPadding(),
        ) {
            EmojiBadge(item.emoji, size = 72.dp)
            Spacer(Modifier.height(14.dp))
            NewsMeta(item)
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                item.summary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(item.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
