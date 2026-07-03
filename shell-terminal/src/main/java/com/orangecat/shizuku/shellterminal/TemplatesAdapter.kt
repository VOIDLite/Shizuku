package com.orangecat.shizuku.shellterminal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.orangecat.shizuku.shellterminal.database.CommandTemplate
import com.orangecat.shizuku.shellterminal.databinding.ItemCommandTemplateBinding

class TemplatesAdapter(
    private var templates: List<CommandTemplate>,
    private val onItemClick: (CommandTemplate) -> Unit,
    private val onItemLongClick: (CommandTemplate) -> Unit
) : RecyclerView.Adapter<TemplatesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCommandTemplateBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommandTemplateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val template = templates[position]
        holder.binding.tvTemplateName.text = template.name
        
        if (template.isCustom) {
            holder.binding.tvTemplateLabel.text = "[Custom]"
            holder.binding.tvTemplateLabel.setTextColor(0xFFE91E63.toInt()) // Pinkish custom color
        } else {
            holder.binding.tvTemplateLabel.text = "[Preset]"
            holder.binding.tvTemplateLabel.setTextColor(0xFF00BCD4.toInt()) // Teal preset color
        }

        holder.itemView.setOnClickListener {
            onItemClick(template)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(template)
            true
        }
    }

    override fun getItemCount(): Int = templates.size

    fun updateData(newTemplates: List<CommandTemplate>) {
        this.templates = newTemplates
        notifyDataSetChanged()
    }
}
