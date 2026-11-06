/**
 * 键型解析器 - 解析键型设置字符串并生成音符序列
 * 支持：多压(a+b)、大键(a~b)、有限随机键(!(a,b)/@(a,b)/#(a,b))、
 *       随机化((概率,配置))、多key适配(:;|)、a~z映射到10~35轨、循环符(/)
 * 不合规键型纠正功能
 * 版本: fix20240731 - 概率预处理器（字符串级展开）+ 全角修正 + @有限随机键修复 + 跨轮+连接
 *
 * 概率语义（字符串级预处理器）：
 *   (0.x,CONFIG) 在解析前展开为普通字符串：
 *     命中 → CONFIG 原样插入；未命中 → 括号整体删除
 *   (p1,cfg1,p2,cfg2,...) 多分支按概率选择其一
 *   嵌套概率递归展开
 *   展开后的字符串完全由普通语法（! @ # + ~ & 数字 等）解析
 */

// 自检：确认新版代码已加载
try { window.__noteParserVersion = "fix20240731_v3"; } catch(e) {}

class NoteParser {
    constructor(keyCount) {
        this.keyCount = keyCount;
        this.lastTrack = -1;
        this.lastRowTracks = [];
        this.lastRowNotes = [];
        this.position = 0;
        this.config = '';
        this.lastConfig = '';
        this.savedConfigStack = [];
        this._bigKeyProtection = {};
        this._loopStartPos = -1;
        this._loopEndPos = -1;
        this._isLooping = false;
        this._inRepeat = false;
        this._pendingConfigs = [];
        this._avoidTracks = null;
        this._inTrailingMerge = false;
        // 概率预处理器状态
        this._rawConfig = '';
        this._expanded = false;
        this._crossRowCycle = false;
        this._crossRowProcessed = false;
    }

    parse(config, keyCount) {
        this.keyCount = keyCount;
        this._rawConfig = config;
        this._crossRowCycle = config.charAt(0) === '+';
        this._crossRowProcessed = false;
        // 概率展开
        this.config = NoteParser.expandAllProbabilities(config, 0);
        this._expanded = true;
        this._applyLoopMarker();
        this.position = 0;
        this.lastTrack = -1;
        this.lastRowTracks = [];
        this.lastRowNotes = [];
        this.savedConfigStack = [];
        this._loopStartPos = -1;
        this._loopEndPos = -1;
        this._isLooping = false;
        this._inRepeat = false;
        this._bigKeyProtection = {};
        this._pendingConfigs = [];

        var rows = [];
        var maxIterations = 5000;
        var iter = 0;

        while (this.position < this.config.length && iter < maxIterations) {
            iter++;
            var row = this.parseRow();
            if (row && row.length > 0) {
                rows.push(row);
                this.lastRowTracks = row.map(function(n) { return n.track; });
                this.lastRowNotes = row.slice();
                if (row.length > 0) {
                    this.lastTrack = row[row.length - 1].track;
                }
            } else {
                this.position++;
            }
        }
        this._pendingConfigs = [];
        return rows;
    }

    getNextRow() {
        // 惰性首次展开（game-core 手动设置 config 时）
        if (!this._expanded) {
            this._rawConfig = this.config;
            this._crossRowCycle = this.config.charAt(0) === '+';
            this._crossRowProcessed = false;
            this.config = NoteParser.expandAllProbabilities(this.config, 0);
            this._expanded = true;
            this._applyLoopMarker();
        }

        // 跨轮 + 循环连接（+12 → 首轮[1]，后续[2+1]）
        if (this._crossRowCycle && this.isCrossRowStart()) {
            var crossRow = this.parseCrossRow();
            if (crossRow && crossRow.length > 0) {
                this.lastRowTracks = crossRow.map(function(n) { return n.track; });
                this.lastRowNotes = crossRow.slice();
                this.lastTrack = crossRow[crossRow.length - 1].track;
                return crossRow;
            }
        }

        // 优先处理上一轮概率命中的"下一行"音符（兼容旧内部逻辑，展开后一般不产生）
        while (this._pendingConfigs.length > 0) {
            var pendingNote = this._pendingConfigs.shift();
            if (!pendingNote) continue;
            if (pendingNote._plainConfig) delete pendingNote._plainConfig;
            var pRow = pendingNote.isMulti && pendingNote.notes ? pendingNote.notes : [pendingNote];
            if (pRow.length > 0) {
                this.lastRowTracks = pRow.map(function(n) { return n.track; });
                this.lastRowNotes = pRow.slice();
                if (pRow.length > 0) this.lastTrack = pRow[pRow.length - 1].track;
                for (var pi = 0; pi < pRow.length; pi++) {
                    if (pRow[pi].isBigKey && pRow[pi].bigKeyTracks) {
                        for (var pti = 0; pti < pRow[pi].bigKeyTracks.length; pti++) {
                            this._bigKeyProtection[pRow[pi].bigKeyTracks[pti]] = true;
                        }
                    }
                }
                return pRow;
            }
        }
        if (this.position >= this.config.length) {
            if (this._isLooping && this._loopStartPos >= 0) {
                this.position = this._loopStartPos;
            } else {
                this.position = 0;
                // 每轮循环重新展开概率（保持概率每轮独立随机）
                this.config = NoteParser.expandAllProbabilities(this._rawConfig, 0);
                this._crossRowProcessed = false;
            }
        }
        var row = this.parseRow();
        if (row === null) {
            if (this._isLooping && this._loopStartPos >= 0) {
                this.position = this._loopStartPos;
                row = this.parseRow();
            }
        }
        if (row === null || (row.length === 0)) {
            while (this.position < this.config.length &&
                   (this.config[this.position] === '/' || this.config[this.position] === ':' ||
                    this.config[this.position] === ';' || this.config[this.position] === '|')) {
                this.position++;
            }
            if (this.position < this.config.length) {
                row = this.parseRow();
            }
        }
        if (row && row.length > 0) {
            this.lastRowTracks = row.map(function(n) { return n.track; });
            this.lastRowNotes = row.slice();
            if (row.length > 0) {
                this.lastTrack = row[row.length - 1].track;
            }
            for (var ni = 0; ni < row.length; ni++) {
                if (row[ni].isBigKey && row[ni].bigKeyTracks) {
                    for (var ti = 0; ti < row[ni].bigKeyTracks.length; ti++) {
                        this._bigKeyProtection[row[ni].bigKeyTracks[ti]] = true;
                    }
                }
            }
            return row;
        }
        return [{ track: this.randomTrack() }];
    }

