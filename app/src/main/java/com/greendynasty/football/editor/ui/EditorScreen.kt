@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.greendynasty.football.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.data.history.entity.ClubEntity
import com.greendynasty.football.data.history.entity.MatchEntity
import com.greendynasty.football.data.history.entity.PlayerEntity
import com.greendynasty.football.editor.club.FacilityType
import com.greendynasty.football.editor.model.EditOperation
import com.greendynasty.football.editor.model.EditOperationType
import com.greendynasty.football.editor.model.EditableClub
import com.greendynasty.football.editor.model.EditableMatch
import com.greendynasty.football.editor.model.EditablePlayer
import com.greendynasty.football.editor.ui.state.EditorTab
import com.greendynasty.football.editor.ui.state.EditorUiState
import com.greendynasty.football.editor.ui.viewmodel.EditorViewModel

/**
 * T25 数据编辑器入口 Composable（V0.2 + T25 任务要求 + 实现方案 §六 UI 结构）。
 *
 * 4 个 Tab：
 * 1. 球员编辑：球员列表 + 选中球员草稿 + 基础信息/属性/CA-PA 修改
 * 2. 俱乐部编辑：俱乐部列表 + 选中俱乐部草稿 + 声望/财政/设施修改
 * 3. 赛程编辑：比赛列表 + 选中比赛草稿 + 日期/对阵/比分修改
 * 4. 变更历史：操作历史 + 撤销/重做
 */
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar 消息
    LaunchedEffect((uiState as? EditorUiState.Normal)?.message) {
        (uiState as? EditorUiState.Normal)?.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is EditorUiState.Loading -> LoadingView()
                is EditorUiState.Locked -> EmptyView(state.reason)
                is EditorUiState.Empty -> EmptyView(state.reason)
                is EditorUiState.Error -> EmptyView(state.message)
                is EditorUiState.Normal -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        EditorTabRow(
                            currentTab = currentTab,
                            onSwitch = viewModel::switchTab,
                            undoableCount = state.undoableCount
                        )
                        when (currentTab) {
                            EditorTab.PLAYER -> PlayerTab(
                                state = state,
                                onSelect = viewModel::selectPlayer,
                                onCreateNew = viewModel::createNewPlayer,
                                onUpdateBasic = viewModel::updatePlayerBasicInfo,
                                onUpdateAttr = viewModel::updatePlayerAttribute,
                                onMarkDeleted = viewModel::markPlayerDeleted,
                                onSave = viewModel::savePlayer,
                                onCancel = viewModel::cancelPlayerEdit
                            )
                            EditorTab.CLUB -> ClubTab(
                                state = state,
                                onSelect = viewModel::selectClub,
                                onCreateNew = viewModel::createNewClub,
                                onUpdateBasic = viewModel::updateClubBasicInfo,
                                onUpdateReputation = viewModel::updateClubReputation,
                                onUpdateFinance = viewModel::updateClubFinance,
                                onUpdateFacility = viewModel::updateClubFacility,
                                onMarkDeleted = viewModel::markClubDeleted,
                                onSave = viewModel::saveClub,
                                onCancel = viewModel::cancelClubEdit
                            )
                            EditorTab.MATCH -> MatchTab(
                                state = state,
                                onSelect = viewModel::selectMatch,
                                onCreateNew = viewModel::createNewMatch,
                                onUpdate = viewModel::updateMatchInfo,
                                onMarkDeleted = viewModel::markMatchDeleted,
                                onSave = viewModel::saveMatch,
                                onCancel = viewModel::cancelMatchEdit
                            )
                            EditorTab.HISTORY -> HistoryTab(
                                state = state,
                                onUndo = viewModel::undo,
                                onRedo = viewModel::redo
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Tab Row ====================

@Composable
private fun EditorTabRow(
    currentTab: EditorTab,
    onSwitch: (EditorTab) -> Unit,
    undoableCount: Int
) {
    TabRow(selectedTabIndex = currentTab.ordinal) {
        EditorTab.values().forEach { tab ->
            Tab(
                selected = currentTab == tab,
                onClick = { onSwitch(tab) },
                text = {
                    val label = if (tab == EditorTab.HISTORY && undoableCount > 0) {
                        "${tab.title}($undoableCount)"
                    } else {
                        tab.title
                    }
                    Text(label)
                }
            )
        }
    }
}

// ==================== 球员 Tab ====================

@Composable
private fun PlayerTab(
    state: EditorUiState.Normal,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int) -> Unit,
    onUpdateBasic: (String, Any?) -> Unit,
    onUpdateAttr: (Int, String, Int) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val editable = state.currentEditablePlayer
    if (editable == null) {
        // 列表视图
        PlayerListView(
            players = state.players,
            onSelect = onSelect,
            onCreateNew = onCreateNew
        )
    } else {
        // 编辑视图
        PlayerEditView(
            editable = editable,
            onUpdateBasic = onUpdateBasic,
            onUpdateAttr = onUpdateAttr,
            onMarkDeleted = onMarkDeleted,
            onSave = onSave,
            onCancel = onCancel
        )
    }
}

@Composable
private fun PlayerListView(
    players: List<PlayerEntity>,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int) -> Unit
) {
    var newIdText by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // 新增区
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newIdText,
                onValueChange = { newIdText = it.filter { c -> c.isDigit() } },
                label = { Text("新球员 ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                newIdText.toIntOrNull()?.let { onCreateNew(it); newIdText = "" }
            }) { Text("新增") }
        }
        Divider()
        // 球员列表
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(players) { player ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    onClick = { onSelect(player.playerId) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${player.realName} (#${player.playerId})",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${player.nationality ?: "-"} | ${player.primaryPosition ?: "-"} | ${player.preferredFoot ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerEditView(
    editable: EditablePlayer,
    onUpdateBasic: (String, Any?) -> Unit,
    onUpdateAttr: (Int, String, Int) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val p = editable.draft
    val attrEntry = editable.attributesDraft.entries.firstOrNull()
    val attr = attrEntry?.value
    val seasonId = attrEntry?.key ?: 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "球员 #${p.playerId}${if (editable.isNew) " (新增)" else ""}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 基础信息
        SectionTitle("基础信息")
        EditableTextField("姓名", p.realName) { onUpdateBasic("real_name", it) }
        EditableTextField("国籍", p.nationality.orEmpty()) { onUpdateBasic("nationality", it) }
        EditableTextField("主要位置", p.primaryPosition.orEmpty()) { onUpdateBasic("primary_position", it) }
        EditableTextField("生日(YYYY-MM-DD)", p.birthDate.orEmpty()) { onUpdateBasic("birth_date", it) }
        EditableOptionalIntField("身高", p.height) { onUpdateBasic("height", it) }
        EditableOptionalIntField("体重", p.weight) { onUpdateBasic("weight", it) }
        EditableTextField("惯用脚", p.preferredFoot.orEmpty()) { onUpdateBasic("preferred_foot", it) }
        EditableIntField("退役年龄", p.retireAgeBase) { onUpdateBasic("retire_age_base", it) }

        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle("能力与属性（赛季 $seasonId）")
        if (attr != null) {
            EditableIntField("CA(1-200)", attr.ca) { onUpdateAttr(seasonId, "ca", it) }
            EditableIntField("PA(1-200)", attr.pa) { onUpdateAttr(seasonId, "pa", it) }
            EditableIntField("射门", attr.shooting) { onUpdateAttr(seasonId, "shooting", it) }
            EditableIntField("传球", attr.passing) { onUpdateAttr(seasonId, "passing", it) }
            EditableIntField("盘带", attr.dribbling) { onUpdateAttr(seasonId, "dribbling", it) }
            EditableIntField("速度", attr.pace) { onUpdateAttr(seasonId, "pace", it) }
            EditableIntField("防守", attr.defending) { onUpdateAttr(seasonId, "defending", it) }
            EditableIntField("视野", attr.vision) { onUpdateAttr(seasonId, "vision", it) }
        } else {
            Text("暂无属性数据（保存时会用默认值）", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        // 操作按钮
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
        }
        if (!editable.isNew) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onMarkDeleted) { Text("标记删除") }
        }
    }
}

// ==================== 俱乐部 Tab ====================

@Composable
private fun ClubTab(
    state: EditorUiState.Normal,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int) -> Unit,
    onUpdateBasic: (String, Any?) -> Unit,
    onUpdateReputation: (Int) -> Unit,
    onUpdateFinance: (Int) -> Unit,
    onUpdateFacility: (FacilityType, Int) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val editable = state.currentEditableClub
    if (editable == null) {
        ClubListView(clubs = state.clubs, onSelect = onSelect, onCreateNew = onCreateNew)
    } else {
        ClubEditView(
            editable = editable,
            onUpdateBasic = onUpdateBasic,
            onUpdateReputation = onUpdateReputation,
            onUpdateFinance = onUpdateFinance,
            onUpdateFacility = onUpdateFacility,
            onMarkDeleted = onMarkDeleted,
            onSave = onSave,
            onCancel = onCancel
        )
    }
}

