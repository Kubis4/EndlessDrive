package sk.kubis.endlessdrive.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.kubis.endlessdrive.ui.l10n.LocalCopy
import sk.kubis.endlessdrive.ui.theme.BtnStyle
import sk.kubis.endlessdrive.ui.theme.GameButton
import sk.kubis.endlessdrive.ui.theme.GameColors
import sk.kubis.endlessdrive.ui.theme.GamePanel

@Composable
fun TutorialScreen(
    startsRun: Boolean,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val copy = LocalCopy.current
    var pageIndex by remember { mutableIntStateOf(0) }
    val pageCount = 6
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(GameColors.panelSoft),
        contentAlignment = Alignment.Center
    ) {
        GamePanel(
            title = copy.howToPlay,
            subtitle = copy.tutorialSubtitle(pageIndex + 1, pageCount, copy.tutorialEyebrow(pageIndex)),
            modifier = Modifier.padding(18.dp).widthIn(max = 680.dp),
            fillHeight = false,
            onClose = onBack
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    copy.tutorialTitle(pageIndex),
                    color = GameColors.accent,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(copy.tutorialBody(pageIndex), color = GameColors.text, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(16.dp))
                repeat(3) { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text("—", color = GameColors.accent, fontWeight = FontWeight.Bold)
                        Text(
                            copy.tutorialCheck(pageIndex, item),
                            color = GameColors.textDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(
                    if (pageIndex == 0) copy.back else copy.previous,
                    if (pageIndex == 0) onBack else ({ pageIndex -= 1 }),
                    style = BtnStyle.Ghost,
                    modifier = Modifier.weight(1f)
                )
                GameButton(
                    if (pageIndex == pageCount - 1) {
                        if (startsRun) copy.startExpedition else copy.done
                    } else copy.next,
                    if (pageIndex == pageCount - 1) onComplete else ({ pageIndex += 1 }),
                    style = BtnStyle.Primary,
                    modifier = Modifier.weight(1.35f)
                )
            }
        }
    }
}
