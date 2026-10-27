package org.yameida.worktool.autoreply

import com.blankj.utilcode.util.LogUtils
import org.yameida.worktool.Constant
import org.yameida.worktool.model.WeworkMessageBean
import org.yameida.worktool.service.WeworkOperationImpl

/**
 * 篝火新增：聊天页读到消息列表后调用。只看最后一条；是别人发的文字消息才交给自动回复引擎。
 * 任何异常都吞掉并记日志，不能影响上游的主循环。日志里不写消息内容和密钥。
 */
object AutoReplyHook {

    fun onMessageList(
        roomType: Int,
        titleList: List<String>,
        messageList: List<WeworkMessageBean.SubMessageBean>
    ) {
        try {
            val last = messageList.lastOrNull() ?: return
            val title = titleList.firstOrNull() ?: return
            if (last.sender != 0) {
                AutoReplyStore.engine.roomMovedOn(title)
                return
            }
            if (last.textType != WeworkMessageBean.TEXT_TYPE_PLAIN) return
            val config = AutoReplyStore.load()
            if (!config.aiEnabled && !config.rulesEnabled) return
            val text = last.itemMessageList.filter { it.feature == 2 }.joinToString("\n") { it.text }
            val isGroup = roomType == WeworkMessageBean.ROOM_TYPE_EXTERNAL_GROUP ||
                roomType == WeworkMessageBean.ROOM_TYPE_INTERNAL_GROUP
            val reply = AutoReplyStore.engine.handle(
                IncomingMessage(title, isGroup, last.nameList.firstOrNull(), text, Constant.myName)
            )
            if (reply == null) {
                val err = AutoReplyStore.engine.lastError
                if (err.isNotEmpty()) LogUtils.w("自动回复未发送：$err")
                return
            }
            LogUtils.d("自动回复(${reply.source}) -> $title")
            val ok = WeworkOperationImpl.sendLocalReply(title, reply.text, reply.atName)
            if (!ok) LogUtils.w("自动回复发送失败：$title")
        } catch (e: Exception) {
            LogUtils.w("自动回复异常：${e.javaClass.simpleName}")
        }
    }
}
