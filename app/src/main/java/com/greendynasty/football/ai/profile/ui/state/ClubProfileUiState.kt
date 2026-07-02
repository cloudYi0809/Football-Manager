package com.greendynasty.football.ai.profile.ui.state

import com.greendynasty.football.ai.profile.model.ClubPersonality
import com.greendynasty.football.ai.profile.model.ClubProfile
import com.greendynasty.football.ai.profile.model.LongTermGoal
import com.greendynasty.football.ai.profile.model.TacticalIdentity
import com.greendynasty.football.ai.profile.repository.ProfileStatistics

/**
 * T18 俱乐部画像页 UI 状态。
 *
 * 两个 Tab：
 * - 列表：所有俱乐部画像，可按性格 / 战术 / 长期目标筛选
 * - 详情：单个俱乐部画像完整信息
 *
 * @property isLoading 加载中标志
 * @property profiles 画像列表
 * @property statistics 画像统计（仪表盘用）
 * @property selectedProfile 当前选中的画像详情
 * @property personalityFilter 性格筛选（null = 不筛选）
 * @property tacticalFilter 战术筛选（null = 不筛选）
 * @property goalFilter 长期目标筛选（null = 不筛选）
 * @property searchKeyword 俱乐部名搜索关键字
 * @property clubNameMap 俱乐部 ID → 俱乐部名称（来自 history.club，UI 显示用）
 * @property message 一次性消息（Snackbar）
 */
data class ClubProfileUiState(
    val isLoading: Boolean = false,
    val profiles: List<ClubProfile> = emptyList(),
    val statistics: ProfileStatistics? = null,
    val selectedProfile: ClubProfile? = null,
    val personalityFilter: ClubPersonality? = null,
    val tacticalFilter: TacticalIdentity? = null,
    val goalFilter: LongTermGoal? = null,
    val searchKeyword: String = "",
    val clubNameMap: Map<Int, String> = emptyMap(),
    val message: String? = null
) {

    /**
     * 经过筛选后的画像列表（性格 / 战术 / 长期目标 / 关键字四重过滤）。
     */
    val filteredProfiles: List<ClubProfile>
        get() = profiles
            .filter { profile ->
                personalityFilter?.let { profile.personality == it } ?: true
            }
            .filter { profile ->
                tacticalFilter?.let { profile.tacticalIdentity == it } ?: true
            }
            .filter { profile ->
                goalFilter?.let { profile.longTermGoal == it } ?: true
            }
            .filter { profile ->
                if (searchKeyword.isBlank()) true
                else clubNameMap[profile.clubId]
                    ?.contains(searchKeyword, ignoreCase = true) ?: false
            }
}

/**
 * 俱乐部画像页 Tab 类型。
 */
enum class ClubProfileTab(val title: String) {
    /** 画像列表 */
    LIST("画像列表"),
    /** 性格分布统计 */
    STATISTICS("性格分布")
}
