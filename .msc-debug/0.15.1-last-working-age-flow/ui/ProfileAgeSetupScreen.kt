package com.mystudycompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mystudycompanion.app.companion.AgeGroup

@Composable
fun ProfileAgeSetupScreen(
    displayName: String,
    minorOnly: Boolean,
    onSelected: (AgeGroup) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(30.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Set up $displayName’s study level",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (minorOnly) {
                                "Google confirmed this is a younger account, but did not provide the exact birthday. Choose the correct level so the app does not treat this user as an adult."
                            } else {
                                "Google did not provide enough age information. Choose the correct level once so the home page, activities, and workbook difficulty match this user."
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        )
                    }
                }
            }
            item { AgeChoice("Child", "Under 10", AgeGroup.CHILD, onSelected) }
            item { AgeChoice("Preteen", "Ages 10–12", AgeGroup.PRETEEN, onSelected) }
            item { AgeChoice("Teen", "Ages 13–17", AgeGroup.TEEN, onSelected) }
            if (!minorOnly) {
                item { AgeChoice("Adult", "Age 18 or older", AgeGroup.ADULT, onSelected) }
            }
        }
    }
}

@Composable
private fun AgeChoice(
    title: String,
    subtitle: String,
    ageGroup: AgeGroup,
    onSelected: (AgeGroup) -> Unit,
) {
    Button(
        onClick = { onSelected(ageGroup) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
