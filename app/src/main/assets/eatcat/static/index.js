/**
 * 主入口文件 - 兼容原版结构，整合模块
 * 使用原版body onload="init()"启动方式
 */

// ============================================================
// 浏览器兼容性检测
// ============================================================
let isDesktop = !navigator['userAgent'].match(/(ipad|iphone|ipod|android|windows phone)/i);
let fontunit = isDesktop ? 20 : ((window.innerWidth > window.innerHeight ? window.innerHeight : window.innerWidth) / 320) * 10;
document.write('<style type="text/css">' +
    'html,body {font-size:' + (fontunit < 30 ? fontunit : '30') + 'px;}' +
    (isDesktop ? '#welcome,#GameTimeLayer,#GameLayerBG,#GameScoreLayer.SHADE{position: absolute;}'
              : '#welcome,#GameTimeLayer,#GameLayerBG,#GameScoreLayer.SHADE{position:fixed;}') +
    '#historyLayer{position:fixed;z-index:15;top:0;left:0;width:100%;bottom:0;overflow-y:auto;background:rgba(0,0,0,0.85);}' +
    '#rulesLayer{position:fixed;z-index:15;top:0;left:0;width:100%;bottom:0;overflow-y:auto;background:rgba(0,0,0,0.85);}' +
    '</style>');

// ============================================================
// 兼容性变量（被HTML事件调用）
// ============================================================
let map = { 'd': 1, 'f': 2, 'j': 3, 'k': 4 };
let key = ['!'];
let chs = ['@', '!', '#', '&', '+', '-', '%', '*'];
let len = key.length;
let hide = false;
let __Time = 20;
let __k = 4;
let _close = false;
let _fsj = true; // 始终开启垂直判定
var url = './static/image/ClickBefore.png';

// ============================================================
// 兼容性函数
// ============================================================

function isplaying() {
    return document.getElementById('welcome').style.display == 'none' &&
        document.getElementById('GameScoreLayer').style.display == 'none' &&
        document.getElementById("setting1").style.display == 'none';
}

function show_setting() {
    UI.showSettings();
}

function show_btn(i) {
    document.getElementById('tt').style.display = 'block';
    document.getElementById('ttt').style.display = 'block';
    document.getElementById('btn_group').style.display = 'block';
    document.getElementById('btn_group2').style.display = 'block';
    var t4 = document.getElementById('tttt'); if (t4) t4.style.display = 'block';
    document.getElementById('setting' + i.toString()).style.display = 'none';
    // 确保开始按钮始终绑定正确事件
    var btn = document.getElementById('ready-btn');
    if (btn) {
        btn.onclick = function() {
            closeWelcomeLayer();
        };
    }
}

function nxtpage(i) {
    document.getElementById("setting" + i.toString()).style.display = "none";
    document.getElementById("setting" + (i + 1).toString()).style.display = "block";
}

function lstpage(i) {
    document.getElementById("setting" + i.toString()).style.display = "none";
    document.getElementById("setting" + (i - 1).toString()).style.display = "block";
}

function showWelcomeLayer() {
    UI.showWelcome();
    // 确保回到首页后开始按钮可用
    var btn = document.getElementById('ready-btn');
    if (btn) {
        btn.onclick = function() {
            closeWelcomeLayer();
        };
    }
}

function closeWelcomeLayer() {
    document.getElementById('welcome').style.display = 'none';
    // 刷新游戏层确保音符显示
    Game.restart();
    // 游戏在第一次点击轨道时由 Game.handleTap() 自动开始
}

function hideGameScoreLayer() {
    document.getElementById('GameScoreLayer').style.display = 'none';
}

function gameRestart() {
    Game.restart();
}

function replayBtn() {
    Game.restart();
    hideGameScoreLayer();
    UI.hideAllOverlays();
}

function backBtn() {
    Game.restart();
    hideGameScoreLayer();
    showWelcomeLayer();
    UI.hideAllOverlays();
}

function showGameScoreLayer() {
    Game.showScoreLayer();
}

