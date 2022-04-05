package com.daofeng.chatroom.mvvm.newredpacket.snatch

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.alibaba.android.arouter.launcher.ARouter
import com.blankj.utilcode.util.SizeUtils
import com.daofeng.baselibrary.DFImage
import com.daofeng.baselibrary.base.mvvmbase.BaseVMNiceDialog
import com.daofeng.baselibrary.bean.chatroom.redpacket.RspChatRedPacketCheck
import com.daofeng.baselibrary.constant.RoutePath
import com.daofeng.baselibrary.listener.Callback
import com.daofeng.baselibrary.net.bugglyreport.toast
import com.daofeng.baselibrary.util.TimeFormatUtils
import com.daofeng.chatroom.R
import com.daofeng.chatroom.databinding.DialogChatSnatchRedPacketBinding
import com.daofeng.chatroom.dialog.Rotate3DAnimation
import com.daofeng.chatroom.mvvm.newredpacket.*
import com.daofeng.chatroom.mvvm.newredpacket.result.ChatRedPacketResultDialog
import com.daofeng.chatroom.utils.RoomGioUtil
import com.othershe.nicedialog.BaseNiceDialog
import com.othershe.nicedialog.NiceDialog
import com.othershe.nicedialog.ViewConvertListener
import com.othershe.nicedialog.ViewHolder
import kotlinx.android.synthetic.main.dialog_chat_snatch_red_packet.*
import org.greenrobot.eventbus.EventBus

/**
 * @Description: 聊天室抢红包弹窗
 * @Author: 张雨民
 * @CreateDate: 2021/11/11 11:16
 */
class ChatSnatchRedPacketDialog : BaseVMNiceDialog<ChatSnatchRedPacketVM, DialogChatSnatchRedPacketBinding>() {
    override fun intLayoutId(): Int {
        return R.layout.dialog_chat_snatch_red_packet
    }

    init {
        setAnimStyle(R.style.CommonCenterDialogStyle)
        setHeight(400)
        setOutCancel(false)
    }

    companion object {
        const val BEAN = "chat_red_packet_check_bean"
        const val COUNT_DOWN = "count_down"
        const val MSG = 1
        const val DELAY = 1000L
        const val MATCHED = 1
    }

//    private val mHandler = object : Handler() {
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            var currCountDown = msg.arg1
//            tvBigTop.text = TimeFormatUtils.getSecondOfMins(currCountDown.toLong())
//
//            currCountDown--
//            if (currCountDown <= 0) return
//            val obtainMessage = obtainMessage(MSG)
//            obtainMessage.arg1 = currCountDown
//            sendMessageDelayed(obtainMessage, DELAY)
//        }
//    }

    private lateinit var mCheckBean: RspChatRedPacketCheck
    private var m3DAnimation: Rotate3DAnimation? = null

    override fun initView(savedInstanceState: Bundle?) {
        super.initView(savedInstanceState)
        newChatRedPacketToasted = true
        mCheckBean = arguments?.getSerializable(BEAN) as RspChatRedPacketCheck
        renderStatus()
        btTip.setOnClickListener {
            val dialog = ChatRoomRedPackgeRuleDialog()
            dialog.show(activity?.supportFragmentManager)
        }
        snatchClose.setOnClickListener {
            dismiss()
            newChatRedPacketToasted = false
            // 当前用户没领红包, 故无需尝试自动弹下一个红包
        }
        DFImage.getInstance().displayAvatar(avatarChatSnatchRedPacket, mCheckBean.avatar)
        nameChatSnatchRedPacket.text = "${mCheckBean.nickname}的红包"
        btSnatch.setOnClickListener {
            // btSnatch 3D翻转动画开始
            btSnatch.startAnimation(m3DAnimation)
            pickRedPacket(mCheckBean.order_id)
        }
        init3DAnimation()

        currCountDown.observe(this) {
            if (mCheckBean.condition_data?.condition_status == MATCHED) {
                tvBigTop.text = it
            }
        }
    }