    /** 循环符 /：/ 前的内容只出现一次，/ 后的内容循环播放（如 12/34 → 12343434…）
     *  须在概率展开后调用，避免展开导致的字符串长度变化使循环起点错位 */
    _applyLoopMarker() {
        var slashIdx = this.config.indexOf('/');
        if (slashIdx !== -1) {
            this._loopStartPos = slashIdx + 1;
            this._loopEndPos = this.config.length;
            this._isLooping = true;
        } else {
            this._loopStartPos = -1;
            this._loopEndPos = -1;
            this._isLooping = false;
        }
    }

    /** 跨轮 + 模式：当前位置是否是循环起点（新一轮开始） */
    isCrossRowStart() {
        if (this.position >= this.config.length) return true;
        if (this._isLooping && this._loopStartPos >= 0 && this.position === this._loopStartPos) return true;
        return this.position === 0;
    }

    /**
     * 跨轮 + 行：+12 → 首轮行[1]，后续行[2+1]
     * 规则：配置以+开头，每轮只产生一行
     *   首轮（无上一轮）：行 = 配置中第一个有效音符
     *   后续轮：行 = 配置中最后一个有效音符 + 配置中第一个有效音符（双押）
     */
    parseCrossRow() {
        var raw = this._rawConfig;
        // 找到第一个有效音符（跳过 + 和符号）
        var firstTrack = -1;
        var lastTrack2 = -1;
        var firstChIdx = -1;
        var lastChIdx = -1;
        for (var i = 0; i < raw.length; i++) {
            var c = raw[i];
            var t = this.charToTrack(c);
            if (t !== null) { firstChIdx = i; break; }
            if (c === '!') { firstTrack = this.randomTrack(); firstChIdx = i; break; }
            if (c === '@') { firstTrack = this.randomTrackDifferent(); firstChIdx = i; break; }
            if (c === '#') { firstTrack = this.lastTrack >= 0 ? this.lastTrack : this.randomTrack(); firstChIdx = i; break; }
        }
        if (firstChIdx === -1) return null;
        if (firstTrack === -1) firstTrack = this.charToTrack(raw[firstChIdx]);
        // 找最后一个有效音符（从末尾向前）
        for (var j = raw.length - 1; j >= 0; j--) {
            var c2 = raw[j];
            var t2 = this.charToTrack(c2);
            if (t2 !== null) { lastTrack2 = t2; lastChIdx = j; break; }
            if (c2 === '!' || c2 === '@' || c2 === '#') { lastTrack2 = this.randomTrack(); lastChIdx = j; break; }
        }
        if (lastChIdx === -1) lastTrack2 = firstTrack;

        var row;
        if (!this._crossRowProcessed) {
            // 首轮只有第一个音符
            row = [this.createNote(firstTrack)];
            this._crossRowProcessed = true;
        } else {
            var tracks = [lastTrack2, firstTrack];
            var uniq = [];
            for (var ui = 0; ui < tracks.length; ui++) {
                if (uniq.indexOf(tracks[ui]) === -1) uniq.push(tracks[ui]);
            }
            row = [];
            for (var ri = 0; ri < uniq.length; ri++) {
                var n = this.createNote(uniq[ri]);
                if (n) row.push(n);
            }
            if (row.length === 0) row = [this.createNote(firstTrack)];
        }
        // 消费完本轮（跨轮模式下每轮只有一行）
        this.position = this.config.length;
        if (this._isLooping && this._loopStartPos >= 0) this.position = this._loopEndPos;
        return row;
    }

    /** 字符转轨道索引（数字/a-z/A-Z），非音符字符返回null */
    charToTrack(ch) {
        if (ch >= '0' && ch <= '9') {
            var t = parseInt(ch) - 1;
            if (t >= 0 && t < this.keyCount) return t;
            return null;
        }
        if (ch >= 'a' && ch <= 'z') {
            // a=第10轨(索引9) ... z=第35轨(索引34)
            var t = 8 + (ch.charCodeAt(0) - 96);
            if (t < this.keyCount) return t;
            return null;
        }
        if (ch >= 'A' && ch <= 'Z') {
            var lower = ch.toLowerCase();
            var t = 8 + (lower.charCodeAt(0) - 96);
            if (t < this.keyCount) return t;
            return null;
        }
        return null;
    }

