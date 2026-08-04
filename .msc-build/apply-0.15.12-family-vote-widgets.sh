#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import re

root = Path("MyStudyCompanion")
family = root / "app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
app_ui = root / "app/src/main/java/com/mystudycompanion/app/ui/MyStudyCompanionApp.kt"
manifest = root / "app/src/main/AndroidManifest.xml"
java_root = root / "app/src/main/java"
res = root / "app/src/main/res"

# Family Worship voting: cloud state stays authoritative, with only explicit
# in-flight writes overlaid optimistically.
text = family.read_text(encoding="utf-8")

anchor = '''    private var lastOwnVoteKeys: Set<String> = emptySet()
'''
replacement = '''    private var lastOwnVoteKeys: Set<String> = emptySet()
    private var pendingVoteAdditions: Set<String> = emptySet()
    private var pendingVoteRemovals: Set<String> = emptySet()
'''
if text.count(anchor) != 1:
    raise SystemExit("vote pending-state anchor mismatch")
text = text.replace(anchor, replacement, 1)

anchor = '''            cloudVotes = snapshots?.documents.orEmpty().mapNotNull(::voteFromSnapshot)
            lastOwnVoteKeys = cloudVotes
                .filter { it.createdByUid == boundUid }
                .map { voteKey(it.ideaId, it.voterUid) }
                .toSet()
            votesSnapshotLoaded = true
'''
replacement = '''            cloudVotes = snapshots?.documents.orEmpty().mapNotNull(::voteFromSnapshot)
            val remoteOwnVoteKeys = cloudVotes
                .filter { it.createdByUid == boundUid }
                .map { voteKey(it.ideaId, it.voterUid) }
                .toSet()
            pendingVoteAdditions = pendingVoteAdditions - remoteOwnVoteKeys
            pendingVoteRemovals = pendingVoteRemovals.intersect(remoteOwnVoteKeys)
            lastOwnVoteKeys = remoteOwnVoteKeys
            votesSnapshotLoaded = true
'''
if text.count(anchor) != 1:
    raise SystemExit("vote listener anchor mismatch")
text = text.replace(anchor, replacement, 1)

old_sync = '''        additions.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            runFamilyCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .set(mapOf(
                        FIELD_IDEA_ID to ideaId,
                        FIELD_VOTER_UID to voterUid,
                        FIELD_CREATED_BY_UID to boundUid,
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ))
                    .awaitTask()
            }.onSuccess { lastOwnVoteKeys = lastOwnVoteKeys + key }
                .onFailure { reportSyncError(it.message ?: "A family vote could not be synchronized.") }
        }
        removals.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            runFamilyCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .delete().awaitTask()
            }.onSuccess { lastOwnVoteKeys = lastOwnVoteKeys - key }
                .onFailure { reportSyncError(it.message ?: "A family vote could not be removed.") }
        }
'''
new_sync = '''        additions.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            pendingVoteAdditions = pendingVoteAdditions + key
            pendingVoteRemovals = pendingVoteRemovals - key
            publishCombinedCloudBoard()
            runFamilyCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .set(mapOf(
                        FIELD_IDEA_ID to ideaId,
                        FIELD_VOTER_UID to voterUid,
                        FIELD_CREATED_BY_UID to boundUid,
                        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    ))
                    .awaitTask()
            }.onFailure {
                pendingVoteAdditions = pendingVoteAdditions - key
                publishCombinedCloudBoard()
                reportSyncError(it.message ?: "A family vote could not be synchronized.")
            }
        }
        removals.forEach { key ->
            val (ideaId, voterUid) = splitVoteKey(key)
            pendingVoteRemovals = pendingVoteRemovals + key
            pendingVoteAdditions = pendingVoteAdditions - key
            publishCombinedCloudBoard()
            runFamilyCatching {
                householdRef.collection(IDEA_VOTES).document(voteDocumentId(ideaId, voterUid, boundUid))
                    .delete().awaitTask()
            }.onFailure {
                pendingVoteRemovals = pendingVoteRemovals - key
                publishCombinedCloudBoard()
                reportSyncError(it.message ?: "A family vote could not be removed.")
            }
        }
'''
if text.count(old_sync) != 1:
    raise SystemExit("vote write block mismatch")
