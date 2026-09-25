package com.siren.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siren.mobile.resources.*
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.ListGroup
import com.siren.mobile.ui.components.ListRow
import com.siren.mobile.ui.components.RowDivider
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.Space
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class GuideItem(val icon: DrawableResource, val title: String, val body: String)
data class GuideSection(val title: String, val items: List<GuideItem>)

private val guideSections = listOf(
    GuideSection(
        "During the shaking",
        listOf(
            GuideItem(Res.drawable.ic_sg_drop_cover_hold, "Drop, cover, hold on", "Get under a sturdy desk, cover your head and neck, and hold on until the shaking stops."),
            GuideItem(Res.drawable.ic_sg_home_person, "Stay where you are", "Do not run outside mid-shake. Most injuries happen while moving."),
            GuideItem(Res.drawable.ic_sg_shelter, "Find hard cover", "If there is no desk, crouch against an interior wall away from windows."),
            GuideItem(Res.drawable.ic_sg_hazard, "Watch for falling objects", "Move clear of shelves, glass, and anything mounted overhead."),
        ),
    ),
    GuideSection(
        "Evacuating",
        listOf(
            GuideItem(Res.drawable.ic_sg_evacuate_run, "Leave calmly", "Once shaking stops, walk quickly — do not run or push."),
            GuideItem(Res.drawable.ic_sg_evac_route, "Follow the marked route", "Use the posted evacuation path, not shortcuts."),
            GuideItem(Res.drawable.ic_sg_fire_exit, "Use stairs, never lifts", "Aftershocks can trap you in an elevator."),
            GuideItem(Res.drawable.ic_sg_assembly_point, "Go to the assembly point", "Stay there so your adviser can complete the roll call."),
        ),
    ),
    GuideSection(
        "Getting help",
        listOf(
            GuideItem(Res.drawable.ic_sg_call_sos, "Send an SOS", "Tap I Need Help in the app — it reaches your adviser and guardians at once."),
            GuideItem(Res.drawable.ic_sg_call_signal, "If there is no signal", "Call or text your saved emergency contacts directly — a voice call often gets through when data will not."),
            GuideItem(Res.drawable.ic_sg_megaphone, "Make noise if trapped", "Shout or tap on pipes in bursts of three. Conserve your voice."),
            GuideItem(Res.drawable.ic_sg_lifering, "Help only if it is safe", "Never enter a damaged structure to reach someone."),
        ),
    ),
    GuideSection(
        "First aid",
        listOf(
            GuideItem(Res.drawable.ic_sg_first_aid, "Treat bleeding first", "Apply firm direct pressure with a clean cloth."),
            GuideItem(Res.drawable.ic_sg_aed_heart, "AED and CPR", "If someone is unresponsive and not breathing, start CPR and send for the AED."),
            GuideItem(Res.drawable.ic_sg_stretcher, "Do not move the injured", "Unless there is immediate danger, wait for trained responders."),
            GuideItem(Res.drawable.ic_sg_care_hands, "Keep them warm and calm", "Shock is common. Reassure and cover them."),
        ),
    ),
    GuideSection(
        "Fire and utilities",
        listOf(
            GuideItem(Res.drawable.ic_sg_extinguisher, "Use an extinguisher", "Pull, aim at the base, squeeze, sweep — only on small fires."),
            GuideItem(Res.drawable.ic_sg_extinguisher_hose, "Know the type", "Never use water on an electrical or oil fire."),
            GuideItem(Res.drawable.ic_sg_hose_reel, "Fire hose reel", "For trained staff only, once the area is clear of people."),
            GuideItem(Res.drawable.ic_sg_valve_shutoff, "Shut the main valve", "Close gas and water mains if you were taught how."),
            GuideItem(Res.drawable.ic_sg_gas_cylinder, "Secure gas cylinders", "Move them upright and away from heat if it is safe."),
            GuideItem(Res.drawable.ic_sg_pipe_leak, "Report leaks", "Do not use switches or flames near a suspected gas leak."),
            GuideItem(Res.drawable.ic_sg_detector, "Heed detectors", "Treat every smoke or gas alarm as real."),
        ),
    ),
    GuideSection(
        "Hazards to avoid",
        listOf(
            GuideItem(Res.drawable.ic_sg_hazard_alt, "Damaged structures", "Stay out of buildings with cracks, tilting, or fallen sections."),
            GuideItem(Res.drawable.ic_sg_biohazard, "Biological spills", "Report and keep everyone well back."),
            GuideItem(Res.drawable.ic_sg_chem_banned, "Chemical spills", "Do not attempt clean-up without protection and training."),
            GuideItem(Res.drawable.ic_sg_flask, "Laboratory areas", "Broken glassware and reagents are a common post-quake hazard."),
            GuideItem(Res.drawable.ic_sg_whirlwind, "Dust and debris", "Cover your mouth and nose while moving through it."),
        ),
    ),
)

@Composable
fun SafetyGuideScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(title = "Safety guide", onBack = onBack) }

        item {
            InfoBanner(
                "A supplementary local reference. It does not replace official PHIVOLCS warnings or your school's drill instructions.",
                Icons.Filled.Info,
            )
        }

        guideSections.forEach { section ->
            item { SectionLabel(section.title) }
            item {
                ListGroup {
                    section.items.forEachIndexed { i, guide ->
                        GuideRow(guide)
                        if (i < section.items.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(item: GuideItem) {
    ListRow(
        title = item.title,
        subtitle = item.body,
        leading = {
            Surface(
                shape = RoundedCornerShape(Layout.tile),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
    )
}
