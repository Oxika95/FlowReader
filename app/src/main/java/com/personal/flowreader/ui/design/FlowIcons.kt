package com.personal.flowreader.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon tokens shared by the app and plugins (`lists[].icon`, `card.stats[].icon`,
 * `card.actions[].icon`). Unknown tokens fall back to [DEFAULT]. The token list is part of the
 * plugin API — see docs/plugins/ui-contract.md before adding or renaming one.
 */
object FlowIcons {
    const val DEFAULT = "bookmark"

    /** Every accepted token, in documentation order. */
    val tokens: List<String> = listOf(
        "add", "favorite", "schedule", "bookmark", "star", "check", "visibility", "list", "flag",
        "download", "eye", "pages", "clock", "user", "heart", "followers", "comment", "like", "link", "share",
        "notifications", "bell",
    )

    fun forToken(token: String?): ImageVector = when (token?.lowercase()) {
        "add" -> Icons.Filled.Add
        "favorite", "heart" -> Icons.Filled.Favorite
        "schedule", "clock" -> Icons.Filled.Schedule
        "star" -> Icons.Filled.Star
        "check" -> Icons.Filled.Check
        "visibility", "eye" -> Icons.Filled.Visibility
        "list" -> Icons.AutoMirrored.Filled.List
        "flag" -> Icons.Filled.Flag
        "download" -> Icons.Filled.Download
        "pages" -> Icons.AutoMirrored.Filled.MenuBook
        "user" -> Icons.Filled.Person
        "followers" -> Icons.Filled.Group
        "comment" -> Icons.Filled.ChatBubble
        "like" -> Icons.Filled.ThumbUp
        "link" -> Icons.Filled.Link
        "share" -> Icons.Filled.Share
        "notifications", "bell" -> Icons.Filled.Notifications
        else -> Icons.Filled.Bookmark
    }
}
