package com.backup.telegram.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.backup.telegram.databinding.ActivitySettingsBinding
import com.backup.telegram.util.SecurePrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SecurePrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(com.backup.telegram.R.string.settings_title)

        prefs = SecurePrefsManager(this)
        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        binding.botTokenInput.setText(prefs.botToken ?: "")
        binding.chatIdInput.setText(prefs.chatId ?: "")
        binding.wifiOnlySwitch.isChecked = prefs.wifiOnly
        binding.compressSwitch.isChecked = prefs.compressImages
        binding.qualitySlider.value = prefs.jpegQuality.toFloat()
        binding.qualityLabel.text = getString(com.backup.telegram.R.string.jpeg_quality_label, prefs.jpegQuality)
        binding.compressSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.qualitySection.alpha = if (isChecked) 1f else 0.4f
            binding.qualitySlider.isEnabled = isChecked
        }
        binding.qualitySection.alpha = if (prefs.compressImages) 1f else 0.4f
        binding.qualitySlider.isEnabled = prefs.compressImages
    }

    private fun setupListeners() {
        binding.qualitySlider.addOnChangeListener { _, value, _ ->
            binding.qualityLabel.text = getString(
                com.backup.telegram.R.string.jpeg_quality_label, value.toInt()
            )
        }

        binding.saveButton.setOnClickListener {
            val token = binding.botTokenInput.text.toString().trim()
            val chatId = binding.chatIdInput.text.toString().trim()

            if (token.isBlank() || chatId.isBlank()) {
                Toast.makeText(this,
                    getString(com.backup.telegram.R.string.error_fields_required),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.botToken = token
            prefs.chatId = chatId
            prefs.wifiOnly = binding.wifiOnlySwitch.isChecked
            prefs.compressImages = binding.compressSwitch.isChecked
            prefs.jpegQuality = binding.qualitySlider.value.toInt()

            Toast.makeText(this,
                getString(com.backup.telegram.R.string.settings_saved),
                Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.helpButton.setOnClickListener {
            showBotHelp()
        }
    }

    private fun showBotHelp() {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(com.backup.telegram.R.string.how_to_get_token))
            .setMessage(getString(com.backup.telegram.R.string.bot_setup_instructions))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
