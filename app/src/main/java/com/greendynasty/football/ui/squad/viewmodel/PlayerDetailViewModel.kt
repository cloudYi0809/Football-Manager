package com.greendynasty.football.ui.squad.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greendynasty.football.data.api.DatabaseManager
import com.greendynasty.football.data.save.management.SaveManager
import com.greendynasty.football.ui.squad.data.PlayerDetail
import com.greendynasty.football.ui.squad.data.SquadRepository
import com.greendynasty.football.ui.squad.model.PlayerAction
import com.greendynasty.football.ui.squad.ui.state.PlayerDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 球员详情页 ViewModel。
 *
 * 通过 [SquadRepository.getPlayerDetail] 聚合 10 模块数据，
 * 暴露 [PlayerDetailUiState]（Loading / Error / Normal）。
 */
class PlayerDetailViewModel(
    app: Application,
    saveId: Int,
    clubId: Int
) : AndroidViewModel(app) {

    private val databaseManager = DatabaseManager.getInstance(app)
    private val repository = SquadRepository(databaseManager, saveId, clubId)

    private val _uiState = MutableStateFlow<PlayerDetailUiState>(PlayerDetailUiState.Loading)
    val uiState: StateFlow<PlayerDetailUiState> = _uiState.asStateFlow()

    /** 操作结果提示 */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /**
     * 加载球员详情。
     *
     * @param playerId 球员 ID
     */
    fun loadPlayer(playerId: Int) {
        _uiState.value = PlayerDetailUiState.Loading
        viewModelScope.launch {
            val detail: PlayerDetail? = repository.getPlayerDetail(playerId)
            _uiState.value = if (detail != null) {
                PlayerDetailUiState.Normal(detail)
            } else {
                PlayerDetailUiState.Error("加载球员详情失败（id=$playerId）")
            }
        }
    }

    /**
     * 执行详情页操作（7 种）。
     */
    fun performAction(playerId: Int, action: PlayerAction) {
        viewModelScope.launch {
            val ok = repository.performPlayerAction(playerId, action)
            _actionMessage.value = if (ok) {
                "${action.displayName}操作已提交"
            } else {
                "${action.displayName}操作失败"
            }
        }
    }

    /** 消费操作结果消息 */
    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    companion object {
        /**
         * 创建 [PlayerDetailViewModel] 工厂。
         * 自动从 [SaveManager] 读取当前存档与经理俱乐部 ID。
         *
         * @param playerId 目标球员 ID
         */
        fun factory(app: Application, playerId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val saveManager = SaveManager.getInstance(app)
                    val saveId = saveManager.currentSaveIdValue?.toIntOrNull() ?: 1
                    val clubId = runCatching {
                        kotlinx.coroutines.runBlocking {
                            saveManager.getCurrentSaveInfo()?.managerClubId ?: 1
                        }
                    }.getOrDefault(1)
                    return PlayerDetailViewModel(app, saveId, clubId) as T
                }
            }
    }
}
