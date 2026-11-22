package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.PeriodCount
import th.ac.kmutnb.prachin.map.data.model.ReportPeriod
import th.ac.kmutnb.prachin.map.data.model.Reports
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar

/** The emergency report and field summary (§2.3.2.4–2.3.2.5, §2.3.3.4–2.3.3.5). */
@Composable
fun ReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val all by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by container.accountRepository.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val hazards by container.hazardRepository.activeHazards.collectAsStateWithLifecycle(initialValue = emptyList())
    var period by rememberSaveable { mutableStateOf(ReportPeriod.MONTH) }
    var withDemo by rememberSaveable { mutableStateOf(true) }
    val reports = if (withDemo) all else all.filterNot { it.isDemo }
    val now = System.currentTimeMillis()
    val months = stringArrayResource(R.array.thai_month_short).toList()
    val categoryLabels = SosCategory.entries.associateWith { stringResource(it.labelRes) }
    val statusLabels = SosStatus.entries.associateWith { stringResource(it.labelRes) }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.report_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.report_with_demo), modifier = Modifier.weight(1f))
                    Switch(checked = withDemo, onCheckedChange = { withDemo = it })
                }
            }
            item {
                ReportCard(stringResource(R.string.report_total, reports.size)) {
                    val top = Reports.mostFrequent(reports)
                    Text(
                        if (top == null) stringResource(R.string.report_no_calls)
                        else stringResource(R.string.report_most_frequent, "${top.first.emoji} ${categoryLabels[top.first]}", top.second),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.report_today, Reports.today(reports, now)), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                ReportCard(stringResource(R.string.report_by_time)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportPeriod.entries.forEach { p ->
                            FilterChip(selected = period == p, onClick = { period = p }, label = { Text(stringResource(periodLabel(p))) })
                        }
                    }
                    val timeline = Reports.timeline(reports, period, now)
                    BarChart(timeline)
                    Row(Modifier.fillMaxWidth()) {
                        Text(bucketLabel(timeline.first(), period, months), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(bucketLabel(timeline.last(), period, months), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                ReportCard(stringResource(R.string.report_by_type)) {
                    val byType = Reports.byCategory(reports)
                    val max = (byType.values.maxOrNull() ?: 0).coerceAtLeast(1)
                    byType.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${c.emoji} ${categoryLabels[c]}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(150.dp))
                            LinearProgressIndicator(progress = { n / max.toFloat() }, modifier = Modifier.weight(1f).height(8.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(n.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item {
                ReportCard(stringResource(R.string.report_by_status)) {
                    Reports.byStatus(reports).forEach { (s, n) -> Text("${statusLabels[s]}: $n", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                ReportCard(stringResource(R.string.report_users)) {
                    val byRole = Reports.usersByRole(if (withDemo) accounts else accounts.filterNot { it.isDemo })
                    UserRole.entries.forEach { r -> Text("${stringResource(r.labelRes)}: ${byRole[r] ?: 0}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                ReportCard(stringResource(R.string.report_hazards, hazards.size)) {
                    if (hazards.isEmpty()) Text(stringResource(R.string.report_no_hazards), style = MaterialTheme.typography.bodySmall)
                    hazards.groupingBy { it.type }.eachCount().forEach { (type, n) ->
                        Text("• ${stringResource(type.labelRes)}: $n", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    val csv = Reports.csv(reports, { categoryLabels[it].orEmpty() }, { statusLabels[it].orEmpty() })
                    val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_TEXT, csv)
                    runCatching { context.startActivity(Intent.createChooser(send, null)) }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.report_export_csv))
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, body: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            body()
        }
    }
}

@Composable
private fun BarChart(timeline: List<PeriodCount>) {
    val colour = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val max = (timeline.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Text(stringResource(R.string.report_chart_max, max), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (timeline.size - 1)) / timeline.size
        timeline.forEachIndexed { i, b ->
            val h = if (b.count == 0) 2.dp.toPx() else size.height * b.count / max
            drawRoundRect(
                color = if (b.count == 0) empty else colour,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

private fun periodLabel(p: ReportPeriod) = when (p) {
    ReportPeriod.DAY -> R.string.report_period_day
    ReportPeriod.MONTH -> R.string.report_period_month
    ReportPeriod.YEAR -> R.string.report_period_year
}

private fun bucketLabel(b: PeriodCount, period: ReportPeriod, months: List<String>): String = when (period) {
    ReportPeriod.DAY -> "${b.day} ${months.getOrElse(b.month - 1) { "" }}"
    ReportPeriod.MONTH -> "${months.getOrElse(b.month - 1) { "" }} ${(b.year + 543) % 100}"
    ReportPeriod.YEAR -> "${b.year + 543}"
}
