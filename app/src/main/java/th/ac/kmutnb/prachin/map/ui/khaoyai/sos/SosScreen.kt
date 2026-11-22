package th.ac.kmutnb.prachin.map.ui.khaoyai.sos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.EmergencyPlace
import th.ac.kmutnb.prachin.map.data.model.EmergencyPlaceKind
import th.ac.kmutnb.prachin.map.data.model.EmergencyPlaces
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosRules
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.ui.khaoyai.FloatingBackButton
import th.ac.kmutnb.prachin.map.ui.khaoyai.rememberLocationUi

/** The park's own 24-hour line, from khaoyainationalpark.com; then the national ones. */
const val PARK_EMERGENCY_NUMBER = "0860926529"

/**
 * Asking for help: the phone first, because a call reaches a person now; then a written
 * call with the position attached, kept for the admin screen and ready to pass on by SMS
 * or chat. The screen says plainly where that call goes today, so nobody waits on it.
 */
@Composable
fun SosScreen(account: UserAccount?, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val activeTrail by container.khaoYaiContentRepository.activeTrail.collectAsStateWithLifecycle(initialValue = null)
    val location = rememberLocationUi(intervalMs = 1_000)
    val fix = (location.state as? LocationState.Available)?.fix
    val scope = rememberCoroutineScope()

    var category by rememberSaveable { mutableStateOf(SosCategory.ACCIDENT) }
    var message by rememberSaveable { mutableStateOf("") }
    var sent by remember { mutableStateOf<SosReport?>(null) }

    val header = stringResource(R.string.sos_share_header)
    val noFix = stringResource(R.string.sos_share_no_fix)

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFFB71C1C), Color(0xFFE53935)))),
        ) {
            FloatingBackButton(onBack, Modifier.statusBarsPadding().padding(12.dp))
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 20.dp),
            ) {
                Text(stringResource(R.string.sos_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.sos_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
        }

        Column(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CallButton(stringResource(R.string.sos_call_park), "086-092-6529", PARK_EMERGENCY_NUMBER, primary = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallButton(stringResource(R.string.about_call_medical), "1669", "1669", modifier = Modifier.weight(1f))
                CallButton(stringResource(R.string.about_call_tourist_police), "1155", "1155", modifier = Modifier.weight(1f))
            }

            LocationCard(location.state, location.requestPermission)

            Text(stringResource(R.string.sos_what_happened), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SosCategory.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text("${c.emoji} ${stringResource(c.labelRes)}") },
                    )
                }
            }
            category.needsPlace?.let { kind ->
                NearestPlaces(kind, fix?.point, container.emergencyPlaces)
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.sos_message)) },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    scope.launch {
                        sent = container.sosRepository.send(
                            username = account?.fullName.orEmpty(),
                            userId = account?.id.orEmpty(),
                            touristId = account?.memberId.orEmpty(),
                            fix = fix,
                            category = category,
                            message = message,
                            trailName = activeTrail?.name.orEmpty(),
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.sos_send), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            sent?.let { report ->
                val categoryLabel = stringResource(report.category.labelRes)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sos_sent_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.sos_sent_body), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            share(context, SosRules.shareText(report, categoryLabel, header, noFix))
                        }) {
                            Text(stringResource(R.string.sos_share))
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.sos_demo_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

/** The nearest hospitals or police stations to where the phone is, with call and directions. */
@Composable
private fun NearestPlaces(kind: EmergencyPlaceKind, here: GeoPoint?, places: List<EmergencyPlace>) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (kind == EmergencyPlaceKind.HOSPITAL) R.string.sos_nearest_hospital else R.string.sos_nearest_police),
                fontWeight = FontWeight.Bold,
            )
            if (here == null) {
                Text(stringResource(R.string.sos_nearest_need_fix), style = MaterialTheme.typography.bodySmall)
            } else {
                EmergencyPlaces.nearest(places, kind, here).forEach { nearby ->
                    Text(
                        stringResource(R.string.sos_nearest_row, nearby.place.name, nearby.distanceMeters / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (nearby.place.phone.isNotBlank()) {
                            OutlinedButton(onClick = { dial(context, nearby.place.phone.filter(Char::isDigit)) }) {
                                Text(stringResource(R.string.sos_nearest_call, nearby.place.phone))
                            }
                        }
                        OutlinedButton(onClick = {
                            openLink(
                                context,
                                String.format(
                                    java.util.Locale.US,
                                    "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f",
                                    nearby.place.point.lat,
                                    nearby.place.point.lon,
                                ),
                            )
                        }) { Text(stringResource(R.string.sos_nearest_directions)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationCard(state: LocationState?, requestPermission: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sos_your_position), style = MaterialTheme.typography.labelLarge)
                when (state) {
                    is LocationState.Available -> Text(
                        stringResource(R.string.sos_position_value, state.fix.point.lat, state.fix.point.lon, state.fix.accuracyMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LocationState.PermissionMissing -> TextButton(onClick = requestPermission) {
                        Text(stringResource(R.string.location_allow))
                    }
                    LocationState.ProviderDisabled -> Text(stringResource(R.string.access_gps_off), color = MaterialTheme.colorScheme.error)
                    else -> Text(stringResource(R.string.sos_position_searching), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CallButton(label: String, shown: String, number: String, modifier: Modifier = Modifier, primary: Boolean = false) {
    val context = LocalContext.current
    Button(
        onClick = { dial(context, number) },
        colors = if (primary) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(if (primary) 64.dp else 52.dp),
    ) {
        Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(shown, fontWeight = FontWeight.Bold)
        }
    }
}

/** ACTION_DIAL fills the number in; it needs no permission and calls nothing by itself. */
private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (_: ActivityNotFoundException) {
        // A tablet with no phone app: the number is printed on the button to read out.
    }
}

/** The share sheet - SMS, LINE, whatever the phone has - with the text ready to send. */
private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // Nothing can share text; the call buttons above still work.
    }
}
