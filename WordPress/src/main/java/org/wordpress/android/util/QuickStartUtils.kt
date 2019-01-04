package org.wordpress.android.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import org.wordpress.android.R
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.analytics.AnalyticsTracker.Stat
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.SiteActionBuilder
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.QuickStartStore
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.CHOOSE_THEME
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.CREATE_SITE
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.CUSTOMIZE_SITE
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.ENABLE_POST_SHARING
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.FOLLOW_SITE
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.PUBLISH_POST
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTask.VIEW_SITE
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType
import org.wordpress.android.fluxc.store.QuickStartStore.QuickStartTaskType.UNKNOWN
import org.wordpress.android.ui.RequestCodes
import org.wordpress.android.ui.prefs.AppPrefs
import org.wordpress.android.ui.quickstart.QuickStartDetails
import org.wordpress.android.ui.quickstart.QuickStartReminderReceiver
import org.wordpress.android.ui.themes.ThemeBrowserActivity

class QuickStartUtils {
    companion object {
        /**
         * Formats the string, to highlight text between %1$s and %2$s with specified color, and add an icon
         * in front of it if necessary
         *
         * @param context Context used to access resources
         * @param messageId resources id of the message to display
         * @param iconId resource if of the icon that goes before the highlighted area
         */
        @JvmStatic
        @JvmOverloads
        fun stylizeQuickStartPrompt(context: Context, messageId: Int, iconId: Int = -1): Spannable {
            val spanTagOpen = context.resources.getString(R.string.quick_start_span_start)
            val spanTagEnd = context.resources.getString(R.string.quick_start_span_end)
            val formattedMessage = context.resources.getString(messageId, spanTagOpen, spanTagEnd)

            val spannedMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(formattedMessage, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(formattedMessage)
            }

            val highlightColor = ContextCompat.getColor(context, R.color.blue_light)

            val mutableSpannedMessage = SpannableStringBuilder(spannedMessage)
            val foregroundColorSpan = mutableSpannedMessage
                    .getSpans(0, spannedMessage.length, ForegroundColorSpan::class.java).firstOrNull()

            // nothing to highlight
            if (foregroundColorSpan != null) {
                val startOfHighlight = mutableSpannedMessage.getSpanStart(foregroundColorSpan)
                val endOfHighlight = mutableSpannedMessage.getSpanEnd(foregroundColorSpan)

                mutableSpannedMessage.removeSpan(foregroundColorSpan)
                mutableSpannedMessage.setSpan(
                        ForegroundColorSpan(highlightColor),
                        startOfHighlight, endOfHighlight, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val icon: Drawable? = try {
                    // .mutate() allows us to avoid sharing the state of drawables
                    ContextCompat.getDrawable(context, iconId)?.mutate()
                } catch (e: Resources.NotFoundException) {
                    null
                }

                if (icon != null) {
                    val iconSize = context.resources.getDimensionPixelOffset(R.dimen.dialog_snackbar_max_icons_size)
                    icon.setBounds(0, 0, iconSize, iconSize)

                    DrawableCompat.setTint(icon, highlightColor)
                    if (startOfHighlight > 0) {
                        mutableSpannedMessage.insert(startOfHighlight - 1, "  ")
                    } else {
                        mutableSpannedMessage.insert(startOfHighlight, "  ")
                    }

                    mutableSpannedMessage.setSpan(
                            ImageSpan(icon), startOfHighlight, startOfHighlight + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            return mutableSpannedMessage
        }

        /**
         * Adds animated quick start focus point targetedView to the top level parent,
         * and places it in the top-right corner of the specified targetedView.
         *
         * @param topLevelParent Parent where quick start focus targetedView will be added.
         * Usually Relative or Frame layout
         * @param targetedView View in top-right corner of which the quick start focus view will be placed. Child of
         * topLevelParent
         * @param rightOffset specifies in px how much should we move view to the left from the right
         * @param topOffset specifies in px how much should we move view to the bottom from the top
         */
        @JvmStatic
        fun addQuickStartFocusPointAboveTheView(
            topLevelParent: ViewGroup,
            targetedView: View,
            rightOffset: Int,
            topOffset: Int
        ) {
            topLevelParent.post {
                val quickStartFocusPointView = LayoutInflater.from(topLevelParent.context)
                        .inflate(R.layout.quick_start_focus_point_view, topLevelParent, false)
                val focusPointSize =
                        topLevelParent.context.resources.getDimensionPixelOffset(R.dimen.quick_start_focus_point_size)

                val topLevelParentViewLocation = IntArray(2)
                topLevelParent.getLocationOnScreen(topLevelParentViewLocation)

                val topLevelParentsHorizontalOffset = topLevelParentViewLocation[0]
                val topLevelParentsVerticalOffset = topLevelParentViewLocation[1]

                val focusPointTargetViewLocation = IntArray(2)
                targetedView.getLocationOnScreen(focusPointTargetViewLocation)

                val realFocusPointContainerX = focusPointTargetViewLocation[0] - topLevelParentsHorizontalOffset
                val realFocusPointOffsetFromTheLeft = targetedView.width - focusPointSize - rightOffset

                val focusPointContainerY = focusPointTargetViewLocation[1] - topLevelParentsVerticalOffset

                val x = realFocusPointContainerX + realFocusPointOffsetFromTheLeft
                val y = focusPointContainerY + topOffset

                val params = quickStartFocusPointView.layoutParams as MarginLayoutParams
                params.leftMargin = x
                params.topMargin = y
                topLevelParent.addView(quickStartFocusPointView)

                quickStartFocusPointView.post {
                    quickStartFocusPointView.layoutParams = params
                }
            }
        }

        @JvmStatic
        fun removeQuickStartFocusPoint(topLevelParent: ViewGroup) {
            val focusPointView = topLevelParent.findViewById<View>(R.id.quick_start_focus_point)
            if (focusPointView != null) {
                val directParent = focusPointView.parent
                if (directParent is ViewGroup) {
                    directParent.removeView(focusPointView)
                }
            }
        }

        @JvmStatic
        fun isQuickStartAvailableForTheSite(siteModel: SiteModel): Boolean {
            return (siteModel.hasCapabilityManageOptions &&
                    ThemeBrowserActivity.isAccessible(siteModel) &&
                    SiteUtils.isAccessedViaWPComRest(siteModel))
        }

        @JvmStatic
        fun isQuickStartInProgress(quickStartStore: QuickStartStore): Boolean {
            return !quickStartStore.getQuickStartCompleted(AppPrefs.getSelectedSite().toLong()) &&
                    quickStartStore.hasDoneTask(AppPrefs.getSelectedSite().toLong(), QuickStartTask.CREATE_SITE)
        }

        @JvmStatic
        fun isEveryQuickStartTaskDone(quickStartStore: QuickStartStore): Boolean {
            return quickStartStore.getDoneCount(AppPrefs.getSelectedSite().toLong()) == QuickStartTask.values().size
        }

        @JvmStatic
        fun completeTask(
            quickStartStore: QuickStartStore,
            task: QuickStartTask,
            dispatcher: Dispatcher,
            site: SiteModel,
            context: Context?
        ) {
            val siteId = site.id.toLong()

            if (quickStartStore.getQuickStartCompleted(siteId) || isEveryQuickStartTaskDone(quickStartStore) ||
                    quickStartStore.hasDoneTask(siteId, task)) {
                return
            }

            if (context != null) {
                stopQuickStartReminderTimer(context)
            }

            quickStartStore.setDoneTask(siteId, task, true)
            AnalyticsTracker.track(getTaskCompletedTracker(task))

            if (isEveryQuickStartTaskDone(quickStartStore)) {
                AnalyticsTracker.track(Stat.QUICK_START_ALL_TASKS_COMPLETED)
                dispatcher.dispatch(SiteActionBuilder.newCompleteQuickStartAction(site))
            } else {
                if (context != null) {
                    val nextTask = getNextUncompletedQuickStartTask(quickStartStore, siteId, task.taskType)
                    if (nextTask != null) {
                        startQuickStartReminderTimer(context, nextTask)
                    }
                }
            }
        }

        @JvmStatic
        fun getQuickStartListTappedTracker(task: QuickStartTask): Stat {
            return when (task) {
                CREATE_SITE -> Stat.QUICK_START_LIST_CREATE_SITE_TAPPED
                VIEW_SITE -> Stat.QUICK_START_LIST_VIEW_SITE_TAPPED
                CHOOSE_THEME -> Stat.QUICK_START_LIST_BROWSE_THEMES_TAPPED
                CUSTOMIZE_SITE -> Stat.QUICK_START_LIST_CUSTOMIZE_SITE_TAPPED
                ENABLE_POST_SHARING -> Stat.QUICK_START_LIST_ADD_SOCIAL_TAPPED
                PUBLISH_POST -> Stat.QUICK_START_LIST_PUBLISH_POST_TAPPED
                FOLLOW_SITE -> Stat.QUICK_START_LIST_FOLLOW_SITE_TAPPED
                else -> {
                    // TODO: Quick Start - Replace else with remaining tasks.
                    Stat.QUICK_START_LIST_BROWSE_THEMES_TAPPED
                }
            }
        }

        private fun getTaskCompletedTracker(task: QuickStartTask): Stat {
            return when (task) {
                CREATE_SITE -> Stat.QUICK_START_CREATE_SITE_TASK_COMPLETED
                VIEW_SITE -> Stat.QUICK_START_VIEW_SITE_TASK_COMPLETED
                CHOOSE_THEME -> Stat.QUICK_START_BROWSE_THEMES_TASK_COMPLETED
                CUSTOMIZE_SITE -> Stat.QUICK_START_CUSTOMIZE_SITE_TASK_COMPLETED
                ENABLE_POST_SHARING -> Stat.QUICK_START_SHARE_SITE_TASK_COMPLETED
                PUBLISH_POST -> Stat.QUICK_START_PUBLISH_POST_TASK_COMPLETED
                FOLLOW_SITE -> Stat.QUICK_START_FOLLOW_SITE_TASK_COMPLETED
                else -> {
                    // TODO: Quick Start - Replace else with remaining tasks.
                    Stat.QUICK_START_BROWSE_THEMES_TASK_COMPLETED
                }
            }
        }

        fun startQuickStartReminderTimer(context: Context, quickStartTask: QuickStartTask) {
            val ONE_DAY = (24 * 60 * 60 * 1000).toLong()

            val intent = Intent(context, QuickStartReminderReceiver::class.java)
            val bundle = Bundle()
            bundle.putSerializable(QuickStartDetails.KEY, QuickStartDetails.getDetailsForTask(quickStartTask))
            intent.putExtra(QuickStartReminderReceiver.ARG_QUICK_START_TASK_BATCH, bundle)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RequestCodes.QUICK_START_REMINDER_RECEIVER,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

            alarmManager.set(
                    AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (1 * 10 * 1000),
                    pendingIntent
            )
        }

        @JvmStatic
        fun stopQuickStartReminderTimer(context: Context) {
            val intent = Intent(context, QuickStartReminderReceiver::class.java)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RequestCodes.QUICK_START_REMINDER_RECEIVER,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.cancel(pendingIntent)
        }

        fun getNextUncompletedQuickStartTask(
            quickStartStore: QuickStartStore,
            siteId: Long,
            taskType: QuickStartTaskType
        ): QuickStartTask? {
            val uncompletedTasksOfPreferredType = quickStartStore.getUncompletedTasksByType(siteId, taskType)

            var nextTask: QuickStartTask? = null

            if (uncompletedTasksOfPreferredType.isEmpty()) {
                val otherQuickStartTaskTypes = QuickStartTaskType.values()
                        .filter { it != taskType && it != UNKNOWN }

                otherQuickStartTaskTypes.forEach {
                    val otherUncompletedTasks = quickStartStore.getUncompletedTasksByType(siteId, it)
                    if (otherUncompletedTasks.isNotEmpty()) {
                        nextTask = quickStartStore.getUncompletedTasksByType(siteId, it).first()
                        return@forEach
                    }
                }
            } else {
                nextTask = uncompletedTasksOfPreferredType.first()
            }

            return nextTask
        }
    }
}
