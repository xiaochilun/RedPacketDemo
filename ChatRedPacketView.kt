package com.daofeng.chatroom.mvvm.newredpacket

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import com.daofeng.baselibrary.bean.chatroom.NewRedPacketReceiveBean
import com.daofeng.baselibrary.util.TimeFormatUtils
import com.daofeng.chatroom.R
import com.daofeng.chatroom.RoomBeanRepository
import com.daofeng.chatroom.mvvm.barrage.SpeakListener
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.view_chat_red_packet.view.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @Description: 聊天室右下角红包
 * @Author: 张雨民
 * @CreateDate: 2021/11/10 15:08
 */

const val CHAT_CONDITION_NO = 1                      // 无门槛
const val CHAT_CONDITION_SEND_TARGET_MESSAGE = 2     // 发送指定公屏消息
const val CHAT_CONDITION_FOLLOW = 3                  // 关注主持用户
const val CHAT_CONDITION_FOLLOW_HOST = 4             // 关注我
const val CHAT_CONDITION_SHARE = 5                   // 分享房间

/**
 * 红包是否展示中, 展示抢、结果、详情弹窗时为true否则为false
 */
var newChatRedPacketToasted = false

/**
 * 当前组件倒计时
 */
var currCountDown = MutableLiveData<String>()

interface NewRedPacketTriggerCallback {
    /**
     * 尝试自动弹出"红包领取"弹窗
     * @param autoTrigger 是否自动触发弹窗. 手动点击View为false, 倒计时和领取后关闭弹窗为true
     */
    fun trigger(t: NewRedPacketReceiveBean?, autoTrigger: Boolean = false)
}