    parseRow() {
        var row = [];
        // 行尾连接符跟踪：+ / ~ 若悬在行尾（其后是分隔符或行结束），
        // 则应与下一轮/下一循环段的第一个音符合并（如 3+/12+ → 3+1, 2+1, 2+1…）
        var trailingConn = null;
        var usedTracksInRow = new Set();
        // 继承外层传递的避免轨道集合（用于有限随机键避免当前行）
        if (this._avoidTracks) {
            var aitOuter = this._avoidTracks.values ? this._avoidTracks.values() : null;
            if (aitOuter) {
                var avOuter = aitOuter.next();
                while (!avOuter.done) { usedTracksInRow.add(avOuter.value); avOuter = aitOuter.next(); }
            } else {
                this._avoidTracks.forEach(function(v) { usedTracksInRow.add(v); });
            }
        }

        while (this.position < this.config.length) {
            var ch = this.config[this.position];
            if (ch === ':' || ch === ';' || ch === '|' || ch === '/') {
                this.position++;
                continue;
            }
            break;
        }

        while (this.position < this.config.length) {
            var ch = this.config[this.position];
            if (ch === '+') { this.position++; trailingConn = '+'; continue; }
            if (ch === '~') { this.position++; trailingConn = '~'; continue; }
            if (ch === ':' || ch === ';' || ch === '|' || ch === '/') break;

            // # 有限随机键 #(a,b)
            if (ch === '#' && this.position + 1 < this.config.length && this.config[this.position + 1] === '(') {
                var prevAvoid = this._avoidTracks;
                this._avoidTracks = usedTracksInRow;
                var lrNote = this.parseLimitedRandom();
                this._avoidTracks = prevAvoid;
                if (lrNote) {
                    if (lrNote.isMulti && lrNote.notes) {
                        // # 平移整个上一行 → 展开多键
                        for (var lri = 0; lri < lrNote.notes.length; lri++) {
                            var ln = lrNote.notes[lri];
                            if (ln && !row.some(function(n) { return n.track === ln.track && !n.isBigKey; })) {
                                row.push(ln);
                                usedTracksInRow.add(ln.track);
                            }
                        }
                    } else if (!row.some(function(n) { return n.track === lrNote.track && !n.isBigKey; })) {
                        row.push(lrNote);
                        usedTracksInRow.add(lrNote.track);
                    }
                    trailingConn = null;
                }
                if (this.position >= this.config.length) break;
                var nch = this.config[this.position];
                if (nch === '+' || nch === '~') continue;
                break;
            }

            // 普通 # 复制上一行（大键整键复制）
            if (ch === '#') {
                this.position++;
                var lastRowCopy = this.lastRowNotes && this.lastRowNotes.length > 0 ? this.lastRowNotes : null;
                var lastHasBigKey = false;
                if (lastRowCopy) {
                    for (var lbi = 0; lbi < lastRowCopy.length; lbi++) {
                        var lbc = lastRowCopy[lbi];
                        if (lbc && lbc.isBigKey && lbc.bigKeyTracks && lbc.bigKeyTracks.length > 1) { lastHasBigKey = true; break; }
                    }
                }
                if (lastRowCopy && lastHasBigKey) {
                    for (var lbi2 = 0; lbi2 < lastRowCopy.length; lbi2++) {
                        var prevN = lastRowCopy[lbi2];
                        if (!prevN) continue;
                        if (prevN.isBigKey && prevN.bigKeyTracks && prevN.bigKeyTracks.length > 1) {
                            var dupBk = row.some(function(n) { return n.isBigKey && n.bigKeyTracks && n.bigKeyTracks.length > 1 && n.bigKeyTracks.join(',') === prevN.bigKeyTracks.join(','); });
                            if (!dupBk) {
                                var newBk = this.createNote(prevN.track);
                                newBk.isBigKey = true;
                                newBk.bigKeyTracks = prevN.bigKeyTracks.slice();
                                row.push(newBk);
                                usedTracksInRow.add(newBk.track);
                            }
                        } else {
                            var tN = prevN.track;
                            if (tN >= 0 && !row.some(function(n) { return n.track === tN && !n.isBigKey; })) {
                                row.push(this.createNote(tN));
                                usedTracksInRow.add(tN);
                            }
                        }
                    }
                } else if (this.lastRowTracks.length > 1) {
                    for (var ti2 = 0; ti2 < this.lastRowTracks.length; ti2++) {
                        var t = this.lastRowTracks[ti2];
                        if (!row.some(function(n) { return n.track === t; })) {
                            row.push(this.createNote(t));
                            usedTracksInRow.add(t);
                        }
                    }
                } else {
                    var t = this.lastTrack;
                    if (t >= 0 && !row.some(function(n) { return n.track === t; })) {
                        row.push(this.createNote(t));
                        usedTracksInRow.add(t);
                    }
                }
                trailingConn = null;
                if (this.position >= this.config.length) break;
                var nch = this.config[this.position];
                if (nch === '+' || nch === '~') continue;
                break;
            }

            // 解析单个音符
            var note = null;
            if (ch === '!' || ch === '@') {
                if (this.position + 1 < this.config.length && this.config[this.position + 1] === '(') {
                    // 有限随机键 !(a,b) / @(a,b)
                    var prevAvoid = this._avoidTracks;
                    this._avoidTracks = usedTracksInRow;
                    note = this.parseLimitedRandom();
                    this._avoidTracks = prevAvoid;
                } else {
                    note = this.parseNote(usedTracksInRow);
                }
            } else if (ch === '(') {
                // 概率括号已被预处理器展开。裸括号(1,2)视为有限随机键无前缀形式
                var prevAvoid = this._avoidTracks;
                this._avoidTracks = usedTracksInRow;
                note = this.parseLimitedRandom();
                this._avoidTracks = prevAvoid;
            } else {
                note = this.parseNote();
            }

            var bigKeyTrailing = null;
            if (note) {
                if (note.isMulti && note.notes) {
                    for (var mi = 0; mi < note.notes.length; mi++) {
                        var multiNote = note.notes[mi];
                        if (multiNote && multiNote.track !== undefined) {
                            var multiIsDup = row.some(function(nn) { return nn.track === multiNote.track && !multiNote.isBigKey && !nn.isBigKey; });
                            if (!multiIsDup) {
                                row.push(multiNote);
                                usedTracksInRow.add(multiNote.track);
                            }
                        }
                    }
                } else {
                    if (this.position < this.config.length && this.config[this.position] === '~') {
                        // 大键 a~b
                        var bigKeyTracks = [note.track];
                        usedTracksInRow.add(note.track);
                        while (this.position < this.config.length && this.config[this.position] === '~') {
                            this.position++;
                            var rightNote = this.parseNote(usedTracksInRow);
                            if (rightNote) {
                                bigKeyTracks.push(rightNote.track);
                                usedTracksInRow.add(rightNote.track);
                            } else break;
                        }
                        var uniqueBigKeyTracks = [];
                        for (var bi_uniq = 0; bi_uniq < bigKeyTracks.length; bi_uniq++) {
                            if (uniqueBigKeyTracks.indexOf(bigKeyTracks[bi_uniq]) === -1) {
                                uniqueBigKeyTracks.push(bigKeyTracks[bi_uniq]);
                            }
                        }
                        bigKeyTracks = uniqueBigKeyTracks;
                        if (bigKeyTracks.length > 1) {
                            note.isBigKey = true;
                            note.bigKeyTracks = bigKeyTracks;
                            note.track = bigKeyTracks[0];
                        } else {
                            // ~ 后没有可合并的音符 → 行尾悬空 ~
                            bigKeyTrailing = '~';
                        }
                        var keyDup = false;
                        for (var bi = 0; bi < bigKeyTracks.length; bi++) {
                            if (row.some(function(nn) { return nn.track === bigKeyTracks[bi] && !nn.isBigKey; })) {
                                keyDup = true;
                                break;
                            }
                        }
                        if (!keyDup) {
                            row.push(note);
                            usedTracksInRow.add(note.track);
                        }
                    } else {
                        var isDup = row.some(function(nn) { return nn.track === note.track && !note.isBigKey && !nn.isBigKey; });
                        if (!isDup) {
                            row.push(note);
                            usedTracksInRow.add(note.track);
                        }
                    }
                }
            } else {
                break;
            }
            // 本轮消费了音符 → 清除行内连接符标记（大键悬空 ~ 除外）
            trailingConn = bigKeyTrailing || null;

            if (this.position >= this.config.length) break;
            ch = this.config[this.position];
            if (ch === '+' || ch === '~') continue;
            if (ch === '(') {
                continue;
            }
            break;
        }
        // 行尾连接符：跨轮/跨循环段合并（如 3+/12+ → 3+1, 2+1, 2+1…；~ 同理）
        if (trailingConn && row.length > 0 && !this._inTrailingMerge) {
            var conn = trailingConn;
            var lastTrackT = row[row.length - 1].track;
            this._inTrailingMerge = true;
            try {
                // 前进到下一轮/下一循环段的开头
                while (this.position < this.config.length &&
                       (this.config[this.position] === '/' || this.config[this.position] === ':' ||
                        this.config[this.position] === ';' || this.config[this.position] === '|')) {
                    this.position++;
                }
                if (this.position >= this.config.length) {
                    if (this._isLooping && this._loopStartPos >= 0) this.position = this._loopStartPos;
                    else this.position = 0;
                }
                var nextRow = this.parseRow();
                if (nextRow && nextRow.length > 0) {
                    var firstN = nextRow[0];
                    if (conn === '~') {
                        if (firstN.track === lastTrackT) {
                            row = [this.createNote(lastTrackT)];
                        } else {
                            var merged = this.createNote(lastTrackT);
                            merged.isBigKey = true;
                            merged.bigKeyTracks = [lastTrackT, firstN.track];
                            nextRow[0] = merged;
                            row = nextRow;
                        }
                    } else {
                        if (firstN.track !== lastTrackT) nextRow.unshift(this.createNote(lastTrackT));
                        row = nextRow;
                    }
                }
            } finally {
                this._inTrailingMerge = false;
            }
        }
        return row;
    }

