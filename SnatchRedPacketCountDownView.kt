package com.daofeng.chatroom.mvvm.newredpacket

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Cap
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.blankj.utilcode.util.SizeUtils
import com.daofeng.baselibrary.listener.Callback

/**
 * @Description: 自定义抢红包倒计时View
 * @Author: 张雨民
 * @CreateDate: 2021/11/11 14:48
 */
class SnatchRedPacketCountDownView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private val mRingColor = Color.parseColor("#FEEDBC")
    private val mRingWidth = SizeUtils.dp2px(3f).toFloat()
    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        color = mRingColor
        style = Paint.Style.STROKE
        strokeCap = Cap.ROUND
        strokeWidth = mRingWidth
    }
    private val mRectF = RectF()
    private var mSweepAngle = 360f
    private var mAnimator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(mRectF, 270f, mSweepAngle, false, mPaint)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        mRectF.set(
            mRingWidth / 2,
            mRingWidth / 2,
            measuredWidth - mRingWidth / 2,
            measuredHeight - mRingWidth / 2
        )
    }

    fun start(countDownTime: Int, callback: Callback<Any?>) {
        initAnimator(countDownTime)
        mAnimator?.addUpdateListener {
            val x = it?.animatedValue.toString().toFloat()
            mSweepAngle = 360 - x
            invalidate()
        }
        mAnimator?.start()

        mAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                callback.callback(null)
            }
        })
    }

    fun end() {
        mAnimator?.removeAllListeners()
        mAnimator?.cancel()
    }

    private fun initAnimator(duration: Int) {
        mAnimator = ValueAnimator.ofFloat(0f, 360f)
        mAnimator?.duration = duration * 1000L
        mAnimator?.interpolator = LinearInterpolator()
        mAnimator?.repeatCount = 0
    }
}