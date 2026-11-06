/**
 * 存储系统 - 使用 localStorage 同步持久化（file:// 协议下可靠）
 * IndexedDB 作为兼容旧数据的辅助读取来源
 */

class Storage {
    static PREFIX = 'eatcat_v2_';
    static KEYS = { SETTINGS: 'settings', PATTERNS: 'patterns' };
    
    // ====== localStorage 同步存储（主存储） ======
    static _lsAvailable = (function() {
        try {
            var k = '__eatcat_test__';
            localStorage.setItem(k, '1');
            localStorage.removeItem(k);
            return true;
        } catch(e) { return false; }
    })();

    static set(key, value) {
        Storage._cache[key] = value;
        if (Storage._lsAvailable) {
            try {
                localStorage.setItem(Storage.PREFIX + key, JSON.stringify(value));
            } catch(e) {
                // 超出 localStorage 容量时，尝试降级为 IndexedDB
                Storage._fallbackSetIDB(key, value);
            }
        } else {
            Storage._fallbackSetIDB(key, value);
        }
    }

    static get(key) {
        if (Storage._cache && Storage._cache[key] !== undefined) return Storage._cache[key];
        if (Storage._lsAvailable) {
            try {
                var raw = localStorage.getItem(Storage.PREFIX + key);
                if (raw !== null) {
                    var val = JSON.parse(raw);
                    Storage._cache[key] = val;
                    return val;
                }
            } catch(e) { console.error(e); }
        }
        return null;
    }

    static remove(key) {
        delete Storage._cache[key];
        if (Storage._lsAvailable) {
            try { localStorage.removeItem(Storage.PREFIX + key); } catch(e) {}
        }
        // 同时删除 IndexedDB 中的对应数据，避免刷新后 _migrateFromIDB 把已删除的旧值恢复回来
        try {
            var r = indexedDB.open('EatCatStorage', 1);
            r.onupgradeneeded = function(e) {
                var db = e.target.result;
                if (!db.objectStoreNames.contains('kv'))
                    db.createObjectStore('kv', { keyPath: 'k' });
            };
            r.onsuccess = function(e) {
                try {
                    var db = e.target.result;
                    var tx = db.transaction('kv', 'readwrite');
                    tx.objectStore('kv').delete(key);
                } catch(ex) {}
            };
        } catch(e) {}
    }

    static _cache = {};

    static _fallbackSetIDB(key, value) {
        try {
            var r = indexedDB.open('EatCatStorage', 1);
            r.onupgradeneeded = function(e) {
                var db = e.target.result;
                if (!db.objectStoreNames.contains('kv'))
                    db.createObjectStore('kv', { keyPath: 'k' });
            };
            r.onsuccess = function(e) {
                try {
                    var tx = e.target.result.transaction('kv', 'readwrite');
                    tx.objectStore('kv').put({ k: key, v: value });
                } catch(ex) {}
            };
        } catch(e) {}
    }

    /** 合并旧 IndexedDB 数据到 localStorage（一次性迁移） */
    static _migrateFromIDB(callback) {
        try {
            var req = indexedDB.open('EatCatStorage', 1);
            req.onupgradeneeded = function(e) {
                var db = e.target.result;
                if (!db.objectStoreNames.contains('kv'))
                    db.createObjectStore('kv', { keyPath: 'k' });
            };
            req.onsuccess = function(e) {
                var db = e.target.result;
                try {
                    var tx = db.transaction('kv', 'readonly');
                    var cur = tx.objectStore('kv').openCursor();
            cur.onsuccess = function(ev) {
                var c = ev.target.result;
                if (c) {
                    var k = c.value.k;
                    var v = c.value.v;
                    // 只在 localStorage 没有该 key 时才迁移（避免旧 IndexedDB 数据覆盖新保存的值）
                    var hasLocal = false;
                    if (Storage._lsAvailable) {
                        var raw = localStorage.getItem(Storage.PREFIX + k);
                        if (raw === null) {
                            try {
                                localStorage.setItem(Storage.PREFIX + k, JSON.stringify(v));
                            } catch(ex) {}
                        } else {
                            hasLocal = true;
                        }
                    }
                    if (!hasLocal) Storage._cache[k] = v;
                    c.continue();
                } else {
                    if (callback) callback();
                }
            };
                    cur.onerror = function() { if (callback) callback(); };
                } catch(ex) { if (callback) callback(); }
            };
            req.onerror = function() { if (callback) callback(); };
        } catch(e) { if (callback) callback(); }
    }

