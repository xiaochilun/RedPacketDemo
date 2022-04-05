package com.daofeng.chatroom.mvvm.newredpacket

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.launcher.ARouter
import com.blankj.utilcode.util.SizeUtils
import com.daofeng.baselibrary.BaseApplication
import com.daofeng.baselibrary.base.mvvmbase.BaseKotlinFragment
import com.daofeng.baselibrary.bean.chatroom.MemberInfo
import com.daofeng.baselibrary.bean.chatroom.redpacket.Condition
import com.daofeng.baselibrary.bean.chatroom.redpacket.RspChatRedPacketConfig
import com.daofeng.baselibrary.constant.Constants
import com.daofeng.baselibrary.constant.RoutePath
import com.daofeng.baselibrary.listener.Callback
import com.daofeng.baselibrary.net.bugglyreport.toast
import com.daofeng.baselibrary.provider.ProviderUtil
import com.daofeng.baselibrary.util.LoginUtils
import com.daofeng.baselibrary.util.SharedPreferencesUtils
import com.daofeng.baselibrary.util.TimeFormatUtils
import com.daofeng.baselibrary.witget.HorizontalSpaceItemDecoration
import com.daofeng.chatroom.R
import com.daofeng.chatroom.RoomBeanRepository
import com.daofeng.chatroom.databinding.FragmentChatSendRedPacketBinding
import com.daofeng.chatroom.dialog.CommonSelectListDialog
import com.daofeng.chatroom.utils.RoomGioUtil
import com.othershe.nicedialog.BaseNiceDialog
import com.othershe.nicedialog.NiceDialog
import com.othershe.nicedialog.ViewConvertListener
import com.othershe.nicedialog.ViewHolder
import kotlinx.android.synthetic.main.fragment_chat_send_red_packet.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Double
import java.math.BigDecimal

/**
 * @Description: 发送红包子页面
 * @Author: 张雨民
 * @CreateDate: 2021/11/5 11:25
 */
class ChatSendRedPacketFragment : BaseKotlinFragment<ChatRedPacketVM, FragmentChatSendRedPacketBinding>() {
    override fun layoutId(): Int {
        return R.layout.fragment_chat_send_red_packet
    }

    private var mType = TYPE_LUCK
    private var mDiamond = 0L // 当前用户钻石余额
    private var mCountDownSelectIndex = 0
    private val mChatCountDownList = arrayListOf<Int>()
    private val mSpUtils = SharedPreferencesUtils.getInstance(BaseApplication.application)
    private var mConditionSelectedType = CONDITION_NO
    private var mMaxDiamond : Int = 0 // 被限制最大红包金额, 手气红包:总红包金额, 等额红包:单个红包金额
    private var mHost: MemberInfo? = null

    companion object {
        const val KEY = "RedPacketKey"
        const val TYPE_LUCK = 6     // 手气红包
        const val TYPE_AVERAGE = 7  // 等额红包

        const val CONDITION_NO = 1                      // 无门槛
        const val CONDITION_SEND_TARGET_MESSAGE = 2     // 发送指定公屏消息
        const val CONDITION_FOLLOW_HOST = 3             // 关注主持用户
        const val CONDITION_FOLLOW = 4                  // 关注我
        const val CONDITION_SHARE = 5                   // 分享房间

        //用户钻石 数量
        var initGem = 0L
    }

    override fun initView(savedInstanceState: Bundle?) {
        super.initView(savedInstanceState)
        EventBus.getDefault().register(this)
        mType = arguments?.getInt(KEY) ?: TYPE_LUCK
        mHost = RoomBeanRepository.host_md.value

        tvRedPacketAmount.text = if (mType == TYPE_LUCK) {
            "红包金额"
        } else {
            "单个红包金额"
        }
        rbAllRoomNotify.setOnClickListener {
            rbAllRoomNotify.isSelected = !rbAllRoomNotify.isSelected
        }

        initSendEvent()
        initConfigData()
        // 红包金额
        initRedPacketDiamond()
        // 红包个数
        initRedPacketCount()
        // 充值
        tvRecharge.setOnClickListener {
            val from = "聊天室发红包充值弹出"
            ProviderUtil.getAppProvider().showDiamondDialog(activity, 0, from
            ) { _, _, rechargeDiamond ->
                EventBus.getDefault().post(
                    ChatDiamondRechargeEvent(
                        mDiamond.plus(rechargeDiamond)
                    )
                )
            }
            // 1602 点击页面上的充值弹出充值钻石窗口
            RoomGioUtil.reportRoomRedPackage(1602, if(mType == TYPE_LUCK) 1 else 2, 1, initGem,
                mChatCountDownList[mCountDownSelectIndex].toString(),
                etRedPacketCount.text.toString().trim(),
                etRedPacketAmount.text.toString().trim())
        }
    }