function autoset(asss) {
    Game.notePattern = asss;
    Game.restart();
}

function stair() {
    UI.generateStair();
}

function showImg(input) {
    var file = input.files[0];
    if (file) {
        var reader = new FileReader();
        reader.onload = function(e) {
            url = e.target.result;
            Game.url = url;
            try { Storage.set('customImage', url); } catch(ex) {}
            try { Storage.set('customImageName', file.name); } catch(ex) {}
            updateCustomImageNameDisplay(file.name);
        };
        reader.readAsDataURL(file);
    }
}

function updateCustomImageNameDisplay(name) {
    var el = document.getElementById('customImageNameDisplay');
    if (!el) return;
    el.textContent = name ? ('当前图片：' + name) : '未选择图片';
}

function resetImg() {
    url = './static/image/ClickBefore.png';
    Game.url = url;
    try {
        Storage.set('customImage', '');
        Storage.remove('customImage');
        Storage.remove('customImageName');
    } catch(ex) {}
    // 清空文件选择
    var upload = document.getElementById('upload-input');
    if (upload) upload.value = '';
    updateCustomImageNameDisplay('');
}

function saveSettings() {
    let str = document.getElementById('keyboard').value;
    let Time = document.getElementById('timeinput').value;
    let note = document.getElementById('note').value;
    hide = document.getElementById('hide').checked;
    _close = document.getElementById('close').checked;
    _fsj = true;

    // 允许留空 = 不绑定该功能键
    let kbQ = document.getElementById('keybindQ').value.trim().toLowerCase();
    let kbR = document.getElementById('keybindR').value.trim().toLowerCase();
    let kbF = document.getElementById('keybindF').value.trim().toLowerCase();
    let kbM = document.getElementById('keybindM').value.trim().toLowerCase();

    let tsmp = parseInt(document.getElementById('k').value);
    if (isNaN(tsmp) || tsmp < 1) tsmp = 4;

    let sanitizedNote = PatternValidator.sanitizeConfig(note);
    if (sanitizedNote.length === 0) {
        sanitizedNote = Game.notePattern || '!';
    }

    // 提取当前键数对应的纯配置（用于分析最小轨道数和游戏运行）
    let extractedConfig = PatternValidator.getKeyConfig(sanitizedNote, tsmp);
    // 用纯配置分析最小轨道数（minTracksNeeded已修复忽略k:前缀）
    let minTracks = PatternValidator.minTracksNeeded(extractedConfig);
    if (minTracks > tsmp) {
        tsmp = minTracks;
        document.getElementById('k').value = tsmp.toString();
    }
    
    // 保留原始多key格式用于存储到数据库，但游戏运行时必须用提取后的纯配置
    var storedNote = sanitizedNote; // 保存完整多key格式

    if (tsmp !== Game.keyCount) {
        Game.keyCount = tsmp;
        recreateGameLayers();
    }

    // 键数改变后刷新键型选择框（6k 下不再显示 4k 专属选项）
    if (typeof UI !== 'undefined' && UI.refreshPatternList) {
        UI.refreshPatternList();
    }

    Input.setKeyMapping(str, tsmp);
    Game.notePattern = extractedConfig; // 游戏始终使用纯配置
    var parsedTime = parseFloat(Time);
    if (isNaN(parsedTime) || parsedTime < 0) parsedTime = 20;
    Game.isPractice = (parsedTime <= 0);
    Game.timeLimit = (parsedTime <= 0) ? 20 : parsedTime;

    if (window.Storage) {
        let settings = Storage.loadSettings();
        if (!settings.keyMappingByKey) settings.keyMappingByKey = {};
        settings.keyMappingByKey[tsmp.toString()] = str;
        settings.keyCount = tsmp;
        settings.timeLimit = Game.timeLimit;
        settings.notePattern = note;
        settings.hideComment = hide;
        settings.soundEnabled = !_close;
        settings.verticalJudge = true;
        settings.centerWidth = Game.centerWidth;
        settings.practiceMode = Game.isPractice;
        var themeColorEl = document.getElementById('themeColor');
        if (themeColorEl) settings.themeColor = themeColorEl.value;
        else settings.themeColor = settings.themeColor || (settings.darkMode ? 'dark' : 'light');
        settings.keybindQ = kbQ;
        settings.keybindR = kbR;
        settings.keybindF = kbF;
        settings.keybindM = kbM;
        Storage.saveSettings(settings);
    }

    // 保存设置后重新应用主题（restart会重置DOM）
    Game.restart();
    if (typeof UI !== 'undefined' && UI.applyThemeFromSettings) {
        UI.applyThemeFromSettings();
    }
}