    parseNote(avoidTracks) {
        if (this.position >= this.config.length) return null;
        var ch = this.config[this.position];
        // 概率括号已展开。裸括号(1,2)作为有限随机键（大键右侧等场景）
        if (ch === '(') {
            return this.parseLimitedRandom();
        }
        // !/@/#后跟(时：若是有限随机键交由parseRow处理，这里只处理普通!/@/#
        if ((ch === '!' || ch === '@' || ch === '#') && this.position + 1 < this.config.length && this.config[this.position + 1] === '(') {
            // 有限随机键由外层 parseRow 拦截，这里不应到达；回退为普通解析
        }
        if (ch === '!') {
            this.position++;
            var track;
            var maxAttempts = 50;
            var attempt = 0;
            do { track = this.randomTrack(); attempt++; }
            while (avoidTracks && avoidTracks.has(track) && attempt < maxAttempts);
            return this.createNote(track);
        }
        if (ch === '@') {
            this.position++;
            var track;
            var maxAttempts = 50;
            var attempt = 0;
            // @需要同时避免上一行全部轨道（含大键）和当前行avoidTracks
            var prevFull = this._getLastRowTracksFull();
            var excludedTracks = {};
            for (var ee = 0; ee < prevFull.length; ee++) excludedTracks[prevFull[ee]] = true;
            if (avoidTracks) {
                var ait = avoidTracks.values ? avoidTracks.values() : null;
                if (ait) {
                    var av = ait.next();
                    while (!av.done) { excludedTracks[av.value] = true; av = ait.next(); }
                } else {
                    avoidTracks.forEach(function(v) { excludedTracks[v] = true; });
                }
            }
            var excludedCount = Object.keys(excludedTracks).length;
            if (excludedCount >= this.keyCount) {
                excludedTracks = {};
                for (var ee2 = 0; ee2 < prevFull.length; ee2++) excludedTracks[prevFull[ee2]] = true;
                excludedCount = Object.keys(excludedTracks).length;
                if (excludedCount >= this.keyCount) {
                    excludedTracks = {};
                }
            }
            do {
                track = Math.floor(Math.random() * this.keyCount);
                attempt++;
            } while (excludedTracks[track] && attempt < maxAttempts);
            return this.createNote(track);
        }
        if (ch === '#') { this.position++; return this.createNote(this.lastTrack); }
        if (ch === '&') { this.position++; return this.createNote(this.keyCount - 1 - this.lastTrack); }
        if (ch >= '0' && ch <= '9') {
            var track = parseInt(ch) - 1;
            this.position++;
            if (track >= 0 && track < this.keyCount) return this.createNote(track);
            return null;
        }
        if (ch >= 'a' && ch <= 'z') {
            this.position++;
            var track = 8 + (ch.charCodeAt(0) - 96);
            if (track < this.keyCount) return this.createNote(track);
            return null;
        }
        if (ch >= 'A' && ch <= 'Z') {
            this.position++;
            var lower = ch.toLowerCase();
            var track = 8 + (lower.charCodeAt(0) - 96);
            if (track < this.keyCount) return this.createNote(track);
            return null;
        }
        if (ch === '~') { this.position++; return null; }
        this.position++;
        return null;
    }

