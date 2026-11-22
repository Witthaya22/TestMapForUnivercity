package th.ac.kmutnb.prachin.map.ui.khaoyai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.ui.theme.ForestDeep
import th.ac.kmutnb.prachin.map.ui.theme.ForestGreen
import th.ac.kmutnb.prachin.map.ui.theme.ForestLeaf
import th.ac.kmutnb.prachin.map.ui.theme.ForestMoss

/** Canopy green, dark at the bottom like the forest floor. */
val ForestBrush: Brush = Brush.verticalGradient(listOf(ForestLeaf, ForestGreen, ForestDeep))

/** Lighter green for the emoji badges on cards. */
val MossBrush: Brush = Brush.linearGradient(listOf(ForestMoss, ForestLeaf))

/**
 * The drawn Khao Yai landscape with a dark fade at the bottom so white text stays legible
 * over it. [content] is laid on top.
 */
@Composable
fun HeroBanner(
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_khaoyai_hero),
            contentDescription = stringResource(R.string.ky_hero_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to ForestDeep.copy(alpha = 0.85f),
                    ),
                ),
        )
        content()
    }
}

/** Two drawn leaves in a corner, purely decorative. */
@Composable
fun LeafCorner(modifier: Modifier = Modifier, size: Dp = 88.dp, alpha: Float = 0.9f) {
    Box(modifier.size(size * 1.4f)) {
        Image(
            painter = painterResource(R.drawable.art_leaf),
            contentDescription = null,
            alpha = alpha,
            modifier = Modifier
                .size(size)
                .align(Alignment.TopEnd)
                .rotate(20f),
        )
        Image(
            painter = painterResource(R.drawable.art_leaf),
            contentDescription = null,
            alpha = alpha * 0.7f,
            modifier = Modifier
                .size(size * 0.65f)
                .align(Alignment.BottomStart)
                .rotate(-35f),
        )
    }
}

/** A round badge holding the trail's or species' emoji. */
@Composable
fun EmojiBadge(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    brush: Brush = MossBrush,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.tertiary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A small icon + text pair, e.g. distance or walking time. */
@Composable
fun InfoChip(
    iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

/** A filled pill, e.g. the difficulty of a trail or a news category. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f),
        contentColor = color,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun DifficultyPill(difficulty: TrailDifficulty, modifier: Modifier = Modifier) =
    Pill(text = difficultyLabel(difficulty), color = difficultyColor(difficulty), modifier = modifier)

/** Says plainly that the trails and news are sample data, so nobody plans a walk on them. */
@Composable
fun SampleNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ky_sample_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhaoYaiTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                }
            }
        },
    )
}

/** A round translucent back button for screens whose header is a picture. */
@Composable
fun FloatingBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.32f),
        contentColor = Color.White,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
        }
    }
}

/** A row in a settings-style list: icon, label, chevron. */
@Composable
fun MenuRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown in place of a detail screen whose item was deleted from the admin screen. */
@Composable
fun NotFound(message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🍂", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    }
}
