package th.ac.kmutnb.prachin.map.ui.khaoyai.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.KhaoYaiContent
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.ui.khaoyai.ForestBrush
import th.ac.kmutnb.prachin.map.ui.khaoyai.HeroBanner
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.SectionHeader
import th.ac.kmutnb.prachin.map.ui.khaoyai.news.NewsCard
import th.ac.kmutnb.prachin.map.ui.khaoyai.trail.TrailFeatureCard
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmber

@Composable
fun HomeTab(
    account: UserAccount,
    content: KhaoYaiContent,
    contentPadding: PaddingValues,
    onOpenNavigation: () -> Unit,
    onOpenTrails: () -> Unit,
    onOpenTrail: (String) -> Unit,
    onOpenNature: (SpeciesKind) -> Unit,
    onOpenNews: () -> Unit,
    onOpenNewsItem: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSos: () -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        item { Hero(account, content, onOpenSos) }
        item {
            NavigationCard(
                onClick = onOpenNavigation,
                modifier = Modifier
                    .offset(y = (-28).dp)
                    .padding(horizontal = 16.dp),
            )
        }
        item {
            QuickMenu(
                onTrails = onOpenTrails,
                onAnimals = { onOpenNature(SpeciesKind.ANIMAL) },
                onPlants = { onOpenNature(SpeciesKind.PLANT) },
                onAbout = onOpenAbout,
                modifier = Modifier.offset(y = (-12).dp),
            )
        }
        item {
            SectionHeader(
                title = stringResource(R.string.home_section_trails),
                actionLabel = stringResource(R.string.action_see_all),
                onAction = onOpenTrails,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(content.trails, key = { it.id }) { trail ->
                    TrailFeatureCard(trail = trail, onClick = { onOpenTrail(trail.id) })
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.home_section_news),
                actionLabel = stringResource(R.string.action_see_all),
                onAction = onOpenNews,
            )
        }
        items(content.newsByDate.take(3), key = { it.id }) { news ->
            NewsCard(
                item = news,
                onClick = { onOpenNewsItem(news.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.home_section_rules))
            Rules(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Hero(account: UserAccount, content: KhaoYaiContent, onOpenSos: () -> Unit) {
    HeroBanner(height = 320.dp) {
        Surface(
            onClick = onOpenSos,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(50),
            color = Color(0xFFD32F2F),
            contentColor = Color.White,
            shadowElevation = 4.dp,
        ) {
            Text(
                stringResource(R.string.sos_short),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.25f),
            contentColor = Color.White,
        ) {
            Text(
                text = stringResource(
                    R.string.home_greeting,
                    account.fullName.ifBlank { stringResource(R.string.home_guest_name) },
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 44.dp),
        ) {
            Text(
                text = stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.home_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill(stringResource(R.string.home_stat_trails, content.trails.size))
                StatPill(stringResource(R.string.home_stat_species, content.species.size))
                StatPill(stringResource(R.string.home_stat_news, content.news.size))
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.2f),
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NavigationCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(Modifier.background(ForestBrush)) {
            LeafCorner(Modifier.align(Alignment.TopEnd), size = 60.dp, alpha = 0.35f)
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_walk), contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_nav_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_nav_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HornbillAmber,
                        contentColor = Color(0xFF2E1F00),
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_navigation), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_nav_card_action), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuickMenu(
    onTrails: () -> Unit,
    onAnimals: () -> Unit,
    onPlants: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickMenuItem("🌲", stringResource(R.string.home_menu_trails), onTrails)
        QuickMenuItem("🐘", stringResource(R.string.home_menu_animals), onAnimals)
        QuickMenuItem("🌿", stringResource(R.string.home_menu_plants), onPlants)
        QuickMenuItem("⛰", stringResource(R.string.home_menu_about), onAbout)
    }
}

@Composable
private fun QuickMenuItem(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun Rules(modifier: Modifier = Modifier) {
    val rules = listOf(
        "🍌" to R.string.rule_no_feeding,
        "🌱" to R.string.rule_stay_on_trail,
        "♻" to R.string.rule_take_trash,
        "🤫" to R.string.rule_keep_quiet,
        "🐘" to R.string.rule_elephant,
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            rules.forEach { (emoji, text) ->
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