    /**
     * 有限随机键解析：(a,b) 中随机选一个，支持 ! / @ / # 前缀及无前缀
     * @: 避免整个上一行和当前行已用轨道
     */
    parseLimitedRandom() {
        var mode = this.config[this.position];
        // 支持无前缀 "(" 形式
        if (mode === '(') {
            mode = '!';
            this.position++;
        } else {
            this.position++;
            this.position++;
        }
        var rawOptions = [];
        var numStr = '';
        var isNegative = false;
        while (this.position < this.config.length && this.config[this.position] !== ')') {
            var ch = this.config[this.position];
            if (ch === ',' || ch === '，') {
                if (numStr) {
                    var val = parseInt(numStr);
                    if (isNegative) val = -val;
                    rawOptions.push(val);
                    numStr = '';
                    isNegative = false;
                }
                this.position++;
                continue;
            }
            if (ch === '-') { isNegative = true; this.position++; continue; }
            if (ch >= '0' && ch <= '9') { numStr += ch; this.position++; continue; }
            // 字母 a~z 表示第10~35轨（仅对 !/@/无前缀 的绝对轨道选择生效，# 模式仍按数字偏移处理）
            if (mode !== '#' && ch >= 'a' && ch <= 'z') { rawOptions.push(9 + (ch.charCodeAt(0) - 96)); this.position++; continue; }
            if (mode !== '#' && ch >= 'A' && ch <= 'Z') { rawOptions.push(9 + (ch.toLowerCase().charCodeAt(0) - 96)); this.position++; continue; }
            this.position++;
        }
        if (numStr) {
            var val = parseInt(numStr);
            if (isNegative) val = -val;
            rawOptions.push(val);
        }
        if (this.position < this.config.length && this.config[this.position] === ')') this.position++;

        // # 模式：偏移量作用于整个上一行（平移整行，保留大键结构）
        if (mode === '#') {
            if (rawOptions.length === 0) return null;
            var delta = rawOptions[Math.floor(Math.random() * rawOptions.length)];
            var prevRow = this.lastRowNotes && this.lastRowNotes.length > 0 ? this.lastRowNotes : null;
            var prevHasBigKey = false;
            if (prevRow) {
                for (var bkChk = 0; bkChk < prevRow.length; bkChk++) {
                    var bkC = prevRow[bkChk];
                    if (bkC && bkC.isBigKey && bkC.bigKeyTracks && bkC.bigKeyTracks.length > 1) { prevHasBigKey = true; break; }
                }
            }
            // 上一行有多键或大键 → 逐音符平移，保留大键结构
            if (prevRow && (prevHasBigKey || (this.lastRowTracks && this.lastRowTracks.length > 1))) {
                var shiftedNotes = [];
                var uniqShift = {};
                for (var si = 0; si < prevRow.length; si++) {
                    var prevNote = prevRow[si];
                    if (!prevNote) continue;
                    if (prevNote.isBigKey && prevNote.bigKeyTracks && prevNote.bigKeyTracks.length > 1) {
                        var shiftedBig = [];
                        var bigUniq = {};
                        for (var bsi = 0; bsi < prevNote.bigKeyTracks.length; bsi++) {
                            var nbt = (prevNote.bigKeyTracks[bsi] + delta + this.keyCount) % this.keyCount;
                            if (!bigUniq[nbt]) { bigUniq[nbt] = true; shiftedBig.push(nbt); }
                        }
                        if (shiftedBig.length > 1) {
                            var bkNote = this.createNote(shiftedBig[0]);
                            bkNote.isBigKey = true;
                            bkNote.bigKeyTracks = shiftedBig;
                            shiftedNotes.push(bkNote);
                        } else if (shiftedBig.length === 1) {
                            var sNote = this.createNote(shiftedBig[0]);
                            if (sNote) shiftedNotes.push(sNote);
                        }
                    } else {
                        var newTrack = (prevNote.track + delta + this.keyCount) % this.keyCount;
                        if (!uniqShift[newTrack]) {
                            uniqShift[newTrack] = true;
                            var nn = this.createNote(newTrack);
                            if (nn) shiftedNotes.push(nn);
                        }
                    }
                }
                if (shiftedNotes.length === 0) return null;
                if (shiftedNotes.length === 1) return shiftedNotes[0];
                return { isMulti: true, tracks: shiftedNotes.map(function(n){ return n.track; }), notes: shiftedNotes };
            }
            // 上一行只有一个轨道 → 单键平移（兼容旧行为）
            var absTrack = (this.lastTrack + delta + this.keyCount) % this.keyCount;
            return this.createNote(absTrack);
        }

        // !/@ 模式：转绝对轨道索引
        var options = [];
        for (var ro = 0; ro < rawOptions.length; ro++) {
            var rel = rawOptions[ro];
            if (rel < 0) {
                var absTrack2 = (this.lastTrack + rel + this.keyCount) % this.keyCount;
                options.push(absTrack2);
            } else {
                if (rel >= 1 && rel <= this.keyCount) options.push(rel - 1);
            }
        }
        if (options.length === 0) return null;
        var track;
        if (mode === '!') {
            // 避免当前行已用轨道（如果通过 _avoidTracks 传入）
            var avoidSet = this._avoidTracks;
            var filtered = null;
            if (avoidSet && avoidSet.size > 0) {
                filtered = options.filter(function(t) { return !avoidSet.has(t); });
            }
            if (!filtered || filtered.length === 0) filtered = options;
            track = filtered[Math.floor(Math.random() * filtered.length)];
        } else if (mode === '@') {
            // 避免整个上一行（含大键全部轨道）+ 当前行已用轨道
            var prevFullA = this._getLastRowTracksFull();
            var excluded = {};
            for (var ei = 0; ei < prevFullA.length; ei++) excluded[prevFullA[ei]] = true;
            if (this._avoidTracks) {
                this._avoidTracks.forEach(function(v) { excluded[v] = true; });
            }
            var filtered2 = options.filter(function(t) { return !excluded[t]; });
            if (filtered2.length === 0) filtered2 = options;
            track = filtered2[Math.floor(Math.random() * filtered2.length)];
        } else {
            track = options[Math.floor(Math.random() * options.length)];
        }
        return this.createNote(track);
    }