function recreateGameLayers() {
    var el = document.getElementById('GameLayerBG');
    var fa = document.body;
    // 从当前父节点移除
    if (el && el.parentNode) el.parentNode.removeChild(el);
    var timeLayer = document.getElementById('GameTimeLayer');
    if (timeLayer.parentNode) timeLayer.parentNode.removeChild(timeLayer);

    fa.appendChild(parseElement(createGameLayer()));
    fa.appendChild(parseElement("<div id=\"GameTimeLayer\"><span class=\"timer-text\">20.00s</span></div>"));

    Game.gameTimeLayer = document.getElementById('GameTimeLayer');
    Game.gameLayers = [];
    Game.gameLayers.push(document.getElementById('GameLayer1'));
    Game.gameLayers.push(document.getElementById('GameLayer2'));
    Game.gameLayerBG = document.getElementById('GameLayerBG');

    if (Game.gameLayerBG.ontouchstart === null) {
        Game.gameLayerBG.ontouchstart = function(e) { Game.handleTap(e); };
    } else {
        Game.gameLayerBG.onmousedown = function(e) { Game.handleTap(e); };
    }
}

function createGameLayer() {
    var k = Game.keyCount;
    var visibleRows = Math.max(6, Math.ceil(window.innerHeight / Game.blockSize) + 4);
    var count = Math.max(visibleRows, 6, k * 2);
    var html = '<div id="GameLayerBG">';
    for (var i = 1; i <= 2; i++) {
        var id = 'GameLayer' + i;
        html += '<div id="' + id + '" class="GameLayer">';
        for (var j = 0; j < count; j++) {
            for (var l = 0; l < k; l++) {
                html += '<div id="' + id + '-' + (l + j * k) + '" num="' + (l + j * k) + '" class="block' + (l ? ' bl' : '') + '"></div>';
            }
        }
        html += '</div>';
    }
    html += '</div>';
    return html;
}

function parseElement(htmlString) {
    return new DOMParser().parseFromString(htmlString, 'text/html').body.childNodes[0];
}

function click(index) {
    Game.handleKeyClick(index);
}

function foreach() {
    var strCookie = document.cookie;
    var arrCookie = strCookie.split("; ");
    for (var i = 0; i < arrCookie.length; i++) {
        var arr = arrCookie[i].split("=");
        if (arr.length > 0) {
            document.cookie = arr[0] + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
        }
    }
    location.reload();
}

function cookie(name, value, time) {
    if (name) {
        if (value) {
            if (time) {
                let date = new Date();
                date.setTime(date.getTime() + 864e5 * time), time = date.toGMTString();
            }
            return document.cookie = name + "=" + escape(value) + (time ? "; expires=" + time : ""), !0;
        }
        value = document.cookie.match("(?:^|;)\\s*" + name.replace(/([-.*+?^${}()|[\]\/\\])/g, "\\$1") + "=([^;]*)");
        return value ? unescape(value[1]) : false;
    }
    return {};
}

