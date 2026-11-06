/**
 * 游戏核心 - 兼容原版核心逻辑，支持多压(NoteParser + handleHit)
 */
class Game {
    static keyCount = 4;
    static notePattern = '!';
    static timeLimit = 20;
    static gameMode = 'normal';
    static isPractice = false;
    static centerWidth = 100;
    static verticalJudge = true;
    static ui = null;
    static themeColor = 'light';
    static url = './static/image/ClickBefore.png';

    static blockSize = 0;
    static gameLayers = [];
    static gameLayerBG = null;
    static gameTimeLayer = null;
    static transform = '';
    static transitionDuration = '';
    static blockOffset = 0;

    static gameBBList = [];
    static gameBBListIndex = 0;
    static gameOver = false;
    static gameStart = false;
    static gamePaused = false;
    static gameTimeNum = 0;
    static gameScore = 0;
    static gameKeyHits = 0;
    static gameRowHits = 0;
    static gameErrors = 0;
    static date1 = null;
    static date2 = null;
    static deviationTime = 0;
    static lastTapTime = 0;
    // 排除暂停后的等效最后点击时间（等效时钟，用于计算坚持时间）
    static effectiveLastTapTime = 0;
    static tapTimes = [];
    static cpsHistory = [];
    static speedMultiplier = 1;
    static baseTransitionDuration = 180;
    static timerMsInterval = null;
    static pausedTimeElapsed = 0;
    static pauseStartTime = 0;
    static _pauseTimes = [];
    static _pauseStart = null;
    static _pausedDuration = 0;
    static protectedCooldown = false;
    static lastProtectedTrack = -1;
    static _lastGeneratedTrack = -1;

    // 多压支持
    static noteParser = null;
    static _currentRowHitTracks = {};
    static _bigKeyProtection = {};
    // 行元数据缓存：rowId -> { isBigKeyRow, bigKeyTrackSet(Set), bigKeyNoteMap(Map), allRowTrackSet(Set) }
    static _rowMetaCache = {};
    // 多压批量隐藏：快速连续按键时（间隔<80ms），整行命中后一次性隐藏所有块；
    // 慢速点按（>80ms）时通过独立定时器自动隐藏，无需等待下一个按键
    static _pendingHideCells = null;
    static _pendingHideSet = null;
    static _pendingHideStart = 0;
    static _pendingHideTimer = null;
    // 行→DOM元素映射缓存：rowId -> { cell: element }（按键判定时 O(1) 查找）
    static _rowCellMapCache = {};
    // 行→gameBBList 索引范围缓存：rowId -> [startIndex, endIndex)
    static _rowBBListRangeCache = {};
    // 行元数据缓存升级：rowId -> { isBigKeyRow, bigKeyTrackSet, bigKeyNoteMap, allRowTrackSet, noteTracks:number[] }
    static _rowMetaCache = {};

    static body = null;
    static _ttreg = / t{1,2}(\d+)/;
    static _clearttClsReg = / t{1,2}\d+| bad| red-flash/g;

    static init() {
        Game.body = document.body;
        Game.body.style.height = window.innerHeight + 'px';
        Game.transform = typeof (Game.body.style.webkitTransform) !== 'undefined' ? 'webkitTransform'
            : (typeof (Game.body.style.msTransform) !== 'undefined' ? 'msTransform' : 'transform');
        Game.transitionDuration = Game.transform.replace(/ransform/g, 'ransitionDuration');
        Game.countBlockSize();
        Game.ensureLayerBlocks();
        Game.gameTimeLayer = document.getElementById('GameTimeLayer');
        Game.gameLayers.push(document.getElementById('GameLayer1'));
        Game.gameLayers.push(document.getElementById('GameLayer2'));
        Game.gameLayerBG = document.getElementById('GameLayerBG');
        if (Game.gameLayerBG.ontouchstart === null)
            Game.gameLayerBG.ontouchstart = function(e) { Game.handleTap(e); };
        else
            Game.gameLayerBG.onmousedown = function(e) { Game.handleTap(e); };
        Game.registerSounds();
        Game.restart();
    }

    static ensureLayerBlocks() {
        var k = Game.keyCount;
        var layers = ['GameLayer1', 'GameLayer2'];
        var visibleRows = Math.max(6, Math.ceil(window.innerHeight / Game.blockSize) + 4);
        var neededRows = Math.max(visibleRows, 6, k * 2);
        for (var li = 0; li < layers.length; li++) {
            var el = document.getElementById(layers[li]);
            if (!el) continue;
            if (el.children.length >= neededRows * k) continue;
            var count = neededRows;
            var html = '';
            for (var j = 0; j < count; j++)
                for (var l = 0; l < k; l++)
                    html += '<div id="' + layers[li] + '-' + (l + j * k) + '" num="' + (l + j * k) + '" class="block' + (l ? ' bl' : '') + '"></div>';
            el.innerHTML = html;
        }
    }

    static registerSounds() {
        try {
            createjs.Sound.registerSound({ src: './static/music/err.mp3', id: 'err' });
            createjs.Sound.registerSound({ src: './static/music/end.mp3', id: 'end' });
            createjs.Sound.registerSound({ src: './static/music/tap.mp3', id: 'tap' });
        } catch(e) {}
    }

    static countBlockSize() {
        var totalWidth = Game.body.offsetWidth;
        var scale = Game.centerWidth / 100;
        var gameWidth = totalWidth * scale;
        Game.blockSize = gameWidth / Game.keyCount;
        Game.blockOffset = (totalWidth - gameWidth) / 2;
        Game.body.style.height = window.innerHeight + 'px';
        if (Game.gameLayerBG)
            Game.gameLayerBG.style.height = window.innerHeight + 'px';
    }

