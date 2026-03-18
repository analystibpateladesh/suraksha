package com.suraksha.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

data class Contact(val name: String, val phone: String)

class ContactsManager(private val context: Context) {

    companion object {
        private const val PREFS = "suraksha_prefs"
        private const val KEY = "contacts"
    }

    fun getContacts(): List<Contact> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val obj = arr.getJSONObject(it)
            Contact(obj.getString("name"), obj.getString("phone"))
        }
    }

    fun saveContact(name: String, phone: String) {
        val contacts = getContacts().toMutableList()
        contacts.add(Contact(name, phone))
        val arr = JSONArray()
        contacts.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("phone", it.phone)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun removeContact(index: Int) {
        val contacts = getContacts().toMutableList()
        if (index in contacts.indices) {
            contacts.removeAt(index)
            val arr = JSONArray()
            contacts.forEach {
                arr.put(JSONObject().apply {
                    put("name", it.name)
                    put("phone", it.phone)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        }
    }

    // Render contacts list into a LinearLayout
    fun loadContactsIntoView(container: LinearLayout) {
        container.removeAllViews()
        val contacts = getContacts()
        if (contacts.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No contacts added yet"
                setTextColor(android.graphics.Color.parseColor("#555555"))
                textSize = 14f
                setPadding(0, 24, 0, 0)
            }
            container.addView(empty)
            return
        }
        contacts.forEachIndexed { i, contact ->
            val row = LayoutInflater.from(context)
                .inflate(R.layout.item_contact, container, false)
            row.findViewById<TextView>(R.id.contactName).text = contact.name
            row.findViewById<TextView>(R.id.contactPhone).text = contact.phone
            row.findViewById<View>(R.id.removeBtn).setOnClickListener {
                removeContact(i)
                loadContactsIntoView(container)
            }
            container.addView(row)
        }
    }
}