    createNote(track) {
        if (track < 0 || track >= this.keyCount) return null;
        return { track: track, isBigKey: false, bigKeyTracks: null };
    }

    randomTrack() { return Math.floor(Math.random() * this.keyCount); }

    randomTrackDifferent() {
        var track = this.randomTrack();
        var prevFull = this._getLastRowTracksFull();
        var maxAttempts = 50;
        var attempt = 0;
        while (prevFull.indexOf(track) !== -1 && attempt < maxAttempts) {
            track = (track + 1) % this.keyCount;
            attempt++;
        }
        return track;
    }

    /** 获取上一行的完整轨道列表（展开大键的全部轨道并去重）；无 lastRowNotes 时回退 lastRowTracks */
    _getLastRowTracksFull() {
        var tracks = [];
        var seen = {};
        var notes = this.lastRowNotes && this.lastRowNotes.length > 0 ? this.lastRowNotes : null;
        if (notes) {
            for (var i = 0; i < notes.length; i++) {
                var n = notes[i];
                if (!n) continue;
                if (n.isBigKey && n.bigKeyTracks && n.bigKeyTracks.length > 1) {
                    for (var b = 0; b < n.bigKeyTracks.length; b++) {
                        if (!seen[n.bigKeyTracks[b]]) { seen[n.bigKeyTracks[b]] = true; tracks.push(n.bigKeyTracks[b]); }
                    }
                } else if (n.track !== undefined) {
                    if (!seen[n.track]) { seen[n.track] = true; tracks.push(n.track); }
                }
            }
        }
        if (tracks.length === 0 && this.lastRowTracks) {
            for (var ti = 0; ti < this.lastRowTracks.length; ti++) {
                if (!seen[this.lastRowTracks[ti]]) { seen[this.lastRowTracks[ti]] = true; tracks.push(this.lastRowTracks[ti]); }
            }
        }
        return tracks;
    }

