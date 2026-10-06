package org.calamares.miga.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.calamares.miga.MainActivity
import org.calamares.miga.R
import org.calamares.miga.data.share.ShoppingIntents

/** Home screen widget: one tap opens the shopping list with the add field ready. */
class ShoppingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ShoppingIntents.ACTION_QUICK_ADD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_shopping)
            views.setOnClickPendingIntent(R.id.widget_shopping_root, pendingIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