    /** 多色主题：将轨道色块固定在屏幕上（不随音符滚动）；
     *  浅色/深色主题仍使用整块底色 */
    static setTrackBackground() {
        var bg = document.getElementById('GameLayerBG');
        if (!bg) return;
        var k = Game.keyCount || 4;
        var theme = Game.themeColor || 'light';
        if (theme === 'light') { bg.style.background = '#fff'; return; }
        if (theme === 'dark') { bg.style.background = '#303030'; return; }
        function tc(trackIdx) {
            switch (theme) {
                case 'pinstripe': return (trackIdx % 2 === 0) ? '#A2B7D5' : '#C8BFE7';
                case 'splitR': return (trackIdx < Math.floor(k / 2)) ? '#99D9EA' : '#FFD2E1';
                case 'splitL': return (trackIdx <= Math.floor((k - 1) / 2)) ? '#99D9EA' : '#FFD2E1';
            }
            return '#ffffff';
        }
        var start = Game.blockOffset || 0;
        var size = Game.blockSize || (bg.offsetWidth / k);
        var end = start + size * k;
        var stops = [];
        for (var t = 0; t < k; t++) {
            var s = start + t * size;
            stops.push(tc(t) + ' ' + s + 'px', tc(t) + ' ' + (s + size) + 'px');
        }
        var leftColor = tc(0), rightColor = tc(k - 1);
        // 轨道外区域与相邻轨道颜色一致
        bg.style.background = 'linear-gradient(to right, ' + leftColor + ' 0%, ' + leftColor + ' ' + start + 'px, ' +
            stops.join(', ') + ', ' + rightColor + ' ' + end + 'px, ' + rightColor + ' 100%)';
    }

    static initNoteParser() {
        var config = Game.notePattern;
        if (typeof PatternValidator !== 'undefined' &&
            (config.indexOf(';') !== -1 || config.indexOf('|') !== -1 ||
             (config.indexOf(':') !== -1 && /\d+k\s*:/.test(config))))
            config = PatternValidator.getKeyConfig(config, Game.keyCount);
        try {
            Game.noteParser = new NoteParser(Game.keyCount);
            Game.noteParser.config = config;
            Game.noteParser.position = 0;
            Game.noteParser.lastTrack = Game._lastGeneratedTrack >= 0 ? Game._lastGeneratedTrack : -1;
            // 循环符 / 由 NoteParser 在概率展开后自动识别
        } catch(e) { Game.noteParser = null; }
        Game._currentRowHitTracks = {};
        Game._rowMetaCache = {};
    }

    static getNextRow() {
        if (Game.noteParser && Game.noteParser.getNextRow)
            return Game.noteParser.getNextRow();
        return [{ track: Math.floor(Math.random() * Game.keyCount) }];
    }

    static restart() {
        if (Game.timerMsInterval) { clearInterval(Game.timerMsInterval); Game.timerMsInterval = null; }
        if (Game._pendingHideTimer) { clearTimeout(Game._pendingHideTimer); Game._pendingHideTimer = null; }
        if (Game.gameLayerBG) Game.gameLayerBG.className = '';
        if (Game.gameTimeLayer) Game.gameTimeLayer.className = Game.gameTimeLayer.className.replace(/\s*timer-flash/g, '');
        Game.gameBBList = []; Game.gameBBListIndex = 0; Game.date1 = null; Game.date2 = null;
        Game.gameScore = 0; Game.gameKeyHits = 0; Game.gameRowHits = 0; Game.gameErrors = 0;
        Game.gameOver = false; Game.gameStart = false; Game.gamePaused = false; Game.lastTapTime = 0; Game.effectiveLastTapTime = 0;
        Game.tapTimes = []; Game.cpsHistory = []; Game.pausedTimeElapsed = 0; Game._pauseTimes = [];
        Game.protectedCooldown = false; Game.lastProtectedTrack = -1; Game.speedMultiplier = 1; Game._lastGeneratedTrack = -1;
        Game._currentRowHitTracks = {};
        Game._bigKeyProtection = {};
        Game._pauseStart = null;
        Game._pausedDuration = 0;
        Game._pendingHideCells = [];
        Game._pendingHideSet = new Set();
        Game._pendingHideStart = 0;
        Game._rowCellMapCache = {};
        Game._rowBBListRangeCache = {};
        Game._rowMetaCache = {};
        var cpsEl = document.getElementById('cpsDisplay');
        if (cpsEl) cpsEl.style.display = 'none';
        if (Game.isPractice) Game.gameTimeNum = 0; else Game.gameTimeNum = Game.timeLimit;
        Game.countBlockSize(); Game.ensureLayerBlocks();
        if (Game.gameLayers.length < 2) {
            Game.gameLayers = [];
            Game.gameLayers.push(document.getElementById('GameLayer1'));
            Game.gameLayers.push(document.getElementById('GameLayer2'));
        }
        Game.updateTimeDisplay();
        Game.initNoteParser();
        if (Game.gameLayers.length > 0) {
            Game.refreshGameLayer(Game.gameLayers[0], 0, -Game.blockSize);
            var numRows = Math.floor(Game.gameLayers[0].children.length / Game.keyCount);
            Game.refreshGameLayer(Game.gameLayers[1], 1, -Game.blockSize * (numRows + 1));
        }
    }

