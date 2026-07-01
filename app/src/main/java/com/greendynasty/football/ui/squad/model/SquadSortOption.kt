package com.greendynasty.football.ui.squad.model

/**
 * 阵容排序选项（8 种）。
 *
 * 每个枚举自带 [comparator]，直接对 [PlayerWithState] 列表排序。
 * 列表表头点击与排序下拉均复用此枚举。
 *
 * 8 种：CA 降序 / PA 降序 / 年龄升序 / 身价降序 / 合同升序 / 体能降序 / 士气降序 / 姓名升序。
 */
enum class SquadSortOption(val displayName: String, val comparator: Comparator<PlayerWithState>) {

    CA_DESC("能力降序", compareByDescending { it.ca }),

    PA_DESC("潜力降序", compareByDescending { it.pa }),

    AGE_ASC("年龄升序", compareBy { it.age }),

    VALUE_DESC("身价降序", compareByDescending { it.marketValue }),

    CONTRACT_ASC("合同到期升序", compareBy(nullsLast()) { it.contractUntil }),

    CONDITION_DESC("体能降序", compareByDescending { it.condition }),

    MORALE_DESC("士气降序", compareByDescending { it.morale }),

    NAME_ASC("姓名升序", compareBy { it.name });

    companion object {
        /** 默认排序 */
        val DEFAULT: SquadSortOption = CA_DESC
    }
}
