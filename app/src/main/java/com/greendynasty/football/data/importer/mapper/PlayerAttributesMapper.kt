package com.greendynasty.football.data.importer.mapper

import com.greendynasty.football.data.history.entity.PlayerAttributesEntity
import com.greendynasty.football.data.importer.parser.CsvRow
import com.greendynasty.football.data.importer.parser.FieldConverter

/**
 * 球员属性映射器（object 单例，无状态）
 *
 * 将 player_attributes.csv 行数据转换为 [PlayerAttributesEntity]。
 *
 * 完整 30+ 属性字段映射：
 * - 主键：player_id + season_id（复合唯一）
 * - 能力值：ca / pa（经 FieldConverter.fmCaToGameCa 从 FM 1-200 转为游戏 1-100）
 * - 技术属性：shooting / finishing / long_shots / passing / crossing / dribbling / technique / first_touch
 * - 身体属性：pace / acceleration / strength / stamina / balance / agility / jumping
 * - 防守属性：defending / tackling / marking / positioning / heading
 * - 精神属性：vision / decision / composure / leadership / work_rate / teamwork
 * - 隐藏属性：injury_proneness / big_match / consistency / professionalism / ambition / loyalty
 * - 门将属性：gk_diving / gk_reflexes / gk_handling / gk_positioning / gk_one_on_one（默认 0）
 *
 * 容错：所有属性字段缺失时使用 Entity 默认值（普通属性 50，门将属性 0）。
 */
object PlayerAttributesMapper : EntityMapper<PlayerAttributesEntity> {

    override fun map(row: CsvRow): PlayerAttributesEntity {
        return PlayerAttributesEntity(
            // 主键 id 自增，由 Room 写入时分配
            playerId = row.getInt("player_id")
                ?: throw IllegalArgumentException("player_id 缺失，无法映射 PlayerAttributesEntity"),
            seasonId = row.getInt("season_id")
                ?: throw IllegalArgumentException("season_id 缺失，无法映射 PlayerAttributesEntity"),

            // 能力值（FM 1-200 → 游戏 1-100）
            ca = FieldConverter.fmCaToGameCa(row.getInt("ca")) ?: 50,
            pa = FieldConverter.fmCaToGameCa(row.getInt("pa")) ?: 50,

            // 技术属性
            shooting = row.getIntOrDefault("shooting", 50),
            finishing = row.getIntOrDefault("finishing", 50),
            longShots = row.getIntOrDefault("long_shots", 50),
            passing = row.getIntOrDefault("passing", 50),
            crossing = row.getIntOrDefault("crossing", 50),
            dribbling = row.getIntOrDefault("dribbling", 50),
            technique = row.getIntOrDefault("technique", 50),
            firstTouch = row.getIntOrDefault("first_touch", 50),

            // 身体属性
            pace = row.getIntOrDefault("pace", 50),
            acceleration = row.getIntOrDefault("acceleration", 50),
            strength = row.getIntOrDefault("strength", 50),
            stamina = row.getIntOrDefault("stamina", 50),
            balance = row.getIntOrDefault("balance", 50),
            agility = row.getIntOrDefault("agility", 50),
            jumping = row.getIntOrDefault("jumping", 50),

            // 防守属性
            defending = row.getIntOrDefault("defending", 50),
            tackling = row.getIntOrDefault("tackling", 50),
            marking = row.getIntOrDefault("marking", 50),
            positioning = row.getIntOrDefault("positioning", 50),
            heading = row.getIntOrDefault("heading", 50),

            // 精神属性
            vision = row.getIntOrDefault("vision", 50),
            decision = row.getIntOrDefault("decision", 50),
            composure = row.getIntOrDefault("composure", 50),
            leadership = row.getIntOrDefault("leadership", 50),
            workRate = row.getIntOrDefault("work_rate", 50),
            teamwork = row.getIntOrDefault("teamwork", 50),

            // 隐藏/特殊属性
            injuryProneness = row.getIntOrDefault("injury_proneness", 50),
            bigMatch = row.getIntOrDefault("big_match", 50),
            consistency = row.getIntOrDefault("consistency", 50),
            professionalism = row.getIntOrDefault("professionalism", 50),
            ambition = row.getIntOrDefault("ambition", 50),
            loyalty = row.getIntOrDefault("loyalty", 50),

            // 门将专属属性（默认 0，非门将球员为 0）
            gkDiving = row.getIntOrDefault("gk_diving", 0),
            gkReflexes = row.getIntOrDefault("gk_reflexes", 0),
            gkHandling = row.getIntOrDefault("gk_handling", 0),
            gkPositioning = row.getIntOrDefault("gk_positioning", 0),
            gkOneOnOne = row.getIntOrDefault("gk_one_on_one", 0)
        )
    }
}
