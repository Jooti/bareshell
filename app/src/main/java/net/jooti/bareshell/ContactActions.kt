package net.jooti.bareshell

import java.util.Locale

internal data class ContactPhone(
    val id: Long,
    val number: String,
    val label: String,
    val primary: Boolean = false,
    val superPrimary: Boolean = false,
)

internal fun preferredPhones(phones: List<ContactPhone>): List<ContactPhone> =
    phones.sortedWith(compareByDescending<ContactPhone> { it.superPrimary }
        .thenByDescending { it.primary }.thenBy { it.id })
        .distinctBy { phone ->
            phone.number.filterNot { it.isWhitespace() || it in "()-." }.lowercase(Locale.ROOT)
        }

internal enum class MessengerAction(val packageName: String, val mimeType: String) {
    TELEGRAM_MESSAGE("org.telegram.messenger", "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"),
    WHATSAPP_MESSAGE("com.whatsapp", "vnd.android.cursor.item/vnd.com.whatsapp.profile"),
    WHATSAPP_CALL("com.whatsapp", "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"),
}

internal data class VerifiedContactAction(
    val dataId: Long,
    val kind: MessengerAction,
    val activityName: String,
    val targetLabel: String,
)

internal data class ContactDetails(
    val phones: List<ContactPhone>,
    val messaging: List<VerifiedContactAction>,
    val contact: ContactEntry? = null,
)

internal sealed interface ContactDetailsResult {
    data object Loading : ContactDetailsResult
    data class Loaded(val details: ContactDetails) : ContactDetailsResult
    data object Denied : ContactDetailsResult
    data object Missing : ContactDetailsResult
    data object Unavailable : ContactDetailsResult
}

internal sealed interface ContactAction {
    data class View(val contact: ContactEntry) : ContactAction
    data class Dial(val phone: ContactPhone) : ContactAction
    data class Sms(val phone: ContactPhone) : ContactAction
    data class Messenger(val target: VerifiedContactAction) : ContactAction
}

internal data class ContactExpansion(val contactId: Long? = null, val otherNumbers: Boolean = false) {
    fun dims(rowContactId: Long?): Boolean = contactId != null && rowContactId != contactId

    fun toggle(id: Long): ContactExpansion =
        if (contactId == id) ContactExpansion() else ContactExpansion(id)

    fun back(): ContactExpansion =
        if (otherNumbers) copy(otherNumbers = false) else ContactExpansion()
}

internal data class ContactMenu(
    val expansion: ContactExpansion = ContactExpansion(),
    val result: ContactDetailsResult = ContactDetailsResult.Loading,
    val refreshing: Boolean = false,
)

internal fun ContactDetails.contains(action: ContactAction): Boolean = when (action) {
    is ContactAction.View -> action.contact == contact
    is ContactAction.Dial -> action.phone in phones
    is ContactAction.Sms -> action.phone in phones
    is ContactAction.Messenger -> action.target in messaging
}
