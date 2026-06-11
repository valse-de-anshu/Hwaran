import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.common.Player
import android.content.Context
import com.google.common.collect.ImmutableList
import androidx.media3.session.CommandButton

class CustomProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        return super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
    }
}