    static start() {
        Game.date1 = new Date();
        Game.gameStart = true;
        Game.lastTapTime = Game.date1.getTime();
        Game.effectiveLastTapTime = Game.lastTapTime;
        var pb = document.getElementById('pauseBtn');
        if (pb) pb.style.display = 'block';
        var cpsEl = document.getElementById('cpsDisplay');
        if (cpsEl) cpsEl.style.display = 'block';
        var modeEl = document.getElementById('gameModeDisplay');
        if (modeEl) {
            if (Game.gameMode === 'protected') { modeEl.textContent = '保护模式'; modeEl.style.display = 'block'; }
            else modeEl.style.display = 'none';
        }
        if (Game.isPractice) {
            Game.gameTimeNum = 0;
            Game.timerMsInterval = setInterval(function() {
                if (!Game.gamePaused && !Game.gameOver) { Game.gameTimeNum += 0.01; Game.updateTimeDisplay(); }
            }, 10);
        } else {
            Game.gameTimeNum = Game.timeLimit;
            Game.updateTimeDisplay();
            Game.timerMsInterval = setInterval(function() {
                if (!Game.gamePaused && !Game.gameOver) {
                    Game.gameTimeNum -= 0.01;
                    if (Game.gameTimeNum <= 0) { Game.gameTimeNum = 0; Game.updateTimeDisplay(); Game.endGame(); }
                    else Game.updateTimeDisplay();
                }
            }, 10);
        }
    }

    static updateTimeDisplay() {
        if (!Game.gameTimeLayer) return;
        var s = Game.gameTimeNum.toFixed(2);
        Game.gameTimeLayer.innerHTML = Game.isPractice ? '<span class="timer-text">练习中: ' + s + 's</span>' : '<span class="timer-text">' + s + 's</span>';
    }

    static endGame(errCell) {
        Game.date2 = new Date(); Game.gameOver = true;
        if (Game.timerMsInterval) { clearInterval(Game.timerMsInterval); Game.timerMsInterval = null; }
        // 如果游戏结束时仍处于暂停状态，先累加暂停时间
        if (Game._pauseStart !== null) {
            Game._pausedDuration += Date.now() - Game._pauseStart;
            Game._pauseStart = null;
        }
        Game.gamePaused = false;
        Game.calculateDeviationTime();
        if (Game.gameTimeNum <= 0 && Game.gameTimeLayer) Game.gameTimeLayer.className += ' timer-flash';
        if (errCell !== undefined && errCell >= 0) {
            for (var li = 0; li < Game.gameLayers.length; li++) {
                var layer = Game.gameLayers[li];
                if (!layer || !layer.children) continue;
                for (var bi = 0; bi < layer.children.length; bi++) {
                    var block = layer.children[bi];
                    if (block && (bi % Game.keyCount) === errCell) block.className += ' red-flash';
                }
            }
        }
        setTimeout(function() { Game.showScoreLayer(); }, 1500);
    }

    static calculateDeviationTime() {
        if (Game.isPractice) { Game.deviationTime = Game.gameTimeNum * 1000; return; }
        if (!Game.date1) { Game.deviationTime = Game.timeLimit * 1000; return; }
        var st = Game.date1.getTime(), et = Game.date2 ? Game.date2.getTime() : Date.now();
        var totalPaused = Game._pausedDuration;
        if (Game._pauseStart !== null) totalPaused += (Date.now() - Game._pauseStart);
        var ms = et - st - totalPaused;
        var d = ms / 1000;
        if (d <= 0) { Game.deviationTime = Game.timeLimit * 1000; return; }
        // 使用排除暂停后的等效时间，避免暂停时间计入"最后一次按键时间点"
        var lc = (Game.effectiveLastTapTime - st) / 1000;
        if (lc < 0) lc = 0;
        Game.deviationTime = Math.max(d - 1, d * 0.9, lc, d - 2 * Game.gameScore / d) * 1000;
    }

    static showScoreLayer() {
        var l = document.getElementById('GameScoreLayer');
        if (!l) return;
        Game.saveCurrentRecord();
        var c = '1';
        if (Game.gameBBList.length > 0 && Game.gameBBListIndex > 0 && Game.gameBBList[Game.gameBBListIndex - 1]) {
            var lb = document.getElementById(Game.gameBBList[Game.gameBBListIndex - 1].id);
            if (lb && lb.className) { var m = lb.className.match(Game._ttreg); if (m) c = m[1]; }
        }
        l.className = l.className.replace(/bgc\d/, 'bgc' + c);
        var settings = window.Storage ? Storage.loadSettings() : {};
        var ct = settings.hideComment ? '' : '<span style="color:red;">' + Game.shareText() + '</span>';
        var ce = document.getElementById('GameScoreLayer-text'); if (ce) ce.innerHTML = ct;
        var cps = Game.gameScore > 0 && Game.deviationTime > 0 ? (Game.gameScore * 1000 / Game.deviationTime).toFixed(2) : '0.00';
        var txt = '您坚持了 ';
        if (Game.isPractice) txt += "<span style='color:red;'>" + Game.gameTimeNum.toFixed(2) + "</span> 秒！<br>";
        else { var at = (Game.deviationTime / 1000).toFixed(2); txt += "<span style='color:red;'>" + at + "</span> 秒！<br>"; txt += '设定时间: <span style="color:red;">' + Game.timeLimit + 's</span><br>'; }
        txt += '得分: <span style="color:red;">' + Game.gameScore + "</span><br>";
        txt += '行数: <span style="color:red;">' + Game.gameRowHits + "</span><br>";
        txt += '平均CPS: <span style="color:red;">' + cps + "</span><br>";
        if (Game.gameMode === 'protected') txt += '按错次数: <span style="color:red;">' + Game.gameErrors + '</span><br>';
        else if (Game.gameMode !== 'normal') txt += '按错次数: <span style="color:red;">' + Game.gameErrors + '</span><br>';
        if (Game.gameRowHits > 0 && Game.deviationTime > 0) txt += '相当于 <span style="color:red;">' + (Game.gameRowHits * 15000 / Game.deviationTime).toFixed(2) + '</span> BPM 下的十六分音符哦！<br>';
        var se = document.getElementById('GameScoreLayer-score'); if (se) se.innerHTML = txt;
        var bast = 0, catKey = Game.keyCount + 'k_' + Game.notePattern.substring(0, 20) + '_' + Game.timeLimit + 's_' + Game.gameMode;
        if (window.Storage) { var br = Storage.getBestRecord(catKey); if (br) bast = br.score; if (Game.gameScore > bast) bast = Game.gameScore; }
        var be = document.getElementById('GameScoreLayer-bast'); if (be) be.innerHTML = '本模式最佳得分 <span style="color:red;">' + bast + "</span>";
        var ne = document.getElementById('now'); if (ne) ne.innerHTML = '键型: <span style="color:red;">' + Game.notePattern + '</span> | 键数: <span style="color:red;">' + Game.keyCount + 'k</span>';
        l.style.display = 'block';
    }

