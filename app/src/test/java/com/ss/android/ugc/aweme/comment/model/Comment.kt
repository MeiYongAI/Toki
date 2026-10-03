package com.ss.android.ugc.aweme.comment.model

/** 评论复制反射契约的宿主模型。 */
class Comment {
    /** @return 测试评论标识。无参数。Callers: CommentCopyHook.Contract 的反射验证。 */
    fun getCid(): String = "test-comment"
}