class ChatRedPacketView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.view_chat_red_packet, this)
    }

    companion object {
        const val AUTO_TOAST_TIME = 3
    }

    private val mRedPackets = arrayListOf<AssembledRedPacket>()

    fun getRedPacket(orderId: String): AssembledRedPacket? {
        return mRedPackets.find {
            it.id == orderId
        }
    }

    fun getFirstRedPacketBean(): NewRedPacketReceiveBean? {
        return if (mRedPackets.isNotEmpty()) {
            mRedPackets[0].bean
        } else {
            null
        }
    }

    private var mFirstPacket: AssembledRedPacket? = null
    private var mDisposable: Disposable? = null
    private var mTriggerCallback: NewRedPacketTriggerCallback? = null
    private var mEnableCountDown: Boolean = true

    private fun interval(): Disposable {
        return Flowable.interval(0, 1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                mFirstPacket ?: return@subscribe
                val showCountDown = mFirstPacket!!.currCountDown > 0
                if (showCountDown) {
                    // 显示倒计时
                    receive.visibility = View.GONE
                    redPacketCountDown.visibility = View.VISIBLE
                    if (mEnableCountDown) {
                        // 右下角的红包组件
                        val secondOfMins =
                            TimeFormatUtils.getSecondOfMins(mFirstPacket!!.currCountDown.toLong())
                        redPacketCountDown.text = secondOfMins
                        currCountDown.value = secondOfMins
                    } else {
                        // 小面板的红包组件
                        redPacketCountDown.text = currCountDown.value
                    }
                    // 第一个红包在倒计时状态下尝试自动弹窗
                    if (mFirstPacket!!.currCountDown == AUTO_TOAST_TIME) {
                        trigger()
                    }
                } else {
                    // 显示"抢红包"
                    redPacketCountDown.visibility = GONE
                    receive.visibility = VISIBLE
                    currCountDown.value = ""
                }

                var hasCountDownRedPacket = false // 是否还有正在倒计时的红包
                mRedPackets.forEach {
                    if (it.currCountDown > 0) {
                        hasCountDownRedPacket = true
                        it.currCountDown--
                    }
                }
                // 没有倒计时的红包则取消当前interval
                if (!hasCountDownRedPacket) cancelInterval()
            }
    }

    fun init(triggerCallback: NewRedPacketTriggerCallback, enableCountDown: Boolean = true) {
        mTriggerCallback = triggerCallback
        mEnableCountDown = enableCountDown
        setOnClickListener {
            mTriggerCallback?.trigger(mFirstPacket?.bean)
        }
    }

    /**
     * 尝试自动弹窗. 尝试弹窗的永远是第一个红包, 新版红包的价值观: 必须按照收到的顺序去领取红包
     */
    fun trigger() {
        // 如果第一个红包的倒计时小于等于3秒 判断第一个红包的条件是否满足
        // 如果第一个红包满足条件则弹, 不满足则不弹
        mFirstPacket?.let {
            if (it.currCountDown <= AUTO_TOAST_TIME && !newChatRedPacketToasted) {
                mTriggerCallback?.trigger(mFirstPacket?.bean, true)
            }
        }
    }

    fun onReceiveRedPacket(bean: NewRedPacketReceiveBean) {
        // 优先按照发送事件排序, 先发先展示
        val assembledRedPacket = AssembledRedPacket(bean)
        val contain = mRedPackets.map {
            it.id
        }.contains(bean.red_order_id)
        if (!contain) {
            mRedPackets.add(assembledRedPacket)
        }
        mRedPackets.sortWith(compareBy { it.createTime })
        if (assembledRedPacket.currCountDown > 0) {
            if (mDisposable == null || mDisposable?.isDisposed == true) {
                mDisposable = interval()
            }
        }

        if (mFirstPacket == null) {
            // 收到第一个红包
            setFirstPacket(assembledRedPacket)
            if (mFirstPacket!!.currCountDown <= 0) {
                // 可领取状态
                redPacketCountDown.visibility = GONE
                receive.visibility = VISIBLE
                // 如果没有倒计时则尝试自动弹窗
                trigger()
            }
        }

        renderRedPacketNum()
        executeAnimation()
    }

    private fun setFirstPacket(bean: AssembledRedPacket) {
        mFirstPacket = bean
        if (bean.bean.condition_id == CHAT_CONDITION_SEND_TARGET_MESSAGE) {
            bean.bean.condition_param?.let {
                SpeakListener.registerMessageAction(SpeakListener.MESSAGE_ACTION_RED_PACKAGE_KEY, SpeakListener.MessageMatchMark(listOf(it), listOf(bean.bean.red_order_id)))
            }
        } else {
            SpeakListener.unRegisterMessageAction(SpeakListener.MESSAGE_ACTION_RED_PACKAGE_KEY)
        }
    }

    fun dropPacket(orderId: String) {
        // 移除指定的红包并刷新显示
        val targetPacket = mRedPackets.find {
            it.id == orderId
        }
        val removeSuccess = mRedPackets.remove(targetPacket)
//        if (!removeSuccess) return
        if (mRedPackets.size == 0) {
            mFirstPacket = null
            RoomBeanRepository.rightSideShowNewRed.value = false
        } else {
            setFirstPacket(mRedPackets[0])
        }
        renderRedPacketNum()
    }

    fun makeZero() {
        mRedPackets.clear()
        cancelInterval()
        mFirstPacket = null
        RoomBeanRepository.rightSideShowNewRed.value = false
    }

    private fun renderRedPacketNum() {
        if (mRedPackets.size > 1) {
            redPacketNum.visibility = View.VISIBLE
            mRedPackets.size.let {
                if (it <= 99) {
                    redPacketNum.text = it.toString()
                } else {
                    redPacketNum.text = "x99"
                }
            }
        } else {
            redPacketNum.visibility = View.GONE
        }
    }

    private fun cancelInterval() {
        mDisposable?.apply {
            if (!isDisposed) {
                dispose()
            }
        }
        mDisposable = null
    }

    private fun executeAnimation() {
        animationGroup.clearAnimation()
        val degree = 8f
        val duration = 500L
        val intervalDelay = 500L
        // 连续左右晃动
        val animatorWaggle1 = getRotationAnimator(animationGroup, duration, 0, 0f, -degree, degree, -degree, degree)
        val animatorWaggle2 = getRotationAnimator(animationGroup, duration, intervalDelay, degree, -degree, degree, -degree, degree, 0f)
        val animatorSet = AnimatorSet()
        animatorSet.play(animatorWaggle2).after(animatorWaggle1)
        animatorSet.start()
    }

    private fun getRotationAnimator(view: View, time: Long, delayTime: Long, vararg values: Float): ObjectAnimator {
        val ani = ObjectAnimator.ofFloat(view, "rotation", *values)
        ani.duration = time
        ani.startDelay = delayTime
        ani.interpolator = LinearInterpolator()
        return ani
    }
}