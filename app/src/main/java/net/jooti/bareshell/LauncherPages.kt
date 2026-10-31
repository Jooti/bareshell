package net.jooti.bareshell

import android.animation.ObjectAnimator
import android.content.Context
import android.icu.text.AlphabeticIndex
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

internal data class PageContent(val apps: AppListResult? = null, val contacts: ContactsResult? = null)

internal class LauncherPages(
    private val context: Context,
    private val page: Page,
    query: String,
    private val launch: (AppEntry) -> Unit,
    private val usageSettings: () -> Unit,
    private val contactsAccess: () -> Unit,
    private val contactSettingsNeeded: () -> Boolean,
    private val retry: () -> Unit,
    private val selectContact: (ContactEntry) -> Unit,
    private val toggleNumbers: () -> Unit,
    private val retryDetails: () -> Unit,
    private val contactAction: (ContactAction) -> Unit,
    private val holdApp: (AppEntry) -> Unit,
    private val holdContact: (ContactEntry) -> Unit,
    private val appOption: (AppEntry, ItemOption) -> Unit,
    private val contactOption: (ContactEntry, ItemOption) -> Unit,
    onQuery: (String) -> Unit,
) {
    private data class Row(
        val key: String,
        val text: String,
        val id: Int,
        val indent: Int = 0,
        val contactId: Long? = null,
        val dimCandidate: Boolean = false,
        val action: (() -> Unit)? = null,
        val hold: (() -> Unit)? = null,
    )
    private data class Frame(val key: String, val top: Int, val color: Int)
    private val rows = mutableListOf<Row>()
    private val identities = mutableMapOf<String, Long>()
    private val colorAnimations = mutableMapOf<TextView, ObjectAnimator>()
    private var nextIdentity = 0L
    private var content: PageContent? = null
    private var matches: SearchMatches? = null
    private var queryText = query
    private var menu = ContactMenu()
    private var spacerHeight = 0
    private var restoredAnchor: Pair<String, Int>? = null
    private var active = true
    private var renderGeneration = 0
    private val favorites = page == Page.FAV_APPS || page == Page.FAV_CONTACTS
    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = identities.getOrPut(rows[position].key) { nextIdentity++ }
        override fun hasStableIds(): Boolean = true
        override fun areAllItemsEnabled(): Boolean = false
        override fun isEnabled(position: Int): Boolean = rows[position].action != null
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = rows[position]
            val text = convertView as? TextView ?: context.textLabel("", row.id)
            colorAnimations.remove(text)?.cancel()
            text.animate().cancel()
            text.translationY = 0f
            text.alpha = 1f
            text.tag = row.key
            text.id = row.id
            text.text = row.text
            text.textSize = if (row.id == R.id.app_row || row.id == R.id.contact_row) 24f else 16f
            val selected = row.key == menu.selectedKey
            text.setTextColor(context.appearanceColor(
                if (row.dimCandidate && menu.expanded && !selected) {
                    R.color.foreground_muted
                } else R.color.foreground,
            ))
            text.isActivated = selected
            text.importantForAccessibility = if (row.id == R.id.list_spacer) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else View.IMPORTANT_FOR_ACCESSIBILITY_YES
            text.contentDescription = when (row.id) {
                R.id.contact_row, R.id.app_row -> context.getString(
                    if (selected) R.string.contact_expanded else R.string.contact_collapsed, row.text,
                )
                R.id.other_numbers -> context.getString(
                    if (menu.expansion.otherNumbers) R.string.contact_expanded else R.string.contact_collapsed, row.text,
                )
                else -> null
            }
            val spacer = row.id == R.id.list_spacer
            text.minHeight = if (spacer) 0 else context.dp(56)
            val indent = (text.paint.measureText("00") * row.indent).roundToInt()
            text.setPaddingRelative(
                if (spacer) 0 else context.dp(8) + indent,
                if (spacer) 0 else context.dp(8),
                if (spacer) 0 else context.dp(8),
                if (spacer) 0 else context.dp(8),
            )
            val params = text.layoutParams as? AbsListView.LayoutParams
                ?: AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = if (spacer) spacerHeight else ViewGroup.LayoutParams.WRAP_CONTENT
            text.layoutParams = params
            return text
        }
    }
    private val list = ListView(context).apply {
        id = R.id.app_list
        isSaveEnabled = false
        divider = null
        clipToPadding = false
        this.adapter = this@LauncherPages.adapter
        setOnItemClickListener { _, _, position, _ -> rows[position].action?.invoke() }
        setOnItemLongClickListener { _, _, position, _ ->
            val hold = rows[position].hold
            hold?.invoke()
            hold != null
        }
    }
    private val index = if (page == Page.ALL_CONTACTS || page == Page.ALL_APPS) {
        AlphabetIndex(context) { position -> list.smoothScrollToPositionFromTop(position, 0, 0) }
    } else null
    private val alphabet by lazy {
        AlphabeticIndex<String>(Locale.getDefault()).addLabels(Locale.ENGLISH).buildImmutableIndex()
    }
    val view: View

    init {
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, state: Int) {
                index?.setScrolling(state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
            }
            override fun onScroll(view: AbsListView, first: Int, visible: Int, total: Int) {
                index?.onScrolled()
            }
        })
        view = if (page == Page.SEARCH) {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(EditText(context).apply {
                    id = R.id.search_input
                    hint = context.getString(R.string.search_hint)
                    setTextColor(context.appearanceColor(R.color.foreground))
                    setHintTextColor(context.appearanceColor(R.color.foreground))
                    textSize = 24f
                    minHeight = context.dp(56)
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    setText(query)
                    setSelection(text.length)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(text: Editable?) { onQuery(text.toString()) }
                    })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        } else {
            object : FrameLayout(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    if (favorites) {
                        val strip = rootView.findViewById<View>(R.id.gesture_strip)
                        val gestureHeight = strip?.height ?: context.dp(96)
                        val height = MeasureSpec.getSize(heightMeasureSpec)
                        val minimum = (context.dp(112) * resources.configuration.fontScale).roundToInt()
                        val gap = favoriteTopGap(height + gestureHeight, gestureHeight, minimum)
                        if (paddingTop != gap) setPadding(0, gap, 0, 0)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply {
                addView(list, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (favorites) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (favorites) Gravity.TOP else Gravity.CENTER_VERTICAL,
                ).apply { if (index != null) marginEnd = context.dp(48) })
                index?.let {
                    addView(it, FrameLayout.LayoutParams(context.dp(48), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
                }
            }
        }
        showLoading()
    }

    fun showLoading() {
        content = null
        if (page == Page.SEARCH) {
            update(emptyList(), false)
            return
        }
        update(listOf(Row("loading", context.getString(
            if (page == Page.FAV_CONTACTS || page == Page.ALL_CONTACTS) R.string.loading_contacts else R.string.loading_apps,
        ), R.id.status_message)), false)
    }

    fun show(content: PageContent, matches: SearchMatches? = null, query: String = "") {
        this.content = content
        this.matches = matches
        queryText = query
        render(false)
    }

    fun setMenu(next: ContactMenu, animate: Boolean = false) {
        val wasExpanded = menu.expanded
        menu = next
        if (content != null && !wasExpanded && next.expanded && page != Page.SEARCH && !favorites) {
            spacerHeight = list.top
        }
        render(animate)
    }

    private fun render(animate: Boolean) {
        val data = content ?: return
        val next = mutableListOf<Row>()
        val expanded = menu.expanded
        if (page != Page.SEARCH && !favorites) {
            (list.layoutParams as FrameLayout.LayoutParams).apply {
                height = if (expanded) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = if (expanded) Gravity.TOP else Gravity.CENTER_VERTICAL
            }
            list.requestLayout()
            if (expanded && spacerHeight > 0) next += Row("spacer", "", R.id.list_spacer)
            if (!expanded) spacerHeight = 0
        }
        when (page) {
            Page.TASKS -> error("Tasks uses its own page")
            Page.FAV_APPS, Page.ALL_APPS -> addApps(next, data.apps)
            Page.ALL_CONTACTS, Page.FAV_CONTACTS -> addContacts(next, data.contacts)
            Page.SEARCH -> {
                val found = matches
                if (queryText.isNotBlank() && found != null) {
                    if (found.apps.isNotEmpty()) {
                        next += Row("apps_heading", context.getString(R.string.apps_heading), R.id.section_heading)
                        found.apps.forEach { addApp(next, it) }
                    }
                    if (found.contacts.isNotEmpty()) {
                        next += Row("contacts_heading", context.getString(R.string.contacts_heading), R.id.section_heading)
                        found.contacts.forEach { addContact(next, it) }
                    }
                    if (found.apps.isEmpty() && found.contacts.isEmpty() && data.apps is AppListResult.Loaded) {
                        next += Row("no_matches", context.getString(
                            if (data.contacts is ContactsResult.Loaded) R.string.no_matches else R.string.no_app_matches,
                        ), R.id.status_message)
                    }
                }
                if (data.apps == AppListResult.Unavailable) {
                    next += Row("apps_error", context.getString(R.string.apps_unavailable), R.id.status_message, action = retry)
                }
                addContactsState(next, data.contacts)
            }
        }
        update(next, animate)
        index?.suppress(expanded)
        index?.update(when {
            page == Page.ALL_CONTACTS && data.contacts is ContactsResult.Loaded ->
                contactSections(data.contacts.contacts, context.getString(R.string.contact_index_favorites), ::bucket)
            page == Page.ALL_APPS && data.apps is AppListResult.Loaded -> alphabetSections(data.apps.apps.map { it.label }, ::bucket)
            else -> emptyList()
        })
    }

    private fun bucket(name: String): String {
        val bucket = requireNotNull(alphabet.getBucket(alphabet.getBucketIndex(name)))
        return if (bucket.labelType == AlphabeticIndex.Bucket.LabelType.NORMAL) bucket.label else "#"
    }

    private fun addApp(next: MutableList<Row>, app: AppEntry) {
        next += Row("app:${app.packageName}", app.label, R.id.app_row, dimCandidate = true,
            action = { launch(app) }, hold = { holdApp(app) })
        if (menu.options != ItemTarget.App(app.packageName)) return
        addOptions(next, "app:${app.packageName}", listOf(ItemOption.APP_INFO, ItemOption.UP, ItemOption.DOWN),
            { appOption(app, it) })
    }

    private fun addOptions(
        next: MutableList<Row>, key: String, options: List<ItemOption>, action: (ItemOption) -> Unit,
        enabled: Boolean = true,
    ) {
        options.forEach { option ->
            val label = when (option) {
                ItemOption.APP_INFO -> R.string.app_info
                ItemOption.VIEW -> R.string.option_view
                ItemOption.EDIT -> R.string.option_edit
                ItemOption.UP -> R.string.ranking_up
                ItemOption.DOWN -> R.string.ranking_down
            }
            next += Row("$key:option:$option", context.getString(label), R.id.item_option, indent = 1,
                action = if (enabled && !menu.saving) ({ action(option) }) else null)
        }
        if (menu.saving) next += Row("$key:saving", context.getString(R.string.settings_saving),
            R.id.status_message, indent = 1)
    }

    private fun addApps(next: MutableList<Row>, result: AppListResult?) {
        when (result) {
            AppListResult.Unavailable -> next += Row(
                "apps_error", context.getString(R.string.apps_unavailable), R.id.status_message, action = retry,
            )
            is AppListResult.Loaded -> {
                result.apps.forEach { addApp(next, it) }
                if (result.apps.isEmpty()) next += Row("no_apps", context.getString(R.string.no_apps), R.id.status_message)
                when (result.usageAccess) {
                    UsageAccess.REQUIRED -> next += Row(
                        "usage", context.getString(R.string.enable_usage_access), R.id.usage_access, action = usageSettings,
                    )
                    UsageAccess.UNAVAILABLE -> next += Row(
                        "usage_error", context.getString(R.string.usage_unavailable), R.id.status_message, action = retry,
                    )
                    UsageAccess.GRANTED, null -> Unit
                }
            }
            null -> error("App content is required for this page")
        }
    }

    private fun addContacts(next: MutableList<Row>, result: ContactsResult?) {
        if (result is ContactsResult.Loaded) {
            result.contacts.forEach { addContact(next, it) }
            if (result.contacts.isEmpty()) next += Row("no_contacts", context.getString(
                if (page == Page.FAV_CONTACTS) R.string.no_fav_contacts else R.string.no_contacts,
            ), R.id.status_message)
            if (result.historyUnavailable) next += Row(
                "history_error", context.getString(R.string.contact_history_unavailable), R.id.status_message, action = retry,
            )
        }
        addContactsState(next, result)
    }

    private fun addContact(next: MutableList<Row>, contact: ContactEntry) {
        next += Row(
            "contact:${contact.id}", contact.label, R.id.contact_row,
            contactId = contact.id, dimCandidate = true, action = { selectContact(contact) },
            hold = { holdContact(contact) },
        )
        if (menu.expansion.contactId != contact.id) return
        fun status(resource: Int, retryAction: (() -> Unit)? = null) {
            next += Row("detail:${contact.id}:$resource", context.getString(resource), R.id.contact_detail_status,
                indent = 1, contactId = contact.id, action = retryAction)
        }
        when (val result = menu.result) {
            ContactDetailsResult.Loading -> status(R.string.loading_contacts)
            ContactDetailsResult.Unavailable -> status(R.string.contact_details_unavailable, retryDetails)
            ContactDetailsResult.Missing -> status(R.string.contact_missing, retry)
            ContactDetailsResult.Denied -> status(R.string.contacts_denied, contactsAccess)
            is ContactDetailsResult.Loaded -> {
                if (menu.options == ItemTarget.Contact(contact.id)) {
                    val current = result.details.contact
                    if (current != null) addOptions(next, "contact:${contact.id}",
                        listOf(ItemOption.VIEW, ItemOption.EDIT, ItemOption.UP, ItemOption.DOWN),
                        { contactOption(current, it) }, enabled = !menu.refreshing)
                    else status(R.string.contact_missing, retry)
                    if (menu.refreshing) status(R.string.contact_refreshing)
                    return
                }
                fun phoneRow(phone: ContactPhone, sms: Boolean, indent: Int, labeled: Boolean) {
                    val description = if (labeled) context.getString(R.string.phone_description, phone.label, phone.number) else phone.number
                    val action = if (sms) ContactAction.Sms(phone) else ContactAction.Dial(phone)
                    next += Row(
                        "detail:${contact.id}:${if (sms) "sms" else "call"}:${phone.id}",
                        context.getString(if (sms) R.string.contact_sms else R.string.contact_call, description),
                        R.id.contact_action, indent, contact.id,
                        action = if (menu.refreshing) null else ({ contactAction(action) }),
                    )
                }
                val phones = result.details.phones
                phones.firstOrNull()?.let { phoneRow(it, false, 1, false) }
                if (phones.size > 1) {
                    next += Row("detail:${contact.id}:other", context.getString(R.string.other_numbers),
                        R.id.other_numbers, 1, contact.id, action = toggleNumbers)
                    if (menu.expansion.otherNumbers) phones.drop(1).forEach {
                        phoneRow(it, false, 2, true)
                        phoneRow(it, true, 2, true)
                    }
                }
                phones.firstOrNull()?.let { phoneRow(it, true, 1, false) }
                result.details.messaging.groupBy { it.kind }.toSortedMap(compareBy { it.ordinal }).forEach { (kind, targets) ->
                    targets.forEachIndexed { position, target ->
                        val label = context.getString(when (kind) {
                            MessengerAction.TELEGRAM_MESSAGE -> R.string.telegram_message
                            MessengerAction.WHATSAPP_MESSAGE -> R.string.whatsapp_message
                            MessengerAction.WHATSAPP_CALL -> R.string.whatsapp_call
                        })
                        val detail = target.targetLabel.takeIf { value ->
                            value.isNotBlank() && targets.count { it.targetLabel == value } == 1
                        } ?: context.getString(R.string.messaging_account, position + 1)
                        next += Row(
                            "detail:${contact.id}:message:${target.dataId}",
                            if (targets.size == 1) label else context.getString(
                                R.string.messaging_target, label,
                                detail,
                            ),
                            R.id.contact_action, 1, contact.id,
                            action = if (menu.refreshing) null else ({ contactAction(ContactAction.Messenger(target)) }),
                        )
                    }
                }
                result.details.contact?.let { contact ->
                    next += Row("detail:${contact.id}:view", context.getString(R.string.view_contact),
                        R.id.contact_action, 1, contact.id,
                        action = if (menu.refreshing) null else ({ contactAction(ContactAction.View(contact)) }))
                }
                if (phones.isEmpty() && result.details.messaging.isEmpty() && result.details.contact == null) status(R.string.no_contact_actions)
                if (menu.refreshing) status(R.string.contact_refreshing)
            }
        }
    }

    private fun addContactsState(next: MutableList<Row>, result: ContactsResult?) {
        when (result) {
            ContactsResult.Denied -> {
                next += Row("contacts_denied", context.getString(R.string.contacts_denied), R.id.status_message)
                next += Row("contacts_access", context.getString(
                    if (contactSettingsNeeded()) R.string.contact_settings else R.string.allow_contacts,
                ), R.id.contacts_access, action = contactsAccess)
            }
            ContactsResult.Unavailable -> next += Row(
                "contacts_error", context.getString(R.string.contacts_unavailable), R.id.status_message, action = retry,
            )
            is ContactsResult.Loaded -> Unit
            null -> error("Contact content is required for this page")
        }
    }

    private fun frames(): List<Frame> = (0 until list.childCount).mapNotNull { position ->
        val text = list.getChildAt(position) as? TextView ?: return@mapNotNull null
        val key = text.tag as? String ?: return@mapNotNull null
        if (key == "spacer") null else Frame(key, list.top + text.top, text.currentTextColor)
    }

    private fun update(next: List<Row>, animate: Boolean) {
        val generation = ++renderGeneration
        val before = frames()
        val keys = next.mapTo(mutableSetOf()) { it.key }
        val selectedKey = menu.selectedKey
        val restored = restoredAnchor?.takeIf { it.first in keys }
        val anchor = restored
            ?: before.firstOrNull { it.key == selectedKey }?.let { it.key to it.top }
            ?: before.firstOrNull { it.key in keys }?.let { it.key to it.top }
        rows.clear()
        rows.addAll(next)
        adapter.notifyDataSetChanged()
        if (anchor == null && !animate) return
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            private var anchored = false
            override fun onPreDraw(): Boolean {
                if (!active || generation != renderGeneration) {
                    list.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
                if (!anchored && anchor != null) {
                    anchored = true
                    val position = rows.indexOfFirst { it.key == anchor.first }
                    if (position >= 0) {
                        val top = if (restored != null) anchor.second.coerceAtMost(list.height / 2) else anchor.second
                        list.setSelectionFromTop(position, top - list.top)
                        return false
                    }
                }
                list.viewTreeObserver.removeOnPreDrawListener(this)
                if (restoredAnchor == restored) restoredAnchor = null
                if (animate) for (position in 0 until list.childCount) {
                    val text = list.getChildAt(position) as? TextView ?: continue
                    val old = before.firstOrNull { it.key == text.tag }
                    text.translationY = old?.let { (it.top - list.top - text.top).toFloat() } ?: -context.dp(8).toFloat()
                    text.alpha = if (old == null) 0f else 1f
                    text.animate().translationY(0f).alpha(1f).setDuration(200).start()
                    if (old != null && old.color != text.currentTextColor) {
                        val animator = ObjectAnimator.ofArgb(text, "textColor", old.color, text.currentTextColor)
                        colorAnimations[text] = animator
                        animator.setDuration(200).start()
                    }
                }
                return true
            }
        }
        list.viewTreeObserver.addOnPreDrawListener(listener)
    }

    fun saveState(state: Bundle) {
        val visible = frames()
        (visible.firstOrNull { it.key == menu.selectedKey } ?: visible.firstOrNull())?.let {
            state.putString("list_anchor", it.key)
            state.putInt("list_anchor_top", it.top)
        }
    }

    fun restoreState(state: Bundle?) {
        state?.getString("list_anchor")?.let { restoredAnchor = it to state.getInt("list_anchor_top") }
    }

    fun dispose() {
        active = false
        colorAnimations.values.forEach { it.cancel() }
        colorAnimations.clear()
    }
}
