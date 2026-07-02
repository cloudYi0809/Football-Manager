package com.greendynasty.football.editor.repository

import com.greendynasty.football.editor.model.EditOperation
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 变更记录服务（变更记录 + 撤销支持）。
 *
 * 维护内存中的撤销/重做栈与全量操作历史，支持：
 * - [record]：记录一条编辑操作（自动清空 redo 栈）
 * - [popForUndo]：弹出最近一条可撤销操作（推入 redo 栈）
 * - [popForRedo]：弹出最近一条可重做操作（推回 undo 栈）
 * - [pushBack]：撤销失败时把操作推回 undo 栈
 * - [markUndone] / [markRedone]：更新历史中的撤销标记
 * - [getHistory]：查询全量操作历史（倒序，最新在前）
 *
 * 撤销步数上限由 [maxUndoSteps] 配置（默认 50，对齐实现方案 editor_config.json）。
 *
 * 简版 V1 为内存实现（不持久化），崩溃后历史丢失但 history.db 备份兜底；
 * 后续可扩展为 editor.db Room 持久化（实现方案 §三.2 EditLogEntity）。
 *
 * @param maxUndoSteps 最大撤销步数
 */
class ChangeLogService(private val maxUndoSteps: Int = 50) {

    /** 撤销栈（LIFO） */
    private val undoStack = ArrayDeque<EditOperation>()

    /** 重做栈（LIFO） */
    private val redoStack = ArrayDeque<EditOperation>()

    /** 全量操作历史（按时间正序，UI 展示时倒序） */
    private val allHistory = mutableListOf<EditOperation>()

    /** 记录一条编辑操作。 */
    fun record(operation: EditOperation) {
        undoStack.addLast(operation)
        while (undoStack.size > maxUndoSteps) undoStack.removeFirst()
        // 新操作后清空 redo 栈（旧的 redo 失效）
        redoStack.clear()
        allHistory.add(operation)
    }

    /** 弹出最近一条可撤销操作（推入 redo 栈），无可撤销时返回 null。 */
    fun popForUndo(): EditOperation? {
        if (undoStack.isEmpty()) return null
        val op = undoStack.removeLast()
        redoStack.addLast(op)
        return op
    }

    /** 撤销失败时把操作推回 undo 栈。 */
    fun pushBack(op: EditOperation) {
        undoStack.addLast(op)
    }

    /** 弹出最近一条可重做操作（推回 undo 栈），无可重做时返回 null。 */
    fun popForRedo(): EditOperation? {
        if (redoStack.isEmpty()) return null
        val op = redoStack.removeLast()
        undoStack.addLast(op)
        return op
    }

    /** 标记某条操作为已撤销（更新历史记录）。 */
    fun markUndone(op: EditOperation) {
        val idx = allHistory.indexOfLast { it.timestamp == op.timestamp && it.targetId == op.targetId }
        if (idx >= 0) allHistory[idx] = allHistory[idx].copy(undone = true)
    }

    /** 标记某条操作为已重做（恢复撤销标记）。 */
    fun markRedone(op: EditOperation) {
        val idx = allHistory.indexOfLast { it.timestamp == op.timestamp && it.targetId == op.targetId }
        if (idx >= 0) allHistory[idx] = allHistory[idx].copy(undone = false)
    }

    /** 查询全量操作历史（倒序，最新在前）。 */
    fun getHistory(): List<EditOperation> = allHistory.reversed()

    /** 可撤销步数。 */
    fun getUndoableCount(): Int = undoStack.size

    /** 可重做步数。 */
    fun getRedoableCount(): Int = redoStack.size

    /** 清空全部历史与栈。 */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        allHistory.clear()
    }

    /** 当前 ISO 本地时间戳。 */
    fun nowTimestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