function initSetting() {
    if (window.Storage) {
        let settings = Storage.loadSettings();
        Game.keyCount = settings.keyCount;
        // 存储的是完整多key格式，需要提取当前键数对应的纯配置
        Game.notePattern = PatternValidator.getKeyConfig(settings.notePattern, settings.keyCount);
        Game.timeLimit = settings.timeLimit;
        Game.gameMode = settings.gameMode;
        Game.isPractice = settings.practiceMode || settings.timeLimit <= 0;
        Game.centerWidth = settings.centerWidth || 100;
        _fsj = true;
        _close = !settings.soundEnabled;
        hide = settings.hideComment || false;
        // 优先使用按keyCount存储的键位映射
        var kStr = Game.keyCount.toString();
        var mapping = (settings.keyMappingByKey && settings.keyMappingByKey[kStr]) || settings.keyMapping || Input.getDefaultMapping(Game.keyCount);
        Input.setKeyMapping(mapping, Game.keyCount);
        
        // 恢复功能键位设置到输入框
        var qEl = document.getElementById('keybindQ');
        if (qEl) qEl.value = (typeof settings.keybindQ === 'string') ? settings.keybindQ : 'q';
        var rEl = document.getElementById('keybindR');
        if (rEl) rEl.value = (typeof settings.keybindR === 'string') ? settings.keybindR : 'r';
        var fEl = document.getElementById('keybindF');
        if (fEl) fEl.value = (typeof settings.keybindF === 'string') ? settings.keybindF : 'f';
        var mEl = document.getElementById('keybindM');
        if (mEl) mEl.value = (typeof settings.keybindM === 'string') ? settings.keybindM : '';
    }
    
    // 刷新键型选择框，确保当前键数下只显示匹配的预设（修复 6k 显示 4k 专属选项）
    if (typeof UI !== 'undefined' && UI.refreshPatternList) {
        UI.refreshPatternList();
    }
    Game.restart();
}

function setGameMode(mode) {
    Game.gameMode = mode;
    if (window.Storage) {
        let settings = Storage.loadSettings();
        settings.gameMode = mode;
        Storage.saveSettings(settings);
    }
    // 模式切换时立即更新左下角显示
    var modeEl = document.getElementById('gameModeDisplay');
    if (modeEl) {
        if (mode === 'protected') {
            modeEl.textContent = '保护模式';
            modeEl.style.display = 'block';
        } else {
            modeEl.style.display = 'none';
        }
    }
}

function editPatternUI() {
    let select = document.getElementById('patternSelect');
    let value = select.value;
    if (value === '__custom__' || value.startsWith('__preset_')) {
        alert('请先从下拉列表中选择一个自定义键型');
        return;
    }
    UI.editPattern(value);
}

function importPatterns() {
    var input = prompt('请将键型数据粘贴到此处：\n（格式：{"键型名":{"pattern":"!","keyCount":4}}）');
    if (input) {
        input = '{' + input.replace(/^\{/, '').replace(/\}$/, '') + '}';
        try { input = JSON.parse(input); } catch(e1) {
            try { input = JSON.parse('{' + input.replace(/^\{/, '').replace(/\}$/, '') + '}'); } catch(e2) {
                alert('格式有误，请检查');
                return;
            }
        }
        var convert = {};
        if (input.patterns) {
            for (var pk in input.patterns) convert[pk] = input.patterns[pk];
        } else {
            for (var pk2 in input) convert[pk2] = input[pk2];
        }
        if (window.Storage) {
            // 合并导入，不覆盖已有键型
            var existing = Storage.loadPatterns();
            var count = 0;
            for (var ck in convert) {
                if (!existing[ck]) {
                    existing[ck] = convert[ck];
                    count++;
                }
            }
            Storage.set(Storage.KEYS.PATTERNS, existing);
            UI.refreshPatternList();
            alert('导入成功！新增 ' + count + ' 个键型');
        }
    }
}