    private fun renderStatus() {
        if (mCheckBean.condition_data?.condition_status == MATCHED) {
            // 达到条件
            val currCountDown = arguments?.getInt(COUNT_DOWN) ?: 0
            if (currCountDown > 0) {
                // 正在倒计时状态下文案渲染
                when (mCheckBean.condition_data?.condition_id) {
                    CHAT_CONDITION_SEND_TARGET_MESSAGE -> {
                        tipBottom.text = "已发送公屏消息, 倒计时结束开抢"
                    }
                    CHAT_CONDITION_FOLLOW, CHAT_CONDITION_FOLLOW_HOST -> {
                        tipBottom.text = "已关注${mCheckBean.condition_data?.condition_desc}, 倒计时结束开抢"
                    }
                    CHAT_CONDITION_SHARE -> {
                        tipBottom.text = "已分享聊天室, 倒计时结束开抢"
                    }
                    else -> {
                        tipBottom.text = ""
                    }
                }
                tvLittleBottom.text = "后开抢"

                // 倒计时
                countDownProgress.start(currCountDown) {
                    // 倒计时结束
                    refreshSnatchView(true)
                }
//                val obtainMessage = mHandler.obtainMessage(MSG)
//                obtainMessage.arg1 = currCountDown
//                mHandler.sendMessage(obtainMessage)
            } else {
                // 显示"抢"
                refreshSnatchView(false)
            }

            btAction.setOnClickListener(null)
        } else {
            // 未达到条件时文案渲染
            when (mCheckBean.condition_data?.condition_id) {
                CHAT_CONDITION_SEND_TARGET_MESSAGE -> {
                    tvBigTop.text = "发消息"
                    tipBottom.text = "发送公屏消息: ${mCheckBean.condition_data?.condition_desc}"
                }
                CHAT_CONDITION_FOLLOW, CHAT_CONDITION_FOLLOW_HOST -> {
                    tvBigTop.text = "关注"
                    tipBottom.text = "关注${mCheckBean.condition_data?.condition_desc}即可瓜分该红包"
                }
                CHAT_CONDITION_SHARE -> {
                    tvBigTop.text = "分享"
                    tipBottom.text = "分享聊天室即可参与抢红包"
                }
                else -> {
                    // 无门槛红包没有未达到条件的情况
                }
            }
            tvLittleBottom.text = "抢红包"

            btAction.setOnClickListener {
                val followCallback = Callback<Any?> {
                    mCheckBean.condition_data?.condition_status = MATCHED
                    renderStatus()
                }
                // 执行任务
                when (mCheckBean.condition_data?.condition_id) {
                    CHAT_CONDITION_SEND_TARGET_MESSAGE -> {
                        dismiss()
                        // 唤起软键盘 自动填充文字
                        val shibboleth = mCheckBean.condition_data?.condition_desc
                        EventBus.getDefault().post(
                            ChatRedPacketEvent(
                                ChatRedPacketEvent.CHAT_SHOW_COMMENT_DIALOG,
                                shibboleth
                            )
                        )
                        newChatRedPacketToasted = false
                    }
                    CHAT_CONDITION_FOLLOW, CHAT_CONDITION_FOLLOW_HOST -> {
                        // 关注用户
                        val uid = mCheckBean.condition_data?.condition_param
                        if (uid != null) {
                            viewModel.follow(uid, followCallback)
                        }
                    }
                    CHAT_CONDITION_SHARE -> {
                        viewModel.saveShareStatus(mCheckBean.order_id) {
                            dismiss()
                            // 唤起分享面板 分享房间
                            EventBus.getDefault()
                                .post(ChatRedPacketEvent(ChatRedPacketEvent.CHAT_SHOW_SHARE_DIALOG))
                            newChatRedPacketToasted = false
                        }
                    }
                }
            }
        }
    }

    private fun showRealNameDialog() {
        NiceDialog.init()
            .setLayoutId(R.layout.dialog_exit_room)
            .setConvertListener(object : ViewConvertListener() {
                override fun convertView(holder: ViewHolder, dialog: BaseNiceDialog) {
                    val title = holder.convertView.findViewById<TextView>(R.id.tv_title)
                    title.text = "未实名认证用户，不可抢红包"
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

    private fun pickRedPacket(orderId: String) {
        viewModel.pickRedPacket(orderId) {
            m3DAnimation?.cancel()
            btSnatch.clearAnimation()

            if (it.code == 1 || it.code == 2) {
                // 1605 领取红包
                RoomGioUtil.reportRoomRedPackage(1605, if(mCheckBean.source == ChatSendRedPacketFragment.TYPE_LUCK) 1 else 2,
                    mCheckBean.condition_data?.condition_id ?: 0, 0, receiveMoney = it.data.money)
                val dialog = ChatRedPacketResultDialog()
                val bundle = Bundle()
                bundle.putSerializable("bean", it)
                dialog.arguments = bundle
                dialog.show(activity?.supportFragmentManager)
                dismiss()
            } else if (it.code == -2) {
                // 不满足实名条件(后端已判断该用户未实名)
                showRealNameDialog()
            } else {
                toast(it.msg)
            }
        }
    }

    private fun init3DAnimation() {
        val centerX = SizeUtils.dp2px(45f).toFloat()
        val centerY = SizeUtils.dp2px(45f).toFloat()
        val depthZ = 0f
        m3DAnimation = Rotate3DAnimation(
            0f, 180 * 1f, centerX, centerY,
            depthZ, Rotate3DAnimation.ROTATE_Y_AXIS, true
        )
        m3DAnimation?.repeatCount = Animation.INFINITE
        m3DAnimation?.repeatMode = Animation.RESTART
        m3DAnimation?.duration = 500
    }

    /**
     * 开抢
     */
    private fun refreshSnatchView(showScaleAnimation: Boolean) {
        if (tvLittleBottom == null) return
        tvLittleBottom.visibility = View.GONE
        tvBigTop.visibility = View.GONE
        when(mCheckBean.condition_data?.condition_id) {
            CHAT_CONDITION_SEND_TARGET_MESSAGE -> {
                tipBottom.text = "已发送公屏消息"
            }
            CHAT_CONDITION_FOLLOW, CHAT_CONDITION_FOLLOW_HOST -> {
                tipBottom.text = "已关注${mCheckBean.condition_data?.condition_desc}"
            }
            CHAT_CONDITION_SHARE -> {
                tipBottom.text = "已分享聊天室"
            }
            else -> {
                tipBottom.text = ""
            }
        }

        if (showScaleAnimation) {
            val scaleShow = AnimationUtils.loadAnimation(context, R.anim.scale_show_center)
            scaleShow.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationEnd(animation: Animation?) {
                    btSnatch.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animation?) {
                }

            })

            val scaleHide = AnimationUtils.loadAnimation(context, R.anim.scale_hide_center)
            scaleHide.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                }

                override fun onAnimationEnd(animation: Animation?) {
                    btAction.visibility = View.GONE
                    btSnatch.startAnimation(scaleShow)
                }

                override fun onAnimationRepeat(animation: Animation?) {
                }

            })
            btAction.startAnimation(scaleHide)
        } else {
            btSnatch.visibility = View.VISIBLE
            btAction.visibility = View.GONE
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
//        mHandler.removeMessages(MSG)
        m3DAnimation?.cancel()
        countDownProgress?.end()
    }
}