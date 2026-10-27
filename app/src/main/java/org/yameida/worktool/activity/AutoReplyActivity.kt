package org.yameida.worktool.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_auto_reply.*
import org.yameida.worktool.R
import org.yameida.worktool.autoreply.*

/**
 * 篝火新增：自动回复设置页（AI 回复 + 关键词规则回复）。
 */
class AutoReplyActivity : AppCompatActivity() {

    companion object {
        fun enterActivity(context: Context) {
            context.startActivity(Intent(context, AutoReplyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_auto_reply)
        iv_back_left.setOnClickListener { finish() }
        fill(AutoReplyStore.load())
        bt_save.setOnClickListener { save() }
        bt_test.setOnClickListener { testApi() }
    }

    private fun fill(c: AutoReplyConfig) {
        sw_ai.isChecked = c.aiEnabled
        sw_rules.isChecked = c.rulesEnabled
        et_groups.setText(c.groupsText)
        sw_private.isChecked = c.includePrivate
        sw_require_at.isChecked = c.requireAt
        et_keywords.setText(c.triggerKeywordsText)
        sw_at_sender.isChecked = c.atSender
        et_api_url.setText(c.apiUrl)
        et_api_key.setText(c.apiKey)
        et_model.setText(c.model)
        et_prompt.setText(c.systemPrompt)
        et_per_minute.setText(c.perMinuteLimit.toString())
        et_per_day.setText(c.perDayLimit.toString())
        et_rules.setText(c.rulesText)
        refreshStatus()
    }

    private fun read(): AutoReplyConfig = AutoReplyConfig(
        aiEnabled = sw_ai.isChecked,
        rulesEnabled = sw_rules.isChecked,
        groupsText = et_groups.text.toString(),
        includePrivate = sw_private.isChecked,
        requireAt = sw_require_at.isChecked,
        triggerKeywordsText = et_keywords.text.toString(),
        atSender = sw_at_sender.isChecked,
        apiUrl = et_api_url.text.toString().trim(),
        apiKey = et_api_key.text.toString().trim(),
        model = et_model.text.toString().trim(),
        systemPrompt = et_prompt.text.toString(),
        perMinuteLimit = et_per_minute.text.toString().toIntOrNull() ?: 5,
        perDayLimit = et_per_day.text.toString().toIntOrNull() ?: 200,
        rulesText = et_rules.text.toString()
    )

    private fun save() {
        val c = read()
        AutoReplyStore.save(c)
        val warnings = arrayListOf<String>()
        val invalid = Rules.parse(c.rulesText).invalidLines
        if (invalid.isNotEmpty()) warnings.add("规则第 ${invalid.joinToString("、")} 行格式不对，已忽略")
        if ((c.aiEnabled || c.rulesEnabled) && Rules.lines(c.groupsText).isEmpty() && !c.includePrivate) {
            warnings.add("还没指定生效的群聊，现在不会回复任何消息")
        }
        if (c.aiEnabled && (c.apiUrl.isBlank() || c.model.isBlank())) warnings.add("AI 接口地址或模型名没填")
        ToastUtils.showLong(if (warnings.isEmpty()) "已保存" else "已保存。注意：" + warnings.joinToString("；"))
        refreshStatus()
    }

    private fun refreshStatus() {
        val c = AutoReplyStore.load()
        val per = if (c.perDayLimit > 0) c.perDayLimit.toString() else "不限"
        tv_status.text = "今天已自动回复 ${AutoReplyStore.limiter.todayCount()} 条（每天上限 $per）。重启 App 不会清零。"
    }

    private fun testApi() {
        val c = read()
        if (c.apiUrl.isBlank() || c.model.isBlank()) {
            ToastUtils.showShort("请先填接口地址和模型名")
            return
        }
        bt_test.isEnabled = false
        Thread {
            val msg = try {
                "接口正常，模型回复：" + ChatClient(UrlConnectionTransport()).chat(c, "请用一句话回复：你好").take(60)
            } catch (e: ChatException) {
                "测试失败：" + (e.message ?: "未知错误")
            }
            runOnUiThread {
                bt_test.isEnabled = true
                ToastUtils.showLong(msg)
            }
        }.start()
    }
}