    // ============================================================
    // 概率预处理器：字符串级展开
    // ============================================================

    /** 判断字符串是否为概率格式（0或.开头的小数） */
    static isProbStr(s) {
        return /^\s*(0?\.\d+)\s*$/.test(s);
    }

    /** 按顶层逗号分割（括号深度感知） */
    static splitTopLevel(str) {
        var parts = [];
        var depth = 0;
        var cur = '';
        for (var i = 0; i < str.length; i++) {
            var c = str[i];
            if (c === '(') depth++;
            else if (c === ')') depth--;
            if ((c === ',' || c === '，') && depth === 0) {
                parts.push(cur);
                cur = '';
                continue;
            }
            cur += c;
        }
        parts.push(cur);
        return parts;
    }

    /**
     * 解析概率分支。
     * firstProb: 首个概率字符串（如 "0.5"）
     * rest: 剩余内容（首个逗号之后的全部）
     * 支持：
     *   (0.5,cfg) → 0.5 概率 cfg，0.5 概率空
     *   (0.4,@,!) → 0.4 @，0.6 !
     *   (0.4,@,0.3,!,0.5,1,0.1,2) → 概率配对
     */
    static parseBranches(firstProb, rest) {
        var parts = NoteParser.splitTopLevel(rest);
        var branches = [];
        var currentProb = parseFloat(firstProb.trim());
        var lastWasProb = true;
        for (var i = 0; i < parts.length; i++) {
            var p = parts[i].trim();
            if (NoteParser.isProbStr(p)) {
                currentProb = parseFloat(p);
                lastWasProb = true;
            } else {
                if (lastWasProb) {
                    // 标准概率-配置对
                    if (currentProb > 0) branches.push({ prob: currentProb, config: p });
                    lastWasProb = false;
                } else {
                    // 缺省概率配置（如 (0.4,@,!) 中的 !）
                    var used = 0;
                    for (var bi = 0; bi < branches.length; bi++) used += branches[bi].prob;
                    var rem = 1 - used;
                    if (rem > 0) branches.push({ prob: rem, config: p });
                }
            }
        }
        // 概率和归一化/截断
        var total = 0;
        for (var bi2 = 0; bi2 < branches.length; bi2++) total += branches[bi2].prob;
        if (total > 1) {
            var cutoff = 0;
            for (var bi3 = 0; bi3 < branches.length; bi3++) {
                if (cutoff + branches[bi3].prob > 1) branches[bi3].prob = Math.max(0, 1 - cutoff);
                cutoff += branches[bi3].prob;
                if (cutoff >= 1) break;
            }
        }
        return branches;
    }

    /** 按概率随机选择一个分支，返回配置字符串；概率和 < 1 时返回 null（跳过） */
    static chooseBranch(branches) {
        var total = 0;
        for (var i = 0; i < branches.length; i++) total += branches[i].prob;
        if (total <= 0) return null;
        var rand = Math.random();
        var cum = 0;
        for (var i = 0; i < branches.length; i++) {
            if (branches[i].prob <= 0) continue;
            cum += branches[i].prob;
            if (rand <= cum) return branches[i].config;
        }
        return null; // 落在 gap（概率和<1）→ 不出现
    }

    /**
     * 展开所有概率括号为普通字符串。
     * (0.x,CONFIG) → 命中：CONFIG（递归展开），未命中：删除括号
     * (p1,cfg1,p2,cfg2,...) → 选一个分支
     * 非概率括号（如 !(1,2) 的 (1,2) 内部）保留原样
     */
    static expandAllProbabilities(config, depth) {
        depth = depth || 0;
        if (depth > 30) return config;
        var result = '';
        var i = 0;
        while (i < config.length) {
            var ch = config[i];
            if (ch === '(') {
                // 查找匹配的右括号（深度感知）
                var d = 0;
                var r = i;
                var found = false;
                while (r < config.length) {
                    if (config[r] === '(') d++;
                    else if (config[r] === ')') {
                        d--;
                        if (d === 0) { found = true; break; }
                    }
                    r++;
                }
                if (!found) { result += ch; i++; continue; }
                var inner = config.substring(i + 1, r);
                // 找顶层第一个逗号
                var d2 = 0;
                var commaIdx = -1;
                for (var scan = 0; scan < inner.length; scan++) {
                    if (inner[scan] === '(') d2++;
                    else if (inner[scan] === ')') d2--;
                    else if ((inner[scan] === ',' || inner[scan] === '，') && d2 === 0) {
                        commaIdx = scan;
                        break;
                    }
                }
                var isProb = false;
                var firstPart = '';
                if (commaIdx >= 0) {
                    firstPart = inner.substring(0, commaIdx).trim();
                    isProb = NoteParser.isProbStr(firstPart);
                }
                if (isProb) {
                    var prob = parseFloat(firstPart);
                    if (prob <= 0) {
                        // 概率0直接跳过（相当于删除）
                        i = r + 1;
                        continue;
                    }
                    var rest = inner.substring(commaIdx + 1);
                    var branches = NoteParser.parseBranches(firstPart, rest);
                    var selected = NoteParser.chooseBranch(branches);
                    if (selected !== null) {
                        result += NoteParser.expandAllProbabilities(selected, depth + 1);
                    }
                    i = r + 1;
                    continue;
                }
                // 非概率括号 → 保留
                result += ch;
                i++;
            } else {
                result += ch;
                i++;
            }
        }
        return result;
    }

