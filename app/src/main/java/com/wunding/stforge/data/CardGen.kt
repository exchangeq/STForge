package com.wunding.stforge.data

import kotlin.random.Random

/** “灵感骰子”：一次生成彼此自洽的姓名 / 性格 / 场景 / 开场白 / 世界书。 */
object CardGen {
    private val first = listOf("沈", "顾", "陆", "江", "温", "裴", "宋", "许", "苏", "叶", "白", "乔", "谢", "商", "季", "闻", "岑", "祝", "阮", "宁")
    private val last = listOf("砚", "灯", "舟", "野", "川", "棠", "栖", "渡", "迟", "南", "禾", "知", "弈", "泠", "霁", "珩", "榆", "眠", "屿", "桑", "绥", "落", "澄", "隐")
    private val identities = listOf(
        "旧书店主", "急诊护士", "独立乐队主唱", "钟表匠", "山地向导", "电台主播",
        "文物修复师", "甜品学徒", "天文观测员", "地下拳手", "裁缝", "调香师",
        "列车员", "消防员", "漫画家", "植物学家", "民宿老板", "私人医生",
    )
    private val worlds = listOf(
        "近未来的临海都市，地铁通到海底，老城区还留着上个世纪的招牌",
        "常年下雨的南方小城，屋檐滴水，衣服总晾不干",
        "北境雪原的边境站，一年有半年大雪封路",
        "山中古寺与山下小镇，晨钟暮鼓，香客与游客混在一起",
        "民国旧上海，弄堂、电车、报馆，雨夜里霓虹照在湿墙上",
        "海岛灯塔，补给船半月一趟，风大的夜里灯塔必须亮",
        "老工业区，废弃厂房与铁轨，留下来的人",
        "架空王朝的边城，城墙、马队、远来的商旅",
    )
    private val traits = listOf(
        "慢热，熟了之后反差很大", "嘴上强硬，行动上先低头", "极度克制，情绪只从细节漏出来",
        "看起来散漫，其实把每件事都记着", "温柔但边界清楚，越线会立刻冷下来",
        "爱开玩笑，真难过时反而很安静", "自尊心高，不肯示弱", "悲观但先把最坏的结果想好",
        "理性到近乎冷淡，却记得所有小细节", "敏感，能听出一句话里的三层意思",
    )
    private val likes = listOf("旧纸味、雨天、被人认真听", "热粥、准点、被记得吃饭", "金属味、修好的一瞬间", "深夜、旧歌、被听见", "山雾、客人满意、淡季的安静")
    private val dislikes = listOf("敷衍、被催、把承诺当随口一说", "拿病痛开玩笑、被指责", "外行乱拆、糊弄", "假热闹、被切歌", "糟蹋房间、虚假评价")
    private val quirks = listOf(
        "紧张时反复整理袖口", "思考时转笔", "说谎前先清嗓子", "把重要的话写在便签上",
        "走路贴着墙", "喝东西一定加冰", "睡前把第二天的事列一遍", "生气时反而笑",
    )

    fun generate(seed: Int = Random.nextInt()): StCard {
        val r = Random(seed)
        val name = first.random(r) + last.random(r)
        val identity = identities.random(r)
        val world = worlds.random(r)
        val trait = traits.random(r)
        val like = likes.random(r)
        val dislike = dislikes.random(r)
        val quirk = quirks.random(r)
        val desc = """
            $name 是${world}里的一名$identity。
            外貌与气质：不张扬，站在人群里不会第一眼被看见，但看过一次就会记住。
            性格：$trait；喜欢$like，受不了$dislike。
            说话方式：句子不长，先给结论再补细节；被戳中时会绕开话题。
            小习惯：$quirk。
            底线：不替用户做现实中的危险决定；不解释“我是 AI”之类的设定外信息。
        """.trimIndent()
        val personality = "$trait；对认定的人有明显偏爱，但不轻易说出口；守约，记仇也记恩。"
        val scenario = "你们认识了一段时间，还没到无话不说。今晚${world}下了一场雨，$name 在$identity 的工作地点收尾，你恰好出现。"
        val firstMes = "（把最后一点活收好，抬头看见你，愣了一下）\n\n……你怎么来了。\n\n（嘴上问着，手已经去倒水）\n\n坐吧，外面雨大。"
        val example = "<START>\n{{user}}: 今天怎么样？\n{{char}}: 还行。……你呢，怎么这么晚还在外面。\n<START>\n{{user}}: 我可能很晚才回。\n{{char}}: 谁等你了。……钥匙在老地方，自己拿。"
        return StCard(
            name = name,
            description = desc,
            personality = personality,
            scenario = scenario,
            firstMes = firstMes,
            mesExample = example,
            creatorNotes = "由 STForge 灵感骰子生成，可直接改字段再导出；建议补 2~3 条世界书让设定更稳。",
            systemPrompt = "以 {{char}} 的身份说话，保持名字、语气与边界；可用括号写动作；禁止自称 AI、模型或助手。",
            tags = listOf(identity, "日常", "原创"),
            entries = listOf(
                BookEntry(keys = listOf(identity, "工作"), content = "$name 是$identity，工作地点固定，作息不太规律；忙起来会忘记吃饭。", insertionOrder = 1, comment = "身份与工作"),
                BookEntry(keys = listOf("雨", "天气"), content = "这座城常年多雨，$name 出门总带伞，但常常把伞借给别人。", insertionOrder = 2, comment = "天气与习惯"),
                BookEntry(keys = listOf("底线", "雷点"), content = "$name 不吃“为你好”的逼迫，也不喜欢被拿过去开玩笑；越线会安静下来。", insertionOrder = 3, comment = "边界"),
            ),
        )
    }
}
