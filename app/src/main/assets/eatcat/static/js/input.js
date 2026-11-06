/**
 * 输入处理 - 键盘/触摸输入管理，绑多key支持
 */

class Input {
    static keyMapping = {};       // { keyChar: trackIndex } 单键映射
    static multiKeyMapping = {};  // { trackIndex: [keyChars] } 绑多key映射
    static isInitialized = false;

    static init() {
        if (Input.isInitialized) return;
        Input.isInitialized = true;

        if (typeof isDesktop === 'undefined') {
            window.isDesktop = !navigator.userAgent.match(/(ipad|iphone|ipod|android|windows phone)/i);
        }

        Input.setKeyMapping(null, 4);
    }

    /**
     * 设置键位映射
     * ~规则：~连接前一个字符和后一个字符到同一轨道
     * 无~的相邻字符各自在不同轨道
     * 例如 s~dfjk~l → s=0, d=0, f=1, j=2, k=3, l=3
     */
    static setKeyMapping(mappingStr, keyCount) {
        Input.keyMapping = {};
        Input.multiKeyMapping = {};

        if (!mappingStr) {
            mappingStr = Input.getDefaultMapping(keyCount);
        }

        var usedKeys = {};
        var trackIndex = 0;
        var i = 0;

        // 先过滤：保留字母、~、以及不需要Shift就能按出的符号/数字/空格
        var allowedChars = 'abcdefghijklmnopqrstuvwxyz0123456789;\'\\,./[]=-` ';
        var filtered = '';
        for (var si = 0; si < mappingStr.length; si++) {
            var c = mappingStr[si];
            if (c === '~') filtered += '~';
            else if (c >= 'a' && c <= 'z') filtered += c;
            else if (c >= 'A' && c <= 'Z') filtered += c.toLowerCase();
            else if (allowedChars.indexOf(c.toLowerCase()) !== -1) filtered += c.toLowerCase();
        }

        // 去除首尾~~
        while (filtered[0] === '~') filtered = filtered.substring(1);
        while (filtered[filtered.length-1] === '~') filtered = filtered.substring(0, filtered.length-1);

        // 遍历字符，按~规则分配轨道（空格也是可绑定的按键，如 9k:asdf jkl; 中空格为第5轨）
        while (i < filtered.length && trackIndex < keyCount) {
            var ch = filtered[i];
            if (ch === '~') { i++; continue; } // 连续~~跳过

            // 当前字符加入当前轨道
            if (!usedKeys[ch]) {
                Input.keyMapping[ch] = trackIndex;
                usedKeys[ch] = true;
                if (!Input.multiKeyMapping[trackIndex]) Input.multiKeyMapping[trackIndex] = [];
                Input.multiKeyMapping[trackIndex].push(ch);
            }

            // 检查下一个字符是否是~
            if (i + 1 < filtered.length && filtered[i + 1] === '~') {
                // ~后的字符也在同一轨道：跳过~让下一个字符加入同一trackIndex
                i += 2;
                // 但不要增加trackIndex
            } else {
                i++;
                trackIndex++;
            }
        }

        // 补全缺少的轨道
        var defLetters = 'abcdefghijklmnopqrstuvwxyz';
        while (trackIndex < keyCount) {
            for (var di = 0; di < defLetters.length; di++) {
                var l = defLetters[di];
                if (!usedKeys[l]) {
                    Input.keyMapping[l] = trackIndex;
                    Input.multiKeyMapping[trackIndex] = [l];
                    usedKeys[l] = true;
                    trackIndex++;
                    break;
                }
            }
        }
    }

    static getDefaultMapping(keyCount) {
        var defaults = {
            1: 'a~s~d~f~j~k~l~;',
            2: 'a~s~d~fj~k~l~;',
            3: 'fjk',
            4: 'dfjk',
            5: 'dfjkl',
            6: 'sdfjkl',
            7: 'sdfjkl;',
            8: 'asdfjkl;',
            9: 'asdf jkl;',
            10: 'asdfv jkl;'
        };
        return defaults[keyCount] || 'dfjk';
    }

    static handleKeyDown(key, e) {
        if (typeof isplaying === 'function' && !isplaying()) return;
        var trackIndex = Input.keyMapping[key];
        if (trackIndex !== undefined) {
            e.preventDefault();
            if (typeof Game !== 'undefined' && Game.handleKeyClick) {
                Game.handleKeyClick(trackIndex + 1);
            } else if (typeof click === 'function') {
                click(trackIndex + 1);
            }
            return;
        }
        if (key === 'r') {
            var sl = document.getElementById('GameScoreLayer');
            if (sl && sl.style.display !== 'none') {
                if (typeof gameRestart === 'function') gameRestart();
                if (typeof replayBtn === 'function') replayBtn();
                sl.style.display = 'none';
            }
        }
    }

    static extractMapping(mappingStr, keyCount) {
        if (!mappingStr) return '';
        var allowedChars = 'abcdefghijklmnopqrstuvwxyz0123456789;\'\\,./[]=-` ';
        var result = '';
        for (var i = 0; i < mappingStr.length; i++) {
            var ch = mappingStr[i].toLowerCase();
            if (ch === '~' || allowedChars.indexOf(ch) !== -1) {
                result += ch;
            }
        }
        return result;
    }

    static getKeysForTrack(trackIndex) {
        return Input.multiKeyMapping[trackIndex] || [];
    }

    static getTrackForKey(key) {
        return Input.keyMapping[key.toLowerCase()];
    }
}