@Composable
private fun ClubListView(
    clubs: List<ClubEntity>,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int) -> Unit
) {
    var newIdText by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newIdText,
                onValueChange = { newIdText = it.filter { c -> c.isDigit() } },
                label = { Text("新俱乐部 ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                newIdText.toIntOrNull()?.let { onCreateNew(it); newIdText = "" }
            }) { Text("新增") }
        }
        Divider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(clubs) { club ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    onClick = { onSelect(club.clubId) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${club.clubName} (#${club.clubId})",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${club.country ?: "-"} | 声望=${club.reputation} | 训练=${club.trainingLevel}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClubEditView(
    editable: EditableClub,
    onUpdateBasic: (String, Any?) -> Unit,
    onUpdateReputation: (Int) -> Unit,
    onUpdateFinance: (Int) -> Unit,
    onUpdateFacility: (FacilityType, Int) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val c = editable.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "俱乐部 #${c.clubId}${if (editable.isNew) " (新增)" else ""}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        SectionTitle("基础信息")
        EditableTextField("名称", c.clubName) { onUpdateBasic("club_name", it) }
        EditableTextField("国家", c.country.orEmpty()) { onUpdateBasic("country", it) }
        EditableTextField("城市", c.city.orEmpty()) { onUpdateBasic("city", it) }
        EditableOptionalIntField("成立年份", c.foundedYear) { onUpdateBasic("founded_year", it) }
        EditableTextField("球场名", c.stadiumName.orEmpty()) { onUpdateBasic("stadium_name", it) }
        EditableOptionalIntField("球场容量", c.stadiumCapacity) { onUpdateBasic("stadium_capacity", it) }

        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle("声望与财政")
        EditableIntField("声望(1-100)", c.reputation) { onUpdateReputation(it) }
        EditableIntField("财政等级(1-100)", c.financeLevel) { onUpdateFinance(it) }

        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle("设施等级")
        EditableIntField("训练设施(1-100)", c.trainingLevel) { onUpdateFacility(FacilityType.TRAINING, it) }
        EditableIntField("青训设施(1-100)", c.youthLevel) { onUpdateFacility(FacilityType.YOUTH, it) }
        EditableIntField("医疗设施(1-100)", c.financeLevel) { onUpdateFacility(FacilityType.MEDICAL, it) }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
        }
        if (!editable.isNew) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onMarkDeleted) { Text("标记删除") }
        }
    }
}