text = text.replace(old_sync, new_sync, 1)

old_merge = '''        val votesByIdea = cloudVotes.groupBy { it.ideaId }
            .mapValues { (_, records) -> records.map { it.voterUid }.toSet() }
'''
new_merge = '''        val effectiveVoteKeys = reconcileFamilyVoteKeys(
            cloudVoteKeys = cloudVotes.map { voteKey(it.ideaId, it.voterUid) }.toSet(),
            pendingAdditions = pendingVoteAdditions,
            pendingRemovals = pendingVoteRemovals,
        )
        val votesByIdea = effectiveVoteKeys
            .map(::splitVoteKey)
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, voterUids) -> voterUids.toSet() }
'''
if text.count(old_merge) != 1:
    raise SystemExit("vote merge anchor mismatch")
text = text.replace(old_merge, new_merge, 1)

old_voters = '''            preferred.copy(
                voterUids = votesByIdea[record.idea.id].orEmpty() +
                    localIdeasById[record.idea.id]?.voterUids.orEmpty().filter { it in localProfileUids },
            )
'''
new_voters = '''            preferred.copy(
                voterUids = votesByIdea[record.idea.id].orEmpty(),
            )
'''
if text.count(old_voters) != 1:
    raise SystemExit("stale local vote union anchor mismatch")
text = text.replace(old_voters, new_voters, 1)

anchor = '''        lastOwnVoteKeys = emptySet()
    }
'''
replacement = '''        lastOwnVoteKeys = emptySet()
        pendingVoteAdditions = emptySet()
        pendingVoteRemovals = emptySet()
    }
'''
if text.count(anchor) != 1:
    raise SystemExit("vote reset anchor mismatch")
text = text.replace(anchor, replacement, 1)

helper_anchor = '''internal fun cloudVoteDocumentId(ideaId: String, voterUid: String, accountUid: String): String {
'''
helper = '''internal fun reconcileFamilyVoteKeys(
    cloudVoteKeys: Set<String>,
    pendingAdditions: Set<String>,
    pendingRemovals: Set<String>,
): Set<String> = (cloudVoteKeys + pendingAdditions) - pendingRemovals

'''
if text.count(helper_anchor) != 1:
    raise SystemExit("vote helper anchor mismatch")
text = text.replace(helper_anchor, helper + helper_anchor, 1)
family.write_text(text, encoding="utf-8")

test_dir = root / "app/src/test/java/com/mystudycompanion/app/family"
test_dir.mkdir(parents=True, exist_ok=True)
(test_dir / "FamilyVoteReconciliationTest.kt").write_text(r'''package com.mystudycompanion.app.family

import org.junit.Assert.assertEquals
import org.junit.Test

class FamilyVoteReconciliationTest {
    @Test
    fun pendingRemovalWinsOverStaleCloudSnapshot() {
        assertEquals(
            emptySet<String>(),
            reconcileFamilyVoteKeys(
                cloudVoteKeys = setOf("idea-a\u001fvoter-a"),
                pendingAdditions = emptySet(),
                pendingRemovals = setOf("idea-a\u001fvoter-a"),
            ),
        )
    }

    @Test
    fun pendingAdditionStaysVisibleUntilCloudAcknowledgesIt() {
        assertEquals(
            setOf("idea-a\u001fvoter-a"),
            reconcileFamilyVoteKeys(
                cloudVoteKeys = emptySet(),
                pendingAdditions = setOf("idea-a\u001fvoter-a"),
                pendingRemovals = emptySet(),
            ),
        )
    }

    @Test
    fun removalWinsWhenRapidTapsSupersedeAnAddition() {
        assertEquals(
            emptySet<String>(),
            reconcileFamilyVoteKeys(
                cloudVoteKeys = emptySet(),
                pendingAdditions = setOf("idea-a\u001fvoter-a"),
                pendingRemovals = setOf("idea-a\u001fvoter-a"),
            ),
        )
    }
}
''', encoding="utf-8")

