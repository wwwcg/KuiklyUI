/*
 * Tencent is pleased to support the open source community by making KuiklyUI
 * available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 * Licensed under the License of KuiklyUI;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://github.com/Tencent-TDS/KuiklyUI/blob/main/LICENSE
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.kuikly.demo.pages.demo

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.CallbackRef
import com.tencent.kuikly.core.timer.cancelPostCallback
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.demo.pages.base.BasePager
import kotlin.math.max

/**
 * 类似iPhone负一屏的卡片式拖动排序组件Demo
 * 支持1x1和2x1两种卡片尺寸
 */
@Page("WidgetGridDemoPage")
internal class WidgetGridDemoPage : BasePager() {

    companion object {
        const val TAG = "WidgetGridDemoPage"
    }

    // ==================== 网格配置 ====================
    private val columnCount = 3           // 列数
    private val cardHeight = 100f         // 卡片高度
    private val cardSpacing = 12f         // 卡片间距
    private val horizontalPadding = 16f   // 左右边距

    // ==================== 拖拽配置 ====================
    private val dragScaleRatio = 1.05f    // 拖拽时卡片放大比例
    private val dragOpacity = 0.9f        // 拖拽时卡片透明度
    private val dragAnimationDuration = 0.3f  // 拖拽过程中其他卡片位移动画时长（秒）

    // ==================== 抖动配置 ====================
    private val shakeEnabled = true       // 是否启用抖动效果
    private val shakeInterval = 200       // 抖动切换间隔（毫秒）
    private val shakeAngleBase = 1.2f     // 基础抖动角度（度）
    private val shakeAngleOffset = 0.5f   // 相邻卡片角度偏移（度），让抖动更自然
    private val shakeAnimationDuration = 0.2f  // 抖动动画时长（秒）
    private val longPressDelay = 350      // 长按触发延迟（毫秒）

    // ==================== 数据和状态 ====================
    // 数据列表
    var cardList by observableList<WidgetCardData>()

    // 编辑模式
    var isEditing by observable(false)

    // 拖拽状态（非响应式，仅用于逻辑计算）
    private var isDragging = false
    private var dragCardData: WidgetCardData? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    
    // 抖动定时器
    private var shakeTimerRef: CallbackRef? = null
    private var shakeDirection = 1  // 1 或 -1，控制抖动方向