    static checkBigKeyProtection(bigKeyTracks, pressedTrack, nextRowTracks) {
        return bigKeyTracks.indexOf(pressedTrack) !== -1 && nextRowTracks.indexOf(pressedTrack) !== -1;
    }
}

class PatternValidator {
    static minTracksNeeded(config) {
        var maxTrack = 0;
        var configOnly = config.replace(/\d+k\s*:/g, '');
        // 跳过概率括号内的概率数字（0或.开头的小数），使用括号深度计数
        var depth = 0;
        var inDecimal = false;
        for (var i = 0; i < configOnly.length; i++) {
            var ch = configOnly[i];
            if (ch === '(') { depth++; inDecimal = false; continue; }
            if (ch === ')') { depth = Math.max(0, depth - 1); inDecimal = false; continue; }
            if (ch === ',' || ch === '，') { inDecimal = false; continue; }
            if (depth > 0) {
                // 括号内：跳过概率小数
                if (ch === '0' || ch === '.') { inDecimal = true; continue; }
                if (inDecimal) {
                    if (ch >= '0' && ch <= '9') continue;
                    if (ch === '.') continue;
                    inDecimal = false;
                }
            }
            if (ch >= '1' && ch <= '9') { var t = parseInt(ch); if (t > maxTrack) maxTrack = t; }
            if (ch >= 'a' && ch <= 'z') { var t = 9 + (ch.charCodeAt(0) - 96); if (t > maxTrack) maxTrack = t; }
        }
        var maxExclaimInRow = 0, curExclaim = 0;
        for (var i = 0; i < config.length; i++) {
            var ch = config[i];
            if (ch === '!') curExclaim++;
            else if (ch === '+') { if (curExclaim > maxExclaimInRow) maxExclaimInRow = curExclaim; curExclaim = 0; }
            else { if (curExclaim > maxExclaimInRow) maxExclaimInRow = curExclaim; curExclaim = 0; }
        }
        if (curExclaim > maxExclaimInRow) maxExclaimInRow = curExclaim;
        if (maxExclaimInRow > maxTrack) maxTrack = maxExclaimInRow;
        return maxTrack;
    }

    static sanitizeConfig(config) {
        if (!config) return '';
        // 全角→半角
        var fullToHalf = {
            '０':'0','１':'1','２':'2','３':'3','４':'4','５':'5','６':'6','７':'7','８':'8','９':'9',
            '！':'!','＠':'@','＃':'#','＆':'&','（':'(','）':')','～':'~','＋':'+','，':',','；':';','：':':','｜':'|'
        };
        var arr = config.split('');
        for (var fi = 0; fi < arr.length; fi++) {
            if (fullToHalf[arr[fi]] !== undefined) arr[fi] = fullToHalf[arr[fi]];
        }
        var converted = arr.join('');
        var result = '';
        for (var i = 0; i < converted.length; i++) {
            var ch = converted[i];
            if ((ch >= '0' && ch <= '9') || ch === '!' || ch === '@' || ch === '#' || ch === '&' ||
                ch === '+' || ch === '-' || ch === '%' || ch === '*' ||
                ch === '~' || ch === '(' || ch === ')' || ch === ',' ||
                ch === ':' || ch === ';' || ch === '|' || ch === '/' ||
                (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                ch === '，' || ch === '！' || ch === '.' || ch === ' ') result += ch;
        }
        result = result.replace(/！/g, '!');
        result = result.replace(/，/g, ',');
        return result;
    }

    static hasDuplicateInRow(config) {
        var lines = config.split('+');
        var seen = new Set();
        for (var item of lines) { if (item >= '0' && item <= '9') { if (seen.has(item)) return true; seen.add(item); } }
        return false;
    }

    static getKeyConfig(config, currentKeyCount) {
        var sections = config.split(/[;|]/);
        var bestConfig = config, bestKey = 0;
        for (var si = 0; si < sections.length; si++) {
            var section = sections[si].trim();
            if (!section) continue;
            var match = section.match(/(\d+)k\s*:(.*)/);
            if (match) {
                var keyNum = parseInt(match[1]), partConfig = match[2];
                if (keyNum === currentKeyCount) return partConfig;
                if (keyNum < currentKeyCount && keyNum > bestKey) { bestKey = keyNum; bestConfig = partConfig; }
            } else {
                var trimmed = section.replace(/^:/, '').trim();
                if (trimmed.length > 0) bestConfig = trimmed;
            }
        }
        return bestConfig;
    }
}