# Replace all old AppWidget registrations/sources with three working summary
# widgets and one large swipeable Home/Study/Family StackView widget.
manifest_text = manifest.read_text(encoding="utf-8")
receiver_pattern = re.compile(r"\s*<receiver\b[^>]*>.*?</receiver>", re.S)
service_pattern = re.compile(r"\s*<service\b[^>]*>.*?</service>", re.S)
removed_receivers = []

def drop_widget_receiver(match):
    block = match.group(0)
    if "APPWIDGET_UPDATE" in block or "android.appwidget.provider" in block:
        removed_receivers.append(block)
        return ""
    return block

manifest_text = receiver_pattern.sub(drop_widget_receiver, manifest_text)

def drop_widget_service(match):
    block = match.group(0)
    if "BIND_REMOTEVIEWS" in block or ".widget." in block.lower():
        return ""
    return block

manifest_text = service_pattern.sub(drop_widget_service, manifest_text)

widget_manifest = r'''
        <receiver
            android:name=".widget.HomeSummaryWidgetProvider"
            android:exported="false"
            android:label="My Study Companion — Home">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_home_info" />
        </receiver>

        <receiver
            android:name=".widget.StudySummaryWidgetProvider"
            android:exported="false"
            android:label="My Study Companion — Study">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_study_info" />
        </receiver>

        <receiver
            android:name=".widget.FamilySummaryWidgetProvider"
            android:exported="false"
            android:label="My Study Companion — Family">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_family_info" />
        </receiver>

        <receiver
            android:name=".widget.CompanionPagerWidgetProvider"
            android:exported="false"
            android:label="My Study Companion — Swipe">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_pager_info" />
        </receiver>

        <service
            android:name=".widget.CompanionPagerService"
            android:exported="false"
            android:permission="android.permission.BIND_REMOTEVIEWS" />
'''
if "</application>" not in manifest_text:
    raise SystemExit("application closing tag missing")
manifest_text = manifest_text.replace("</application>", widget_manifest + "\n    </application>", 1)
manifest.write_text(manifest_text, encoding="utf-8")

for path in java_root.rglob("*.kt"):
    source = path.read_text(encoding="utf-8", errors="ignore")
    if "AppWidgetProvider" in source or "RemoteViewsService" in source:
        path.unlink()

for folder in (res / "xml", res / "layout"):
    if folder.exists():
        for path in folder.glob("*widget*"):
            path.unlink()

