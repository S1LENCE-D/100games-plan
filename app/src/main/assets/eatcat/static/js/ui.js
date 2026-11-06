class UI {
    static init() {
        setTimeout(function(){
            UI.refreshPatternList();
            UI.applyThemeFromSettings();
        }, 100);
    }

    static refreshPatternList() {
        var sel = document.getElementById('patternSelect');
        if (!sel) return;
        var k = 4;
        try { k = Game.keyCount || 4; } catch(e){}
        
        var currentValue = sel.value;
        var h = '<option value="__custom__">自定义</option>';
        
        // 读取自定义键型
        var savedPatterns = window.Storage ? Storage.loadPatterns() : {};
        var patternKeys = Object.keys(savedPatterns);
        for (var pi = 0; pi < patternKeys.length; pi++) {
            var pname = patternKeys[pi];
            var pp = savedPatterns[pname];
            var show = false;
            if (pp.keyCount === 0 || pp.keyCount === '0') show = true;
            else if (typeof pp.keyCount === 'number') show = (pp.keyCount === k);
            else if (Array.isArray(pp.keyCount)) show = pp.keyCount.indexOf(k) !== -1;
            if (show) h += '<option value="' + pp.pattern + '">' + pname + '</option>';
        }
        
        // 预设键型
        if (k === 1) {
            h += '<option value="1">纵连</option>';
        }
        if (k >= 2) {
            h += '<option value="@">乱</option>';
            h += '<option value="!">纯随机</option>';
            h += '<option value="@#">双纵</option>';
            h += '<option value="@##">三纵</option>';
        }
        if (k === 2) {
            h += '<option value="1+2">双压</option>';
            h += '<option value="1+2(0.4,1,0.4,2,0.2,1+2)">强弱交替</option>';
        }
        if (k >= 3 && k <= 10) {
            h += '<option value="3k:1232;4k:123432;5k:12345432;6k:1234565432;7k:123456765432;8k:12345678765432;9k:1234567898765432;10k:123456789a98765432">回旋楼梯</option>';
            h += '<option value="3k:123;4k:1234;5k:12345;6k:123456;7k:1234567;8k:12345678;9k:123456789;10k:123456789a">顺轮</option>';
            h += '<option value="3k:321;4k:4321;5k:54321;6k:654321;7k:7654321;8k:87654321;9k:987654321;10k:a987654321">逆轮</option>';
            h += '<option value="3k:1+2+3;4k:1+2+3+4;5k:1+2+3+4+5;6k:1+2+3+4+5+6;7k:1+2+3+4+5+6+7;8k:1+2+3+4+5+6+7+8;9k:1+2+3+4+5+6+7+8+9;10k:1+2+3+4+5+6+7+8+9+a">全押</option>';
            h += '<option value="3k:(0.5,1+3,0.5,2);4k:(0.5,1+4,0.5,2+3);5k:(0.333,1+5,0.333,2+4,0.334,3);6k:(0.333,1+6,0.333,2+5,0.334,3+4);7k:(0.25,1+7,0.25,2+6,0.25,3+5,0.25,4);8k:(0.25,1+8,0.25,2+7,0.25,3+6,0.25,4+5);9k:(0.2,1+9,0.2,2+8,0.2,3+7,0.2,4+6,0.2,5);10k:(0.2,1+a,0.2,2+9,0.2,3+8,0.2,4+7,0.2,5+6)">轴对称</option>'
        }
        if (k >= 3) {
            h += '<option value="!+@">双乱</option>';
        }
        if (k >= 4 && k % 2 === 0 && k<=10) {
            h += '<option value="4k:1+23+4;6k:1+2+34+5+6;8k:1+2+3+45+6+7+8;10k:1+2+3+4+56+7+8+9+a">对拍</option>';
            h += '<option value="4k:!(1,2)!(3,4);6k:!(1,2,3)!(4,5,6);8k:!(1,2,3,4)!(5,6,7,8);10k:!(1,2,3,4,5)!(6,7,8,9,a);">双手随机交互</option>';
            h += '<option value="4k:1243;6k:123654;8k:12348765;10k:12345a9876;">内轮</option>';
            h += '<option value="4k:2134;6k:321456;8k:43215678;10k:543216789a;">外轮</option>';
        }
        if (k === 4) {
            h += '<option value="1+42+3">对切</option>';
            h += '<option value="@(0.2,1~2)(0.2,3~4)">双轨大键</option>';
            h += '<option value="(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))(0.9,4(0.3,+@(2,1))(0.9,3(0.3,+@(2,1))))))))))))))))))))))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))(0.9,1(0.3,+@(3,4))(0.9,2(0.3,+@(3,4))))))))))))))))))))))">随机切</option>';
            h += '<option value="!+!(0.7,+!(0.2,+!))">大叠</option>';
            h += '<option value="!(0.7,+!(0.2,+!))">小叠</option>';
        }
        if (k === 8) {
            h+= '<option value="(0.25,1234,0.25,234,0.25,34,0.25,4)(0.25,5,0.25,65,0.25,765,0.25,8765)">不定长内轮</option>';
        }
        if (k >= 8 && k<=10) {
            h += '<option value="4~5~6/#(-1,1)">大键弯纵</option>';
            h += '<option value="8k:(0.167,1~2~3,0.167,2~3~4,0.167,3~4~5,0.167,4~5~6,0.167,5~6~7,0.167,6~7~8);9k:(0.143,1~2~3,0.143,2~3~4,0.143,3~4~5,0.143,4~5~6,0.143,5~6~7,0.143,6~7~8,0.143,7~8~9);10k:(0.125,1~2~3,0.125,2~3~4,0.125,3~4~5,0.125,4~5~6,0.125,5~6~7,0.125,6~7~8,0.125,7~8~9,0.125,8~9~a)">大键乱</option>';
            h += '<option value="8k:(0.333,1~2~3,0.333,2~3~4,0.334,3~4~5)(0.334,4~5~6,0.333,5~6~7,0.333,6~7~8);9k:(0.25,1~2~3,0.25,2~3~4,0.25,3~4~5,0.25,4~5~6)(0.25,4~5~6,0.25,5~6~7,0.25,6~7~8,0.25,7~8~9);10k:(0.25,1~2~3,0.25,2~3~4,0.25,3~4~5,0.25,4~5~6)(0.25,5~6~7,0.25,6~7~8,0.25,7~8~9,0.25,8~9~a)">大键交互</option>'
        }
        sel.innerHTML = h;
        // 恢复当前选择（例如用户刚改为"自定义"时刷新不应重置为"纯随机"）
        var restored = false;
        if (currentValue) {
            for (var oi = 0; oi < sel.options.length; oi++) {
                if (sel.options[oi].value === currentValue) {
                    sel.value = currentValue;
                    restored = true;
                    break;
                }
            }
        }
        if (!restored) {
            // 尝试匹配当前生效的键型（提取各预设中对应键数的纯配置）
            var activePattern = '';
            try { activePattern = Game.notePattern || ''; } catch(e) {}
            var matched = false;
            if (activePattern && typeof PatternValidator !== 'undefined') {
                for (var oj = 0; oj < sel.options.length; oj++) {
                    var ov = sel.options[oj].value;
                    if (ov === '__custom__') continue;
                    try {
                        if (PatternValidator.getKeyConfig(ov, k) === activePattern) {
                            sel.value = ov;
                            matched = true;
                            break;
                        }
                    } catch(e) {}
                }
            }
            if (!matched) sel.value = '__custom__';
        }
    }

    static onPatternSelectChange() {
        var sel = document.getElementById('patternSelect');
        var v = sel.value;
        var noteEl = document.getElementById('note');
        
        if (v === '__custom__') {
            // 切换到"自定义"时，从输入框读取用户输入
            if (noteEl && noteEl.value) {
                var extracted = PatternValidator ? PatternValidator.getKeyConfig(noteEl.value, Game.keyCount) : noteEl.value;
                Game.notePattern = extracted;
                Game.restart();
            }
            return;
        }
        
        // 选择预设：同步更新键型设置输入框，并保存到storage
        if (noteEl) noteEl.value = v;
        var extracted = PatternValidator ? PatternValidator.getKeyConfig(v, Game.keyCount) : v;
        Game.notePattern = extracted;
        // 保存到storage，确保下次打开设置时输入框显示正确值
        if (window.Storage) {
            var s = Storage.loadSettings();
            s.notePattern = v;
            Storage.saveSettings(s);
        }
        Game.restart();
    }

    static showRules() {
        var o = document.getElementById('rulesLayer');
        if (!o) return;
        o.innerHTML = '<div class="rules-content"><h2 style="color:#fff;text-align:center;">规则介绍</h2><div class="rules-text"><p>这是一款类似别踩白块的游戏，兼容键盘输入和点击输入。</p><h3>键型设置</h3><p>音符的生成由键型设置决定：</p><ul><li><b>数字/字母</b>：轨道序号，数字可表示1~9，字母可表示10~35</li><li><b>!</b>：随机轨道（允许纵连）</li><li><b>@</b>：与上一行不同的随机轨道</li><li><b>#</b>：与上一行相同的轨道</li><li><b>+</b>：多压连接符</li><li><b>~</b>：大键连接符</li><li><b>/</b>：循环符，/ 前的内容只出现一次，/ 后的内容重复</li></ul><h3>多压 a+b</h3><p>例如 <code>1+23+4</code>：第一行1、2轨双压；第二行3、4轨双压。</p><h3>大键 a~b</h3><p>与多压类似，但只需按任意一轨即可判定，由染色按键表示。</p><h3>有限随机键 !(a,b(,c)) / @(a,b(,c)) / #(a(,b))</h3><p><code>!(1,2)</code>：表示在1、2轨中任选一个。</p><p><code>@(1,2,3)</code>：表示在1、2、3轨中任选一个，且不与上一行相同。</p><p><code>@!(1,-1)</code>：表示第一行随机，第二行是第一行按键序号+1或-1的按键。</p><h3>随机化 (概率,配置(,概率2,配置2))</h3><p><code>(0.8,@)1</code>：每轮循环有0.8的概率出现@1，0.2的概率只出现1。</p><h3>游戏模式</h3><ul><li><b>普通</b>：按错即结束</li><li><b>保护</b>：按错暂停，可通过按下正确键位以继续</li></ul></div><button type="button" class="btn btn-secondary btn-lg" onclick="UI.hideRules()">关闭</button></div>';
        o.style.display = 'block';
    }
    static hideRules() {
        var o = document.getElementById('rulesLayer'); if(o) o.style.display = 'none';
    }

    static showHistory() {
        var o = document.getElementById('historyLayer');
        if (!o) return;
        var history = {};
        if (window.Storage) {
            try { history = Storage.getAllBestRecords(); } catch(e) {}
        }
        var html = '<div class="history-content">';
        html += '<h2 style="color:#fff;text-align:center;">历史成绩</h2>';
        html += '<div class="history-list">';
        var keys = Object.keys(history);
        if (keys.length === 0) {
            html += '<p style="color:#fff;text-align:center;">暂无记录</p>';
        } else {
            for (var ck = 0; ck < keys.length; ck++) {
                var catKey = keys[ck];
                var record = history[catKey];
                // 格式化显示：4k_@_3s_normal → 4k    @    3s    正常模式
                var parts = catKey.split('_');
                var displayParts = [];
                for (var pi = 0; pi < parts.length; pi++) {
                    var p = parts[pi];
                    if (p === 'normal') displayParts.push('正常模式');
                    else if (p === 'immortal') displayParts.push('不死模式');
                    else if (p === 'protected') displayParts.push('保护模式');
                    else displayParts.push(p);
                }
                var displayName = displayParts.join('    ');
                html += '<div class="history-item">';
                html += '<div class="history-category">' + displayName + '</div>';
                html += '<div class="history-detail">';
                html += '得分: <span style="color:#ff0;">' + record.score + '</span> | ';
                html += 'CPS: ' + (record.cps ? Number(record.cps).toFixed(2) : '0.00');
                // 保护模式显示按错次数，普通模式不显示
                var catMode = catKey.split('_').pop();
                if (catMode === 'protected') {
                    html += ' | 按错: ' + (record.errorCount || 0) + '次';
                }
                if (record.isDead) html += ' | <span style="color:#f44;">[死亡]</span>';
                html += '<br><small>' + (record.date || '') + '</small>';
                html += '</div></div>';
            }
        }
        html += '</div>';
        html += '<button type="button" class="btn btn-secondary btn-lg" onclick="UI.hideAbout()">关闭</button>';
        html += '</div>';
        o.innerHTML = html;
        o.style.display = 'block';
    }
    static showAbout() {
        var o = document.getElementById('historyLayer');
        if (!o) return;
        var html = '<div class="about-content">';
        html += '<h2 style="color:#fff;text-align:center;">关于</h2>';
        html += '<div style="text-align:center;margin-top:20px;">';
        html += '<h4 style="color:#aaa;text-align:center;">EatCat-Master</h4>';
        html += '<p><a href="https://github.com/UlyssesZha/eatcat" target="_blank" style="color:#4fc3f7;">源代码(改版后)</a></p>';
        html += '<p><a href="https://github.com/arcxingye/EatKano" target="_blank" style="color:#4fc3f7;">源代码(原版)</a></p>';
        html += '<p><a href="https://space.bilibili.com/470549205" target="_blank" style="color:#4fc3f7;">Atopos酱的B站主页</a></p>';
        html += '<p><a href="https://space.bilibili.com/394102487" target="_blank" style="color:#4fc3f7;">tzh654321的B站主页</a></p>';
        html += '<br><button type="button" class="btn btn-secondary btn-lg" onclick="UI.hideAbout()">关闭</button>';
        html += '</div></div>';
        o.innerHTML = html;
        o.style.display = 'block';
    }

    static hideAbout() {
        var o = document.getElementById('historyLayer');
        if (o) o.style.display = 'none';
    }

    static showSettings() {
        var s = window.Storage ? Storage.loadSettings() : {};
        var kEl = document.getElementById('k'); if(kEl) kEl.value = Game.keyCount.toString();
        var kbEl = document.getElementById('keyboard');
        // 根据当前的键数输入框的值加载对应键位映射
        var kVal = kEl ? kEl.value : Game.keyCount.toString();
        var mapping = (s.keyMappingByKey && s.keyMappingByKey[kVal] !== undefined && s.keyMappingByKey[kVal] !== null)
            ? s.keyMappingByKey[kVal] : Input.getDefaultMapping(parseInt(kVal) || Game.keyCount);
        if(kbEl) kbEl.value = mapping;
        var tEl = document.getElementById('timeinput'); if(tEl) tEl.value = Game.timeLimit.toString();
        // note输入框显示存储的原始值（可能是多key格式如3k:1232;4k:123432），而不是纯配置
        var savedNote = (window.Storage ? Storage.loadSettings().notePattern : null) || Game.notePattern;
        var nEl = document.getElementById('note'); if(nEl) nEl.value = savedNote;
        var hEl = document.getElementById('hide'); if(hEl) hEl.checked = s.hideComment || false;
        var cEl = document.getElementById('close'); if(cEl) cEl.checked = !(s.soundEnabled !== false);
        var tcEl = document.getElementById('themeColor'); if(tcEl) tcEl.value = s.themeColor || (s.darkMode ? 'dark' : 'light');
        var ws = document.getElementById('widthSlider'); if(ws) ws.value = Game.centerWidth || 100;
        var wv = document.getElementById('widthValue'); if(wv) wv.textContent = (Game.centerWidth || 100) + '%';
        // 恢复功能键位设置输入框（留空 = 不绑定，仅从未保存过时才用默认值）
        var qEl = document.getElementById('keybindQ'); if(qEl) qEl.value = (typeof s.keybindQ === 'string') ? s.keybindQ : 'q';
        var rEl = document.getElementById('keybindR'); if(rEl) rEl.value = (typeof s.keybindR === 'string') ? s.keybindR : 'r';
        var fEl = document.getElementById('keybindF'); if(fEl) fEl.value = (typeof s.keybindF === 'string') ? s.keybindF : 'f';
        var mEl = document.getElementById('keybindM'); if(mEl) mEl.value = (typeof s.keybindM === 'string') ? s.keybindM : '';
        document.getElementById('btn_group').style.display = 'none';
        document.getElementById('btn_group2').style.display = 'none';
        document.getElementById('tt').style.display = 'none';
        document.getElementById('ttt').style.display = 'none';
        var t4 = document.getElementById('tttt'); if (t4) t4.style.display = 'none';
        document.getElementById('setting1').style.display = 'block';
    }

    static hideAllOverlays() {
        var h = document.getElementById('historyLayer'); if(h) h.style.display='none';
        var r = document.getElementById('rulesLayer'); if(r) r.style.display='none';
        var p = document.getElementById('pauseOverlay'); if(p) p.style.display='none';
    }

    /** 根据主题名应用背景色/轨道色/网格线/CPS颜色 */
    static applyTheme(theme) {
        if (!theme) theme = 'light';
        // 同步到 Game 供 refreshGameLayer 使用
        if (typeof Game !== 'undefined') Game.themeColor = theme;
        var bg = document.getElementById('GameLayerBG');
        if (!bg) return;
        var k = (typeof Game !== 'undefined' && Game.keyCount) ? Game.keyCount : 4;
        var isDark = theme === 'dark';

        // 轨道颜色函数（按轨道索引返回背景色）
        function trackColor(trackIdx) {
            switch (theme) {
                case 'dark': return '#303030';
                case 'pinstripe': return (trackIdx % 2 === 0) ? '#A2B7D5' : '#C8BFE7';
                case 'splitR': return (trackIdx < Math.floor(k / 2)) ? '#99D9EA' : '#FFD2E1';
                case 'splitL': return (trackIdx <= Math.floor((k - 1) / 2)) ? '#99D9EA' : '#FFD2E1';
                default: return '#ffffff';
            }
        }
        function gridColor() {
            switch (theme) {
                case 'dark': return '#0D559D';
                case 'pinstripe':
                case 'splitR':
                case 'splitL': return '#6f9d77';
                default: return '#b8dfe6';
            }
        }

        // 设置背景层颜色（多色主题的轨道色带固定不滚动，由 Game.setTrackBackground 统一处理）
        if (typeof Game !== 'undefined' && Game.setTrackBackground) {
            Game.setTrackBackground();
        } else {
            bg.style.background = isDark ? '#303030' : '#fff';
        }

        // 更新所有块的颜色与网格线
        var isMultiColor = (theme === 'pinstripe' || theme === 'splitR' || theme === 'splitL');
        var allBlocks = document.querySelectorAll('.block');
        for (var i = 0; i < allBlocks.length; i++) {
            var blk = allBlocks[i];
            var track = parseInt(blk.getAttribute('num')) % k;
            if (!isNaN(track)) {
                var tc = trackColor(track);
                // 有音符的块不要覆盖背景图区域
                if (!isMultiColor && (blk.style.backgroundImage === 'none' || !blk.style.backgroundImage)) {
                    blk.style.backgroundColor = tc;
                }
            }
            blk.style.borderTopColor = gridColor();
        }
        var allBl = document.querySelectorAll('.bl');
        for (var i2 = 0; i2 < allBl.length; i2++) {
            allBl[i2].style.borderLeftColor = gridColor();
        }

        // 更新CPS显示颜色适配主题
        var cpsEl = document.getElementById('cpsDisplay');
        if (cpsEl) {
            var numEl = cpsEl.querySelector('.cps-number');
            var hitsEl = cpsEl.querySelector('.cps-hits');
            var barBg = cpsEl.querySelector('.cps-bar-bg');
            if (isDark) {
                if (numEl) numEl.style.color = '#ffeb3b';
                if (hitsEl) hitsEl.style.color = '#ffffff';
                if (barBg) barBg.style.background = 'rgba(255,255,255,0.25)';
            } else {
                if (numEl) numEl.style.color = '#1565C0';
                if (hitsEl) hitsEl.style.color = '#000000';
                if (barBg) barBg.style.background = 'rgba(0,0,0,0.15)';
            }
        }

        // 更新设置页选择框
        var sel = document.getElementById('themeColor');
        if (sel && sel.value !== theme) sel.value = theme;
    }

    /** 从设置加载并应用主题（用于初始化/刷新后恢复） */
    static applyThemeFromSettings() {
        var s = window.Storage ? Storage.loadSettings() : {};
        var theme = s.themeColor || (s.darkMode ? 'dark' : 'light');
        UI.applyTheme(theme);
    }

    static savePattern() {
        var noteEl = document.getElementById('note');
        if (!noteEl || !noteEl.value) { alert('请先在"键型设置"中输入键型'); return; }
        var name = prompt('请输入键型名称：');
        if (!name) return;
        if (window.Storage) {
            Storage.savePattern(name, noteEl.value, Game.keyCount);
            UI.refreshPatternList();
            alert('键型 "' + name + '" 已保存！');
        }
    }

    static editPattern(name) {
        if (!name) return;
        var patterns = window.Storage ? Storage.loadPatterns() : {};
        var p = patterns[name];
        if (!p) { alert('键型不存在'); return; }
        var newName = prompt('修改键型名称：', name);
        if (!newName) return;
        var newPattern = prompt('修改键型配置：', p.pattern);
        if (newPattern === null) return;
        var newK = prompt('修改键数（0不限，多个用逗号如1,2,3,4）：', p.keyCount || '0');
        if (window.Storage) {
            var kc = newK;
            if (kc.indexOf(',') !== -1) {
                kc = kc.split(',').map(function(x){return parseInt(x.trim());}).filter(function(x){return !isNaN(x);});
            } else {
                kc = parseInt(kc) || 0;
            }
            Storage.editPattern(name, newName, newPattern, kc);
            UI.refreshPatternList();
            UI.showPatternManager();
        }
    }

    static deletePattern(name) {
        if (!name) return;
        if (!confirm('确定删除键型 "' + name + '" 吗？')) return;
        if (window.Storage) {
            Storage.deletePattern(name);
            UI.refreshPatternList();
        }
    }

    static showPatternManager() {
        var patterns = window.Storage ? Storage.loadPatterns() : {};
        var html = '<div class="pattern-manager">';
        html += '<h2 style="color:#fff;text-align:center;">键型管理</h2>';
        html += '<div style="text-align:center;margin-bottom:15px;">';
        html += '<button type="button" class="btn btn-secondary btn-sm" onclick="UI.addPatternFromNote()">新增</button> ';
        html += '<button type="button" class="btn btn-secondary btn-sm" onclick="UI.exportSelectedPatterns()">导出选中</button> ';
        html += '<button type="button" class="btn btn-secondary btn-sm" onclick="importPatterns()">导入</button>';
        html += '</div>';
        html += '<div class="pattern-list">';
        var keys = Object.keys(patterns);
        if (keys.length === 0) {
            html += '<p style="color:#fff;text-align:center;">暂无自定义键型</p>';
        } else {
            for (var i = 0; i < keys.length; i++) {
                var name = keys[i];
                var p = patterns[name];
                html += '<div class="pattern-item">';
                html += '<input type="checkbox" class="pattern-check" value="' + name.replace(/"/g, '"') + '" style="width:20px;height:20px;">';
                html += '<span class="pattern-name" style="color:#4fc3f7;">' + name + '</span>';
                html += '<span class="pattern-desc" style="color:#aaa;font-size:0.8em;"> ' + p.pattern + ' (' + (p.keyCount || '?') + 'k)</span>';
                html += '<div class="pattern-actions">';
                html += '<button type="button" class="btn btn-secondary btn-sm" onclick="UI.editPattern(\'' + name.replace(/'/g, "\\'") + '\')">编辑</button> ';
                html += '<button type="button" class="btn btn-secondary btn-sm" onclick="UI.deletePattern(\'' + name.replace(/'/g, "\\'") + '\')">删除</button>';
                html += '</div></div>';
            }
        }
        html += '</div>';
        html += '<div style="text-align:center;margin-top:15px;">';
        html += '<button type="button" class="btn btn-secondary btn-lg" onclick="UI.hidePatternManager()">关闭</button>';
        html += '</div></div>';
        
        var overlay = document.getElementById('rulesLayer');
        if (overlay) {
            overlay.innerHTML = html;
            overlay.style.display = 'block';
        }
    }

    static hidePatternManager() {
        var overlay = document.getElementById('rulesLayer');
        if (overlay) overlay.style.display = 'none';
    }

    static addPatternFromNote() {
        var noteEl = document.getElementById('note');
        if (!noteEl || !noteEl.value) { alert('请先在设置页的键型输入框中输入键型'); return; }
        var name = prompt('请输入新键型名称：');
        if (!name) return;
        if (window.Storage) {
            Storage.savePattern(name, noteEl.value, Game.keyCount);
            UI.refreshPatternList();
            UI.showPatternManager();
        }
    }

    static selectPattern(name) {
        var patterns = window.Storage ? Storage.loadPatterns() : {};
        var p = patterns[name];
        if (p) {
            Game.notePattern = p.pattern;
            Game.restart();
            // 同步到选择框和键型输入框
            var sel = document.getElementById('patternSelect');
            if (sel) { sel.value = name; UI.refreshPatternList(); }
            var noteEl = document.getElementById('note');
            if (noteEl) noteEl.value = p.pattern;
        }
    }

    static exportSelectedPatterns() {
        var checkboxes = document.querySelectorAll('.pattern-check:checked');
        var names = [];
        for (var i = 0; i < checkboxes.length; i++) {
            names.push(checkboxes[i].value);
        }
        if (names.length === 0) { alert('请先选择要导出的键型'); return; }
        var patterns = window.Storage ? Storage.loadPatterns() : {};
        var exportData = {};
        for (var j = 0; j < names.length; j++) {
            if (patterns[names[j]]) exportData[names[j]] = patterns[names[j]];
        }
        var data = JSON.stringify({ version: 2, patterns: exportData }, null, 2);
        if (navigator.clipboard) {
            navigator.clipboard.writeText(data).then(function() {
                alert('已导出 ' + names.length + ' 个键型到剪贴板！');
            }).catch(function() {
                prompt('复制以下数据：', data);
            });
        } else {
            prompt('复制以下数据：', data);
        }
    }

    static onKeyCountInputChange() {
        // 修改键数输入框时自动加载对应键数的键位映射
        var kEl = document.getElementById('k');
        var kbEl = document.getElementById('keyboard');
        if (!kEl || !kbEl) return;
        var kVal = parseInt(kEl.value);
        if (isNaN(kVal) || kVal < 1) return;
        var s = window.Storage ? Storage.loadSettings() : {};
        var kStr = kVal.toString();
        var mapping = (s.keyMappingByKey && s.keyMappingByKey[kStr] !== undefined && s.keyMappingByKey[kStr] !== null)
            ? s.keyMappingByKey[kStr] : Input.getDefaultMapping(kVal);
        kbEl.value = mapping;
        // 刷新键型选择框（按当前键数过滤预设）
        if (typeof UI !== 'undefined' && UI.refreshPatternList) {
            UI.refreshPatternList();
        }
    }

    static onKeyboardInputChange() {
        // 修改键位输入框时主界面选择框切换到"自定义"
        var sel = document.getElementById('patternSelect');
        if (sel) {
            sel.value = '__custom__';
            // 触发选择框change事件，应用当前键型设置框中的配置
            if (typeof UI.onPatternSelectChange === 'function') {
                UI.onPatternSelectChange();
            }
        }
    }

    static onNoteInputChange() {
        // 手动修改键型设置框时，主界面选择框切换到"自定义"
        var sel = document.getElementById('patternSelect');
        if (sel) {
            sel.value = '__custom__';
        }
    }

    static showWelcome() {
        var l = document.getElementById('welcome'); if(l) l.style.display='block';
        var s = document.getElementById('GameScoreLayer'); if(s) s.style.display='none';
        var tttt = document.getElementById('tttt');
        if(tttt) {
            tttt.style.display = 'block';
            tttt.innerHTML = '更新内容：绑多key、不合规纠正、键型收纳、历史成绩、规则页面等！';
        }
    }
}