    // 计算卡片宽度
    private fun getCardWidth(): Float {
        return (pagerData.pageViewWidth - horizontalPadding * 2 - cardSpacing * (columnCount - 1)) / columnCount
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFF1C1C1EL))
            }

            // 顶部导航栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color(0xFF2C2C2EL))
                }
                View {
                    attr {
                        height(56f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                    }

                    // 返回按钮
                    View {
                        attr {
                            size(32f, 32f)
                            allCenter()
                        }
                        event {
                            click {
                                ctx.getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                        Text {
                            attr {
                                text("←")
                                fontSize(20f)
                                color(Color.WHITE)
                            }
                        }
                    }

                    // 标题
                    Text {
                        attr {
                            flex(1f)
                            text("小组件")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color.WHITE)
                            textAlignCenter()
                        }
                    }

                    // 编辑/完成按钮
                    View {
                        attr {
                            paddingLeft(12f)
                            paddingRight(12f)
                            paddingTop(6f)
                            paddingBottom(6f)
                            backgroundColor(if (ctx.isEditing) Color(0xFF0A84FFL) else Color(0xFF3A3A3CL))
                            borderRadius(16f)
                        }
                        event {
                            click {
                                ctx.isEditing = !ctx.isEditing
                                if (ctx.isEditing) {
                                    ctx.startShakeAnimation()
                                } else {
                                    ctx.stopShakeAnimation()
                                }
                            }
                        }
                        Text {
                            attr {
                                text(if (ctx.isEditing) "完成" else "编辑")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }

            // 卡片网格容器
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(ctx.horizontalPadding)
                    paddingRight(ctx.horizontalPadding)
                    paddingTop(16f)
                    paddingBottom(100f)
                }

                // 使用绝对定位的容器来放置卡片
                View {
                    attr {
                        // 计算容器高度
                        val totalRows = ctx.calculateTotalRows()
                        height(totalRows * (ctx.cardHeight + ctx.cardSpacing) + 100f)
                        width(pagerData.pageViewWidth - ctx.horizontalPadding * 2)
                    }

                    vforIndex({ ctx.cardList }) { cardData, index, _ ->
                        WidgetCard {
                            ref {
                                cardData.viewRef = it
                            }

                            attr {
                                data = cardData
                                editing = ctx.isEditing
                                longPressDelay = ctx.longPressDelay
                            }

                            // 基础定位（不带动画，确保位置更新立即生效）
                            attr {
                                // 实时查找卡片在列表中的当前索引（列表变化时会自动更新）
                                val currentIndex = ctx.cardList.indexOf(cardData)
                                val pos = if (currentIndex >= 0) ctx.calculateCardPosition(currentIndex) else Position(0f, 0f, 0, 0)
                                val width = if (cardData.spanX == 2) {
                                    ctx.getCardWidth() * 2 + ctx.cardSpacing
                                } else {
                                    ctx.getCardWidth()
                                }
                                absolutePosition(
                                    top = pos.y,
                                    left = pos.x
                                )
                                size(width, ctx.cardHeight)
                                zIndex(if (cardData.isDragging) 100 else 0)
                            }
                            
                            // 变换和动画（与位置分开，避免动画影响位置更新）
                            attr {
                                // 被拖拽卡片：放大 + 位移（无抖动，无动画）
                                if (cardData.isDragging) {
                                    transform(
                                        scale = Scale(ctx.dragScaleRatio, ctx.dragScaleRatio),
                                        translate = Translate(
                                            percentageX = 0f,
                                            percentageY = 0f,
                                            offsetX = cardData.offsetX,
                                            offsetY = cardData.offsetY
                                        )
                                    )
                                    opacity(ctx.dragOpacity)
                                } else {
                                    // 非拖拽卡片：位移 + 抖动旋转 + 动画
                                    transform(
                                        rotate = Rotate(cardData.shakeAngle),
                                        translate = Translate(
                                            percentageX = 0f,
                                            percentageY = 0f,
                                            offsetX = cardData.offsetX,
                                            offsetY = cardData.offsetY
                                        )
                                    )
                                    // 根据是否需要位移动画选择动画类型
                                    // 位移变化用弹性动画，纯抖动用快速动画
                                    if (cardData.needsAnimation) {
                                        animate(
                                            Animation.springEaseInOut(ctx.dragAnimationDuration, 1f, 0f),
                                            cardData.animationKey
                                        )
                                    } else if (ctx.shakeEnabled) {
                                        // 只有启用抖动时才添加抖动动画
                                        animate(
                                            Animation.easeInOut(ctx.shakeAnimationDuration),
                                            cardData.shakeKey
                                        )
                                    }
                                }
                            }

                            event {
                                onDelete {
                                    ctx.deleteCard(cardData)
                                }
                                onLongPress {
                                    if (!ctx.isEditing) {
                                        ctx.isEditing = true
                                        ctx.startShakeAnimation()
                                    }
                                }
                                onDrag { params ->
                                    // 实时查找卡片的当前索引
                                    val currentIndex = ctx.cardList.indexOf(cardData)
                                    if (currentIndex >= 0) {
                                        ctx.handleDrag(params, cardData, currentIndex)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部添加按钮
            vif({ ctx.isEditing }) {
                View {
                    attr {
                        absolutePosition(bottom = 40f + ctx.pagerData.safeAreaInsets.bottom)
                        alignSelfCenter()
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                        paddingTop(12f)
                        paddingBottom(12f)
                        backgroundColor(Color(0xFF0A84FFL))
                        borderRadius(24f)
                    }
                    event {
                        click {
                            ctx.addNewCard()
                        }
                    }
                    Text {
                        attr {
                            text("+ 添加小组件")
                            fontSize(16f)
                            fontWeightMedium()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }

    // 计算卡片位置
    private fun calculateCardPosition(index: Int): Position {
        if (index < 0 || index >= cardList.size) {
            return Position(0f, 0f, 0, 0)
        }

        var currentRow = 0
        var currentCol = 0

        for (i in 0 until index) {
            if (i >= cardList.size) break
            val card = cardList[i]
            val spanX = card.spanX

            if (currentCol + spanX > columnCount) {
                currentRow++
                currentCol = 0
            }

            currentCol += spanX
            if (currentCol >= columnCount) {
                currentRow++
                currentCol = 0
            }
        }

        val currentCard = cardList.getOrNull(index) ?: return Position(0f, 0f, currentRow, currentCol)
        if (currentCol + currentCard.spanX > columnCount) {
            currentRow++
            currentCol = 0
        }

        val x = currentCol * (getCardWidth() + cardSpacing)
        val y = currentRow * (cardHeight + cardSpacing)

        return Position(x, y, currentRow, currentCol)
    }

    // 计算总行数
    private fun calculateTotalRows(): Int {
        if (cardList.isEmpty()) return 1

        var currentRow = 0
        var currentCol = 0

        for (card in cardList) {
            val spanX = card.spanX

            if (currentCol + spanX > columnCount) {
                currentRow++
                currentCol = 0
            }

            currentCol += spanX
            if (currentCol >= columnCount) {
                currentRow++
                currentCol = 0
            }
        }

        if (currentCol > 0) {
            currentRow++
        }

        return max(currentRow, 1)
    }

    // 上一次的目标索引，用于减少不必要的更新
    private var lastTargetIndex = -1

    // 处理拖拽
    private fun handleDrag(params: PanGestureParams, cardData: WidgetCardData, index: Int) {
        when (params.state) {
            "start" -> {
                startDragging(params, cardData, index)
            }
            "move" -> {
                // 如果收到 move 但还没初始化拖拽状态，则初始化（处理 longPress 后直接 move 的情况）
                if (!isDragging) {
                    startDragging(params, cardData, index)
                }
                if (dragCardData != cardData) return

                val deltaX = params.pageX - dragStartX
                val deltaY = params.pageY - dragStartY

                // 更新被拖拽卡片的位移
                cardData.offsetX = deltaX
                cardData.offsetY = deltaY

                // 计算当前拖拽位置对应的目标索引
                val targetIndex = findTargetIndex(cardData, index, deltaX, deltaY)
                
                // 只有当目标位置变化时才更新预览
                if (targetIndex != lastTargetIndex) {
                    lastTargetIndex = targetIndex
                    // 预览：让其他卡片移动以显示插入位置
                    previewReorder(index, targetIndex, cardData)
                    KLog.d(TAG, "Target changed: $targetIndex")
                }
            }
            "end" -> {
                if (!isDragging || dragCardData != cardData) return

                val targetIndex = lastTargetIndex
                
                // 重新查找卡片在列表中的当前索引（防止列表在拖拽过程中发生变化）
                val currentIndex = cardList.indexOf(cardData)
                if (currentIndex < 0) {
                    // 卡片已不在列表中，直接重置状态
                    KLog.d(TAG, "Drag end: card not found in list")
                    isDragging = false
                    dragCardData = null
                    lastTargetIndex = -1
                    return
                }
                
                KLog.d(TAG, "Drag end: from=$currentIndex, to=$targetIndex")

                // 更新列表顺序（先更新列表，再重置偏移）
                // targetIndex 表示在新顺序中应该排在第几位
                if (targetIndex != currentIndex && targetIndex >= 0 && targetIndex < cardList.size) {
                    // 先复制一份当前列表
                    val originalList = cardList.toList()
                    
                    // 构建新的顺序索引
                    val newOrderIndices = originalList.indices.toMutableList()
                    newOrderIndices.removeAt(currentIndex)
                    val insertAt = targetIndex.coerceIn(0, newOrderIndices.size)
                    newOrderIndices.add(insertAt, currentIndex)
                    
                    // 按新顺序获取卡片
                    val reorderedCards = newOrderIndices.map { originalList[it] }
                    
                    // 清空并重新添加
                    cardList.clear()
                    cardList.addAll(reorderedCards)
                    
                    KLog.d(TAG, "Reordered cards: indices=$newOrderIndices, titles=${reorderedCards.map { it.title }}")
                }

                // 列表更新后，再重置所有卡片的偏移和状态
                cardList.forEach { card ->
                    card.isDragging = false
                    card.offsetX = 0f
                    card.offsetY = 0f
                    card.needsAnimation = false
                }

                isDragging = false
                dragCardData = null
                lastTargetIndex = -1
            }
        }
    }

    // 初始化拖拽状态
    private fun startDragging(params: PanGestureParams, cardData: WidgetCardData, index: Int) {
        isDragging = true
        dragCardData = cardData
        dragStartX = params.pageX
        dragStartY = params.pageY
        lastTargetIndex = index
        cardData.isDragging = true
        cardData.needsAnimation = false
        KLog.d(TAG, "Drag start: index=$index, card=${cardData.title}")
    }

    // 查找目标索引 - 返回在新顺序中被拖拽卡片应该排在第几位（0-based）
    private fun findTargetIndex(cardData: WidgetCardData, currentIndex: Int, deltaX: Float, deltaY: Float): Int {
        if (cardList.size <= 1) return currentIndex

        val currentPos = calculateCardPosition(currentIndex)
        val cardW = if (cardData.spanX == 2) getCardWidth() * 2 + cardSpacing else getCardWidth()
        
        // 拖拽卡片的中心点
        val dragCenterX = currentPos.x + deltaX + cardW / 2
        val dragCenterY = currentPos.y + deltaY + cardHeight / 2

        val cellWidth = getCardWidth() + cardSpacing
        val cellHeight = cardHeight + cardSpacing

        // 构建"不包含被拖拽卡片"的布局
        // 计算每个"槽位"的中心位置，找到拖拽卡片应该插入的位置
        var row = 0
        var col = 0
        var slotIndex = 0  // 在不含被拖拽卡片的顺序中的位置
        
        for (i in cardList.indices) {
            if (i == currentIndex) continue
            
            val card = cardList[i]
            val spanX = card.spanX
            
            // 检查当前行是否能放下这个卡片
            if (col + spanX > columnCount) {
                row++
                col = 0
            }
            
            // 计算这个槽位的左边缘
            val slotLeftX = col * cellWidth
            val slotCenterY = row * cellHeight + cellHeight / 2
            
            // 如果拖拽中心在这个卡片的上方，或者在同一行但在其左边，则插入到这里
            if (dragCenterY < slotCenterY - cellHeight * 0.4f) {
                // 在这个卡片上方的行，插入到这里
                return slotIndex
            } else if (dragCenterY < slotCenterY + cellHeight * 0.4f && dragCenterX < slotLeftX + cellWidth * 0.5f) {
                // 在同一行，且在这个卡片的左半边，插入到这里
                return slotIndex
            }
            
            col += spanX
            if (col >= columnCount) {
                row++
                col = 0
            }
            slotIndex++
        }

        // 如果没有找到合适的位置，放在最后
        return cardList.size - 1
    }

    // 预览重新排序效果
    // toIndex 表示：在新顺序中，被拖拽卡片应该排在第 toIndex 位
    private fun previewReorder(fromIndex: Int, toIndex: Int, dragCard: WidgetCardData) {
        if (fromIndex == toIndex) {
            // 目标位置就是原位置，所有其他卡片回到原位
            cardList.forEach { card ->
                if (card != dragCard && (card.offsetX != 0f || card.offsetY != 0f)) {
                    card.offsetX = 0f
                    card.offsetY = 0f
                    card.needsAnimation = true
                    card.animationKey++
                }
            }
            return
        }

        // 构建新的排列顺序
        // 1. 先创建不包含 fromIndex 的顺序
        val withoutDrag = cardList.indices.filter { it != fromIndex }
        // 2. 在 toIndex 位置插入 fromIndex
        val newOrder = withoutDrag.toMutableList()
        val insertAt = toIndex.coerceIn(0, newOrder.size)
        newOrder.add(insertAt, fromIndex)
        
        KLog.d(TAG, "previewReorder: from=$fromIndex, to=$toIndex, newOrder=$newOrder")

        // 计算每个卡片在新顺序下的位置
        cardList.forEachIndexed { originalIndex, card ->
            if (card == dragCard) return@forEachIndexed

            // 找到这个卡片在新顺序中的位置
            val newPositionIndex = newOrder.indexOf(originalIndex)
            if (newPositionIndex < 0) return@forEachIndexed

            // 计算新顺序下的布局位置
            val targetPos = calculatePositionInNewOrder(newOrder, newPositionIndex)
            val currentPos = calculateCardPosition(originalIndex)

            val newOffsetX = targetPos.x - currentPos.x
            val newOffsetY = targetPos.y - currentPos.y

            // 只有偏移变化时才更新
            if (card.offsetX != newOffsetX || card.offsetY != newOffsetY) {
                card.offsetX = newOffsetX
                card.offsetY = newOffsetY
                card.needsAnimation = true
                card.animationKey++
            }
        }
    }

    // 计算在新顺序下，第 positionIndex 个位置的坐标
    private fun calculatePositionInNewOrder(newOrder: List<Int>, positionIndex: Int): Position {
        var row = 0
        var col = 0

        for (i in 0 until positionIndex) {
            if (i >= newOrder.size) break
            val cardIndex = newOrder[i]
            if (cardIndex >= cardList.size) continue
            
            val card = cardList[cardIndex]
            val spanX = card.spanX

            // 检查当前行是否能放下这个卡片
            if (col + spanX > columnCount) {
                row++
                col = 0
            }

            col += spanX
            if (col >= columnCount) {
                row++
                col = 0
            }
        }

        // 当前位置的卡片
        if (positionIndex < newOrder.size) {
            val cardIndex = newOrder[positionIndex]
            if (cardIndex < cardList.size) {
                val card = cardList[cardIndex]
                if (col + card.spanX > columnCount) {
                    row++
                    col = 0
                }
            }
        }

        val x = col * (getCardWidth() + cardSpacing)
        val y = row * (cardHeight + cardSpacing)

        return Position(x, y, row, col)
    }

    // 删除卡片
    private fun deleteCard(cardData: WidgetCardData) {
        val deleteIndex = cardList.indexOf(cardData)
        if (deleteIndex < 0) return
        
        // Android 平台：直接删除，不使用位置动画（避免动画冲突问题）
        if (pagerData.isAndroid) {
            // 重置所有卡片状态
            cardList.forEach { card ->
                card.offsetX = 0f
                card.offsetY = 0f
                card.shakeAngle = 0f
                card.needsAnimation = false
            }
            // 执行删除
            cardList.remove(cardData)
            return
        }
        
        // iOS 及其他平台：使用位置动画实现平滑过渡
        // 1. 计算删除后每个卡片的位置变化，设置偏移量实现平滑过渡
        val oldPositions = cardList.mapIndexed { index, card -> 
            card to calculateCardPosition(index)
        }.toMap()
        
        // 2. 执行删除
        cardList.remove(cardData)
        
        // 3. 计算删除后的新位置，设置偏移量让视觉上保持在旧位置
        cardList.forEachIndexed { newIndex, card ->
            val oldPos = oldPositions[card] ?: return@forEachIndexed
            val newPos = calculateCardPosition(newIndex)
            
            // 设置偏移量 = 旧位置 - 新位置（视觉上保持在旧位置）
            card.offsetX = oldPos.x - newPos.x
            card.offsetY = oldPos.y - newPos.y
            card.shakeAngle = 0f  // 重置抖动角度
            // needsAnimation = true 会让抖动定时器跳过这个卡片
            card.needsAnimation = true
            card.animationKey++
        }
        
        // 4. 延迟将偏移量动画过渡到 0
        setTimeout(16) {  // 等待一帧
            cardList.forEach { card ->
                card.offsetX = 0f
                card.offsetY = 0f
            }
            
            // 5. 位移动画完成后，重置 needsAnimation，让抖动恢复
            setTimeout((dragAnimationDuration * 1000).toInt() + 50) {
                cardList.forEach { card ->
                    card.needsAnimation = false
                }
            }
        }
    }

    // 添加新卡片
    private fun addNewCard() {
        val newId = (cardList.maxOfOrNull { it.id } ?: 0) + 1
        val spanX = if (newId % 3 == 0) 2 else 1
        cardList.add(WidgetCardData(this).apply {
            id = newId
            this.spanX = spanX
            title = "新组件 $newId"
            iconColor = Color(
                (100..255).random(),
                (100..255).random(),
                (100..255).random(),
                1.0f
            )
        })
    }
    
    // 启动抖动动画
    private fun startShakeAnimation() {
        if (!shakeEnabled) return  // 如果未启用抖动则直接返回
        stopShakeAnimation()  // 先停止之前的
        shakeDirection = 1
        scheduleNextShake()
    }
    
    // 停止抖动动画
    private fun stopShakeAnimation() {
        shakeTimerRef?.let { cancelPostCallback(it) }
        shakeTimerRef = null
        // 重置所有卡片的抖动角度
        cardList.forEach { card ->
            card.shakeAngle = 0f
            card.shakeKey++
        }
    }
    
    // 调度下一次抖动
    private fun scheduleNextShake() {
        shakeTimerRef = setTimeout(shakeInterval) {
            // 切换方向
            shakeDirection = -shakeDirection
            val angle = shakeAngleBase * shakeDirection
            
            // 更新所有非拖拽中、非位移动画中的卡片的抖动角度
            cardList.forEachIndexed { index, card ->
                // 跳过正在拖拽或正在进行位置动画的卡片
                if (!card.isDragging && !card.needsAnimation) {
                    // 给每个卡片一个略微不同的角度，让抖动看起来更自然
                    val offset = if (index % 2 == 0) shakeAngleOffset else -shakeAngleOffset
                    card.shakeAngle = angle + offset
                    card.shakeKey++
                }
            }
            
            // 继续下一次抖动
            if (isEditing) {
                scheduleNextShake()
            }
        }
    }

    override fun created() {
        super.created()

        // 初始化测试数据
        val testData = listOf(
            Triple(1, 1, "天气"),
            Triple(2, 2, "日历"),
            Triple(3, 1, "时钟"),
            Triple(4, 1, "备忘录"),
            Triple(5, 2, "音乐"),
            Triple(6, 1, "健康"),
            Triple(7, 1, "相册"),
            Triple(8, 1, "快捷指令"),
            Triple(9, 2, "屏幕使用时间"),
            Triple(10, 1, "电池")
        )

        for ((id, span, title) in testData) {
            cardList.add(WidgetCardData(this).apply {
                this.id = id
                this.spanX = span
                this.title = title
                this.iconColor = Color(
                    (100..255).random(),
                    (100..255).random(),
                    (100..255).random(),
                    1.0f
                )
            })
        }
    }
}

// 位置数据类
internal data class Position(
    val x: Float,
    val y: Float,
    val row: Int,
    val col: Int
)

// 卡片数据模型
internal class WidgetCardData(scope: PagerScope) : BaseObject(), PagerScope by scope {
    var id: Int = 0
    var spanX: Int by observable(1)  // 1 = 1x1, 2 = 2x1
    var title: String by observable("")
    var iconColor: Color by observable(Color.BLUE)

    // 位移偏移量
    var offsetX: Float by observable(0f)
    var offsetY: Float by observable(0f)

    // 拖拽状态
    var isDragging: Boolean by observable(false)
    
    // 动画控制
    var needsAnimation: Boolean by observable(false)
    var animationKey: Int by observable(0)
    
    // 抖动角度（编辑模式下使用）
    var shakeAngle: Float by observable(0f)
    var shakeKey: Int by observable(0)

    lateinit var viewRef: ViewRef<WidgetCardView>
}

// 卡片视图属性
internal class WidgetCardAttr : ComposeAttr() {
    lateinit var data: WidgetCardData
    var editing: Boolean by observable(false)
    var longPressDelay: Int = 350  // 长按触发延迟（毫秒）
}

// 卡片视图事件
internal class WidgetCardEvent : ComposeEvent() {
    private var onDeleteHandler: (() -> Unit)? = null
    private var onLongPressHandler: (() -> Unit)? = null
    private var onDragHandler: ((PanGestureParams) -> Unit)? = null

    fun onDelete(handler: () -> Unit) {
        onDeleteHandler = handler
    }

    fun onLongPress(handler: () -> Unit) {
        onLongPressHandler = handler
    }

    fun onDrag(handler: (PanGestureParams) -> Unit) {
        onDragHandler = handler
    }

    internal fun fireDelete() {
        onDeleteHandler?.invoke()
    }

    internal fun fireLongPress() {
        onLongPressHandler?.invoke()
    }

    internal fun fireDrag(params: PanGestureParams) {
        onDragHandler?.invoke(params)
    }
}

// 卡片视图组件
internal class WidgetCardView : ComposeView<WidgetCardAttr, WidgetCardEvent>() {

    // 用于模拟长按的定时器
    private var longPressCallback: CallbackRef? = null
    private var isTouching by observable(false)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 外层包装（不设置背景和圆角，让删除按钮可以溢出显示）
            
            // 卡片主体（带背景和圆角）
            View {
                attr {
                    absolutePosition(top = 0f, left = 0f, right = 0f, bottom = 0f)
                    backgroundColor(Color(0xFF2C2C2EL))
                    borderRadius(16f)
                    // 触摸时轻微变暗
                    opacity(if (ctx.isTouching && !ctx.attr.editing) 0.7f else 1f)
                }

                // 卡片内容
                View {
                    attr {
                        flex(1f)
                        padding(12f)
                    }

                    // 图标
                    View {
                        attr {
                            size(40f, 40f)
                            backgroundColor(ctx.attr.data.iconColor)
                            borderRadius(10f)
                            marginBottom(8f)
                        }
                    }

                    // 标题
                    Text {
                        attr {
                            text(ctx.attr.data.title)
                            fontSize(14f)
                            fontWeightMedium()
                            color(Color.WHITE)
                        }
                    }

                    // 尺寸标签
                    Text {
                        attr {
                            marginTop(4f)
                            text(if (ctx.attr.data.spanX == 2) "2×1" else "1×1")
                            fontSize(12f)
                            color(Color(0xFF8E8E93L))
                        }
                    }
                }
            }

            // 编辑模式下的删除按钮（在外层，不受卡片圆角裁剪）
            vif({ ctx.attr.editing }) {
                View {
                    attr {
                        absolutePosition(top = -8f, left = -8f)
                        size(24f, 24f)
                        backgroundColor(Color(0xFFFF3B30L))
                        borderRadius(12f)
                        allCenter()
                    }
                    event {
                        click {
                            ctx.event.fireDelete()
                        }
                    }
                    Text {
                        attr {
                            text("−")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
            }

            // 事件处理 - 使用 touchDown + setTimeout 模拟长按，避免与 pan 冲突
            event {
                register(EventName.TOUCH_DOWN.value) {
                    ctx.isTouching = true
                    // 只在非编辑模式下监听长按，用于进入编辑模式
                    if (!ctx.attr.editing) {
                        ctx.longPressCallback = setTimeout(ctx.attr.longPressDelay) {
                            ctx.event.fireLongPress()
                        }
                    }
                }
                register(EventName.TOUCH_UP.value) {
                    ctx.isTouching = false
                    // 取消长按定时器
                    ctx.longPressCallback?.let { callback ->
                        cancelPostCallback(callback)
                        ctx.longPressCallback = null
                    }
                }
                pan { params ->
                    // 开始拖拽时取消长按定时器
                    if (params.state == "start") {
                        ctx.longPressCallback?.let { callback ->
                            cancelPostCallback(callback)
                            ctx.longPressCallback = null
                        }
                    }
                    if (ctx.attr.editing) {
                        ctx.event.fireDrag(params)
                    }
                }
            }
        }
    }

    override fun createAttr(): WidgetCardAttr = WidgetCardAttr()

    override fun createEvent(): WidgetCardEvent = WidgetCardEvent()
}

// 扩展函数
internal fun ViewContainer<*, *>.WidgetCard(init: WidgetCardView.() -> Unit) {
    addChild(WidgetCardView(), init)
}
