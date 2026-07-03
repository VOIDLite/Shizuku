package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeInAppTerminalBinding
import moe.shizuku.manager.model.ServiceStatus
import com.orangecat.shizuku.shellterminal.ShellTerminalActivity
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class InAppTerminalViewHolder(private val binding: HomeInAppTerminalBinding, private val root: View) :
    BaseViewHolder<ServiceStatus>(root),
    View.OnClickListener {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeInAppTerminalBinding.inflate(inflater, outer.root, true)
            InAppTerminalViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener(this)
    }

    private inline val summary get() = binding.text2

    override fun onBind() {
        val context = itemView.context
        if (!data.isRunning) {
            root.isEnabled = false
            summary.text =
                context.getString(R.string.home_status_service_not_running, context.getString(R.string.app_name))
        } else {
            root.isEnabled = true
            summary.text = context.getString(R.string.shell_terminal_description)
        }
    }

    override fun onClick(v: View) {
        v.context.startActivity(Intent(v.context, ShellTerminalActivity::class.java))
    }
}
