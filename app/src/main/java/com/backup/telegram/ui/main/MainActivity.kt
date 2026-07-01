package com.backup.telegram.ui.main

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.backup.telegram.R
import com.backup.telegram.data.local.entity.UploadStatus
import com.backup.telegram.databinding.ActivityMainBinding
import com.backup.telegram.ui.settings.SettingsActivity
import com.backup.telegram.util.SecurePrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var prefs: SecurePrefsManager
    private lateinit var adapter: MediaFilesAdapter

    // تتبع الفلتر الحالي
    private var currentFilter: UploadStatus? = null // null = الكل

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startBackupIfReady()
        } else {
            binding.backupSwitch.isChecked = false
            binding.statusText.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        prefs = SecurePrefsManager(this)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        updateToggleState()
    }

    private fun setupRecyclerView() {
        adapter = MediaFilesAdapter()
        binding.filesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.filesRecyclerView.adapter = adapter
    }

    private fun setupObservers() {
        // إحصائيات السرعة
        viewModel.uploadedCount.observe(this) {
            binding.uploadedCountText.text = it.toString()
        }
        viewModel.remainingCount.observe(this) {
            binding.remainingCountText.text = it.toString()
        }
        viewModel.failedCount.observe(this) {
            binding.failedCountText.text = it.toString()
        }

        // قائمة الملفات مع الفلتر
        viewModel.allFiles.observe(this) { files ->
            val filtered = when (currentFilter) {
                null -> files
                else -> files.filter { it.status == currentFilter }
            }
            adapter.submitList(filtered)
            updateEmptyState(filtered.isEmpty())
        }
    }

    private fun setupListeners() {
        binding.backupSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!prefs.isConfigured()) {
                    binding.backupSwitch.isChecked = false
                    binding.statusText.text = getString(R.string.configure_bot_first)
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return@setOnCheckedChangeListener
                }
                requestPermissionsAndStart()
            } else {
                viewModel.toggleBackup(false)
                binding.statusText.text = getString(R.string.backup_disabled)
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ─── فلاتر الـ Chips ───────────────────────────────────────
        binding.chipAll.setOnClickListener {
            setFilter(null)
            uncheckOthers(binding.chipAll.id)
        }
        binding.chipPending.setOnClickListener {
            setFilter(UploadStatus.PENDING)
            uncheckOthers(binding.chipPending.id)
        }
        binding.chipUploaded.setOnClickListener {
            setFilter(UploadStatus.UPLOADED)
            uncheckOthers(binding.chipUploaded.id)
        }
        binding.chipFailed.setOnClickListener {
            setFilter(UploadStatus.FAILED)
            uncheckOthers(binding.chipFailed.id)
        }
    }

    private fun setFilter(status: UploadStatus?) {
        currentFilter = status
        // أعد تطبيق الفلتر على القائمة الحالية
        val files = viewModel.allFiles.value ?: return
        val filtered = if (status == null) files else files.filter { it.status == status }
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun uncheckOthers(checkedId: Int) {
        if (checkedId != binding.chipAll.id)     binding.chipAll.isChecked = false
        if (checkedId != binding.chipPending.id) binding.chipPending.isChecked = false
        if (checkedId != binding.chipUploaded.id) binding.chipUploaded.isChecked = false
        if (checkedId != binding.chipFailed.id)  binding.chipFailed.isChecked = false
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.filesRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun requestPermissionsAndStart() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.POST_NOTIFICATIONS
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startBackupIfReady() {
        viewModel.toggleBackup(true)
        binding.statusText.text = getString(R.string.backup_running)
    }

    private fun updateToggleState() {
        val enabled = viewModel.isBackupEnabled()
        binding.backupSwitch.isChecked = enabled
        binding.statusText.text = getString(
            if (enabled) R.string.backup_running else R.string.backup_disabled
        )
    }

    override fun onResume() {
        super.onResume()
        // تحديث حالة الـ toggle لو تغيرت الإعدادات في شاشة Settings
        updateToggleState()
    }
}
