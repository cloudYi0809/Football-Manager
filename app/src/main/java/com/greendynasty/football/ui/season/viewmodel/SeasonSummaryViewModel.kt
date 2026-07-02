package com.greendynasty.football.ui.season.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.season.repository.SeasonRepository
import com.greendynasty.football.season.summary.SeasonSummaryGenerator
import com.greendynasty.football.ui.season.ui.state.ArchivedSeasonDisplay
import com.greendynasty.football.ui.season.ui.state.SeasonSummaryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 赛季总结页 ViewModel（T19 赛季归档 UI 入口）
 *
 * 持有 [SeasonRepository] 实例，暴露：
 * 1. 已归档赛季列表（从 season_archive 表读取）
 * 2. 选中赛季的完整摘要（反序列化 summary_json）
 * 3. 玩家操作：切换查看不同赛季
 *
 * UI 状态：[SeasonSummaryUiState]，5 种完备状态。
 *
 * @param app Application，用于初始化 DatabaseManager / SaveManager
 */
class SeasonSummaryViewModel(
    app: Application
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val saveManager = SaveManager.getInstance(app)

    private val summaryGenerator = SeasonSummaryGenerator(databaseManager)
    private val seasonRepository = SeasonRepository(databaseManager, summaryGenerator)

    /** UI 状态 */
    private val _uiState = MutableStateFlow<SeasonSummaryUiState>(SeasonSummaryUiState.Loading)
    val uiState: StateFlow<SeasonSummaryUiState> = _uiState.asStateFlow()

    /** 当前选中的赛季 ID */
    private var selectedSeasonId: Int? = null

    init {
        loadArchivedSeasons()
    }

    // ==================== 数据加载 ====================

    /** 加载已归档赛季列表 */
    fun loadArchivedSeasons() {
        viewModelScope.launch {
            try {
                val saveUuid = saveManager.currentSaveIdValue
                if (saveUuid == null) {
                    _uiState.value = SeasonSummaryUiState.Locked()
                    return@launch
                }

                val archives = seasonRepository.getAllArchivedSeasons(saveUuid)
                if (archives.isEmpty()) {
                    _uiState.value = SeasonSummaryUiState.Empty()
                    return@launch
                }

                // 默认选中最近一个赛季（seasonId 最大）
                val targetSeasonId = selectedSeasonId ?: archives.maxOf { it.seasonId }
                selectedSeasonId = targetSeasonId

                val displays = archives
                    .sortedByDescending { it.seasonId }
                    .map { archive ->
                        ArchivedSeasonDisplay(
                            archiveId = archive.archiveId,
                            seasonId = archive.seasonId,
                            seasonLabel = "赛季${archive.seasonId}",
                            createdAt = archive.createdAt,
                            isSelected = archive.seasonId == targetSeasonId
                        )
                    }

                val summary = seasonRepository.getSeasonSummary(saveUuid, targetSeasonId)
                val selectedIndex = displays.indexOfFirst { it.seasonId == targetSeasonId }

                _uiState.value = SeasonSummaryUiState.Normal(
                    archivedSeasons = displays,
                    selectedSummary = summary,
                    selectedIndex = selectedIndex.coerceAtLeast(0)
                )
            } catch (e: Exception) {
                Log.e(TAG, "加载归档赛季失败", e)
                _uiState.value = SeasonSummaryUiState.Error("加载赛季总结失败：${e.message}")
            }
        }
    }

    // ==================== 玩家操作 ====================

    /** 切换查看指定赛季 */
    fun selectSeason(seasonId: Int) {
        viewModelScope.launch {
            try {
                val saveUuid = saveManager.currentSaveIdValue ?: return@launch
                selectedSeasonId = seasonId

                val currentState = _uiState.value
                if (currentState !is SeasonSummaryUiState.Normal) return@launch

                val displays = currentState.archivedSeasons.map {
                    it.copy(isSelected = it.seasonId == seasonId)
                }
                val summary = seasonRepository.getSeasonSummary(saveUuid, seasonId)
                val selectedIndex = displays.indexOfFirst { it.seasonId == seasonId }

                _uiState.value = SeasonSummaryUiState.Normal(
                    archivedSeasons = displays,
                    selectedSummary = summary,
                    selectedIndex = selectedIndex.coerceAtLeast(0)
                )
            } catch (e: Exception) {
                Log.w(TAG, "切换赛季失败：seasonId=$seasonId, ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SeasonSummaryViewModel"

        /**
         * 创建 [SeasonSummaryViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档上下文。
         */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SeasonSummaryViewModel(app) as T
                }
            }
    }
}