// ==================== 赛程 Tab ====================

@Composable
private fun MatchTab(
    state: EditorUiState.Normal,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int, Int) -> Unit,
    onUpdate: (String, Any?) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val editable = state.currentEditableMatch
    if (editable == null) {
        MatchListView(matches = state.matches, onSelect = onSelect, onCreateNew = onCreateNew)
    } else {
        MatchEditView(
            editable = editable,
            onUpdate = onUpdate,
            onMarkDeleted = onMarkDeleted,
            onSave = onSave,
            onCancel = onCancel
        )
    }
}

@Composable
private fun MatchListView(
    matches: List<MatchEntity>,
    onSelect: (Int) -> Unit,
    onCreateNew: (Int, Int) -> Unit
) {
    var seasonIdText by remember { mutableStateOf("1") }
    var compIdText by remember { mutableStateOf("1") }
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = seasonIdText,
                onValueChange = { seasonIdText = it.filter { c -> c.isDigit() } },
                label = { Text("赛季 ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = compIdText,
                onValueChange = { compIdText = it.filter { c -> c.isDigit() } },
                label = { Text("赛事 ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val s = seasonIdText.toIntOrNull() ?: 1
                val c = compIdText.toIntOrNull() ?: 1
                onCreateNew(s, c)
            }) { Text("新增") }
        }
        Divider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(matches) { match ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    onClick = { onSelect(match.matchId) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "#${match.matchId} ${match.matchDate}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${match.homeClubId} vs ${match.awayClubId} | " +
                                "比分 ${match.homeScoreReal ?: "-"}:${match.awayScoreReal ?: "-"} | " +
                                "赛季 ${match.seasonId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchEditView(
    editable: EditableMatch,
    onUpdate: (String, Any?) -> Unit,
    onMarkDeleted: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val m = editable.draft
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "比赛 #${m.matchId}${if (editable.isNew) " (新增)" else ""}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        SectionTitle("比赛信息")
        EditableTextField("日期(YYYY-MM-DD)", m.matchDate) { onUpdate("match_date", it) }
        EditableIntField("赛季 ID", m.seasonId) { onUpdate("season_id", it) }
        EditableIntField("赛事 ID", m.competitionId) { onUpdate("competition_id", it) }
        EditableIntField("主队 ID", m.homeClubId) { onUpdate("home_club_id", it) }
        EditableIntField("客队 ID", m.awayClubId) { onUpdate("away_club_id", it) }
        EditableOptionalIntField("主队比分", m.homeScoreReal) { onUpdate("home_score_real", it) }
        EditableOptionalIntField("客队比分", m.awayScoreReal) { onUpdate("away_score_real", it) }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
        }
        if (!editable.isNew) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onMarkDeleted) { Text("标记删除") }
        }
    }
}