    static _migrated = false;
    static _migrateDone = {};

    static getSettingsSync(key) { return Storage._cache[key] || Storage.get(key); }

    static loadSettings() {
        var defaults = {
            keyCount: 4, keyMapping: 'dfjk', timeLimit: 20, notePattern: '!',
            hideComment: false, soundEnabled: true, verticalJudge: true,
            gameMode: 'normal', centerWidth: 100, practiceMode: false,
            themeColor: 'light', keyMappingByKey: {},
            keybindQ: 'q', keybindR: 'r', keybindF: 'f', keybindM: ''
        };
        var saved = Storage.get(Storage.KEYS.SETTINGS);
        if (saved) for (var k in saved) defaults[k] = saved[k];
        // 兼容旧数据：新版本无 themeColor 但旧版本有 darkMode=true → 默认深色
        if (!defaults.themeColor) {
            defaults.themeColor = (saved && saved.darkMode) ? 'dark' : 'light';
        }
        return defaults;
    }
    static saveSettings(settings) { Storage.set(Storage.KEYS.SETTINGS, settings); }

    static loadPatterns() { return Storage.get(Storage.KEYS.PATTERNS) || {}; }
    static savePattern(name, pattern, keyCount) {
        var p = Storage.loadPatterns(); p[name] = { pattern, keyCount }; Storage.set(Storage.KEYS.PATTERNS, p);
    }
    static editPattern(on, nn, p, kc) {
        var p2 = Storage.loadPatterns(); if (on !== nn) delete p2[on]; p2[nn] = { pattern: p, keyCount: kc }; Storage.set(Storage.KEYS.PATTERNS, p2);
    }
    static deletePattern(name) { var p = Storage.loadPatterns(); delete p[name]; Storage.set(Storage.KEYS.PATTERNS, p); }
    static importPatterns(j) {
        try { var d = JSON.parse(j); if (d.patterns) { Storage.set(Storage.KEYS.PATTERNS, d.patterns); return true; } else if (typeof d === 'object') { Storage.set(Storage.KEYS.PATTERNS, d); return true; } return false; } catch(e) { return false; }
    }
    static exportPatterns() { return JSON.stringify({ version: 2, patterns: Storage.loadPatterns() }, null, 2); }
    static getDefaultMapping(kc) {
        var d = {1:'a~s~d~f~j~k~l~;',2:'a~s~d~fj~k~l~;',3:'fjk',4:'dfjk',5:'dfjkl',6:'sdfjkl',7:'sdfjkl;',8:'asdfjkl;',9:'asdf jkl;',10:'asdfv jkl;'};
        return d[kc] || 'dfjk';
    }

