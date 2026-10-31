package net.jooti.bareshell

import android.Manifest
import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

internal sealed interface ContactsResult {
    data class Loaded(val contacts: List<ContactEntry>, val historyUnavailable: Boolean = false) : ContactsResult
    data object Denied : ContactsResult
    data object Unavailable : ContactsResult
}

internal fun contactLookupUri(key: String): Uri =
    ContactsContract.Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(key).build()

internal fun contactViewUri(contact: ContactEntry): Uri =
    if (contact.lookupKey.isNotBlank()) contactLookupUri(contact.lookupKey)
    else ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)

internal class ContactRepository(private val context: Context, private val history: ContactHistoryStore) {
    fun loadDetails(contactId: Long): ContactDetailsResult {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactDetailsResult.Denied
        }
        try {
            val resolver = context.contentResolver
            val contactCursor = resolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED, ContactsContract.Contacts.LOOKUP_KEY), null, null, null,
            ) ?: throw IllegalStateException("Contacts provider returned no contact cursor")
            val contact = contactCursor.use {
                if (!it.moveToFirst()) return ContactDetailsResult.Missing
                ContactEntry(it.getLong(0), it.getString(1).orEmpty(), it.getInt(2) != 0, it.getString(3).orEmpty())
            }
            val phones = mutableListOf<ContactPhone>()
            val messaging = mutableListOf<VerifiedContactAction>()
            val columns = arrayOf(
                ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1, ContactsContract.Data.DATA2, ContactsContract.Data.DATA3,
                ContactsContract.Data.IS_PRIMARY, ContactsContract.Data.IS_SUPER_PRIMARY,
            )
            val mimeTypes = listOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) +
                MessengerAction.entries.map { it.mimeType }
            val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN " +
                mimeTypes.joinToString(",", "(", ")") { "?" }
            val cursor = resolver.query(
                ContactsContract.Data.CONTENT_URI, columns, selection,
                (listOf(contactId.toString()) + mimeTypes).toTypedArray(), "${ContactsContract.Data._ID} ASC",
            ) ?: throw IllegalStateException("Contacts provider returned no details cursor")
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val mime = it.getString(1)
                    if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                        val number = it.getString(2)?.trim()
                        if (number.isNullOrEmpty()) {
                            Log.w("BareShellContacts", "Skipping an empty contact phone field")
                            continue
                        }
                        phones += ContactPhone(
                            id, number,
                            ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources, it.getInt(3), it.getString(4),
                            ).toString(),
                            it.getInt(5) != 0, it.getInt(6) != 0,
                        )
                    } else {
                        val kind = MessengerAction.entries.first { entry -> entry.mimeType == mime }
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id), mime)
                            .setPackage(kind.packageName)
                        val handler = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                            ?.activityInfo
                        if (handler != null && handler.packageName == kind.packageName &&
                            handler.exported && handler.enabled && handler.applicationInfo.enabled &&
                            (handler.permission == null ||
                                context.checkSelfPermission(handler.permission) == PackageManager.PERMISSION_GRANTED)
                        ) {
                            messaging += VerifiedContactAction(
                                id, kind, handler.name, it.getString(4)?.takeIf(String::isNotBlank).orEmpty(),
                            )
                        }
                    }
                }
            }
            return ContactDetailsResult.Loaded(ContactDetails(preferredPhones(phones), messaging, contact))
        } catch (error: SecurityException) {
            Log.w("BareShellContacts", "Contact detail access was denied", error)
            return if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                ContactDetailsResult.Unavailable
            } else ContactDetailsResult.Denied
        } catch (error: SQLiteException) {
            Log.e("BareShellContacts", "Contact detail query failed", error)
            return ContactDetailsResult.Unavailable
        } catch (error: IllegalStateException) {
            Log.e("BareShellContacts", "Contact detail provider failed", error)
            return ContactDetailsResult.Unavailable
        }
    }

    fun load(favoritesOnly: Boolean = false, starredFirst: Boolean = false, limit: Int = 10): ContactsResult {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactsResult.Denied
        }
        try {
            val contacts = mutableListOf<ContactEntry>()
            val uri = ContactsContract.Contacts.CONTENT_URI
            val columns = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.LOOKUP_KEY,
            )
            val cursor = context.contentResolver.query(uri, columns, null, null, null)
                ?: throw IllegalStateException("Contacts provider returned no cursor")
            cursor.use {
                while (it.moveToNext()) {
                    contacts += ContactEntry(
                        it.getLong(0),
                        it.getString(1)?.takeIf(String::isNotBlank)
                            ?: context.getString(R.string.unnamed_contact),
                        it.getInt(2) != 0,
                        it.getString(3).orEmpty(),
                    )
                }
            }
            if (!favoritesOnly) return ContactsResult.Loaded(orderContacts(contacts, starredFirst))
            val scores = try {
                history.reconcile(contacts, ::resolveLookup)
            } catch (error: SQLiteException) {
                Log.e("BareShellContacts", "Contact history could not be loaded", error)
                return ContactsResult.Unavailable
            }
            return ContactsResult.Loaded(favoriteContacts(contacts, limit = limit, scores = scores))
        } catch (error: SecurityException) {
            Log.w("BareShellContacts", "Contacts access was denied or revoked", error)
            return if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                ContactsResult.Unavailable
            } else {
                ContactsResult.Denied
            }
        } catch (error: SQLiteException) {
            Log.e("BareShellContacts", "Contacts provider query failed", error)
            return ContactsResult.Unavailable
        } catch (error: IllegalStateException) {
            Log.e("BareShellContacts", "Contacts provider is unavailable", error)
            return ContactsResult.Unavailable
        }
    }

    private fun resolveLookup(key: String): String? {
        val cursor = context.contentResolver.query(
            contactLookupUri(key), arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null,
        ) ?: throw IllegalStateException("Contacts provider returned no lookup cursor")
        return cursor.use {
            if (!it.moveToFirst()) null else it.getString(0)?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("Contacts provider returned an invalid lookup identity")
        }
    }
}