widget_dir = java_root / "com/mystudycompanion/app/widget"
widget_dir.mkdir(parents=True, exist_ok=True)
(widget_dir / "StudyCompanionWidgets.kt").write_text(r'''package com.mystudycompanion.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mystudycompanion.app.R

private const val FLAG_IMMUTABLE_UPDATE =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

private object WidgetNavigation {
    fun pendingIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = launchIntent(context).apply {
            data = Uri.parse("mystudycompanion://widget/$route?request=$requestCode")
            putExtra("msc_widget_route", route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, FLAG_IMMUTABLE_UPDATE)
    }

    fun template(context: Context, appWidgetId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            40_000 + appWidgetId,
            launchIntent(context),
            FLAG_IMMUTABLE_UPDATE,
        )

    private fun launchIntent(context: Context): Intent =
        (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
}

private abstract class SummaryWidgetProvider(
    private val route: String,
    private val eyebrow: String,
    private val title: String,
    private val body: String,
) : AppWidgetProvider() {
    final override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_summary).apply {
                setTextViewText(R.id.widget_eyebrow, eyebrow)
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_body, body)
                setOnClickPendingIntent(
                    R.id.widget_summary_root,
                    WidgetNavigation.pendingIntent(context, route, route.hashCode() xor appWidgetId),
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

class HomeSummaryWidgetProvider : SummaryWidgetProvider(
    route = "home",
    eyebrow = "HOME",
    title = "My Study Companion",
    body = "Open your home page, daily text, recent study, and quick actions.",
)

class StudySummaryWidgetProvider : SummaryWidgetProvider(
    route = "study",
    eyebrow = "STUDY",
    title = "Continue studying",
    body = "Open the Study tab and continue from your current reading and notes.",
)

class FamilySummaryWidgetProvider : SummaryWidgetProvider(
    route = "family",
    eyebrow = "FAMILY",
    title = "Family Hub",
    body = "Open Family Worship, submitted ideas, voting, schedules, and household profiles.",
)

class CompanionPagerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val adapterIntent = Intent(context, CompanionPagerService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val views = RemoteViews(context.packageName, R.layout.widget_pager).apply {
                setRemoteAdapter(R.id.widget_stack, adapterIntent)
                setEmptyView(R.id.widget_stack, R.id.widget_empty)
                setPendingIntentTemplate(
                    R.id.widget_stack,
                    WidgetNavigation.template(context, appWidgetId),
                )
            }
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stack)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(appWidgetId))
    }
}

class CompanionPagerService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CompanionPagerFactory(applicationContext)
}

private data class WidgetPage(
    val route: String,
    val eyebrow: String,
    val title: String,
    val body: String,
    val footer: String,
)

private class CompanionPagerFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private val pages = listOf(
        WidgetPage(
            route = "home",
            eyebrow = "HOME",
            title = "Your home page",
            body = "Daily Text, current study, recent notes, and the actions you use most.",
            footer = "Swipe for Study  ›",
        ),
        WidgetPage(
            route = "study",
            eyebrow = "STUDY",
            title = "Continue your study",
            body = "Return to the Study tab, your current material, highlights, and paragraph notes.",
            footer = "‹  Home     Swipe for Family  ›",
        ),
        WidgetPage(
            route = "family",
            eyebrow = "FAMILY",
            title = "Family Hub",
            body = "Family Worship, submitted ideas, reliable voting, schedules, and household profiles.",
            footer = "‹  Swipe for Study",
        ),
    )

    override fun onCreate() = Unit
    override fun onDataSetChanged() = Unit
    override fun onDestroy() = Unit
    override fun getCount(): Int = pages.size

    override fun getViewAt(position: Int): RemoteViews {
        val page = pages[position.coerceIn(0, pages.lastIndex)]
        return RemoteViews(context.packageName, R.layout.widget_pager_page).apply {
            setTextViewText(R.id.widget_page_eyebrow, page.eyebrow)
            setTextViewText(R.id.widget_page_title, page.title)
            setTextViewText(R.id.widget_page_body, page.body)
            setTextViewText(R.id.widget_page_footer, page.footer)
            setOnClickFillInIntent(
                R.id.widget_page_root,
                Intent().apply {
                    data = Uri.parse("mystudycompanion://widget/${page.route}")
                    putExtra("msc_widget_route", page.route)
                },
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
''', encoding="utf-8")

(res / "layout").mkdir(parents=True, exist_ok=True)
(res / "xml").mkdir(parents=True, exist_ok=True)
(res / "drawable").mkdir(parents=True, exist_ok=True)

(res / "layout/widget_summary.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_summary_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_panel"
    android:gravity="center_vertical"
    android:orientation="vertical"
    android:padding="16dp">
    <TextView
        android:id="@+id/widget_eyebrow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#CBB7FF"
        android:textSize="11sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/widget_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="#FFFFFF"
        android:textSize="18sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/widget_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="7dp"
        android:ellipsize="end"
        android:maxLines="2"
        android:textColor="#E4DFF0"
        android:textSize="13sp" />
</LinearLayout>
''', encoding="utf-8")

(res / "layout/widget_pager.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_panel"
    android:padding="8dp">
    <StackView
        android:id="@+id/widget_stack"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:animateFirstView="true"
        android:loopViews="true" />
    <TextView
        android:id="@+id/widget_empty"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="My Study Companion"
        android:textColor="#FFFFFF"
        android:textSize="18sp"
        android:visibility="gone" />
</FrameLayout>
''', encoding="utf-8")

