package ai.openduo.inkboard.ui.epd

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark

@Composable
internal fun EpdSectionTitle(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            color = InkBlack,
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            letterSpacing = (-0.4).sp
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = InkDark,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
internal fun EpdSystemNotice(text: String) {
    Text(
        text = text,
        color = InkDark,
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
    )
}
