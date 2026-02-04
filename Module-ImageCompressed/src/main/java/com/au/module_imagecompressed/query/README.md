需要权限：
//Framgent/Activity 全局变量
private val permissionUtil = createMediaPermissionForResult(arrayOf(PermissionMediaType.IMAGE, PermissionMediaType.VIDEO, PermissionMediaType.AUDIO))

//然后在某处调用
permissionUtil.safeRun {
    ...
}