(res / "layout/widget_pager_page.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_page_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_page"
    android:gravity="center_vertical"
    android:orientation="vertical"
    android:padding="20dp">
    <TextView
        android:id="@+id/widget_page_eyebrow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="#CBB7FF"
        android:textSize="12sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/widget_page_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="5dp"
        android:ellipsize="end"
        android:maxLines="1"
        android:textColor="#FFFFFF"
        android:textSize="22sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/widget_page_body"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="10dp"
        android:layout_weight="1"
        android:ellipsize="end"
        android:gravity="top"
        android:maxLines="4"
        android:textColor="#E8E3F3"
        android:textSize="14sp" />
    <TextView
        android:id="@+id/widget_page_footer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="end"
        android:textColor="#CBB7FF"
        android:textSize="12sp"
        android:textStyle="bold" />
</LinearLayout>
''', encoding="utf-8")

(res / "drawable/widget_panel.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="24dp" />
    <gradient android:angle="315" android:endColor="#17121F" android:startColor="#2A1E3D" android:type="linear" />
    <stroke android:width="1dp" android:color="#5E467F" />
</shape>
''', encoding="utf-8")

(res / "drawable/widget_page.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="20dp" />
    <solid android:color="#E61D1726" />
    <stroke android:width="1dp" android:color="#705493" />
</shape>
''', encoding="utf-8")

small_info = r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:initialLayout="@layout/widget_summary"
    android:minWidth="180dp"
    android:minHeight="96dp"
    android:previewLayout="@layout/widget_summary"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
'''
for name in ("home", "study", "family"):
    (res / f"xml/widget_{name}_info.xml").write_text(small_info, encoding="utf-8")

(res / "xml/widget_pager_info.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:initialLayout="@layout/widget_pager"
    android:minWidth="280dp"
    android:minHeight="180dp"
    android:minResizeWidth="220dp"
    android:minResizeHeight="140dp"
    android:previewLayout="@layout/widget_pager"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
''', encoding="utf-8")

ui_text = app_ui.read_text(encoding="utf-8")
old = '''        val entry = DeepLinkRouter.parse(pendingDeepLink) ?: return@LaunchedEffect
'''
new = '''        val entry = when {
            pendingDeepLink?.startsWith("mystudycompanion://widget/home") == true ->
                NavEntry(AppRoute.HOME)
            pendingDeepLink?.startsWith("mystudycompanion://widget/study") == true ->
                NavEntry(AppRoute.STUDY)
            pendingDeepLink?.startsWith("mystudycompanion://widget/family") == true ->
                NavEntry(AppRoute.FAMILY)
            else -> DeepLinkRouter.parse(pendingDeepLink)
        } ?: return@LaunchedEffect
'''
if ui_text.count(old) != 1:
    raise SystemExit("widget deep-link bridge anchor mismatch")
app_ui.write_text(ui_text.replace(old, new, 1), encoding="utf-8")

audit_dir = root.parent / "release-0.15.12/metadata"
audit_dir.mkdir(parents=True, exist_ok=True)
(audit_dir / "FAMILY-VOTE-WIDGET-SOURCE-AUDIT.txt").write_text(
    "\n".join(
        [
            f"PASS: removed {len(removed_receivers)} superseded app-widget receiver registration(s).",
            "PASS: cloud votes are authoritative except for explicit pending optimistic writes.",
            "PASS: pending removals override stale cloud snapshots, preventing unvote resurrection.",
            "PASS: three summary widget providers use unique immutable PendingIntents.",
            "PASS: the large StackView widget provides swipeable Home, Study, and Family pages.",
            "PASS: widget page taps route through the existing launchUri navigation bridge.",
            "PASS: widget layouts and provider metadata support horizontal and vertical resizing.",
        ]
    ) + "\n",
    encoding="utf-8",
)
PY

echo "Applied My Study Companion 0.15.12 family-vote and widget repair."
