package com.au.module_imagecompressed

import androidx.fragment.app.Fragment
import com.au.module_simplepermission.ICameraFileProviderSupply
import com.au.module_simplepermission.PickerType

/**
 * 为了webView的拍照，选择图片，当TakePhotoActionDialog不论是否经历过选择，拍照，或者权限失败等原因，
 * 都必须有回调，因此这里添加一个统一回调
 */
class CameraAndSelectPhotosPermissionHelper(val f: Fragment,
                                            maxNum:Int = 9,
                                            var pickerType : PickerType = PickerType.IMAGE,
                                            supplier: ICameraFileProviderSupply) {

    var takePhotoCallback: TakePhotoActionDialog.ITakePhotoActionDialogCallback? = null

    //可以在早期修改
    val multiResult = f.multiPickUriWrapForResult(maxNum)

    val cameraHelper = CameraPermissionHelp(f, supplier)

    /**
     * 调用本函数，将会触发弹出界面。然后Dialog的回调会触发Fragment的onClickTakePic/onClickSelectPhoto
     *
     */
    fun showTakeActionDialog(maxNum:Int, pickerType: PickerType) {
        this.pickerType = pickerType
        multiResult.setCurrentMaxItems(maxNum)
        TakePhotoActionDialog.pop(f, takePhotoCallback)
    }

    fun launchSelectPhotos(callback: (Array<PickUriWrap>) -> Unit) {
        multiResult.launchByAll(pickerType, null, callback)
    }
}