    static saveCurrentRecord() {
        if (!window.Storage) return;
        var cps = Game.gameScore > 0 && Game.deviationTime > 0 ? (Game.gameScore * 1000 / Game.deviationTime) : 0;
        Storage.saveHistoryRecord({ keyCount: Game.keyCount, notePattern: Game.notePattern, timeLimit: Game.timeLimit, gameMode: Game.gameMode, score: Game.gameScore, cps: cps, keyHits: Game.gameKeyHits, rowHits: Game.gameRowHits, errorCount: Game.gameErrors, date: new Date().toLocaleString(), isDead: Game.gameErrors > 0 && Game.gameBBListIndex < Game.gameBBList.length, actualTime: Game.deviationTime / 1000 });
    }

    static shareText() {
        var s = Game.gameScore, t = Game.timeLimit;
        if (s <= 3 * t) return '加油！我相信您可以的！';
        if (s <= 6 * t) return '^_^ 加把劲，底力大王就是您！';
        if (s <= 9 * t) return '您！';
        if (s <= 12 * t) return '太 您 了！';
        return '您是外星人嘛？';
    }

    static _cpsRafPending = false;
    static recordHit() {
        var now = Date.now(); Game.lastTapTime = now;
        // 等效时间戳 = 实际时间 - 已累计暂停时长（排除暂停时间）
        Game.effectiveLastTapTime = now - Game._pausedDuration;
        Game.tapTimes.push(now); Game.cpsHistory.push(now);
        while (Game.cpsHistory.length > 0 && Game.cpsHistory[0] < now - 2000) Game.cpsHistory.shift();
        Game.updateScrollSpeed();
        // rAF 节流：同一帧内多次按键只更新一次 CPS DOM
        if (!Game._cpsRafPending) {
            Game._cpsRafPending = true;
            if (window.requestAnimationFrame) {
                window.requestAnimationFrame(function() {
                    Game._cpsRafPending = false;
                    Game.updateCPSDisplay();
                });
            } else {
                Game._cpsRafPending = false;
                Game.updateCPSDisplay();
            }
        }
    }

    static updateCPSDisplay() {
        var el = document.getElementById('cpsDisplay'); if (!el) return;
        var now = Date.now(), oneSec = now - 1000, recent = 0, first = 0;
        for (var ci = Game.cpsHistory.length - 1; ci >= 0; ci--) {
            if (Game.cpsHistory[ci] >= oneSec) { recent++; first = Game.cpsHistory[ci]; }
            else break;
        }
        var cpsVal = 0;
        if (recent > 1 && first > 0) { var span = (now - first) / 1000; if (span > 0) cpsVal = (recent - 1) / span; }
        else if (recent === 1) cpsVal = 1;
        var n = el.querySelector('.cps-number'); if (n) n.textContent = cpsVal.toFixed(2) + ' CPS';
        var b = el.querySelector('.cps-bar-inner'); if (b) b.style.width = Math.min(100, (cpsVal / 20) * 100) + '%';
        var h = el.querySelector('.cps-hits'); if (h) h.textContent = Game.gameKeyHits + '/' + Game.gameRowHits;
    }

    static updateScrollSpeed() {
        var r = Game.gameBBList.length - Game.gameBBListIndex;
        if (r <= 4) Game.speedMultiplier = Math.min(3, 1 + (4 - r) * 0.5);
        else if (r <= 8) Game.speedMultiplier = 1 + (4 - (r - 4)) * 0.25;
        else Game.speedMultiplier = 1;
        if (Game.speedMultiplier < 1) Game.speedMultiplier = 1;
    }

    /** 一次性隐藏所有收集到的块（多压快速连按时视觉上同时消失）；清除待定隐藏定时器 */
    static flushPendingHides() {
        if (Game._pendingHideTimer) {
            clearTimeout(Game._pendingHideTimer);
            Game._pendingHideTimer = null;
        }
        if (!Game._pendingHideCells || Game._pendingHideCells.length === 0) {
            Game._pendingHideStart = 0;
            return;
        }
        var len = Game._pendingHideCells.length;
        for (var i = 0; i < len; i++) {
            var cell = Game._pendingHideCells[i];
            if (!cell) continue;
            cell.className = cell.className.replace(Game._ttreg, ' tt$1');
            cell.style.backgroundImage = 'none';
        }
        Game._pendingHideCells = [];
        if (Game._pendingHideSet) Game._pendingHideSet.clear();
        Game._pendingHideStart = 0;
    }

    /** 多压行内慢速点按时的独立定时器：到点自动隐藏已收集的块，无需等待下一个按键 */
    static schedulePendingHideFlush() {
        if (Game._pendingHideTimer) {
            clearTimeout(Game._pendingHideTimer);
            Game._pendingHideTimer = null;
        }
        // 无待隐藏块时不安排定时器
        if (!Game._pendingHideCells || Game._pendingHideCells.length === 0) {
            Game._pendingHideStart = 0;
            return;
        }
        Game._pendingHideTimer = setTimeout(function() {
            Game._pendingHideTimer = null;
            Game.flushPendingHides();
        }, 80);
    }

