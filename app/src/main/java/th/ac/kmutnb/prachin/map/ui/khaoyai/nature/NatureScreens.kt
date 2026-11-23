package th.ac.kmutnb.prachin.map.ui.khaoyai.nature

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.Species
import th.ac.kmutnb.prachin.map.data.khaoyai.SpeciesKind
import th.ac.kmutnb.prachin.map.data.khaoyai.filterSpecies
import th.ac.kmutnb.prachin.map.ui.khaoyai.ContentPhoto
import th.ac.kmutnb.prachin.map.ui.khaoyai.FloatingBackButton
import th.ac.kmutnb.prachin.map.ui.khaoyai.PhotoCreditLine
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.SearchField
import th.ac.kmutnb.prachin.map.ui.khaoyai.LeafCorner
import th.ac.kmutnb.prachin.map.ui.khaoyai.NotFound
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.speciesKindLabel
import th.ac.kmutnb.prachin.map.ui.theme.BarkBrown
import th.ac.kmutnb.prachin.map.ui.theme.ForestLeaf
import th.ac.kmutnb.prachin.map.ui.theme.ForestMoss
import th.ac.kmutnb.prachin.map.ui.theme.HornbillAmber

/** Animals in warm earth tones, plants in leaf greens, so the two read apart at a glance. */
private fun kindBrush(kind: SpeciesKind): Brush = when (kind) {
    SpeciesKind.ANIMAL -> Brush.linearGradient(listOf(HornbillAmber, BarkBrown))
    SpeciesKind.PLANT -> Brush.linearGradient(listOf(ForestMoss, ForestLeaf))
}

/**
 * The nature tab: animals and plants in a searchable grid.
 *
 * @param kind the filter to open with; the home screen's animal and plant shortcuts set it.
 */
@Composable
fun NatureTab(
    species: List<Species>,
    kind: SpeciesKind?,
    onKindChange: (SpeciesKind?) -> Unit,
    contentPadding: PaddingValues,
    onOpenSpecies: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = filterSpecies(species, kind, query)

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.nature_title))
        SearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.nature_search))
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = kind == null,
                onClick = { onKindChange(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            SpeciesKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { onKindChange(k) },
                    label = { Text(speciesKindLabel(k)) },
                )
            }
        }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.nature_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(shown, key = { it.id }) { item ->
                SpeciesCard(item, onClick = { onOpenSpecies(item.id) })
            }
        }
    }
}

@Composable
private fun SpeciesCard(species: Species, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        ContentPhoto(
            species.photo,
            species.emoji,
            Modifier
                .fillMaxWidth()
                .height(116.dp),
            brush = kindBrush(species.kind),
            contentDescription = species.name,
        )
        Column(Modifier.padding(12.dp)) {
            Text(
                species.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                species.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Pill(
                speciesKindLabel(species.kind),
                if (species.kind == SpeciesKind.ANIMAL) BarkBrown else ForestLeaf,
            )
        }
    }
}

@Composable
fun SpeciesDetailScreen(species: Species?, onBack: () -> Unit) {
    if (species == null) {
        NotFound(stringResource(R.string.species_not_found), onBack)
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            ContentPhoto(
                species.photo,
                species.emoji,
                Modifier.fillMaxSize(),
                brush = kindBrush(species.kind),
                contentDescription = species.name,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
            )
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
                Text(
                    species.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    species.scientificName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        Column(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoCreditLine(species.photo)
            FactCard("🛡", stringResource(R.string.species_status), species.conservationStatus)
            FactCard("📍", stringResource(R.string.species_habitat), species.habitat)
            FactCard("📖", stringResource(R.string.species_about), species.description)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 " + stringResource(R.string.species_tip), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(species.tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FactCard(emoji: String, title: String, body: String) {
    if (body.isBlank()) return
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "$emoji  $title",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
