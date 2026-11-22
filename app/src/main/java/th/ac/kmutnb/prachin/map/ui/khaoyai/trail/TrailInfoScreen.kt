package th.ac.kmutnb.prachin.map.ui.khaoyai.trail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink

/**
 * How walking a trail works at Khao Yai, from the park's own hiking pages: registering,
 * getting ready, the rules on the trail and what the trail posts mean. Text only, so it
 * reads offline; the source link is at the bottom.
 */
@Composable
fun TrailInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.trail_info_title), onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.trail_info_hours_title), listOf(stringResource(R.string.trail_info_hours)))
            Section(stringResource(R.string.trail_info_steps_title), stringArrayResource(R.array.trail_info_steps).toList(), numbered = true)
            Section(stringResource(R.string.trail_info_prepare_title), stringArrayResource(R.array.trail_info_prepare).toList(), numbered = true)
            Section(stringResource(R.string.trail_info_rules_title), stringArrayResource(R.array.trail_info_rules).toList(), numbered = true)
            Section(stringResource(R.string.trail_info_markers_title), listOf(stringResource(R.string.trail_info_markers)))
            Section(stringResource(R.string.trail_info_contact_title), listOf(stringResource(R.string.trail_info_contact)))
            OutlinedButton(
                onClick = { openLink(context, "https://khaoyainationalpark.com/plan-your-visit/thing-to-do/hiking") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.trail_info_source)) }
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>, numbered: Boolean = false) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            lines.forEachIndexed { i, line ->
                Text(if (numbered) "${i + 1}. $line" else line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
