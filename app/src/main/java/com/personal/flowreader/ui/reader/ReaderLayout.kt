package com.personal.flowreader.ui.reader

import com.personal.flowreader.ui.theme.FlowTokens

/*
 * Reading-column geometry. The text column shares the screen gutter with every card, so docked
 * floating cards and fullscreen cards land on the same edges as the text.
 */

/** Left inset of reading text (locus rail gutter); rail bars are centered in this width. */
internal val ReaderContentStartPadding = FlowTokens.Pad.Screen

/** End padding on the reading LazyColumn (right gutter). */
internal val ReaderListEndPadding = FlowTokens.Pad.Screen
