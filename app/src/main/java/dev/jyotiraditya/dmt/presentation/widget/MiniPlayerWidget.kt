package dev.jyotiraditya.dmt.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.MainActivity

class MiniPlayerWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PLAY_PAUSE = "dev.jyotiraditya.dmt.ACTION_PLAY_PAUSE"
        private const val ACTION_NEXT = "dev.jyotiraditya.dmt.ACTION_NEXT"
        private const val ACTION_PREV = "dev.jyotiraditya.dmt.ACTION_PREV"

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            progress: Int,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MiniPlayerWidget::class.java))
            for (id in ids) {
                updateWidget(context, manager, id, title, artist, isPlaying, progress)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            title: String,
            artist: String,
            isPlaying: Boolean,
            progress: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_mini_player)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_subtitle, artist.lowercase())
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            views.setTextViewText(R.id.widget_play, if (isPlaying) "||" else "|>")

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )

            views.setOnClickPendingIntent(R.id.widget_play,
                createControlIntent(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_next,
                createControlIntent(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_prev,
                createControlIntent(context, ACTION_PREV, 3))

            manager.updateAppWidget(widgetId, views)
        }

        private fun createControlIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, MiniPlayerWidget::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateWidget(context, manager, id, "no track", "", false, 0)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ACTION_NEXT -> sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            ACTION_PREV -> sendMediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun sendMediaButton(context: Context, keyCode: Int) {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
