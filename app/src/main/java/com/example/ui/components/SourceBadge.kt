package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadStatus
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOffline
import com.example.ui.theme.SoftRose
import com.example.ui.theme.WarmAmber
import com.example.ui.theme.WarmCoral

@Composable
fun TrackSourceBadge(
    track: TrackEntity,
    downloadProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val (label, color, icon) = when {
        downloadProgress != null || track.downloadStatus == DownloadStatus.DOWNLOADING -> {
            val pct = ((downloadProgress ?: 0f) * 100).toInt()
            Triple("SAVING $pct%", WarmAmber, Icons.Rounded.Downloading)
        }
        track.isLiveStream -> Triple("LIVE RADIO", SoftRose, Icons.Rounded.Podcasts)
        track.sourceType == TrackSourceType.LOCAL -> Triple("LOCAL", EmeraldOffline, Icons.Rounded.Folder)
        track.downloadStatus == DownloadStatus.DOWNLOADED -> Triple("OFFLINE", EmeraldOffline, Icons.Rounded.CloudDone)
        else -> Triple("ONLINE", ElectricCyan, Icons.Rounded.Public)
    }

    SourcePill(
        label = label,
        accentColor = color,
        icon = icon,
        modifier = modifier
    )
}

@Composable
fun SourcePill(
    label: String,
    accentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(0.75.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = accentColor,
            maxLines = 1
        )
    }
}

@Composable
fun ConnectivityModeChip(
    isDeviceOnline: Boolean,
    offlineOnlyMode: Boolean,
    onToggleOfflineMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOffline = offlineOnlyMode || !isDeviceOnline
    val accent = if (isOffline) WarmAmber else EmeraldOffline
    val label = when {
        offlineOnlyMode -> "Offline Mode"
        !isDeviceOnline -> "No Network"
        else -> "Online"
    }
    val icon = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Public

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onToggleOfflineMode)
            .testTag("connectivity_mode_toggle")
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}