    /** 多压核心判定逻辑，支持大键 */
    static handleHit(clickedTrack, e) {
        if (Game.gameOver) return false;

        // ====== 暂停分支 ======
        if (Game.gamePaused) {
            if (Game.protectedCooldown) return false;
            var pps = Game.gameBBList[Game.gameBBListIndex];
            if (!pps) return false;
            var rnP = pps.rowNotes || [pps.cell];
            var hitP = false;
            // 暂停时也要检查大键的bigKeyTracks
            for (var rp = 0; rp < rnP.length; rp++) {
                var rnp = rnP[rp];
                if (typeof rnp === 'object' && rnp !== null) {
                    if (rnp.track === clickedTrack) { hitP = true; break; }
                    if (rnp.isBigKey && rnp.bigKeyTracks && rnp.bigKeyTracks.indexOf(clickedTrack) !== -1) { hitP = true; break; }
                } else {
                    if (rnp === clickedTrack) { hitP = true; break; }
                }
            }
            if (hitP) {
                Game.continueFromPause(); Game.playSound('tap');
                var rowIdP = pps.rowId;
                if (!Game._currentRowHitTracks[rowIdP]) Game._currentRowHitTracks[rowIdP] = {};
                if (!Game._currentRowHitTracks[rowIdP][clickedTrack]) {
                    Game._currentRowHitTracks[rowIdP][clickedTrack] = true;
                    Game.gameKeyHits++; Game.gameScore++;
                    // 暂停时的点击不更新 lastTapTime，以免暂停时间被计入坚持时间
                    var now = Date.now(); Game.tapTimes.push(now); Game.cpsHistory.push(now);
                    while (Game.cpsHistory.length > 0 && Game.cpsHistory[0] < now - 2000) Game.cpsHistory.shift();
                    Game.updateScrollSpeed(); Game.updateCPSDisplay();
                }
                // 使用行缓存 O(1) 查找隐藏块
                var foundP = null;
                var cellMapP = Game._rowCellMapCache[rowIdP];
                if (cellMapP && cellMapP[clickedTrack]) {
                    foundP = cellMapP[clickedTrack];
                    foundP.className = foundP.className.replace(Game._ttreg, ' tt$1');
                    foundP.style.backgroundImage = 'none';
                }
                if (!foundP) { var el3P = document.getElementById(pps.id); if (el3P) { el3P.className = el3P.className.replace(Game._ttreg, ' tt$1'); el3P.style.backgroundImage = 'none'; } }
                // 检查该行所有note是否都按下了（大键只需bigKeyTracks中任一轨道被按下）
                var allHitP = true;
                for (var tiP = 0; tiP < rnP.length; tiP++) {
                    var ntP = rnP[tiP];
                    if (typeof ntP === 'object' && ntP !== null && ntP.isBigKey && ntP.bigKeyTracks && ntP.bigKeyTracks.length > 1) {
                        // 大键：检查 bigKeyTracks 中是否有任一轨道被按下
                        var anyHit = false;
                        for (var bkiP = 0; bkiP < ntP.bigKeyTracks.length; bkiP++) {
                            if (Game._currentRowHitTracks[rowIdP] && Game._currentRowHitTracks[rowIdP][ntP.bigKeyTracks[bkiP]]) {
                                anyHit = true; break;
                            }
                        }
                        if (!anyHit) { allHitP = false; break; }
                    } else {
                        var tvP = (typeof ntP === 'object' && ntP !== null) ? ntP.track : ntP;
                        if (!Game._currentRowHitTracks[rowIdP] || !Game._currentRowHitTracks[rowIdP][tvP]) { allHitP = false; break; }
                    }
                }
                if (allHitP) {
                    delete Game._currentRowHitTracks[rowIdP];
                    Game.gameRowHits++;
                    while (Game.gameBBListIndex < Game.gameBBList.length) {
                        var nxP = Game.gameBBList[Game.gameBBListIndex];
                        if (!nxP || nxP.rowId !== rowIdP) break;
                        Game.gameBBListIndex++;
                    }
                    Game.moveNextRow(); Game.updateCPSDisplay();
                }
                return false;
            }
            return false;
        }

        // ====== 非暂停（游玩中）分支 ======
        var p = Game.gameBBList[Game.gameBBListIndex];
        if (!p) return false;
        var rowNotes = p.rowNotes || [p.cell];

        // 使用行元数据缓存（Set/Map），避免每次点击重建数组
        var rowMeta = Game._rowMetaCache[p.rowId];
        if (!rowMeta) {
            rowMeta = { isBigKeyRow: false, bigKeyTrackSet: null, bigKeyNoteMap: null, allRowTrackSet: null };
            var bigKeyTrackSet = null;
            var bigKeyNoteMap = null;
            var allRowTrackSet = new Set();
            for (var ri = 0; ri < rowNotes.length; ri++) {
                var n = rowNotes[ri];
                if (typeof n === 'object' && n !== null) {
                    if (n.isBigKey && n.bigKeyTracks && n.bigKeyTracks.length > 1) {
                        rowMeta.isBigKeyRow = true;
                        if (!bigKeyTrackSet) bigKeyTrackSet = new Set();
                        if (!bigKeyNoteMap) bigKeyNoteMap = new Map();
                        for (var bki3 = 0; bki3 < n.bigKeyTracks.length; bki3++) {
                            var bkt = n.bigKeyTracks[bki3];
                            bigKeyTrackSet.add(bkt);
                            bigKeyNoteMap.set(bkt, n.bigKeyTracks);
                        }
                    }
                    allRowTrackSet.add(n.track);
                } else {
                    allRowTrackSet.add(n);
                }
            }
            rowMeta.bigKeyTrackSet = bigKeyTrackSet || new Set();
            rowMeta.bigKeyNoteMap = bigKeyNoteMap || new Map();
            rowMeta.allRowTrackSet = allRowTrackSet;
            Game._rowMetaCache[p.rowId] = rowMeta;
        }
        var isBigKeyRow = rowMeta.isBigKeyRow;
        var allBigKeyTracks = rowMeta.bigKeyTrackSet;
        var bigKeyNoteMap = rowMeta.bigKeyNoteMap;
        var allRowTracksSet = rowMeta.allRowTrackSet;

        // 大键保护：检查当前行是否包含被保护的轨道
        var isProtected = Game._bigKeyProtection[clickedTrack] === true;
        var isProtectedButInCurrentRow = false;
        if (isProtected) {
            // 如果当前行的有效轨道中包含被保护的轨道，则跳过保护
            isProtectedButInCurrentRow = allRowTracksSet.has(clickedTrack) || allBigKeyTracks.has(clickedTrack);
        }

        var hit = false;
        if (isBigKeyRow) {
            // 大键行：点击allBigKeyTracks中的任一轨道 或 点击同一行的单键轨道
            hit = allBigKeyTracks.has(clickedTrack) || allRowTracksSet.has(clickedTrack);
        } else if (isProtected && !isProtectedButInCurrentRow) {
            delete Game._bigKeyProtection[clickedTrack];
            return false;
        } else {
            hit = allRowTracksSet.has(clickedTrack);
        }

        if (hit) {
            if (!Game.gameStart) Game.start();
            Game.playSound('tap');
            var rowId = p.rowId;
            if (!Game._currentRowHitTracks[rowId]) Game._currentRowHitTracks[rowId] = {};
            if (!Game._currentRowHitTracks[rowId][clickedTrack]) {
                Game._currentRowHitTracks[rowId][clickedTrack] = true;
                Game.gameKeyHits++; Game.gameScore++; Game.recordHit();
            }

            // 收集待隐藏的块（多压快速连按时整行一起消失，见 flushPendingHides）
            var isMultiRow = rowNotes.length > 1 || isBigKeyRow;
            var nowMs = Date.now();
            if (!Game._pendingHideCells) { Game._pendingHideCells = []; Game._pendingHideSet = new Set(); }
            // 行→gameBBList 范围缓存
            var bbRange = Game._rowBBListRangeCache[rowId];
            if (!bbRange) {
                var _s = Game.gameBBListIndex;
                while (_s < Game.gameBBList.length && Game.gameBBList[_s] && Game.gameBBList[_s].rowId !== rowId) _s++;
                var _e = _s;
                while (_e < Game.gameBBList.length && Game.gameBBList[_e] && Game.gameBBList[_e].rowId === rowId) _e++;
                bbRange = [_s, _e];
                Game._rowBBListRangeCache[rowId] = bbRange;
            }
            // 行→DOM元素映射缓存
            var cellMap = Game._rowCellMapCache[rowId];
            if (!cellMap) {
                cellMap = {};
                for (var _ci = bbRange[0]; _ci < bbRange[1]; _ci++) {
                    var _en = Game.gameBBList[_ci];
                    if (!_en) continue;
                    var _el = document.getElementById(_en.id);
                    if (_el) cellMap[_en.cell] = _el;
                }
                Game._rowCellMapCache[rowId] = cellMap;
            }
            // 大键命中时：收集被按轨道所属的特定大键的所有轨道
            if (isBigKeyRow && allBigKeyTracks.has(clickedTrack)) {
                var targetBigKeyTracks = bigKeyNoteMap.get(clickedTrack);
                if (targetBigKeyTracks) {
                    var targetSet = new Set(targetBigKeyTracks);
                    for (var hidx = bbRange[0]; hidx < bbRange[1]; hidx++) {
                        var hen = Game.gameBBList[hidx];
                        if (!hen) continue;
                        if (targetSet.has(hen.cell)) {
                            var hel = cellMap[hen.cell];
                            if (hel && hel.style.backgroundImage !== 'none' && !Game._pendingHideSet.has(hel)) {
                                Game._pendingHideSet.add(hel);
                                Game._pendingHideCells.push(hel);
                            }
                        }
                    }
                }
            }
            // 收集被按下的单个轨道
            var el2 = cellMap[clickedTrack];
            var foundCell = null;
            if (el2) {
                if (el2.style.backgroundImage !== 'none' && !Game._pendingHideSet.has(el2)) {
                    Game._pendingHideSet.add(el2);
                    Game._pendingHideCells.push(el2);
                }
                foundCell = clickedTrack;
            }
            if (foundCell === null) {
                var el3 = document.getElementById(p.id);
                if (el3 && el3.style.backgroundImage !== 'none' && !Game._pendingHideSet.has(el3)) {
                    Game._pendingHideSet.add(el3);
                    Game._pendingHideCells.push(el3);
                }
            }
            // 单键行立即隐藏；多压行安排 80ms 独立定时器（快速连按→整行一起消失；慢速点按→到点自动隐藏，不等下一个按键）
            if (!isMultiRow) {
                Game.flushPendingHides();
            } else {
                Game.schedulePendingHideFlush();
            }

            // 判定该行是否全部完成
            var allHit = true;
            // 检查所有note：大键只需bigKeyTracks中任一轨道，普通键需精确命中
            for (var ti = 0; ti < rowNotes.length; ti++) {
                var nt = rowNotes[ti];
                if (typeof nt === 'object' && nt !== null && nt.isBigKey && nt.bigKeyTracks && nt.bigKeyTracks.length > 1) {
                    // 大键：检查 bigKeyTracks 中是否有任一轨道被按下
                    var anyBk = false;
                    for (var bki2 = 0; bki2 < nt.bigKeyTracks.length; bki2++) {
                        if (Game._currentRowHitTracks[rowId] && Game._currentRowHitTracks[rowId][nt.bigKeyTracks[bki2]]) {
                            anyBk = true; break;
                        }
                    }
                    if (!anyBk) { allHit = false; break; }
                } else {
                    var trackVal3 = (typeof nt === 'object' && nt !== null) ? nt.track : nt;
                    if (!Game._currentRowHitTracks[rowId] || !Game._currentRowHitTracks[rowId][trackVal3]) { allHit = false; break; }
                }
            }

            if (allHit) {
                delete Game._currentRowHitTracks[rowId];
                Game.gameRowHits++;
                // 整行全部命中 → 立即一次性隐藏该行所有已收集块（视觉上同时消失）
                Game.flushPendingHides();
                // 设置大键保护（保护所有大键轨道）
                Game._bigKeyProtection = {};
                if (isBigKeyRow && allBigKeyTracks.size > 0) {
                    allBigKeyTracks.forEach(function(bkTrack) {
                        Game._bigKeyProtection[bkTrack] = true;
                    });
                }
                while (Game.gameBBListIndex < Game.gameBBList.length) {
                    var nx = Game.gameBBList[Game.gameBBListIndex];
                    if (!nx || nx.rowId !== rowId) break;
                    Game.gameBBListIndex++;
                }
                Game.moveNextRow(); Game.updateCPSDisplay();
            }
            return false;
        } else if (Game.gameStart) {
            Game.gameErrors++; Game.playSound('err');
            if (Game.gameMode === 'protected') { Game.enterProtectedPause(e ? e.target : null); return false; }
            else { Game.endGame(clickedTrack); return false; }
        }
        return false;
    }

