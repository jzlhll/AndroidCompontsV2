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
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.CameraAndSelectPhotosPermissionHelper
import com.au.module_imagecompressed.CameraPermissionHelp
import com.au.module_imagecompressed.PickUriWrap
import com.au.module_imagecompressed.TakePhotoActionDialog
import com.au.module_imagecompressed.multiPickUriWrapForResult
import com.au.module_imagecompressed.pickUriWrapForResult
import com.au.module_simplepermission.ICameraFileProviderSupply
import com.au.module_simplepermission.PickerType
import com.au.module_simplepermission.getAudioForResult
import com.au.module_simplepermission.getAudiosForResult
import com.au.module_simplepermission.getContentForResult
import com.au.module_simplepermission.getMultipleContentsForResult
import com.au.module_simplepermission.multiPickerForResult
import com.au.module_simplepermission.pickerForResult
import com.au.module_simplepermission.selectSysDirForResult
import java.io.File

@EntryFrgName(priority = 10)
class NewPhotoPickerFragment : BindingFragment<FragmentPhotoPickerBinding>(), TakePhotoActionDialog.ITakePhotoActionDialogCallback {
    val singleResult = pickUriWrapForResult()
    val multiResult = multiPickUriWrapForResult(3)

    //用于授权存储访问框架（Storage Access Framework, SAF），得到某个目录的长久权限，详情见函数内
    val selectDirResult = selectSysDirForResult()

     //单选图片和视频，通过launchByAll的第一个参数来决定选择的类型
    val origUriPickerResult = pickerForResult()

    //多选图片和视频，通过launchByAll的第一个参数来决定选择的类型
    val origMultiUriPickerResult = multiPickerForResult(9)

    //选择单文件，自行传入start(参数)，参数传入，文件类型的mimeType
    val shortDocResult = getContentForResult()

    //选择多文件，自行传入start(参数)，参数传入，文件类型的mimeType
    val shortDocsResult = getMultipleContentsForResult()

    //选择音频
    val audioResult = getAudioForResult()

    //选择多音频
    val audiosResult = getAudiosForResult()

    val cameraHelper = CameraPermissionHelp(this, object : ICameraFileProviderSupply {
        override fun createFileProvider(): Pair<File, Uri> {
            return createFileProviderMine()
        }
    })

    val cameraAndSelectHelper = CameraAndSelectPhotosPermissionHelper(this, 9, supplier = object : ICameraFileProviderSupply {
        override fun createFileProvider(): Pair<File, Uri> {
            return createFileProviderMine()
        }
    }).also {
        it.multiResult.paramsBuilder.asCopyAndStingy()
    }

    private fun createFileProviderMine(): Pair<File, Uri> {
        val picture = File(Globals.goodCacheDir.path + "/shared")
        picture.mkdirs()
        val file = File(picture, "pic_" + System.currentTimeMillis() + ".jpg")
        val uri = FileProvider.getUriForFile(Globals.app, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return file to uri
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.selectShortAudioBtn.onClick {
            audiosResult.start { results->
                logdNoFile { "allan uri: $results" }
            }
        }
        binding.selectShortPdfBtn.onClick {
            shortDocsResult.start("application/pdf") {

            }
        }

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
            cameraHelper.safeRunTakePicMust(true)
                {mode, uriWrap->
                logdNoFile { "take pic mode $mode $uriWrap" }
                showPic(uriWrap)
            }
        }
        binding.directTakePicDeepBtn.onClick {
            cameraHelper.safeRunTakePicMust(true, "deep")
            {mode, uriWrap->
                logdNoFile { "take pic mode $mode $uriWrap" }
                showPic(uriWrap)
            }
        }
        binding.directTakePicShallowBtn.onClick {
            cameraHelper.safeRunTakePicMust(true, "shallow")
            {mode, uriWrap->
                logdNoFile { "take pic mode $mode $uriWrap" }
                showPic(uriWrap)
            }
        }
        binding.directTakePic2Btn.onClick {
            cameraHelper.safeRunTakePicMust(false)
            {mode, uriWrap->
                logdNoFile { "#take pic mode $mode $uriWrap" }
                showPic(uriWrap)
            }
        }

        binding.singlePic.onClick {
            singleResult.launchOneByOne(PickerType.IMAGE, null) { uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.singleVideo.onClick {
            singleResult.launchOneByOne(PickerType.VIDEO, null) { uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.singlePicAndVideo.onClick {
            singleResult.launchOneByOne(PickerType.IMAGE_AND_VIDEO, null) { uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiPic4.onClick {
            multiResult.setCurrentMaxItems(6)
            multiResult.launchOneByOne(PickerType.IMAGE, null) {uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiVideo3.onClick {
            multiResult.setCurrentMaxItems(3)
            multiResult.launchOneByOne(PickerType.VIDEO, null) {uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
        binding.multiPicAndVideo5.onClick {
            multiResult.setCurrentMaxItems(9)
            multiResult.launchOneByOne(PickerType.IMAGE_AND_VIDEO, null) {uri->
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }

        binding.multiPicV2.onClick {
            multiResult.setCurrentMaxItems(Int.MAX_VALUE)
            multiResult.launchByAll(PickerType.IMAGE, null) {uris->
                for (uri in uris) {
                    logdNoFile { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }
        binding.multiVideoV2.onClick {
            multiResult.setCurrentMaxItems(3)
            multiResult.launchByAll(PickerType.VIDEO, null) {uris->
                for (uri in uris) {
                    logdNoFile { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }
        binding.multiPicAndVideoV2.onClick {
            multiResult.setCurrentMaxItems(9)
            multiResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                for (uri in uris) {
                    logdNoFile { "allan uri: $uri" }
                    showPic(uri)
                }
            }
        }


        binding.multiUri1.onClick {
            origUriPickerResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                logdNoFile{"single picker: file uris ${uris.size}"}
                for (uri in uris) {
                    logdNoFile { "file allan uri: $uri" }
                }
            }
        }

        binding.multiUri2.onClick {
            origMultiUriPickerResult.launchByAll(PickerType.IMAGE_AND_VIDEO, null) { uris->
                logdNoFile{"multi picker: file uris ${uris.size}"}
                for (uri in uris) {
                    logdNoFile { "file allan uri: $uri" }
                }
            }
        }

        binding.multiUri3.onClick {
            origMultiUriPickerResult.launchByAll(PickerType.IMAGE, null) { uris->
                logdNoFile{"multi picker: file uris ${uris.size}"}
                for (uri in uris) {
                    logdNoFile { "file allan uri: $uri" }
                }
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
            6 -> binding.pic6
            7 -> binding.pic7
            8 -> binding.pic8
            9 -> binding.pic9
            else -> null
        }
        currentIndex++
        if (currentIndex == 10) {
            currentIndex = 1
        }
        pic?.picture?.glideSetAny(uriWrap.uriParsedInfo.uri)
        if (uriWrap.isImage) {
            pic?.centerIcon?.gone()
        } else {
            pic?.centerIcon?.visible()
        }
    }

    override fun onClickTakePic() : Boolean{
        return cameraAndSelectHelper.cameraHelper.safeRunTakePicMust(true) { mode, uriWrap ->
            showPic(uriWrap)
        }
    }

    override fun onClickSelectPhoto() {
        cameraAndSelectHelper.launchSelectPhotos {uris->
            for (uri in uris) {
                logdNoFile { "allan uri: $uri" }
                showPic(uri)
            }
        }
    }

    override fun onNothingTakeDialogClosed() {
    }
}