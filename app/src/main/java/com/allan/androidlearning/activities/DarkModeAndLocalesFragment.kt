package com.allan.androidlearning.activities

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.allan.androidlearning.R
import com.allan.androidlearning.databinding.FragmentDarkModeSettingBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.AppNightMode
import com.au.module_android.appNightMode
import com.au.module_android.click.onClick
import com.au.module_android.openApplicationLanguageSettings
import com.au.module_android.setAppNightMode
import com.au.module_android.supportedAppLocales
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo

@EntryFrgName(customName = "GenerateSetting")
class DarkModeAndLocalesFragment : BindingFragment<FragmentDarkModeSettingBinding>() {
    override fun toolbarInfo(): YourToolbarInfo = YourToolbarInfo.Defaults(getString(R.string.settings))

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.languageBtn.onClick {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!requireContext().openApplicationLanguageSettings()) {
                    Toast.makeText(requireContext(), R.string.error_open_language_settings, Toast.LENGTH_LONG).show()
                }
            } else {
                showLanguagePicker()
            }
        }
        binding.nightModeBtn.onClick {
            val modes = AppNightMode.entries
            val labels = arrayOf(getString(R.string.follow_system), getString(R.string.light), getString(R.string.dark))
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dark_mode)
                .setSingleChoiceItems(labels, modes.indexOf(appNightMode(requireContext()))) { dialog, which ->
                    dialog.dismiss()
                    setAppNightMode(requireContext(), modes[which])
                    updateSummaries()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        updateSummaries()
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        val locale = AppCompatDelegate.getApplicationLocales()[0]
        binding.languageTv.text = locale?.getDisplayName(locale) ?: getString(R.string.follow_system)
        binding.nightModeTv.setText(when (appNightMode(requireContext())) {
            AppNightMode.FollowSystem -> R.string.follow_system
            AppNightMode.Light -> R.string.light
            AppNightMode.Dark -> R.string.dark
        })
    }

    private fun showLanguagePicker() {
        val locales = supportedAppLocales(requireContext(), R.xml.locales_config)
        val labels = listOf(getString(R.string.follow_system)) + locales.map { it.getDisplayName(it) }
        val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
        val selected = locales.indexOfFirst { it.toLanguageTag() == currentTag } + 1
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                val tags = if (which == 0) "" else locales[which - 1].toLanguageTag()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                updateSummaries()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