    static handleTap(e) {
        if (Game.gameOver) return false;
        var bodyRect = Game.body.getBoundingClientRect();
        var x = (e.clientX || (e.targetTouches ? e.targetTouches[0].clientX : 0)) - bodyRect.left - Game.blockOffset;
        var track = Math.floor(x / Game.blockSize);
        if (track < 0) track = 0; if (track >= Game.keyCount) track = Game.keyCount - 1;
        return Game.handleHit(track, e);
    }

    static handleKeyClick(trackNumber) {
        if (Game.gameOver) return;
        var track = trackNumber - 1;
        if (Game.gamePaused) {
            if (Game.protectedCooldown) return;
            Game.handleHit(track, null);
            return;
        }
        Game.handleHit(track, null);
    }

    static enterProtectedPause(tar) {
        if (Game.protectedCooldown) return;
        // 如果已经在暂停中（_pauseStart 非 null），先累加已有暂停时长，避免覆盖丢失
        if (Game._pauseStart !== null) {
            Game._pausedDuration += Date.now() - Game._pauseStart;
        }
        Game.protectedCooldown = true; Game.gamePaused = true;
        Game._pauseStart = Date.now();
        var overlay = document.getElementById('pauseOverlay');
        if (overlay) overlay.style.display = 'block';
        var cd = document.querySelector('.cd-bar-container'), ci = document.getElementById('cdBarInner');
        if (cd) cd.style.display = 'block';
        if (ci) { ci.style.transition = 'none'; ci.style.width = '100%'; setTimeout(function() { ci.style.transition = 'width 0.3s linear'; ci.style.width = '0%'; }, 10); }
        setTimeout(function() { Game.protectedCooldown = false; if (cd) cd.style.display = 'none'; }, 300);
    }

