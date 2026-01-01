package com.allan.androidlearning.activities

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.allan.androidlearning.databinding.FragmentDatastoreBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_cached.AppDataStore
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_android.log.logt
import com.au.module_androidui.toast.ToastUtil.toastOnTop
import com.au.module_cached.delegate.AppDataStoreIntCache
import com.au.module_cached.delegate.AppDataStoreStringCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @author au
 * @date :2023/11/7 15:33
 * @description:
 */
@EntryFrgName
class DataStoreFragment : BindingFragment<FragmentDatastoreBinding>() {
    private var delegateStr by AppDataStoreStringCache("infoString", "")
    private var delegateInt by AppDataStoreIntCache("infoInteger", 0)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding
        viewBinding.clearBtn.onClick {
            lifecycleScope.launch(Dispatchers.Default) {
                logt { "clear...." }
                AppDataStore.clear()
            }
        }

        viewBinding.saveBtn.onClick {
            AppDataStore.save("info", "randomStr " + System.currentTimeMillis())
        }

        viewBinding.readBtn.onClick {
            lifecycleScope.launch {
                val data = AppDataStore.read<String>("info", "default_info")
                toastOnTop("data: $data")
            }
        }

        viewBinding.containsBtn.onClick {
            lifecycleScope.launch {
                val isContains = AppDataStore.containsKey<String>("info")
                toastOnTop("isContains: $isContains")
            }
        }

        viewBinding.removeKeyBtn.onClick {
            lifecycleScope.launch {
                val r = AppDataStore.removeSuspend<String>("info")
                toastOnTop("removed: $r")
            }
        }
        viewBinding.updateIntBtn.onClick {
            delegateInt += 1
            toastOnTop("updateInt: $delegateInt")
        }
        viewBinding.updateStringBtn.onClick {
            delegateStr += "a,"
            toastOnTop("updateStr: $delegateStr")
        }
    }
}