function exportPatterns() {
    if (window.Storage) {
        var data = Storage.exportPatterns();
        // 通过prompt复制到剪贴板
        if (navigator.clipboard) {
            navigator.clipboard.writeText(data).then(function() {
                alert('键型数据已复制到剪贴板！\n可粘贴到文件中保存或分享给他人。');
            }).catch(function() {
                prompt('请复制以下键型数据：', data);
            });
        } else {
            prompt('请复制以下键型数据：', data);
        }
    }
}
// ============================================================
// 初始化 - 由body onload触发
// ============================================================
function init() {
    Input.init();
    Game.init();
    UI.init();
    // 等待IndexedDB加载完成后初始化设置（确保刷新后设置保留）
    // callback 在 localStorage 同步恢复后立即执行，设置立即可用
    // migrateCallback 在 IndexedDB 迁移完成后执行，用于恢复降级存储的大型数据
    Storage.init(function() {
        initSetting();
        if (typeof UI !== 'undefined' && UI.applyThemeFromSettings) {
            UI.applyThemeFromSettings();
        }
        // 恢复自定义图片（优先从内存/同步缓存读取）
        try {
            var imgUrl = Storage.get('customImage');
            if (imgUrl) {
                url = imgUrl;
                Game.url = imgUrl;
            }
            updateCustomImageNameDisplay(Storage.get('customImageName'));
        } catch(ex) {}
    }, function() {
        // 迁移完成后再检查一次自定义图片（可能因为超过 localStorage 容量而降级存储在 IndexedDB）
        try {
            var imgUrl2 = Storage.get('customImage');
            if (imgUrl2) {
                url = imgUrl2;
                Game.url = imgUrl2;
                Game.restart();
            }
            updateCustomImageNameDisplay(Storage.get('customImageName'));
        } catch(ex) {}
    });

    // 绑定开始按钮事件
    var btn = document.getElementById('ready-btn');
    if (btn) {
        btn.className = 'btn btn-primary btn-lg';
        btn.onclick = function() {
            closeWelcomeLayer();
        };
    }

    // 键盘监听
    document.addEventListener('keydown', function(e) {
        var key = e.key.toLowerCase();
        
        // 从输入框获取当前功能键位设置（优先使用输入框值，回退到默认）
        var settings = (window.Storage) ? Storage.loadSettings() : {};
        var bindQEl = document.getElementById('keybindQ');
        var bindREl = document.getElementById('keybindR');
        var bindFEl = document.getElementById('keybindF');
        var bindMEl = document.getElementById('keybindM');
        // 以输入框当前值为准（留空 = 不绑定），输入框不存在时才回退到已保存/默认设置
        var bindQ = bindQEl ? bindQEl.value.trim().toLowerCase() : (typeof settings.keybindQ === 'string' ? settings.keybindQ : 'q');
        var bindR = bindREl ? bindREl.value.trim().toLowerCase() : (typeof settings.keybindR === 'string' ? settings.keybindR : 'r');
        var bindF = bindFEl ? bindFEl.value.trim().toLowerCase() : (typeof settings.keybindF === 'string' ? settings.keybindF : 'f');
        var bindM = bindMEl ? bindMEl.value.trim().toLowerCase() : (typeof settings.keybindM === 'string' ? settings.keybindM : '');
        
        // 辅助函数：检查键是否匹配（支持~连接语法，如 q~Q 表示 q 或 Q）
        function matchesBind(targetKey, bindStr) {
            if (!bindStr) return false;
            var parts = bindStr.split('~');
            for (var pi = 0; pi < parts.length; pi++) {
                var p = parts[pi].trim().toLowerCase();
                if (p && p === targetKey) return true;
            }
            return false;
        }
        
        // Q功能键 - 暂停/退出/返回主页
        if (matchesBind(key, bindQ)) {
            // 结算界面 → 返回主页
            var sl = document.getElementById('GameScoreLayer');
            if (sl && sl.style.display !== 'none') {
                hideGameScoreLayer();
                showWelcomeLayer();
                Game.restart();
                return;
            }
            
            var isPlaying = typeof Game !== 'undefined' && Game.gameStart && !Game.gameOver && !Game.gamePaused;
            var isPaused = typeof Game !== 'undefined' && Game.gamePaused;
            var isWaiting = typeof Game !== 'undefined' && !Game.gameStart && document.getElementById('welcome').style.display === 'none' && document.getElementById('setting1').style.display === 'none';
            
            if (isPaused) {
                backBtn();
                UI.hideAllOverlays();
                return;
            } else if (isPlaying) {
                Game.togglePause();
                return;
            } else if (isWaiting || document.getElementById('setting1').style.display !== 'none') {
                backBtn();
                return;
            }
        }
        
        // F功能键 - 结算/开始界面重玩
        if (matchesBind(key, bindF)) {
            var sl = document.getElementById('GameScoreLayer');
            var wl = document.getElementById('welcome');
            if ((sl && sl.style.display !== 'none') || (wl && wl.style.display !== 'none')) {
                replayBtn();
                return;
            }
        }
        
        // R功能键重开 - 结算/游玩中/等待中
        if (matchesBind(key, bindR)) {
            var sl2 = document.getElementById('GameScoreLayer');
            if (sl2 && sl2.style.display !== 'none') {
                replayBtn();
                return;
            }
            // 游玩中或等待第一次按键时按r重开
            if (typeof Game !== 'undefined' && Game.gameStart && !Game.gameOver) {
                replayBtn();
                return;
            }
            if (typeof Game !== 'undefined' && !Game.gameStart && document.getElementById('welcome').style.display === 'none' && document.getElementById('setting1').style.display === 'none') {
                replayBtn();
                return;
            }
        }
        
        if (!isplaying()) return;
        // 模拟左键：用设定键位模拟一次鼠标左键点击（以当前鼠标位置判定音符）
        if (matchesBind(key, bindM)) {
            if (typeof Game !== 'undefined' && Game.handleTap) {
                Game.handleTap({ clientX: (typeof Game._lastMouseClientX === 'number' ? Game._lastMouseClientX : window.innerWidth / 2) });
            }
            return;
        }
        // 使用用户设置的键位映射
        if (typeof Input !== 'undefined' && Input.keyMapping && Input.keyMapping[key] !== undefined) {
            e.preventDefault();
            Game.handleKeyClick(Input.keyMapping[key] + 1);
        }
    });

    // 记录鼠标位置，供"模拟左键"功能键使用
    document.addEventListener('mousemove', function(e) {
        if (typeof Game !== 'undefined') Game._lastMouseClientX = e.clientX;
    });

    window.addEventListener('resize', refreshSize, false);
}

