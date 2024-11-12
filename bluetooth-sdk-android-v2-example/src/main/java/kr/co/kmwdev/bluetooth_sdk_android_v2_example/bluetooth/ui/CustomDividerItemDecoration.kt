package kr.co.kmwdev.bluetooth_sdk_android_v2_example.bluetooth.ui

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CustomDividerRadiusItemDecoration(
    private val topRadiusDrawable: Drawable,
    private val bottomRadiusDrawable: Drawable,
    private val dividerDrawable: Drawable
) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val layoutParams = child.layoutParams as RecyclerView.LayoutParams

            // 첫 번째 아이템에만 상단 라운드 배경 적용
            if (i == 0) {
                val left = child.left - layoutParams.leftMargin
                val right = child.right + layoutParams.rightMargin
                val top = child.top - layoutParams.topMargin
                val bottom = child.bottom + layoutParams.bottomMargin

                topRadiusDrawable.setBounds(left, top, right, bottom)
                topRadiusDrawable.draw(canvas)
            }

        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < state.itemCount - 1) {
            outRect.bottom = dividerDrawable.intrinsicHeight
        }
    }
}
