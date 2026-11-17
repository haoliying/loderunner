package com.haoly.loderunner

/**
 * 常量与复古调色板。
 *
 * 配色思路：中华学习机 / Apple II 上的《警察抓小偷》——黑底、土黄砖墙、
 * 钢灰实心块、木色梯子、黄金块；小偷是黑白条纹囚服 + 黑面罩，警察是藏蓝警服 + 黄帽徽。
 */
object Cfg {

    const val W = 28
    const val H = 16

    // 地形
    const val T_EMPTY = 0
    const val T_BRICK = 1     // 可挖
    const val T_SOLID = 2     // 钢块，不可挖不可穿
    const val T_LADDER = 3
    const val T_BAR = 4       // 横杆，可悬挂
    const val T_TRAP = 5      // 挖出来的坑（会回填）
    const val T_EXIT = 6      // 隐藏出口梯（收完金块后激活）

    // 速度（格/秒）—— 小偷比警察快，跑得掉但甩不干净
    const val P_SPEED = 4.5f
    const val P_CLIMB = 3.3f
    const val P_FALL = 6.4f
    const val G_SPEED = 3.45f
    const val G_CLIMB = 2.7f
    const val G_FALL = 5.6f

    const val DIG_CD = 0.32f        // 挖洞冷却
    const val TRAP_TIME = 3.6f      // 坑回填时间
    const val READY_TIME = 2.2f     // 每关开场警察冻结时间，给玩家反应余量
    const val GOLD_SCORE = 100
    const val CLEAR_SCORE = 500
    const val LIVE_BONUS = 250
    const val START_LIVES = 3
    const val HIT_DIST = 0.62f      // 警察抓到小偷的判定距离

    // ---------------------------------------------------------------- 调色板
    // 场景
    const val C_BG = 0xFF06060C.toInt()
    const val C_BG2 = 0xFF100C1C.toInt()
    const val C_CAVE = 0xFF1A1526.toInt()
    const val C_FRAME = 0xFF3A4A7A.toInt()
    const val C_FRAME2 = 0xFF141A2C.toInt()

    // 砖块（可挖）
    const val C_BRICK = 0xFF9C5230.toInt()
    const val C_BRICK_LT = 0xFFC07048.toInt()
    const val C_BRICK_DK = 0xFF6B331C.toInt()
    const val C_MORTAR = 0xFF3E1E12.toInt()

    // 钢块（不可挖）
    const val C_SOLID = 0xFF5C667E.toInt()
    const val C_SOLID_LT = 0xFF9AA6BE.toInt()
    const val C_SOLID_DK = 0xFF2E3446.toInt()
    const val C_RIVET = 0xFFB8C2D6.toInt()

    // 梯子 / 横杆
    const val C_LADDER = 0xFFE8C87A.toInt()
    const val C_LADDER_DK = 0xFF8A6A2A.toInt()
    const val C_LADDER_LT = 0xFFF6E6B4.toInt()
    const val C_BAR = 0xFFC6CCDC.toInt()
    const val C_BAR_DK = 0xFF6E7688.toInt()
    const val C_BAR_LT = 0xFFEEF2FA.toInt()

    // 坑
    const val C_TRAP = 0xFF07050A.toInt()
    const val C_DIRT = 0xFF8A5730.toInt()
    const val C_DIRT_DK = 0xFF4A2C16.toInt()

    // 金块
    const val C_GOLD = 0xFFFFD24A.toInt()
    const val C_GOLD_LT = 0xFFFFF3B0.toInt()
    const val C_GOLD_DK = 0xFFB87A14.toInt()

    // 出口梯
    const val C_EXIT = 0xFF2BE04A.toInt()
    const val C_EXIT_DK = 0xFF127A28.toInt()
    const val C_EXIT_LT = 0xFFC8FFD4.toInt()

    // 通用
    const val C_WHITE = 0xFFF2F2F2.toInt()
    const val C_BLACK = 0xFF12141C.toInt()
    const val C_RED = 0xFFE23B4A.toInt()
    const val C_RED2 = 0xFF8E1B2A.toInt()
    const val C_GREEN = 0xFF2BE04A.toInt()
    const val C_CYAN = 0xFF4FE8E0.toInt()
    const val C_YELLOW = 0xFFFFD24A.toInt()
    const val C_ORANGE = 0xFFFF8A28.toInt()
    const val C_SKIN = 0xFFF5C9A0.toInt()

    // 小偷（玩家）
    const val C_TH_HAT = 0xFF2A3550.toInt()
    const val C_TH_MASK = 0xFF14161F.toInt()
    const val C_TH_STRIPE = 0xFF1B1F2E.toInt()
    const val C_TH_PANTS = 0xFF3A4A78.toInt()

    // 警察
    const val C_PO_CAP = 0xFF1E3A8A.toInt()
    const val C_PO_CAP_DK = 0xFF0E1B3A.toInt()
    const val C_PO_UNI = 0xFF2F5BC0.toInt()
    const val C_PO_UNI_DK = 0xFF1B3570.toInt()
    const val C_PO_PANTS = 0xFF1E3A8A.toInt()
    const val C_BADGE = 0xFFFFD23F.toInt()
}
