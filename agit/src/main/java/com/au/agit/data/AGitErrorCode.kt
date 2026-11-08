/*
 * AGit 用户错误编号的唯一来源。
 * 编号使用明确的固定值，不根据 ordinal、资源 ID 或运行时计数拼接 E 和数字。
 * 通用错误从 E0001 开始，其余类别按百位分组，从 E0100、E0200 等起点依次分配。
 * 新错误在所属组内追加未使用的编号；已有编号不重排、不复用，同一原因的不同文案共享编号。
 * 字符串资源只保留可翻译正文，最终格式统一为“正文 [E0001]”，且只在末尾追加一次。
 * 中英文使用同一编号；成功、空态、普通指引和主动取消不编号。
 * 内部业务错误标识保持不变，不能用用户编号替换 AUTHENTICATION 等业务分支判断。
 */
package com.au.agit.data

import androidx.annotation.StringRes

enum class AGitErrorCode(
    val code: String,
    @StringRes val messageResId: Int,
    @StringRes vararg aliases: Int,
) {
    // E0001–E0099：通用错误与 Git 引擎运行环境。
    ENGINE_UNAVAILABLE("E0001", com.au.agit.R.string.engine_unavailable),
    UNEXPECTED("E0002", com.au.agit.R.string.unexpected_failed),
    NETWORK_OR_STORAGE("E0003", com.au.agit.R.string.network_storage_failed),
    OPERATION_INCOMPLETE("E0004", com.au.agit.R.string.operation_incomplete),

    // E0100–E0199：Token 凭据、分组及提交作者。
    INVALID_TOKEN("E0100", com.au.agit.R.string.invalid_token),
    AUTHENTICATION("E0101", com.au.agit.R.string.auth_failed),
    PERMISSION("E0102", com.au.agit.R.string.permission_failed),
    INVALID_AUTHOR("E0103", com.au.agit.R.string.invalid_author),
    CONFIGURE_AUTHOR("E0104", com.au.agit.R.string.configure_author),
    MISSING_GROUP("E0105", com.au.agit.R.string.missing_group),
    DECRYPT_FAILED("E0106", com.au.agit.R.string.decrypt_failed),
    TOKEN_DRAFT_MISSING("E0107", com.au.agit.R.string.token_reenter),

    // E0200–E0299：仓库、仓库地址及路径访问限制。
    NOT_FOUND("E0200", com.au.agit.R.string.repo_not_found),
    MISSING_PROJECT("E0201", com.au.agit.R.string.missing_project),
    INVALID_PROJECT_ID("E0202", com.au.agit.R.string.invalid_project_id),
    INVALID_URL("E0203", com.au.agit.R.string.invalid_url),
    HTTPS_ONLY("E0204", com.au.agit.R.string.https_only),
    INVALID_REPO_PATH("E0205", com.au.agit.R.string.invalid_repo_path),
    INVALID_REPO_NAME("E0206", com.au.agit.R.string.invalid_repo_name),
    PROJECT_EXISTS("E0207", com.au.agit.R.string.project_exists),
    REPO_MISSING("E0208", com.au.agit.R.string.repo_missing),
    ORIGIN_CHANGED("E0209", com.au.agit.R.string.origin_changed),
    FILTERS_UNSUPPORTED("E0210", com.au.agit.R.string.filters_unsupported),
    FORBIDDEN_PATH("E0211", com.au.agit.R.string.forbidden_path),
    SYMLINK_UNSUPPORTED("E0212", com.au.agit.R.string.symlink_unsupported),
    PATH_ESCAPE("E0213", com.au.agit.R.string.path_escape),

    // E0300–E0399：本地文件、外部应用及编辑回写。
    MISSING_DIRECTORY("E0300", com.au.agit.R.string.missing_directory),
    UNREADABLE_DIRECTORY("E0301", com.au.agit.R.string.unreadable_directory),
    MISSING_FILE("E0302", com.au.agit.R.string.missing_file),
    REGULAR_FILES_ONLY("E0303", com.au.agit.R.string.regular_files_only),
    COPY_CHANGED("E0304", com.au.agit.R.string.copy_changed),
    FILE_WRITING("E0305", com.au.agit.R.string.file_writing),
    EDIT_NOT_FINISHED("E0306", com.au.agit.R.string.edit_not_finished),
    NO_HANDLER("E0307", com.au.agit.R.string.no_handler),
    OPEN_FAILED("E0308", com.au.agit.R.string.open_failed),

    // E0400–E0499：Git 状态、提交、更新、临时分支与推送。
    REMOTE_AHEAD("E0400", com.au.agit.R.string.remote_ahead),
    REPO_UNSAFE("E0401", com.au.agit.R.string.repo_unsafe, com.au.agit.R.string.repo_operation_pending),
    DETACHED_HEAD("E0402", com.au.agit.R.string.detached_head),
    UNBORN_HEAD("E0403", com.au.agit.R.string.unborn_head),
    MISSING_UPSTREAM("E0404", com.au.agit.R.string.missing_upstream, com.au.agit.R.string.no_upstream),
    REMOTE_DELETED("E0405", com.au.agit.R.string.remote_deleted),
    MISSING_TRACKING("E0406", com.au.agit.R.string.missing_tracking),
    UNRELATED_HISTORY("E0407", com.au.agit.R.string.unrelated_history),
    EMPTY_COMMIT("E0408", com.au.agit.R.string.empty_commit),
    CONFLICTS_UNSUPPORTED("E0409", com.au.agit.R.string.conflicts_unsupported),
    SELECTION_CHANGED("E0410", com.au.agit.R.string.selection_changed),
    CONTENT_CHANGED("E0411", com.au.agit.R.string.content_changed),
    DIRTY_UPDATE("E0412", com.au.agit.R.string.dirty_update),
    DIVERGED("E0413", com.au.agit.R.string.diverged),
    CHANGED_UPDATE("E0414", com.au.agit.R.string.changed_update),
    FF_FAILED("E0415", com.au.agit.R.string.ff_failed),
    HEAD_CHANGED("E0416", com.au.agit.R.string.head_changed),
    TEMPORARY_COLLISION("E0417", com.au.agit.R.string.temporary_collision),
    BRANCH_SWITCH_FAILED("E0418", com.au.agit.R.string.branch_switch_failed),
    PUSH_UNCERTAIN("E0419", com.au.agit.R.string.push_uncertain, com.au.agit.R.string.push_unknown),
    PUSH_REJECTED("E0420", com.au.agit.R.string.push_rejected),

    // E0500–E0599：日志文件读取、导出及图片和视频预览。
    LOG_MISSING("E0500", com.au.agit.R.string.log_missing),
    LOG_LINE_TOO_LONG("E0501", com.au.agit.R.string.log_line_too_long),
    LOG_DELETE("E0502", com.au.agit.R.string.log_delete_failed),
    LOG_SELECTION("E0503", com.au.agit.R.string.log_select_limit),
    LOG_EXPORT("E0504", com.au.agit.R.string.log_export_failed),
    MEDIA_ERROR("E0505", com.au.agit.R.string.media_error);

    private val messageResIds = intArrayOf(messageResId, *aliases)

    fun format(message: String): String {
        val suffix = " [$code]"
        return if (message.endsWith(suffix)) message else message + suffix
    }

    companion object {
        private val byMessageResource: Map<Int, AGitErrorCode> = buildMap {
            AGitErrorCode.entries.forEach { error -> error.messageResIds.forEach { put(it, error) } }
        }

        fun fromResource(@StringRes id: Int): AGitErrorCode? = byMessageResource[id]
    }
}
