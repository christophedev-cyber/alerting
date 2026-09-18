package com.sophiaengineering.alerting

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * Adaptateur du sélecteur de pays :
 * - vue repliée (sélection) : uniquement l'indicatif, ex. "+33"
 * - liste déroulante : drapeau + nom + indicatif, ex. "🇫🇷  France  (+33)"
 */
class CountryAdapter(
    context: Context,
    private val items: List<Country>
) : ArrayAdapter<Country>(context, android.R.layout.simple_spinner_item, items) {

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        view.text = "+${items[position].dialCode}"
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.text = items[position].display()
        return view
    }
}
