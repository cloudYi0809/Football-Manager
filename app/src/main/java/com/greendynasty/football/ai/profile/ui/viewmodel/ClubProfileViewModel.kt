package com.greendynasty.football.ai.profile.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.ai.profile.generator.ClubProfileGenerator
import com.greendynasty.football.ai.profile.model.ClubPersonality
import com.greendynasty.football.ai.profile.model.ClubProfile
import com.greendynasty.football.ai.profile.model.LongTermGoal
import com.greendynasty.football.ai.profile.model.TacticalIdentity
import com.greendynasty.football.ai.profile.repository.ClubProfileRepository
import com.greendynasty.football.ai.profile.ui.state.ClubProfileTab
import com.greendynasty.football.ai.profile.ui.state.ClubProfileUiState
import com.greendynasty.football.data.api.DatabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * T18 俱乐部画像页 ViewModel（V0.2 05 §二 + T18 方案 §六）。
 *
 * 持有：
 * - [ClubProfileUiState]：画像列表 + 筛选状态 + 选中详情
 * - 当前 Tab（默认列表）
 *
 * 核心交互：
 * 1. 进入页面 → 自动订阅 [observeAllProfiles] Flow，列表实时刷新
 * 2. 设置性格 / 战术 / 长期目标筛选 → 由 [ClubProfileUiState.filteredProfiles] 计算过滤后列表
 * 3. 点击列表项 → [selectProfile] 加载详情
 * 4. 切换到统计 Tab → [loadStatistics] 拉取统计
 * 5. 初始化存档 → [initializeForSave] 调用 Repository 批量生成画像
 *
 * @param app Application，用于初始化 [DatabaseManager]
 */
class ClubProfileViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = ClubProfileRepository(databaseManager, ClubProfileGenerator())

    /** UI 状态 */
    private val _uiState = MutableStateFlow(ClubProfileUiState())
    val uiState: StateFlow<ClubProfileUiState> = _uiState.asStateFlow()

    /** 当前 Tab */
    private val _currentTab = MutableStateFlow(ClubProfileTab.LIST)
    val currentTab: StateFlow<ClubProfileTab> = _currentTab.asStateFlow()

    init {
        // 订阅画像列表 Flow（自动刷新 UI）
        viewModelScope.launch {
            try {
                repository.observeAllProfiles().collectLatest { profiles ->
                    _uiState.value = _uiState.value.copy(
                        profiles = profiles,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载画像列表失败：${e.message}"
                )
            }
        }
        // 加载俱乐部名称映射（用于列表显示俱乐部名）
        loadClubNameMap()
    }

    // ==================== 列表加载与筛选 ====================

    /**
     * 加载俱乐部名称映射（clubId → clubName，来自 history.club）。
     */
    private fun loadClubNameMap() {
        viewModelScope.launch {
            try {
                val clubs = databaseManager.historyClubDao().getAllClubs().first()
                _uiState.value = _uiState.value.copy(
                    clubNameMap = clubs.associate { it.clubId to it.clubName }
                )
            } catch (_: Exception) {
                // 静默失败：UI 显示 clubId 即可
            }
        }
    }

    /** 设置性格筛选（null = 清除筛选）。 */
    fun setPersonalityFilter(personality: ClubPersonality?) {
        _uiState.value = _uiState.value.copy(personalityFilter = personality)
    }

    /** 设置战术风格筛选（null = 清除筛选）。 */
    fun setTacticalFilter(tactical: TacticalIdentity?) {
        _uiState.value = _uiState.value.copy(tacticalFilter = tactical)
    }

    /** 设置长期目标筛选（null = 清除筛选）。 */
    fun setGoalFilter(goal: LongTermGoal?) {
        _uiState.value = _uiState.value.copy(goalFilter = goal)
    }

    /** 设置搜索关键字。 */
    fun setSearchKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(searchKeyword = keyword)
    }

    /** 清除所有筛选。 */
    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            personalityFilter = null,
            tacticalFilter = null,
            goalFilter = null,
            searchKeyword = ""
        )
    }

    // ==================== 详情查看 ====================

    /** 选择俱乐部画像查看详情。 */
    fun selectProfile(profile: ClubProfile) {
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
    }

    /** 清除选中的画像（返回列表）。 */
    fun clearSelectedProfile() {
        _uiState.value = _uiState.value.copy(selectedProfile = null)
    }

    // ==================== 统计 Tab ====================

    /** 加载画像统计（仪表盘用）。 */
    fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val stats = repository.getProfileStatistics()
                _uiState.value = _uiState.value.copy(
                    statistics = stats,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "加载统计失败：${e.message}"
                )
            }
        }
    }

    // ==================== Tab 切换 ====================

    /** 切换 Tab。 */
    fun switchTab(tab: ClubProfileTab) {
        _currentTab.value = tab
        if (tab == ClubProfileTab.STATISTICS) {
            loadStatistics()
        }
    }

    // ==================== 存档初始化 ====================

    /**
     * 为当前存档批量生成画像（首次进入俱乐部画像页时调用）。
     *
     * - 若画像已存在则跳过（避免覆盖玩家手动调整的画像）
     * - 否则调用 [ClubProfileRepository.initializeForSave] 批量生成
     */
    fun initializeProfilesIfEmpty() {
        viewModelScope.launch {
            val current = _uiState.value.profiles
            if (current.isNotEmpty()) return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, message = "正在生成俱乐部画像…")
            try {
                val profiles = repository.initializeForSave()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "已生成 ${profiles.size} 家俱乐部画像"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "生成画像失败：${e.message}"
                )
            }
        }
    }

    // ==================== 消息消费 ====================

    /** 消费消息（避免 Snackbar 重复显示）。 */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        /** ViewModel 工厂。 */
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ClubProfileViewModel(app) as T
            }
        }
    }
}