    // 初始化：从 localStorage 同步恢复 + 异步从 IndexedDB 迁移旧数据
    // callback：同步恢复完成（设置立即可用）
    // migrateCallback：IndexedDB 迁移完成后（用于恢复降级存储的大型数据如自定义图片）
    static init(callback, migrateCallback) {
        // 从 localStorage 同步加载所有已存的 key（立即恢复，不等待异步）
        if (Storage._lsAvailable) {
            try {
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.indexOf(Storage.PREFIX) === 0) {
                        var realKey = k.substring(Storage.PREFIX.length);
                        var raw = localStorage.getItem(k);
                        if (raw !== null) {
                            try {
                                Storage._cache[realKey] = JSON.parse(raw);
                            } catch(e) {}
                        }
                    }
                }
            } catch(e) {}
        }

        // 立即初始化历史成绩（localStorage 同步恢复）
        var histRaw = null;
        if (Storage._lsAvailable) {
            try { histRaw = localStorage.getItem(Storage._HISTORY_KEY); } catch(e) {}
        }
        if (histRaw) {
            try {
                var hist = JSON.parse(histRaw);
                for (var hk in hist) Storage._bc[hk] = hist[hk];
            } catch(e) {}
        }

        // 同步调 callback，确保设置立即恢复
        if (callback) callback();

        // 从 IndexedDB 迁移旧数据（异步，后台进行，不阻塞）
        Storage._migrateFromIDB(function() {
            // 从旧 cookie 迁移（兼容更早版本）
            try {
                var old = document.cookie.match(/eatcat_v2_settings=([^;]*)/);
                if (old && old[1]) {
                    var data = JSON.parse(decodeURIComponent(old[1]));
                    if (!Storage._cache['settings']) {
                        Storage._cache['settings'] = data;
                        Storage.set('settings', data);
                    }
                }
            } catch(e) {}
            // 异步迁移历史成绩（从旧 IndexedDB EatCatScores）
            Storage._initHistory(function() {
                // 迁移完成后通知，用于恢复降级存储在 IndexedDB 的大型数据（如自定义图片）
                if (migrateCallback) migrateCallback();
            });
        });
    }

    // ====== 历史成绩（localStorage 同步存储 + 旧 IndexedDB 迁移） ======
    static _bc = {};
    static _HISTORY_KEY = 'eatcat_v2_history';

    static _initHistory(callback) {
        // 先从 localStorage 同步恢复历史成绩
        if (Storage._lsAvailable) {
            try {
                var raw = localStorage.getItem(Storage._HISTORY_KEY);
                if (raw) {
                    var hist = JSON.parse(raw);
                    for (var hk in hist) Storage._bc[hk] = hist[hk];
                }
            } catch(e) {}
        }
        // 迁移旧 IndexedDB 历史成绩（EatCatScores）
        try {
            var r = indexedDB.open('EatCatScores', 1);
            r.onupgradeneeded = function(e) {
                if (!e.target.result.objectStoreNames.contains('b'))
                    e.target.result.createObjectStore('b', { keyPath: 'k' });
            };
            r.onsuccess = function(e) {
                var db = e.target.result;
                try {
                    var tx = db.transaction('b', 'readonly');
                    var req = tx.objectStore('b').openCursor();
                    req.onsuccess = function(ev) {
                        var c = ev.target.result;
                        if (c) {
                            var ck = c.value.k;
                            // 只迁移更高分的记录
                            if (!Storage._bc[ck] || c.value.score > Storage._bc[ck].score) {
                                Storage._bc[ck] = c.value;
                            }
                            c.continue();
                        } else {
                            Storage._persistHistorySync();
                            if (callback) callback();
                        }
                    };
                    req.onerror = function() { Storage._persistHistorySync(); if (callback) callback(); };
                } catch(ex) { Storage._persistHistorySync(); if (callback) callback(); }
            };
            r.onerror = function() { Storage._persistHistorySync(); if (callback) callback(); };
        } catch(e) { Storage._persistHistorySync(); if (callback) callback(); }
    }

    /** 同步将历史成绩写入 localStorage */
    static _persistHistorySync() {
        if (!Storage._lsAvailable) return;
        try {
            localStorage.setItem(Storage._HISTORY_KEY, JSON.stringify(Storage._bc));
        } catch(e) {}
    }

    static saveHistoryRecord(r) {
        var ck = r.keyCount + 'k_' + (r.notePattern || '').substring(0, 20) + '_' + r.timeLimit + 's_' + r.gameMode;
        var isDead = (r.gameMode === 'protected') ? false : (r.isDead || false);
        var ex = Storage._bc[ck];
        if (!ex || r.score > ex.score) {
            Storage._bc[ck] = { k: ck, score: r.score, cps: r.cps, keyHits: r.keyHits || 0, rowHits: r.rowHits || 0, errorCount: r.errorCount || 0, date: r.date || new Date().toLocaleString(), isDead: isDead, actualTime: r.actualTime || 0 };
        }
        // 同步写入 localStorage，确保刷新后不丢失
        Storage._persistHistorySync();
        // 异步辅助写入 IndexedDB（兼容旧数据迁移）
        try {
            var r2 = indexedDB.open('EatCatScores', 1);
            r2.onupgradeneeded = function(e) {
                if (!e.target.result.objectStoreNames.contains('b'))
                    e.target.result.createObjectStore('b', { keyPath: 'k' });
            };
            r2.onsuccess = function(e) {
                var tx = e.target.result.transaction('b', 'readwrite');
                tx.objectStore('b').put(Storage._bc[ck]);
            };
        } catch(ev) {}
    }
    static getBestRecord(ck) { 
        if (!Storage._bc[ck] && Storage._lsAvailable) {
            try {
                var raw = localStorage.getItem(Storage._HISTORY_KEY);
                if (raw) {
                    var hist = JSON.parse(raw);
                    if (hist[ck]) Storage._bc[ck] = hist[ck];
                }
            } catch(e) {}
        }
        return Storage._bc[ck] || null; 
    }
    static getAllBestRecords() { return Storage._bc; }
}
