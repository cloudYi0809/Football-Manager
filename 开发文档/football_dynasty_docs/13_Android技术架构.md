# 13. Android 技术架构

---

# 一、推荐技术栈

- 语言：Kotlin
- UI：Jetpack Compose
- 数据库：SQLite + Room
- 架构：MVVM + Repository
- 异步：Kotlin Coroutines
- 状态：StateFlow
- 本地文件：App private storage
- 序列化：kotlinx.serialization 或 Moshi
- 图表：Compose Canvas 或轻量图表库

---

# 二、模块划分

```text
app/
├── core/
│   ├── database/
│   ├── model/
│   ├── repository/
│   ├── engine/
│   ├── utils/
│   └── config/
├── feature_home/
├── feature_squad/
├── feature_player/
├── feature_tactics/
├── feature_match/
├── feature_transfer/
├── feature_scout/
├── feature_youth/
├── feature_training/
├── feature_board/
├── feature_news/
├── feature_calendar/
├── feature_editor/
└── assets/
```

---

# 三、核心引擎模块

## 1. DateEngine

负责日期推进。

方法：

- advanceOneDay()
- advanceToNextMatch()
- processDailyEvents()
- processWeeklyEvents()
- processMonthlyEvents()
- processSeasonEnd()

## 2. MatchEngine

负责比赛模拟。

方法：

- simulateMatch(matchId)
- calculateTeamStrength()
- generateEvents()
- calculateScore()
- generateMatchReport()

## 3. GrowthEngine

负责球员成长。

方法：

- processMonthlyGrowth()
- updatePlayerAttributes()
- processDecline()
- processPotentialChange()

## 4. TransferEngine

负责转会。

方法：

- submitOffer()
- evaluateClubResponse()
- evaluatePlayerInterest()
- negotiateContract()
- completeTransfer()
- interruptHistoricalTransfer()

## 5. ScoutEngine

负责球探。

方法：

- createAssignment()
- progressAssignment()
- generateReport()
- revealProspectInfo()

## 6. YouthEngine

负责青训。

方法：

- generateYouthIntake()
- processYouthGrowth()
- promoteToFirstTeam()
- signYouthContract()

## 7. EventEngine

负责历史事件。

方法：

- checkHistoricalEvents()
- triggerEvent()
- applyEventChoice()
- generateButterflyEffects()

## 8. SaveEngine

负责存档。

方法：

- createSave()
- loadSave()
- autoSave()
- backupSave()
- migrateSave()

---

# 四、数据库策略

## 1. history.db

只读，放在 assets。

首次启动复制到：

```text
/data/data/{package}/databases/history.db
```

## 2. save.db

每个存档单独一个文件：

```text
/files/saves/save_001.db
```

## 3. 读取策略

Repository 同时读取 history.db 和当前 save.db。

例如球员详情：

- 基础资料来自 history.db.player
- 当前状态来自 save.db.save_player_state
- 属性显示可以合并历史属性和当前状态

---

# 五、Repository 设计

核心 Repository：

- PlayerRepository
- ClubRepository
- SquadRepository
- MatchRepository
- TransferRepository
- ScoutRepository
- YouthRepository
- TrainingRepository
- BoardRepository
- NewsRepository
- SaveRepository
- EventRepository

---

# 六、UI 架构

每个页面结构：

- Screen
- ViewModel
- UiState
- UiEvent
- Repository

示例：

```text
SquadScreen
SquadViewModel
SquadUiState
SquadRepository
```

UiState 必须包含：

- isLoading
- data
- errorMessage
- emptyState
- warning

---

# 七、性能建议

## 1. 列表分页

球员搜索、转会市场、数据库编辑器需要分页。

## 2. 索引

数据库必须建立索引：

- player name
- position
- nationality
- club_id
- season_id
- match_date

## 3. 异步推进

每日推进和赛季结算必须在后台协程执行，并显示进度。

## 4. 大任务分批

赛季结算、AI 转会、球员成长等应分批处理，避免 UI 卡死。

---

# 八、存档安全

必须支持：

- 手动保存
- 自动保存
- 比赛前自动保存
- 赛季结束前备份
- 存档版本号
- 存档迁移

---

# 九、资源加载

资源路径存数据库，例如：

- portraits/ronaldo.png
- logos/real_madrid.png
- kits/ac_milan_home_2002.png

若资源缺失，显示默认占位图。

---

# 十、错误处理

必须处理：

- 数据库不存在
- 存档损坏
- 外键缺失
- 球员资源缺失
- 日期推进失败
- 比赛模拟异常

错误要写入本地日志。

---

# 十一、开发环境建议

- Android Studio
- Kotlin 2.x
- Gradle Kotlin DSL
- Minimum SDK 可设 26 或以上
- Target SDK 按当前开发环境设置

---

# 十二、验收标准

1. App 可离线启动。
2. 可创建新存档。
3. 可读取 history.db。
4. 可显示球员、俱乐部、阵容。
5. 可推进日期。
6. 可模拟比赛。
7. 可保存和读取存档。
8. 大列表不卡顿。
9. 数据缺失时不崩溃。
