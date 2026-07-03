package com.orangecat.shizuku.shellterminal

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orangecat.shizuku.shellterminal.database.CommandTemplate
import com.orangecat.shizuku.shellterminal.databinding.ActivityShellTerminalBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.InputStream
import java.io.OutputStream

class ShellTerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShellTerminalBinding
    private lateinit var templatesAdapter: TemplatesAdapter

    private var activeProcess: Process? = null
    private var processJob: Job? = null
    private var isRunningProcess = false

    private val templateManager = TemplateCommandManager.instance
    
    // History states
    private var historyList: List<String> = emptyList()
    private var historyIndex = -1
    private var inputDraft = ""

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateUIState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateUIState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShellTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Setup Templates RecyclerView
        templatesAdapter = TemplatesAdapter(
            templates = emptyList(),
            onItemClick = { template ->
                binding.commandInput.setText(template.command)
                binding.commandInput.setSelection(template.command.length)
                // Reset history index when a new command is chosen
                historyIndex = -1
            },
            onItemLongClick = { template ->
                if (template.isCustom) {
                    showCustomTemplateOptionsDialog(template)
                }
            }
        )
        binding.templatesRecyclerView.adapter = templatesAdapter

        // Setup Listeners
        binding.btnRun.setOnClickListener {
            if (isRunningProcess) {
                stopActiveProcess()
            } else {
                runCommand()
            }
        }

        binding.btnSaveTemplate.setOnClickListener {
            showSaveTemplateDialog()
        }

        binding.btnHistoryUp.setOnClickListener {
            navigateHistoryUp()
        }

        binding.btnHistoryDown.setOnClickListener {
            navigateHistoryDown()
        }

        // Listen for user typing to track draft text
        binding.commandInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (historyIndex == -1) {
                    inputDraft = s?.toString() ?: ""
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initialize Shizuku Listeners
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        // Load templates and history
        loadTemplates()
        loadHistory()
        updateUIState()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        stopActiveProcess()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "Clear Console")?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        menu?.add(0, 2, 0, "Clear History")?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                binding.terminalOutput.text = ""
                true
            }
            2 -> {
                lifecycleScope.launch {
                    templateManager.clearHistory(this@ShellTerminalActivity)
                    loadHistory()
                    Toast.makeText(this@ShellTerminalActivity, "History cleared", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUIState() {
        val isShizukuRunning = Shizuku.pingBinder()
        val isPermissionGranted = isShizukuRunning && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        if (!isShizukuRunning) {
            binding.commandInput.isEnabled = false
            binding.btnRun.isEnabled = false
            binding.btnSaveTemplate.isEnabled = false
            binding.btnHistoryUp.isEnabled = false
            binding.btnHistoryDown.isEnabled = false
            appendOutputSystem("[System Error] Shizuku is not running. Please start Shizuku and grant permission to use the terminal.\n")
        } else if (!isPermissionGranted) {
            binding.commandInput.isEnabled = false
            binding.btnRun.isEnabled = false
            binding.btnSaveTemplate.isEnabled = false
            binding.btnHistoryUp.isEnabled = false
            binding.btnHistoryDown.isEnabled = false
            appendOutputSystem("[System Error] Shizuku permission not granted. Please authorize this app first.\n")
            // Try requesting permission dynamically
            Shizuku.requestPermission(1001)
        } else {
            binding.commandInput.isEnabled = true
            binding.btnRun.isEnabled = true
            binding.btnSaveTemplate.isEnabled = true
            binding.btnHistoryUp.isEnabled = true
            binding.btnHistoryDown.isEnabled = true
        }
    }

    private fun loadTemplates() {
        lifecycleScope.launch {
            val list = templateManager.getAllTemplates(this@ShellTerminalActivity)
            templatesAdapter.updateData(list)
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            historyList = templateManager.getHistory(this@ShellTerminalActivity)
            historyIndex = -1
        }
    }

    private fun runCommand() {
        val command = binding.commandInput.text.toString().trim()
        if (command.isEmpty()) {
            Toast.makeText(this, getString(R.string.shell_terminal_empty_command), Toast.LENGTH_SHORT).show()
            return
        }

        binding.commandInput.setText("")
        appendOutput("\n$ $command\n")

        // Save command to history
        lifecycleScope.launch {
            templateManager.addToHistory(this@ShellTerminalActivity, command)
            loadHistory()
        }

        // Set running state
        isRunningProcess = true
        binding.btnRun.setImageResource(R.drawable.ic_stop)

        processJob = lifecycleScope.launch {
            var process: Process? = null
            try {
                // Execute via shell -c so we support piping, grep, environment, etc.
                process = withContext(Dispatchers.IO) {
                    val method = Shizuku::class.java.getDeclaredMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.isAccessible = true
                    method.invoke(null, arrayOf("/system/bin/sh", "-c", command), null, null) as Process
                }
                activeProcess = process

                // Stream stdout
                val stdoutJob = launch(Dispatchers.IO) {
                    try {
                        val buffer = ByteArray(2048)
                        val inputStream = process.inputStream
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            val text = String(buffer, 0, bytesRead)
                            withContext(Dispatchers.Main) {
                                appendOutput(text)
                            }
                        }
                    } catch (e: Exception) {
                        // Stream closed
                    }
                }

                // Stream stderr
                val stderrJob = launch(Dispatchers.IO) {
                    try {
                        val buffer = ByteArray(2048)
                        val errorStream = process.errorStream
                        var bytesRead: Int
                        while (errorStream.read(buffer).also { bytesRead = it } != -1) {
                            val text = String(buffer, 0, bytesRead)
                            withContext(Dispatchers.Main) {
                                appendOutput(text) // Can format as red if needed
                            }
                        }
                    } catch (e: Exception) {
                        // Stream closed
                    }
                }

                // Wait for exit code
                val exitCode = withContext(Dispatchers.IO) {
                    process.waitFor()
                }
                stdoutJob.join()
                stderrJob.join()

                withContext(Dispatchers.Main) {
                    appendOutputSystem("\n[Process finished with exit code: $exitCode]\n")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutputSystem("\n[Error executing command: ${e.message}]\n")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRunningProcess = false
                    binding.btnRun.setImageResource(R.drawable.ic_play)
                    activeProcess = null
                }
            }
        }
    }

    private fun stopActiveProcess() {
        activeProcess?.destroy()
        processJob?.cancel()
        isRunningProcess = false
        binding.btnRun.setImageResource(R.drawable.ic_play)
        appendOutputSystem("\n[Process terminated by user]\n")
        activeProcess = null
    }

    private fun appendOutput(text: String) {
        binding.terminalOutput.append(text)
        binding.terminalScrollView.post {
            binding.terminalScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun appendOutputSystem(text: String) {
        // Simple indicator for system logs
        binding.terminalOutput.append(text)
        binding.terminalScrollView.post {
            binding.terminalScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // --- Command History Navigation ---

    private fun navigateHistoryUp() {
        if (historyList.isEmpty()) return

        if (historyIndex == -1) {
            // Store current input drafting
            inputDraft = binding.commandInput.text.toString()
        }

        if (historyIndex < historyList.size - 1) {
            historyIndex++
            binding.commandInput.setText(historyList[historyIndex])
            binding.commandInput.setSelection(historyList[historyIndex].length)
        }
    }

    private fun navigateHistoryDown() {
        if (historyIndex > 0) {
            historyIndex--
            binding.commandInput.setText(historyList[historyIndex])
            binding.commandInput.setSelection(historyList[historyIndex].length)
        } else if (historyIndex == 0) {
            historyIndex = -1
            binding.commandInput.setText(inputDraft)
            binding.commandInput.setSelection(inputDraft.length)
        }
    }

    // --- Template Dialogs ---

    private fun showSaveTemplateDialog() {
        val currentCommand = binding.commandInput.text.toString().trim()
        if (currentCommand.isEmpty()) {
            Toast.makeText(this, "Type a command to save it as template", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = getString(R.string.shell_terminal_save_dialog_hint)
        input.setSingleLine(true)

        AlertDialog.Builder(this)
            .setTitle(R.string.shell_terminal_save_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.shell_terminal_dialog_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    templateManager.saveCustomTemplate(this@ShellTerminalActivity, name, currentCommand)
                    loadTemplates()
                    Toast.makeText(this@ShellTerminalActivity, "Template saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.shell_terminal_dialog_cancel, null)
            .show()
    }

    private fun showCustomTemplateOptionsDialog(template: CommandTemplate) {
        val options = arrayOf(getString(R.string.shell_terminal_dialog_edit), getString(R.string.shell_terminal_dialog_delete))
        AlertDialog.Builder(this)
            .setTitle(template.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditTemplateDialog(template)
                    1 -> showDeleteConfirmDialog(template)
                }
            }
            .show()
    }

    private fun showEditTemplateDialog(template: CommandTemplate) {
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 16, 32, 16)

        val nameInput = EditText(this)
        nameInput.hint = "Template Name"
        nameInput.setText(template.name)
        layout.addView(nameInput)

        val commandInput = EditText(this)
        commandInput.hint = "Command String"
        commandInput.setText(template.command)
        layout.addView(commandInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.shell_terminal_edit_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.shell_terminal_dialog_save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val cmd = commandInput.text.toString().trim()
                if (name.isEmpty() || cmd.isEmpty()) {
                    Toast.makeText(this, "Name and command cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val updated = template.copy(name = name, command = cmd)
                    templateManager.updateCustomTemplate(this@ShellTerminalActivity, updated)
                    loadTemplates()
                    Toast.makeText(this@ShellTerminalActivity, "Template updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.shell_terminal_dialog_cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(template: CommandTemplate) {
        AlertDialog.Builder(this)
            .setTitle("Delete Template?")
            .setMessage("Are you sure you want to delete template '${template.name}'?")
            .setPositiveButton(R.string.shell_terminal_dialog_delete) { _, _ ->
                lifecycleScope.launch {
                    templateManager.deleteCustomTemplate(this@ShellTerminalActivity, template)
                    loadTemplates()
                    Toast.makeText(this@ShellTerminalActivity, "Template deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.shell_terminal_dialog_cancel, null)
            .show()
    }
}