    private fun initRedPacketCount() {
        etRedPacketCount.setText("1")
        etRedPacketCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty() || mType == TYPE_LUCK) return
                viewModel.chatRedPacketConfigData.value?.rate?.let {
                    var count = s.toString().toInt()
                    if (count == 0) {
                        etRedPacketCount.setText("1")
                        count = 1
                    }
                    val diamondAmountStr = etRedPacketAmount.text.toString().trim()
                    val diamondAmount =
                        if (diamondAmountStr.isEmpty()) 0 else diamondAmountStr.toInt()
                    calculateServiceFee(it, (diamondAmount * count).toString())
                }
            }

        })
    }

    private fun initRedPacketDiamond() {
        etRedPacketAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                var amount = s.toString() // 红包总金额
                viewModel.chatRedPacketConfigData.value?.rate?.let {
                    if (amount.isEmpty()) return
                    // 判断是否超过最大值, 如果超过最大值则自动重置为最大值
                    val diamondAmountStr = etRedPacketAmount.text.toString().trim()
                    val diamondAmount =
                        if (diamondAmountStr.isEmpty()) 0 else diamondAmountStr.toInt()
                    if (mMaxDiamond != 0 && diamondAmount > mMaxDiamond) {
                        toast("红包金额不可超过${mMaxDiamond}蜂蜜")
                        etRedPacketAmount.setText(mMaxDiamond.toString())
                        return
                    }
                    if (mType == TYPE_AVERAGE) {
                        val redCountStr = etRedPacketCount.text.toString().trim()
                        val redCount = if (redCountStr.isEmpty()) 0 else redCountStr.toInt()
                        amount = (diamondAmount * redCount).toString()
                    }
                    calculateServiceFee(it, amount)
                }

                // 检查红包总金额是否达到全服推送设置的金额
                val roomPushNeedDiamond =
                    viewModel.chatRedPacketConfigData.value?.room_push_need_diamond
                roomPushNeedDiamond?.let {
                    rbAllRoomNotify.isSelected = amount.toInt() >= it
                }
            }
        })
    }

    private fun initConfigData() {
        viewModel.chatRedPacketConfigData.observe(this, {
            mDiamond = it?.user_diamond ?: 0
            desAllRoomNotify.text = "发送红包金额大于${it.room_push_need_diamond}蜂蜜可触发全服通知"
            renderChatCountDown(it)
            renderChatConditions(it.condition_list)
            calculateServiceFee(it?.rate, etRedPacketAmount.text.toString())
            tvBalance.text = mDiamond.toString()
            mMaxDiamond = if (mType == TYPE_LUCK)
                it.lucky_diamond_limit.diamond_max
            else
                it.equal_diamond_limit.diamond_max

            initGem = it.user_diamond
            // 1600 打开发红包页面
            if (mType == TYPE_LUCK) {
                RoomGioUtil.reportRoomRedPackage(1600, 1, 1, it.user_diamond, mChatCountDownList[mCountDownSelectIndex].toString())
            }
        })
        viewModel.fetchRedPacketConfig()
    }

    private fun showRealNameDialog() {
        NiceDialog.init()
            .setLayoutId(R.layout.dialog_exit_room)
            .setConvertListener(object : ViewConvertListener() {
                override fun convertView(holder: ViewHolder, dialog: BaseNiceDialog) {
                    val title = holder.convertView.findViewById<TextView>(R.id.tv_title)
                    title.text = "未实名认证用户，不可发送红包"
                    val cancel = holder.convertView.findViewById<TextView>(R.id.btn_dialog_cancle)
                    cancel.text = "取消"
                    val ok = holder.convertView.findViewById<TextView>(R.id.btn_dialog_ok)
                    ok.text = "实名认证"
                    cancel.setOnClickListener {
                        dialog.dismiss()
                    }
                    ok.setOnClickListener {
                        dialog.dismiss()
                        // 跳转实名认证页面
                        ARouter.getInstance().build(RoutePath.AUTH_NAME_ACTIVITY).navigation()
                    }
                }

            })
            .setHeight(146)
            .setMargin(30)
            .setOutCancel(true)
            .show(childFragmentManager)
    }

    private fun initSendEvent() {
        btSendRedPacket.setOnClickListener {
            // 检查是否实名
            val needRealName = viewModel.chatRedPacketConfigData.value?.need_realname
            val realNameStatus = viewModel.chatRedPacketConfigData.value?.user_realname_status
            if (needRealName == 1 && realNameStatus == 0) {
                showRealNameDialog()
                return@setOnClickListener
            }
            if (matchRules()) {
                val conditionParam =
                    when (mConditionSelectedType) {
                        CONDITION_SEND_TARGET_MESSAGE -> {
                            etTargetMessage.text.toString().trim()
                        }
                        CONDITION_FOLLOW_HOST -> {
                            mHost?.uid
                        }
                        CONDITION_FOLLOW -> {
                            LoginUtils.getUid()
                        }
                        else -> {
                            null
                        }
                    }

                viewModel.pushRedPacket(
                    mType,
                    mConditionSelectedType,
                    etRedPacketAmount.text.toString().trim(),
                    etRedPacketCount.text.toString().trim(),
                    mChatCountDownList[mCountDownSelectIndex].toString(),
                    conditionParam,
                    if (rbAllRoomNotify.isSelected) 1 else 0
                )
                //1603 点击发红包
                RoomGioUtil.reportRoomRedPackage(1603, if(mType == TYPE_LUCK) 1 else 2, mConditionSelectedType, initGem,
                    mChatCountDownList[mCountDownSelectIndex].toString(),
                    etRedPacketCount.text.toString().trim(),
                    etRedPacketAmount.text.toString().trim())
            }
        }

        viewModel.orderId.observe(this, {
            if (it.isNotEmpty()) {
                //1604 点击发红包
                RoomGioUtil.reportRoomRedPackage(1604, if(mType == TYPE_LUCK) 1 else 2, mConditionSelectedType, initGem,
                    mChatCountDownList[mCountDownSelectIndex].toString(),
                    etRedPacketCount.text.toString().trim(),
                    etRedPacketAmount.text.toString().trim())

                toast("红包发送成功")
                EventBus.getDefault().post("dismiss_chat_red_packet_dialog")
            }
        })
    }

    /**
     * 抢红包条件
     */
    private fun renderChatConditions(conditions: List<Condition>?) {
        conditions ?: return

        val conditionList = conditions.map {
            val description = if (it.id == CONDITION_FOLLOW_HOST) {
                mHost?.nickname ?: ""
            } else {
                it.info
            }
            RedPacketConditionData(it.id, it.condition, description, false)
        }
        // 默认选择无门槛
        conditionList[0].isSelected = true

        val decoration = HorizontalSpaceItemDecoration(SizeUtils.dp2px(8f), conditionList.size)
        listCondition.addItemDecoration(decoration)
        listCondition.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val adapter = ChatRedPacketConditionListAdapter()
        listCondition.adapter = adapter

        adapter.setData(conditionList)
        adapter.setOnItemClickListener(object :
            ChatRedPacketConditionListAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                mConditionSelectedType = conditionList[position].type
                etTargetMessage.visibility =
                    if (mConditionSelectedType == CONDITION_SEND_TARGET_MESSAGE) View.VISIBLE else View.GONE
            }
        })
    }

    /**
     * 红包倒计时
     */
    private fun renderChatCountDown(it: RspChatRedPacketConfig?) {
        var selectedCountDown = mSpUtils.get(Constants.CHAT_RED_PACKET_COUNT_DOWN, 0)
        mChatCountDownList.clear()
        it?.countdown_second?.let {
            mChatCountDownList.addAll(it)
        }
        val assembledCountDownList = arrayListOf<String>()
        mChatCountDownList.forEach {
            if (it <= 0) {
                assembledCountDownList.add("无")
            } else {
                val secondOfMins = TimeFormatUtils.formatSeconds2HMS(it)
                assembledCountDownList.add(secondOfMins)
            }
        }
        if (!mChatCountDownList.contains(selectedCountDown)) {
            selectedCountDown = mChatCountDownList[0]
            mSpUtils.put(Constants.CHAT_RED_PACKET_COUNT_DOWN, selectedCountDown)
        }
        mCountDownSelectIndex = mChatCountDownList.indexOf(selectedCountDown)
        tvRedPacketCountDown.text = assembledCountDownList[mCountDownSelectIndex]

        rectRedPacketCountDown.setOnClickListener {
            val callback = Callback<Int> {
                mCountDownSelectIndex = it
                tvRedPacketCountDown.text = assembledCountDownList[mCountDownSelectIndex]
                mSpUtils.put(
                    Constants.CHAT_RED_PACKET_COUNT_DOWN,
                    mChatCountDownList[mCountDownSelectIndex]
                )
            }

            val dialog = CommonSelectListDialog(
                ChatSendRedPacketDialog.HEIGHT,
                "红包倒计时",
                assembledCountDownList,
                mCountDownSelectIndex,
                callback
            )
            dialog.show(activity?.supportFragmentManager)
        }
    }

    private fun matchRules(): Boolean {
        val configData = viewModel.chatRedPacketConfigData.value
        if (configData == null) {
            toast("数据异常，请稍后再试")
            return false
        }

        // 红包金额
        val diamondAmountStr = etRedPacketAmount.text.toString().trim()
        val diamondAmount = if (diamondAmountStr.isEmpty()) 0 else diamondAmountStr.toInt()
        // 红包数量
        val redCountStr = etRedPacketCount.text.toString().trim()
        val redCount = if (redCountStr.isEmpty()) 0 else redCountStr.toInt()

        if (mType == TYPE_LUCK) {
            // 手气红包
            if (diamondAmount < configData.lucky_diamond_limit.diamond_min) {
                toast("红包金额不可低于${configData.lucky_diamond_limit.diamond_min}蜂蜜")
                return false
            }
            if (diamondAmount > configData.lucky_diamond_limit.diamond_max) {
                toast("红包金额不可高于${configData.lucky_diamond_limit.diamond_max}蜂蜜")
                return false
            }
            if (redCount < configData.people_min || redCount > configData.people_max) {
                toast("红包个数最少${configData.people_min}个,最多${configData.people_max}个")
                return false
            }
        } else {
            // 等额红包
            if (diamondAmount < configData.equal_diamond_limit.diamond_min) {
                toast("单个红包金额不可低于${configData.equal_diamond_limit.diamond_min}蜂蜜")
                return false
            }
            if (diamondAmount > configData.equal_diamond_limit.diamond_max) {
                toast("单个红包金额不可高于${configData.equal_diamond_limit.diamond_max}蜂蜜")
                return false
            }
            if (redCount < configData.people_min || redCount > configData.people_max) {
                toast("红包个数最少${configData.people_min}个,最多${configData.people_max}个")
                return false
            }
            if (diamondAmount * redCount > configData.equal_diamond_limit.diamond_total) {
                toast("总红包金额不可高于${configData.equal_diamond_limit.diamond_total}蜂蜜")
                return false
            }
        }

        // 红包数量需要小于蜂蜜数量
        if (redCount > diamondAmount) {
            toast("红包数量需小于等于红包金额")
            return false
        }

        if (mConditionSelectedType == CONDITION_SEND_TARGET_MESSAGE) {
            // 指定公屏消息内容风控
            val targetMessage = etTargetMessage.text.toString().trim()
            if (targetMessage.isEmpty()) {
                toast("指定公屏消息内容不能为空")
                return false
            }
        } else if (mConditionSelectedType == CONDITION_FOLLOW_HOST) {
            if (mHost?.uid.isNullOrEmpty()) {
                toast("当前麦上无主持用户，不可选择关注主持用户条件")
                return false
            }
        }

        // 余额不足充值
        val diff = tvPayMoney.text.toString().toDouble() - mDiamond.toDouble()
        if (diff > 0) {
            toast("蜂蜜不足，请充值")
            val from = "聊天室发手气红包不够自动弹出"
            ProviderUtil.getAppProvider().showDiamondDialog(activity, diff.toLong(), from
            ) { _, _, rechargeDiamond ->
                EventBus.getDefault().post(
                    ChatDiamondRechargeEvent(
                        mDiamond.plus(
                            rechargeDiamond
                        )
                    )
                )
            }
            return false
        }

        return true
    }

    private fun calculateServiceFee(envelopeRatio: kotlin.Double?, amount: String) {
        if (amount.isEmpty() || envelopeRatio == null) return
        val sendMoney = BigDecimal.valueOf(Double.valueOf(amount)).setScale(0, BigDecimal.ROUND_CEILING)
        val redEnvelope: BigDecimal = BigDecimal.valueOf(envelopeRatio)
        val ratePercent = BigDecimal.valueOf(envelopeRatio)
            .multiply(BigDecimal.valueOf(100))
        val serviceFee = redEnvelope.multiply(sendMoney).setScale(0, BigDecimal.ROUND_CEILING)

        val payMoney = sendMoney.plus(serviceFee)
        tvPayMoney.text = "$payMoney"

        tvRedPacketRate.text = String.format(
            "红包需额外支付%s蜂蜜服务费(费率%s)", serviceFee.toString(),
            "$ratePercent%"
        )
    }

    data class ChatDiamondRechargeEvent(val diamond: Long)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onChatDiamondRechargeEvent(event: ChatDiamondRechargeEvent) {
        // 刷新当前用户钻石余额
        mDiamond = event.diamond
        tvBalance.text = mDiamond.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
    }
}
