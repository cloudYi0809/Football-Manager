package com.greendynasty.football.editor.model

/**
 * 编辑操作类型（INSERT / UPDATE / DELETE）。
 *
 * 对应 ChangeLogService 撤销/重做时的逆操作：
 * - INSERT 的撤销 = DELETE
 * - DELETE 的撤销 = INSERT 旧值
 * - UPDATE 的撤销 = UPDATE 回旧值
 */
enum class EditOperationType { INSERT, UPDATE, DELETE }

/**
 * 编辑模式（V0.2 §二 数据来源治理）。
 * - OFFLINE：离线编辑 history.db（影响所有新存档，需备份）
 * - REALTIME：实时编辑当前存档（影响当前存档，高风险）
 * - SAVE：存档编辑指定 save.db（未加载时）
 *
 * 简版 V1 默认使用 OFFLINE 模式。
 */
enum class EditMode { OFFLINE, REALTIME, SAVE }

/**
 * 编辑目标表枚举。
 *
 * 简版 V1 仅支持 player / player_attributes / club / match 四张表的基础字段编辑。
 *
 * @property tableName 物理表名（对齐 T00 Entity）
 * @property displayName 中文展示名
 */
enum class EditTargetTable(val tableName: String, val displayName: String) {
    PLAYER("player", "球员"),
    PLAYER_ATTRIBUTES("player_attributes", "球员属性"),
    CLUB("club", "俱乐部"),
    MATCH("match", "比赛");

    companion object {
        fun fromTableName(name: String): EditTargetTable? = values().firstOrNull { it.tableName == name }
    }
}

/**
 * 单条编辑操作记录（操作类型 / 目标表 / 目标 ID / 旧值 / 新值 / 时间戳）。
 *
 * 用于 [com.greendynasty.football.editor.repository.ChangeLogService] 撤销/重做，
 * 以及编辑历史 UI 展示。
 *
 * @property operationType 操作类型
 * @property targetTable 目标表
 * @property targetId 目标主键值（字符串化）
 * @property oldValue 修改前实体引用（DELETE 时为被删除实体，INSERT 时为 null）
 * @property newValue 修改后实体引用（DELETE 时为 null，INSERT/UPDATE 时为新值）
 * @property timestamp ISO 本地时间戳
 * @property editMode 编辑模式
 * @property undone 是否已撤销
 */
data class EditOperation(
    val operationType: EditOperationType,
    val targetTable: EditTargetTable,
    val targetId: String,
    val oldValue: Any?,
    val newValue: Any?,
    val timestamp: String,
    val editMode: EditMode = EditMode.OFFLINE,
    val undone: Boolean = false
)