    static continueFromPause() {
        if (Game.protectedCooldown) return;
        if (Game.gamePaused && Game._pauseStart !== null) {
            Game._pausedDuration += Date.now() - Game._pauseStart;
            Game._pauseStart = null;
        }
        Game.gamePaused = false;
        var o = document.getElementById('pauseOverlay'); if (o) o.style.display = 'none';
        var c = document.querySelector('.cd-bar-container'); if (c) c.style.display = 'none';
    }

    static playSound(id) {
        try { var s = window.Storage ? Storage.loadSettings() : {}; if (s.soundEnabled !== false) createjs.Sound.play(id); } catch(e) {}
    }

    static refreshGameLayer(box, loop, yOffset) {
        if (!box || !box.children) return;
        // 多色主题：轨道色块固定在背景层
        Game.setTrackBackground();
        // 层重建时清理行缓存（旧的 rowId → DOM 映射已失效）
        Game._rowCellMapCache = {};
        Game._rowBBListRangeCache = {};
        Game._rowMetaCache = {};
        var k = Game.keyCount, numRows = Math.floor(box.children.length / k);
        var startRow = 0;
        // 根据当前主题计算轨道背景色（层刷新时重新应用）
        var theme = Game.themeColor || 'light';
        function _trackBg(trackIdx) {
            switch (theme) {
                case 'dark': return '#303030';
                case 'pinstripe':
                case 'splitR':
                case 'splitL': return 'transparent'; // 轨道颜色由固定的背景层提供
                default: return '#ffffff';
            }
        }
        var gridCol = (theme === 'dark') ? '#0D559D' : ((theme === 'light') ? '#b8dfe6' : '#B8CFBC');
        for (var j = 0; j < box.children.length; j++) {
            var r = box.children[j], rs = r.style;
            rs.left = (j % k) * Game.blockSize + Game.blockOffset + 'px';
            rs.bottom = Math.floor(j / k) * Game.blockSize + 'px';
            rs.width = Game.blockSize + 'px'; rs.height = Game.blockSize + 'px';
            rs.backgroundImage = 'none';
            rs.backgroundColor = _trackBg(j % k);
            rs.borderTopColor = gridCol;
            rs.filter = '';
            r.className = r.className.replace(Game._clearttClsReg, '');
            r.notEmpty = false;
        }
        for (var ri = startRow; ri < numRows; ri++) {
            if (Game.gameBBList.length > 5000) break;
            var rowNotes = Game.getNextRow();
            if (!rowNotes || rowNotes.length === 0) continue;
            var rowId = 'r' + Game.gameBBList.length + '_' + Date.now() + '_' + Math.random();
            // 为大键生成颜色
            var bigKeyColors = [];
            for (var ni = 0; ni < rowNotes.length; ni++) {
                if (rowNotes[ni].isBigKey && rowNotes[ni].bigKeyTracks && rowNotes[ni].bigKeyTracks.length > 1) {
                    var hue = Math.floor(Math.random() * 360);
                    while (bigKeyColors.some(function(c) { return Math.abs(c - hue) < 60; })) {
                        hue = (hue + 137.5) % 360;
                    }
                    bigKeyColors.push(hue);
                }
            }
            var bkcIdx = 0;
            var renderedCells = {};
            for (var ni = 0; ni < rowNotes.length; ni++) {
                var note = rowNotes[ni], cell = note.track;
                if (note.isBigKey && note.bigKeyTracks && note.bigKeyTracks.length > 1) {
                    var hue = bigKeyColors[bkcIdx] || 0;
                    bkcIdx++;
                    for (var bkt = 0; bkt < note.bigKeyTracks.length; bkt++) {
                        var bkCell = note.bigKeyTracks[bkt];
                        if (bkCell < 0 || bkCell >= k) continue;
                        if (renderedCells[bkCell]) continue;
                        renderedCells[bkCell] = true;
                        var bgi = bkCell + ri * k;
                        if (bgi >= box.children.length) continue;
                        var rb = box.children[bgi], rsb = rb.style;
                        rsb.backgroundImage = 'url(' + Game.url + ')';
                        rsb.backgroundSize = 'cover';
                        // 大键染色：略微降低饱和、提高亮度
                        rsb.filter = 'brightness(0.7) sepia(1) hue-rotate(' + hue + 'deg) saturate(3)';
                        rb.className += ' t' + (Math.floor(Math.random() * 1000) % (k + 1) + 1);
                        rb.notEmpty = true;
                        Game.gameBBList.push({ cell: bkCell, id: rb.id, rowNotes: rowNotes, rowId: rowId });
                    }
                } else {
                    if (cell < 0 || cell >= k) continue;
                    if (renderedCells[cell]) continue;
                    renderedCells[cell] = true;
                    var gi = cell + ri * k;
                    if (gi >= box.children.length) continue;
                    var r2 = box.children[gi], rs2 = r2.style;
                    rs2.backgroundImage = 'url(' + Game.url + ')';
                    rs2.backgroundSize = 'cover';
                    rs2.filter = '';
                    r2.className += ' t' + (Math.floor(Math.random() * 1000) % (k + 1) + 1);
                    r2.notEmpty = true;
                    Game.gameBBList.push({ cell: cell, id: r2.id, rowNotes: rowNotes, rowId: rowId });
                }
            }
            if (rowNotes.length > 0) Game._lastGeneratedTrack = rowNotes[rowNotes.length - 1].track;
        }
        if (loop) {
            var otherLayer = null;
            for (var li = 0; li < Game.gameLayers.length; li++) {
                if (Game.gameLayers[li] && Game.gameLayers[li] !== box) {
                    otherLayer = Game.gameLayers[li];
                    break;
                }
            }
            var newY = otherLayer ? otherLayer.y : 0;
            box.style.webkitTransitionDuration = '0ms';
            box.style[Game.transitionDuration] = '0ms';
            box.style.display = 'none';
            box.y = newY - Game.blockSize * numRows;
            var thatBox = box;
            setTimeout(function() {
                thatBox.style[Game.transform] = 'translate3D(0,' + thatBox.y + 'px,0)';
                setTimeout(function() {
                    thatBox.style.display = 'block';
                }, 0);
            }, 0);
        } else {
            box.y = yOffset || 0;
            box.style[Game.transform] = 'translate3D(0,' + box.y + 'px,0)';
        }
        box.style[Game.transitionDuration] = '180ms';
    }