let refreshSizeTime;

function refreshSize() {
    clearTimeout(refreshSizeTime);
    refreshSizeTime = setTimeout(_refreshSize, 200);
}

function _refreshSize() {
    Game.countBlockSize();
    // 宽度缩放后重新应用主题（背景渐变色随 blockOffset 变化）
    if (typeof UI !== 'undefined' && UI.applyThemeFromSettings) {
        UI.applyThemeFromSettings();
    }
    for (let i = 0; i < Game.gameLayers.length; i++) {
        let box = Game.gameLayers[i];
        if (!box) continue;
        for (let j = 0; j < box.children.length; j++) {
            let r = box.children[j],
                rstyle = r.style;
            rstyle.left = (j % Game.keyCount) * Game.blockSize + Game.blockOffset + 'px';
            rstyle.bottom = Math.floor(j / Game.keyCount) * Game.blockSize + 'px';
            rstyle.width = Game.blockSize + 'px';
            rstyle.height = Game.blockSize + 'px';
        }
    }
    let f, a;
    if (Game.gameLayers[0] && Game.gameLayers[1]) {
        if (Game.gameLayers[0].y > Game.gameLayers[1].y) {
            f = Game.gameLayers[0];
            a = Game.gameLayers[1];
        } else {
            f = Game.gameLayers[1];
            a = Game.gameLayers[0];
        }
        let y = ((Game.gameBBListIndex) % 10) * Game.blockSize;
        if (f) {
            f.y = y;
            f.style[Game.transform] = 'translate3D(0,' + f.y + 'px,0)';
        }
        if (a) {
            a.y = -Game.blockSize * Math.floor(f.children.length / Game.keyCount) + y;
            a.style[Game.transform] = 'translate3D(0,' + a.y + 'px,0)';
        }
    }
}
