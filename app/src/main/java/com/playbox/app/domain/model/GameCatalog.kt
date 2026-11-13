package com.playbox.app.domain.model

object GameCatalog {
    val all: List<GameInfo> = listOf(
        GameInfo(
            id = GameId.GAME_2048,
            name = "2048",
            tagline = "滑动合并数字，向 2048 前进。",
            rules = listOf(
                "在 4×4 棋盘上向四个方向滑动，所有数字方块一起移动。",
                "两个相同数字碰撞时合并为一个，并得到对应分数。",
                "每次有效滑动会生成一个新方块；棋盘填满且无法合并时游戏结束。",
            ),
            category = GameCategory.PUZZLE,
        ),
        GameInfo(
            id = GameId.TETRIS,
            name = "俄罗斯方块",
            tagline = "旋转与堆叠，填满整行消除。",
            rules = listOf(
                "移动并旋转下落的七种方块，让它们整齐堆叠。",
                "填满一整行即可消除得分，连续消除可获得额外分数。",
                "随着等级提升下落速度加快；方块堆到顶部时游戏结束。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.SNAKE,
            name = "贪吃蛇",
            tagline = "吃掉果实变长，别咬到自己。",
            rules = listOf(
                "通过滑动改变前进方向，蛇会持续向当前方向移动。",
                "吃到果实后身体增长并得分，速度会逐渐提升。",
                "撞到自己的身体即结束；是否穿墙可在开局前选择。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.BREAKOUT,
            name = "打砖块",
            tagline = "拖动挡板反弹小球，击碎砖墙。",
            rules = listOf(
                "拖动屏幕底部挡板，将小球反弹向上击碎砖块。",
                "不同颜色砖块分值不同；清空全部砖块即可过关。",
                "漏接小球会失去一条生命，三条生命耗尽时游戏结束。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.MINESWEEPER,
            name = "扫雷",
            tagline = "推理雷区，标记所有危险方格。",
            rules = listOf(
                "翻开方格，数字表示相邻八格中的地雷数量。",
                "长按方格插旗标记地雷；第一次翻开的方格保证安全。",
                "翻开所有非雷方格即获胜，翻开地雷则游戏结束。",
                "提供初级、中级、高级三档棋盘与雷数。",
            ),
            category = GameCategory.STRATEGY,
        ),
        GameInfo(
            id = GameId.MEMORY,
            name = "记忆翻牌",
            tagline = "记住位置，找出全部相同图案。",
            rules = listOf(
                "可选择 4×4、4×5 或 5×6 牌面，每次翻开两张卡片。",
                "图案相同即可配对并保持翻开，不同则短暂停留后盖回。",
                "用更少的步数和更短的时间完成全部配对，成绩更好。",
            ),
            category = GameCategory.CASUAL,
        ),
        GameInfo(
            id = GameId.SUDOKU,
            name = "数独",
            tagline = "填入 1–9，让每行每列每宫不重复。",
            rules = listOf(
                "在 9×9 棋盘中填入数字，使每行、每列、每个 3×3 宫都包含 1–9。",
                "每道题均由程序生成，并保证只有唯一解。",
                "可标记候选数；填入错误数字时会给出提示。",
                "提供简单、中等、困难三档难度。",
            ),
            category = GameCategory.PUZZLE,
        ),
        GameInfo(
            id = GameId.TIC_TAC_TOE,
            name = "井字棋",
            tagline = "连成三子，挑战三档 AI。",
            rules = listOf(
                "双方轮流在 3×3 棋盘中落子，先将三子连成一线者获胜。",
                "随机、简单、完美三档 AI：完美 AI 使用极小化极大算法，不会被击败。",
                "棋盘填满且无人三连时，本局平局。",
            ),
            category = GameCategory.STRATEGY,
        ),
        GameInfo(
            id = GameId.FIFTEEN_PUZZLE,
            name = "数字华容道",
            tagline = "移动滑块，把 1–15 按顺序排好。",
            rules = listOf(
                "点击空位同行或同列的滑块，或直接滑动棋盘。",
                "被推动的整行/整列滑块会连续滑入空位。",
                "每次开局都会随机打乱，并保证拼图有解。",
                "将数字 1–15 依次排列好即完成；步数越少成绩越好。",
            ),
            category = GameCategory.PUZZLE,
        ),
        GameInfo(
            id = GameId.DINO,
            name = "谷歌小恐龙",
            tagline = "跳过仙人掌，跑得越远越好。",
            rules = listOf(
                "点击屏幕或跳跃按钮，让小恐龙越过仙人掌。",
                "奔跑距离越远，速度越快，得分越高。",
                "撞到仙人掌即结束；跳起后可以越过较矮的仙人掌。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.BALL_BATTLE,
            name = "小球对对碰",
            tagline = "自由加点，单机挑战 AI，联机对战好友。",
            rules = listOf(
                "每局拥有 12 个技能点，可自由分配到力量、生命、速度、体型和暴击。",
                "技能点分配完成后开始自动对战，两个球在场地中反弹并碰撞；速度会随渲染尺寸等比提升。",
                "碰撞时双方同时结算伤害，生命归零的一方落败；45 秒后按剩余生命比例判定。",
                "单机模式对手为随机加点 AI；联机模式与好友各自加点后开战。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.GOMOKU,
            name = "五子棋",
            tagline = "十五路棋盘，先连成五子者胜。",
            rules = listOf(
                "15×15 棋盘，黑棋先手，双方轮流在交叉点落子。",
                "横、竖、斜任意方向先连成五子或更多者获胜。",
                "支持人机对战与双人同屏；人机模式可选择执黑先手或执白后手。",
                "人机模式可使用悔棋，会同时撤回 AI 的上一手；双人同屏只撤回一手。",
            ),
            category = GameCategory.STRATEGY,
        ),
        GameInfo(
            id = GameId.CHESS,
            name = "国际象棋",
            tagline = "8×8 棋盘，王车易位、升变与将死。",
            rules = listOf(
                "标准 8×8 国际象棋，白棋先手，目标是将死对方的王。",
                "支持王车易位、吃过路兵、兵升变、将军、将死、逼和、五十回合与三次重复局面。",
                "支持人机对战与双人同屏；人机模式可选择执白先手或执黑后手。",
                "人机模式悔棋会同时撤回 AI 的上一手；双人同屏只撤回一手。",
            ),
            category = GameCategory.STRATEGY,
        ),
        GameInfo(
            id = GameId.EATCAT,
            name = "吃掉小猫猫",
            tagline = "本地网页音游，原生读取得分与 CPS。",
            rules = listOf(
                "内置开源的 eatcat 网页音游，可离线游玩。",
                "网页负责判定、连击和计分；原生界面实时读取网页上报的得分、命中、行数和 CPS。",
                "结算时原生界面同步网页数据并记录本机最高分。",
                "网页内可设置键数、键型、时间限制和音效。",
            ),
            category = GameCategory.ARCADE,
        ),
        GameInfo(
            id = GameId.REACTION,
            name = "反应力挑战",
            tagline = "屏息等待变色，第一时间点击。",
            rules = listOf(
                "开始前跟随音频节拍完成 4 轮延迟校准，补偿系统与硬件的输入延迟。",
                "屏幕会先显示等待状态，随后突然变色。",
                "看到颜色变化的瞬间立即点击；过早点击会被判定违规。",
                "每轮记录反应毫秒数，并统计平均反应时间与最佳成绩。",
            ),
            category = GameCategory.CASUAL,
            lowerScoreIsBetter = true,
        ),
    )

    private val byId: Map<GameId, GameInfo> = all.associateBy(GameInfo::id)
    private val bySlug: Map<String, GameInfo> = all.associateBy { it.id.slug }

    fun findById(id: GameId): GameInfo? = byId[id]

    fun findBySlug(slug: String): GameInfo? = bySlug[slug]
}
