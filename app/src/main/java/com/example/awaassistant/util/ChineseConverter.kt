package com.example.awaassistant.util

import android.os.Build
import android.util.Log

object ChineseConverter {
    private const val TAG = "ChineseConverter"
    private var transliterator: Any? = null
    private var isIcuSupported = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // 使用反射或直接引用以防编译链上的 SDK 版本差异
                transliterator = android.icu.text.Transliterator.getInstance("Traditional-Simplified")
                isIcuSupported = true
                Log.d(TAG, "ICU Transliterator initialized successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ICU Transliterator", e)
            }
        }
    }

    // 常用繁简对照表，用作 API < 29 或 ICU 不可用时的兜底
    private const val TRADITIONAL = "萬與醜專業叢東絲丟兩嚴並喪個豐臨麗舉義烏樂喬習鄉書買亂爭於亞產畝親億僅從侖倉儀們價眾優伙會偉傳傷倫儉借傾債傭僉聯聽職肅膠腦膳膽腫臉脹腳輿興舊藝藥處裝補覺覽觀規觸設訪許話該詳語誤說請諸諾讀變讓讚識議譯護貝貞負貢財責賢賬貨質販貪貧貶購貯貫貳賤貼貴貸貿費賀貽賊賈賄貲賃賂贓資賅賑賚賒賦賭賡賣賴賺賽贅贇贈贍贏贖贗辦邊遼達遷過邁運還進遠違連遲邇逕選遜遞郵鄒鄔鄆酈鄧鄭鄰鄲鄴鄶鄺針釘釣釤釦釧釩釵釷釹鈹鈧鈸鈺錢鉦鉗鈷鉢鈳鉕鈽鈾鈿鉀鉅鉈鉉鉋鉍鉑鉬鉭鉸鉺鉻鉿銀銃銅銍銑銓銖銘銚銜銠銣銥銦銨銩銪銫銱銳銻銼鋇鋁鋅鋌鋏鋒鋗鋙鋝鋟鋣鋤鋥鋦鋨鋩鋪銳鋮鋯鋰鋱鋼鋶鋷鋸鋹鋺鋻錀錁錄錆錇錈錏錐錒錕錘錙錚錛錝錞錟錠錡錦錨錩錫锢錯録錳錸錺錻鍀鍁鍃鍄鍅鍆鍇鍈鍋鍍鍔鍘鍚鍛鍠鍡鍤鍥鍩鍫鍬鍭鍮鍳鍵鍶鍷鍺鍼鍽鍾鎂鎄鎇鎉鎊鎋鎌鎍鎎鎐鎔鎖鎗鎘鎛鎝鎞鎟鎠鎡鎢營鎤鎦鎧鎨鎩鎪鎬鎯鎰鎲鎳鎵鎷鎸鎺鎻鎼鎽鎾鎿鏀鏁鏂鏃鏄鏆旋鏈鏗鏊鏋鏌鏍鏎鏏鏐鏑鏒鏓鏔鏕鏖鏗鏘鏜鏝鏞鏟鏡鏢鏤鏨鏰鏱鏲鏳鏵鏶鋪鏷鏸鏹鏺鏻鏼鏽鏾鏿鐀鐁鐂鐃鐄鐅鐆鐇鐈鐉鐊鐋鐌鐍鐎鐏鐐鐓鐔鐕鐖鐗鐘鐙鐚鐛鐜鐝鐞鐵鐠鐡鐸鐣鐤鐥鐦鐧費鐩鐪鐫鐮鐯鐰鐱鐲鐳鐴鐵鐶鐷鐸鐹鐺鐻鐼鐽鐾鐿鑀鑁鑂鑃鑄鑅鑆鑇鑈鑉鑊鑋鑌鑍鑎鑏鑐鑑鑒鑓鑔鑕鑖鑗鑘鑙鑽礦鑜鑝鑞鑟爍鑡鑢鑣刨鑥鑦鑧鑨鑩爐鑫鑬鑭鑮鑯鑰镵鑲鑳鑴罐鑶鑷鑸镩鑺鑻鑼鑽鑾鑿钁钂钃钄長門閃閆閈閉開閌閎閏閑閒間閔閞閣閡閥閨閩閭閱闆闇闊闈闉闊闋闌闍闎闏闐闔闖關闠闡闢闤闥闦闧隊陽陰陣階際陸隴陳陘陝隉隕險隨隱隸隻雋雖雙雛雜雞難雨雪雫雰雯雲電願類顧飛館馬驅馱馴馳駁驢駢驍驕驗驚驛髓體髮魚魯鮮鱘鯨鱺鷄麵齊齒龍"
    private const val SIMPLIFIED  = "万与丑专业丛东丝丢两严并丧个丰临丽举义乌乐乔习乡书买乱争于亚产亩亲亿仅从仑仓仪们价众优伙会伟传伤伦俭借倾债佣佥联听职肃胶脑膳胆肿脸胀脚舆兴旧艺药处装补觉览观规触设访许话该详语误说请诸诺读变让赞识议译护贝贞负贡财责贤账货质贩贪贫贬购贮贯贰贱贴贵贷贸费贺贻贼贾贿资赁赂赃资赅赈赉赊赋赌赓卖赖赚赛赘赟赠赡赢赎赝办边辽达迁过迈运还进远违连迟迩迳选逊递邮邹邬郓郦邓郑邻郸邺郐邝针钉钓钐扣钏钒钗钍钕铍钪钹钰钱钲钳钴钵钶钷钸铀钿钾巨铊铉刨铋铂钼钽铰铒铬铪银铳铜铚铣铨铢铭铫衔铑铷 iridium 铟铵铥铕铯铞锐锑挫钡铝锌铤铗锋铘铻锊锓铘锄锃锔锇铓铺锐铖锆锂铽钢锍锱锯锓锾键仑锞录锖锫锩铔锥锕锟锤锱铮锛镦锬锭锜锦锚锠锡锢错录锰铼锠锔锝锨锪锾锔钔锴锳锅镀锷铡镴锻锽锽锸锲锘锹锹锳锳鉴键锶锔锗锾锾钟镁锿镅锾磅辖镰锔锔锔熔锁枪镉镈锝铋锔锔镃钨蓥锾镏铠锔铩锪镐锔镒镋镍镓锔镌锔锁锾锾锾镎锔锁锾镞镈锾旋链铿鏊锾镆螺锾锾镏镝锾锾锾锾鏖铿锵镗镘镛铲镜镖镂錾崩锾锾锾铧锾镤锾镪锾锾锾锈锾锾柜锾镏铙锾锾锾锾锾锾镴镋锾锾焦鐏镣镦镡锾锾锏钟登锾锾镦镢锾铁镠铁铎锾鼎锾锎锏镄燧锾镌镰锾锾锾镯镭锾铁环锾铎锾铛镈锾锾錾镱锾锾锾锾铸锾锾锾锾锾镬锾镔锾锾锾锾鉴鉴锾镲锧锾锾锾锾钻矿锾锾镴锾烁锾锾镖刨镥锾锾锾锾炉鑫锾镧镈锾钥镵镶锾锾罐锾镊锾镩锾锾锣钻銮凿镢镋锾锾长门闪闫闳闭开闶闳闰闲闲间闵锾阁阂阀闺闽闾阅板暗阔闱闉锾阕阑阇锾锾阗阖闯关阓阐辟阛闼锾锾队阳阴阵阶际陆陇陈陉陕隉陨险随隐隶只隽虽双雏杂鸡难雨雪雫雰雯云电愿类顾飞馆马驱驮驯驰驳驴骈骁骄验惊驿髓体发鱼鲁鲜鲟鲸鲡鸡面齐齿龙"

    /**
     * 将繁体中文（或简繁混杂文本）转换为规范的简体中文
     */
    fun toSimplified(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        
        // 优先使用 Android 系统自带的 ICU 库
        if (isIcuSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val result = (transliterator as android.icu.text.Transliterator).transliterate(text)
                if (result != null) return result
            } catch (e: Exception) {
                Log.e(TAG, "ICU transliterate failed, falling back to manual mapping.", e)
            }
        }

        // 兜底方案：单字一一映射
        val sb = StringBuilder(text.length)
        for (char in text) {
            val idx = TRADITIONAL.indexOf(char)
            if (idx != -1 && idx < SIMPLIFIED.length) {
                sb.append(SIMPLIFIED[idx])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }
}
