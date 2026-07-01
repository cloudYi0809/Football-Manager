package com.greendynasty.football.data.save.management

/**
 * 存档状态枚举
 *
 * 描述 [SaveManager] 当前管理的存档生命周期状态。
 * 状态变更通过 [SaveManager.currentState]（StateFlow）对外暴露，UI 可观察以刷新界面。
 *
 * 状态机转换规则见 [transitions]，非法转换将抛出 [IllegalStateException]。
 */
enum class SaveState {

    /** 未加载：无存档加载，初始状态 */
    UNLOADED,

    /** 加载中：正在加载存档（读取元信息、完整性校验、迁移、恢复状态） */
    LOADING,

    /** 已加载：存档已加载到内存，可进行游戏 */
    LOADED,

    /** 保存中：正在异步保存存档（写入数据、更新元信息、创建 checkpoint） */
    SAVING,

    /** 切换中：正在切换存档（保存当前 + 关闭 + 加载新存档） */
    SWITCHING,

    /** 错误：存档操作发生异常，需用户介入或恢复 */
    ERROR;

    companion object {

        /**
         * 合法状态转换映射表（from -> 允许转入的 to 集合）。
         *
         * 转换规则说明：
         * - UNLOADED -> LOADING / LOADED（创建新存档后直接进入 LOADED）/ ERROR
         * - LOADING  -> LOADED / UNLOADED（加载失败回退）/ ERROR / Corrupted（归到 ERROR）
         * - LOADED   -> SAVING / SWITCHING / UNLOADED（关闭存档）/ ERROR
         * - SAVING   -> LOADED（保存完成）/ ERROR（保存失败）
         * - SWITCHING-> LOADED（切换成功）/ UNLOADED（切换失败）/ ERROR
         * - ERROR    -> UNLOADED（重置）/ LOADING（重试加载）
         */
        private val transitions: Map<SaveState, Set<SaveState>> = mapOf(
            UNLOADED to setOf(LOADING, LOADED, ERROR),
            LOADING to setOf(LOADED, UNLOADED, ERROR),
            LOADED to setOf(SAVING, SWITCHING, UNLOADED, ERROR),
            SAVING to setOf(LOADED, ERROR),
            SWITCHING to setOf(LOADED, UNLOADED, ERROR),
            ERROR to setOf(UNLOADED, LOADING)
        )

        /**
         * 判断状态转换是否合法。
         *
         * @param from 起始状态
         * @param to 目标状态
         * @return true 表示允许从 [from] 转入 [to]
         */
        fun canTransition(from: SaveState, to: SaveState): Boolean {
            if (from == to) return true // 同状态保持合法
            return transitions[from]?.contains(to) ?: false
        }

        /**
         * 校验状态转换合法性，非法转换抛出异常。
         *
         * @param from 起始状态
         * @param to 目标状态
         * @throws IllegalStateException 如果转换不合法
         */
        fun checkTransition(from: SaveState, to: SaveState) {
            require(canTransition(from, to)) {
                "非法存档状态转换：$from -> $to（允许的目标：${transitions[from] ?: emptySet<SaveState>()}）"
            }
        }
    }
}
