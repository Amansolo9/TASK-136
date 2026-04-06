package com.eaglepoint.task136.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.shared.viewmodel.InvoiceDraft

class InvoiceListAdapter(
    private val onInvoiceClick: (String) -> Unit = {},
) : ListAdapter<InvoiceDraft, InvoiceListAdapter.ViewHolder>(InvoiceDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view, onInvoiceClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View, private val onClick: (String) -> Unit) : RecyclerView.ViewHolder(view) {
        private val text1: TextView = view.findViewById(android.R.id.text1)
        private val text2: TextView = view.findViewById(android.R.id.text2)

        fun bind(invoice: InvoiceDraft) {
            text1.text = invoice.id
            text2.text = "Total: ${"$"}${"%.2f".format(invoice.total)}"
            itemView.setOnClickListener { onClick(invoice.id) }
        }
    }

    private object InvoiceDiff : DiffUtil.ItemCallback<InvoiceDraft>() {
        override fun areItemsTheSame(oldItem: InvoiceDraft, newItem: InvoiceDraft) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: InvoiceDraft, newItem: InvoiceDraft) = oldItem == newItem
    }
}
