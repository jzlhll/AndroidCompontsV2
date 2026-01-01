package com.allan.androidlearning.activities

import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import com.allan.androidlearning.BuildConfig
import com.allan.androidlearning.databinding.FragmentPhotoPickerBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_android.log.logd
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.CameraAndSelectPhotosPermissionHelper
import com.au.module_imagecompressed.CameraPermissionHelp
import com.au.module_imagecompressed.PickUriWrap
import com.au.module_imagecompressed.TakePhotoActionDialog
import com.au.module_imagecompressed.compatMultiPhotoPickerForResult
import com.au.module_imagecompressed.compatMultiUriPickerForResult
import com.au.module_imagecompressed.photoPickerForResult
import com.au.module_imagecompressed.uriPickerForResult
import com.au.module_simplepermission.BaseCameraPermissionHelp
import com.au.module_simplepermission.PickerType
import com.au.module_simplepermission.selectSysDirForResult
import java.io.File

@EntryFrgName
class NewPhotoPickerFragment : BindingFragment<FragmentPhotoPickerBinding>(), TakePhotoActionDialog.ITakePhotoActionDialogCallback {
    val singleResult = photoPickerForResult()

    val multiResult = compatMultiPhotoPickerForResult(3)

    val mutiUriResult = compatMultiUriPickerForResult(8)
    val uriResult = uriPickerForResult()

    val selectDirResult = selectSysDirForResult()

    val cameraHelper = CameraPermissionHelp(this, object : BaseCameraPermissionHelp.Supplier {
        override fun createFileProvider(): Pair<File, Uri> {
            return createFileProviderMine()
        }
    })

    val cameraAndSelectHelper = CameraAndSelectPhotosPermissionHelper(this, supplier = object : BaseCameraPermissionHelp.Supplier {
        override fun createFileProvider(): Pair<File, Uri> {
            return createFileProviderMine()
        }
    })

    private fun createFileProviderMine(): Pair<File, Uri> {
        val picture = File(Globals.goodCacheDir.path + "/shared")
        picture.mkdirs()
        val file = File(picture, "pic_" + System.currentTimeMillis() + ".jpg")
        val uri = FileProvider.getUriForFile(Globals.app, "${BuildConfig.APPLICATION_ID}.provider", file)
        return file to uri
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.takeActionDialog1Btn.onClick {
            cameraAndSelectHelper.showTakeActionDialog(1, PickerType.IMAGE)
        }
        binding.takeActionDialog3Btn.onClick {
            cameraAndSelectHelper.showTakeActionDialog(3, PickerType.VIDEO)
        }
        binding.takeActionDialog5Btn.onClick {
            cameraAndSelectHelper.showTakeActionDialog(5, PickerType.IMAGE_AND_VIDEO)
        }

        binding.directTakePicBtn.onClick {
            cameraHelper.safeRunTakePicMust(requireContext())
                {mode, uriWrap->
                logd { "take pic mode $mode" }
                showPic(uriWrap)
            }
        }

        binding.singlePic.onClick {
            singleResult.launchOneByOne(PickerType.IMAGE, null) { uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.singleVideo.onClick {
            singleResult.launchOneByOne(PickerType.VIDEO, null) { uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.singlePicAndVideo.onClick {
            singleResult.launchOneByOne(PickerType.IMAGE_AND_VIDEO, null) { uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiPic4.onClick {
            multiResult.setCurrentMaxItems(6)
            multiResult.launchOneByOne(PickerType.IMAGE, null) {uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiVideo3.onClick {
            multiResult.setCurrentMaxItems(3)
            multiResult.launchOneByOne(PickerType.VIDEO, null) {uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiPicAndVideo5.onClick {
            multiResult.setCurrentMaxItems(9)
            multiResult.launchOneByOne(PickerType.IMAGE_AND_VIDEO, null) {uri->
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }

        binding.multiPicV2.onClick {
            multiResult.setCurrentMaxItems(Int.MAX_VALUE)
            multiResult.launchByAll(PickerType.IMAGE, null) {uris->
                for (uri in uris) {
                    logd { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }
        binding.multiVideoV2.onClick {
            multiResult.setCurrentMaxItems(3)
            multiResult.launchByAll(PickerType.VIDEO, null) {uris->
                for (uri in uris) {
                    logd { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }
        binding.multiPicAndVideoV2.onClick {
            multiResult.setCurrentMaxItems(9)
            multiResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                for (uri in uris) {
                    logd { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }


        binding.multiUri1.onClick {
            uriResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                logd { "file allan uri: $uris" }
            }
        }

        binding.multiUri2.onClick {
            mutiUriResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                logd { "file allan uri: $uris" }
            }
        }
        binding.selectDirBtn.onClick {
            selectDirResult.start(null) {

            }
        }
    }

    var currentIndex = 1
    @Synchronized
    private fun showPic(uriWrap: PickUriWrap?) {
        uriWrap ?: return
        val pic = when(currentIndex) {
            1 -> binding.pic1
            2 -> binding.pic2
            3 -> binding.pic3
            4 -> binding.pic4
            5 -> binding.pic5
            else -> null
        }
        currentIndex++
        if (currentIndex == 6) {
            currentIndex = 1
        }
        pic?.glideSetAny(uriWrap.uriParsedInfo.uri)
    }

    override fun onClickTakePic() : Boolean{
        return cameraAndSelectHelper.cameraHelper.safeRunTakePicMust(requireContext()) { mode, uriWrap ->
            showPic(uriWrap)
        }
    }

    override fun onClickSelectPhoto() {
        cameraAndSelectHelper.launchSelectPhotos {uris->
            for (uri in uris) {
                logd { "allan uri: $uri" }
                showPic(uri)
            }
        }
    }

    override fun onNothingTakeDialogClosed() {
    }
}