    static randomPos() { return 0; }

    static moveNextRow() {
        var ms = Math.max(60, Math.round(Game.baseTransitionDuration / Game.speedMultiplier));
        for (var i = 0; i < Game.gameLayers.length; i++) {
            var g = Game.gameLayers[i];
            if (!g) continue;
            g.y += Game.blockSize;
        }
        var threshold = Game.blockSize * Math.floor((Game.gameLayers[0] ? Game.gameLayers[0].children.length : 0) / Game.keyCount);
        for (var i = 0; i < Game.gameLayers.length; i++) {
            var g = Game.gameLayers[i];
            if (!g) continue;
            if (g.y >= threshold) {
                Game.refreshGameLayer(g, 1);
            } else {
                g.style[Game.transitionDuration] = ms + 'ms';
                g.style[Game.transform] = 'translate3D(0,' + g.y + 'px,0)';
            }
        }
    }

    static togglePause() {
        if (Game.gameOver) return;
        if (!Game.gamePaused) {
            Game.gamePaused = true;
            Game._pauseStart = Date.now();
        } else {
            if (Game._pauseStart !== null) {
                Game._pausedDuration += Date.now() - Game._pauseStart;
                Game._pauseStart = null;
            }
            Game.gamePaused = false;
        }
        var o = document.getElementById('pauseOverlay');
        if (o) o.style.display = Game.gamePaused ? 'flex' : 'none';
    }
}
