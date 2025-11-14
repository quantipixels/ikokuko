package com.quantipixels.ikokuko.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ikokuko_cmp.composeapp.generated.resources.Res
import ikokuko_cmp.composeapp.generated.resources.ikokuko
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var signUpData by remember { mutableStateOf<SignUpData?>(null) }

        LaunchedEffect(signUpData) {
            println(signUpData)
        }

        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Res.drawable.ikokuko),
                    contentDescription = null,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "ìkọkúkọ", style = MaterialTheme.typography.displayMedium)
            }
            Spacer(Modifier.height(24.dp))
            SignUpForm(onSubmit = { signUpData = it })
        }

        signUpData?.let { data ->
            SignUpSummaryDialog(data) { signUpData = null }
        }
    }
}

@Composable
private fun SignUpSummaryDialog(
    data: SignUpData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Congratulations!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You’ve successfully completed the demo signup form.")
                HorizontalDivider()
                InfoLine("Phone number", data.phoneNumber)
                InfoLine("Email", data.email)
                InfoLine("Password", "•".repeat(data.password.length))
                InfoLine("Password confirmed?", if (data.password == data.confirmation) "Yes" else "No")
                InfoLine("Capacity", data.capacity.name)
                InfoLine("Projects", data.projects.joinToString(", ") { it.name })
                InfoLine("Terms accepted", if (data.terms) "Yes" else "No")
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Thanks for trying out the ìkọkúkọ demo!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