// ==================== 变更历史 Tab ====================

@Composable
private fun HistoryTab(
    state: EditorUiState.Normal,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // 撤销/重做按钮
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onUndo,
                enabled = state.undoableCount > 0,
                modifier = Modifier.weight(1f)
            ) { Text("撤销 (${state.undoableCount})") }
            Button(
                onClick = onRedo,
                enabled = state.redoableCount > 0,
                modifier = Modifier.weight(1f)
            ) { Text("重做 (${state.redoableCount})") }
        }
        Divider()
        if (state.history.isEmpty()) {
            EmptyView("暂无变更历史")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.history) { op ->
                    OperationItem(op)
                }
            }
        }
    }
}

@Composable
private fun OperationItem(op: EditOperation) {
    val opLabel = when (op.operationType) {
        EditOperationType.INSERT -> "新增"
        EditOperationType.UPDATE -> "修改"
        EditOperationType.DELETE -> "删除"
    }
    val statusLabel = if (op.undone) " [已撤销]" else ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "$opLabel ${op.targetTable.displayName} #${op.targetId}$statusLabel",
                fontWeight = FontWeight.Bold
            )
            Text(
                op.timestamp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ==================== 通用视图 ====================

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/** 可编辑文本字段（String）。 */
@Composable
private fun EditableTextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}

/** 可编辑整数字段（支持空值）。 */
@Composable
private fun EditableOptionalIntField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter { c -> c.isDigit() }
            onChange(text.toIntOrNull())
        },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}

/** 不可空可编辑整数字段。 */
@Composable
private fun EditableIntField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter { c -> c.isDigit() }
            text.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true
    )
}
