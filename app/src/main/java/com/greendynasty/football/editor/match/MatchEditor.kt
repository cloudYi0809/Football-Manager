package com.greendynasty.football.editor.match

import com.greendynasty.football.data.history.HistoryDatabase
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.editor.model.EditableMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 赛程编辑器（比赛日期 + 对阵 + 比分）。
 *
 * 职责：
 * - [loadEditable]：读取比赛原始数据构建草稿态
 * - [createNew]：创建新比赛草稿（match_id 自增，传入 0 由数据库分配）
 * - [updateMatchInfo]：修改比赛日期/主客队/比分/赛季/赛事
 * - [markDeleted]：标记草稿为待删除
 *
 * @param historyDb 历史库
 */
class MatchEditor(private val historyDb: HistoryDatabase) {

    /**
     * 加载比赛草稿态。
     * @param matchId 比赛 ID
     * @throws IllegalArgumentException 比赛不存在
     */
    suspend fun loadEditable(matchId: Int): EditableMatch = withContext(Dispatchers.IO) {
        val match = historyDb.matchDao().getMatch(matchId)
            ?: throw IllegalArgumentException("比赛不存在：$matchId")
        EditableMatch(original = match, draft = match.copy())
    }

    /**
     * 创建新比赛草稿。
     * @param seasonId 赛季 ID
     * @param competitionId 赛事 ID
     */
    fun createNew(seasonId: Int, competitionId: Int): EditableMatch {
        val newMatch = MatchEntity(
            matchId = 0, // autoGenerate
            seasonId = seasonId,
            competitionId = competitionId,
            matchDate = "",
            homeClubId = 0,
            awayClubId = 0,
            homeScoreReal = null,
            awayScoreReal = null,
            homeScoreSim = null,
            awayScoreSim = null,
            status = "scheduled",
            isHistorical = 1,
            matchStatsJson = null
        )
        return EditableMatch(original = null, draft = newMatch)
    }

    /**
     * 修改比赛字段。
     * @param field 字段名（match_date / home_club_id / away_club_id / home_score_real / away_score_real / season_id / competition_id）
     */
    fun updateMatchInfo(editable: EditableMatch, field: String, value: Any?): EditableMatch {
        val d = editable.draft
        val updated = when (field) {
            "match_date" -> d.copy(matchDate = value as String)
            "home_club_id" -> d.copy(homeClubId = value as Int)
            "away_club_id" -> d.copy(awayClubId = value as Int)
            "home_score_real" -> d.copy(homeScoreReal = value as Int?)
            "away_score_real" -> d.copy(awayScoreReal = value as Int?)
            "season_id" -> d.copy(seasonId = value as Int)
            "competition_id" -> d.copy(competitionId = value as Int)
            else -> throw IllegalArgumentException("不支持的字段：$field")
        }
        return editable.copy(draft = updated)
    }

    /** 标记草稿为待删除。 */
    fun markDeleted(editable: EditableMatch): EditableMatch = editable.copy(isDeleted = true)
}
