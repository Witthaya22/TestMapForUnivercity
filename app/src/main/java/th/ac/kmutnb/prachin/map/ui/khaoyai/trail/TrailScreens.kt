package th.ac.kmutnb.prachin.map.ui.khaoyai.trail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailSchedule
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation
import th.ac.kmutnb.prachin.map.ui.khaoyai.openStateColor
import th.ac.kmutnb.prachin.map.ui.khaoyai.openStateLabel
import th.ac.kmutnb.prachin.map.ui.khaoyai.trailHours
import th.ac.kmutnb.prachin.map.ui.khaoyai.ContentPhoto
import th.ac.kmutnb.prachin.map.ui.khaoyai.HERO_PHOTO
import th.ac.kmutnb.prachin.map.ui.khaoyai.PhotoCreditLine
import th.ac.kmutnb.prachin.map.ui.khaoyai.DifficultyPill
import th.ac.kmutnb.prachin.map.ui.khaoyai.EmojiBadge
import th.ac.kmutnb.prachin.map.ui.khaoyai.FloatingBackButton
import th.ac.kmutnb.prachin.map.ui.khaoyai.ForestBrush
import th.ac.kmutnb.prachin.map.ui.khaoyai.HeroBanner
import th.ac.kmutnb.prachin.map.ui.khaoyai.InfoChip
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.NotFound
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.SampleNotice
import th.ac.kmutnb.prachin.map.ui.khaoyai.difficultyLabel
import th.ac.kmutnb.prachin.map.ui.khaoyai.trailDuration

/** The trails tab: every trail, filtered by difficulty. */
@Composable
fun TrailsTab(
    trails: List<Trail>,
    contentPadding: PaddingValues,
    onOpenTrail: (String) -> Unit,
    onOpenInfo: () -> Unit,
) {
    var difficulty by rememberSaveable { mutableStateOf<TrailDifficulty?>(null) }
    val shown = trails.filter { difficulty == null || it.difficulty == difficulty }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.trails_title))
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SampleNotice() }
            item {
                Card(
                    onClick = onOpenInfo,
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📋", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.trail_info_title), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.trail_info_teaser), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = difficulty == null,
                        onClick = { difficulty = null },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    TrailDifficulty.entries.forEach { level ->
                        FilterChip(
                            selected = difficulty == level,
                            onClick = { difficulty = level },
                            label = { Text(difficultyLabel(level)) },
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.trail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { trail ->
                TrailRow(trail = trail, onClick = { onOpenTrail(trail.id) })
            }
        }
    }
}

/** A wide card for the home screen carousel. */
@Composable
fun TrailFeatureCard(trail: Trail, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(240.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(124.dp),
        ) {
            ContentPhoto(trail.photo, trail.emoji, Modifier.fillMaxSize(), contentDescription = trail.name)
            DifficultyPill(
                trail.difficulty,
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color.White, RoundedCornerShape(50)),
            )
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                trail.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            TrailFacts(trail)
        }
    }
}

@Composable
private fun TrailRow(trail: Trail, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            ContentPhoto(
                trail.photo,
                trail.emoji,
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentDescription = trail.name,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(trail.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    trail.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                TrailFacts(trail)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyPill(trail.difficulty)
                    val state = TrailSchedule.stateNow(trail)
                    if (!trail.demoUnlocked) Pill(openStateLabel(state), openStateColor(state))
                    if (trail.needsGuide) {
                        Pill(stringResource(R.string.trail_needs_guide), MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailFacts(trail: Trail) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoChip(R.drawable.ic_walk, stringResource(R.string.trail_distance, trail.distanceKm))
        InfoChip(R.drawable.ic_schedule, trailDuration(trail.durationMinutes))
    }
}

/** One trail: what it is, what to look for on the way, and the way into the navigation. */
@Composable
fun TrailDetailScreen(
    trail: Trail?,
    onBack: () -> Unit,
    onStartNavigation: () -> Unit,
) {
    if (trail == null) {
        NotFound(stringResource(R.string.trail_not_found), onBack)
        return
    }
    val hoursText = trailHours(trail)
    Scaffold(
        bottomBar = { TrailAccessPanel(trail = trail, onStart = onStartNavigation) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
            item {
                HeroBanner(height = 300.dp, photo = trail.photo.ifBlank { HERO_PHOTO }) {
                    FloatingBackButton(
                        onBack,
                        Modifier
                            .statusBarsPadding()
                            .padding(12.dp),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                    ) {
                        EmojiBadge(trail.emoji, size = 64.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            trail.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            stringResource(R.string.trail_start_point, trail.startPoint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
            item {
                PhotoCreditLine(trail.photo, Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
                StatsCard(trail)
            }
            if (trail.openNote.isNotBlank() || trail.endPoint.isNotBlank() || hoursText.isNotBlank()) {
                item {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (trail.endPoint.isNotBlank()) {
                            InfoChip(R.drawable.ic_place, stringResource(R.string.trail_end_point, trail.endPoint))
                        }
                        val hours = trailHours(trail)
                        if (hours.isNotBlank()) {
                            val state = TrailSchedule.stateNow(trail)
                            InfoChip(R.drawable.ic_schedule, "$hours · ${openStateLabel(state)}", tint = openStateColor(state))
                        }
                        if (trail.openNote.isNotBlank()) {
                            InfoChip(R.drawable.ic_info, trail.openNote)
                        }
                    }
                }
            }
            if (trail.needsGuide) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            "🧭  " + stringResource(R.string.trail_guide_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.trail_about)) {
                    Text(trail.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (trail.stations.isNotEmpty()) {
                item {
                    DetailSection(stringResource(R.string.trail_stations_title)) {
                        trail.stations.forEachIndexed { index, station ->
                            StationRow(number = index + 1, station = station, isLast = index == trail.stations.lastIndex)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(trail: Trail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat(R.drawable.ic_walk, stringResource(R.string.trail_distance, trail.distanceKm), Modifier.weight(1f))
            StatDivider()
            Stat(R.drawable.ic_schedule, trailDuration(trail.durationMinutes), Modifier.weight(1f))
            StatDivider()
            Stat(R.drawable.ic_terrain, difficultyLabel(trail.difficulty), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(iconRes: Int, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun DetailSection(title: String, body: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        body()
    }
}

/** A numbered stop on a vertical line, like the posts along a real trail. */
@Composable
private fun StationRow(number: Int, station: TrailStation, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (station.description.isBlank()) 26.dp else 64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = 8.dp),
        ) {
            Text(
                listOf(station.emoji, station.name).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (station.description.isNotBlank()) {
                Text(
                    station.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (station.photo.isNotBlank()) {
            ContentPhoto(
                station.photo,
                station.emoji,
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
